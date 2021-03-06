/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.wyb.sows.server;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wyb.sows.server.security.AuthHandler;
import org.wyb.sows.websocket.SowsAuthHelper;
import org.wyb.sows.websocket.SowsConnectCmd;
import org.wyb.sows.websocket.SowsStatusType;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;

/**
 * Handles handshakes and messages
 */
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

	final Logger logger = LoggerFactory.getLogger(WebSocketServerHandler.class);

	private static final String WEBSOCKET_PATH = "/websocket";

	private WebSocketServerHandshaker handshaker;

	private volatile Channel outboundChannel;

	private boolean remoteConnect = false;

	private final AuthHandler authHandler;

	public WebSocketServerHandler(AuthHandler authHandler) {
		this.authHandler = authHandler;
	}

	@Override
	public void channelRead0(ChannelHandlerContext ctx, Object msg) {
		if (msg instanceof FullHttpRequest) {
			handleHttpRequest(ctx, (FullHttpRequest) msg);
		} else if (msg instanceof WebSocketFrame) {
			handleWebSocketFrame(ctx, (WebSocketFrame) msg);
		}
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		ctx.flush();
	}

	private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
		// Handle a bad request.
		if (!req.getDecoderResult().isSuccess()) {
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
			return;
		}

		// Allow only GET methods.
		if (req.getMethod() != GET) {
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
			return;
		}

		// Send the demo page and favicon.ico
		if ("/".equals(req.getUri())) {
			ByteBuf content = WebSocketServerIndexPage.getContent(getWebSocketLocation(req));
			FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, OK, content);

			res.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
			HttpHeaders.setContentLength(res, content.readableBytes());

			sendHttpResponse(ctx, req, res);
			return;
		}
		if ("/favicon.ico".equals(req.getUri())) {
			FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND);
			sendHttpResponse(ctx, req, res);
			return;
		}

		// check auth header
		if (req.headers().contains(SowsAuthHelper.HEADER_SOWS_USER)
				&& req.headers().contains(SowsAuthHelper.HEADER_SOWS_TOKEN)
				&& req.headers().contains(SowsAuthHelper.HEADER_SOWS_SEED)) {
			String user = req.headers().get(SowsAuthHelper.HEADER_SOWS_USER);
			String token = req.headers().get(SowsAuthHelper.HEADER_SOWS_TOKEN);
			String seed = req.headers().get(SowsAuthHelper.HEADER_SOWS_SEED);
			if (authHandler.login(user, token, seed) != AuthHandler.OK) {
				FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, UNAUTHORIZED);
				sendHttpResponse(ctx, req, res);
				return;
			}

		} else {
			FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, UNAUTHORIZED);
			sendHttpResponse(ctx, req, res);
			return;
		}
		// Handshake
		WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(getWebSocketLocation(req),
				null, true);
		handshaker = wsFactory.newHandshaker(req);
		if (handshaker == null) {
			WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
		} else {
			handshaker.handshake(ctx.channel(), req);
		}
	}

	private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {

		// Check for closing frame
		if (frame instanceof CloseWebSocketFrame) {
			handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
			return;
		}
		if (frame instanceof PingWebSocketFrame) {
			ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
			return;
		}
		if (!(frame instanceof WebSocketFrame)) {
			throw new UnsupportedOperationException(
					String.format("%s frame types not supported", frame.getClass().getName()));
		}
		if (frame instanceof BinaryWebSocketFrame) {
			if (remoteConnect) {
				BinaryWebSocketFrame binFrame = (BinaryWebSocketFrame) frame;
				if (outboundChannel.isActive()) {
					outboundChannel.writeAndFlush(binFrame.content().retain()).addListener(new ChannelFutureListener() {
						@Override
						public void operationComplete(ChannelFuture future) {
							if (future.isSuccess()) {
								ctx.channel().read();
							} else {
								future.channel().close();
							}
						}
					});
				}
			} else {
				ctx.close();
			}
		} else if (frame instanceof TextWebSocketFrame) {
			String request = ((TextWebSocketFrame) frame).text();
			if (!remoteConnect) {
				SowsConnectCmd cmd = new SowsConnectCmd();
				try {
					cmd.decode(request);
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
					ctx.close();
				}
				String host = cmd.getHost();
				int port = cmd.getPort();
				final Channel inboundChannel = ctx.channel();
				// invalid host
				if(host.equals("localhost")||host.equals("127.0.0.1")){
					logger.warn("Invalid host. Target=" + host + ":" + port);
					WebSocketFrame frame2 = new TextWebSocketFrame(SowsStatusType.FAILED.stringValue());
					inboundChannel.writeAndFlush(frame2).addListener(ChannelFutureListener.CLOSE);
				}
				
				// Start the connection attempt.
				Bootstrap b = new Bootstrap();
				b.group(inboundChannel.eventLoop()).channel(ctx.channel().getClass())
						.handler(new HexDumpProxyInitializer(inboundChannel, handshaker))
						.option(ChannelOption.AUTO_READ, false);
				ChannelFuture f = b.connect(host, port);
				outboundChannel = f.channel();
				f.addListener(new ChannelFutureListener() {
					@Override
					public void operationComplete(ChannelFuture future) {
						if (future.isSuccess()) {
							logger.info("Target connection is established. Target=" + host + ":" + port);
							WebSocketFrame frame = new TextWebSocketFrame(SowsStatusType.SUCCESS.stringValue());
							inboundChannel.writeAndFlush(frame);
							// connection complete start to read first data
							inboundChannel.read();
						} else {
							logger.warn("Not able to connect target. Target=" + host + ":" + port);
							WebSocketFrame frame = new TextWebSocketFrame(SowsStatusType.FAILED.stringValue());
							inboundChannel.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE);
						}
					}
				});
				remoteConnect = true;
			} else {
				logger.warn("Unknown SowsCmd! " + request);
				ctx.close();
			}
		}

	}

	private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
		// Generate an error page if response getStatus code is not OK (200).
		if (res.getStatus().code() != 200) {
			ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8);
			res.content().writeBytes(buf);
			buf.release();
			HttpHeaders.setContentLength(res, res.content().readableBytes());
		}

		// Send the response and close the connection if necessary.
		ChannelFuture f = ctx.channel().writeAndFlush(res);
		if (!HttpHeaders.isKeepAlive(req) || res.getStatus().code() != 200) {
			f.addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		ctx.close();
	}

	private static String getWebSocketLocation(FullHttpRequest req) {
		String location = req.headers().get(HOST) + WEBSOCKET_PATH;
		if (WebSocketServer.SSL) {
			return "wss://" + location;
		} else {
			return "ws://" + location;
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		if (logger.isDebugEnabled()) {
			logger.debug("Websocket inbound inactive.");
		}
		if (outboundChannel != null) {
			closeOnFlush(outboundChannel);
		}
	}

	/**
	 * Closes the specified channel after all queued write requests are flushed.
	 */
	static void closeOnFlush(Channel ch) {
		if (ch.isActive()) {
			ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
		}
	}

}
