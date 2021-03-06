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
package org.wyb.sows.client;

import java.net.URI;

import org.wyb.sows.websocket.SowsAuthHelper;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.socks.SocksCmdRequest;
import io.netty.handler.codec.socks.SocksCmdResponse;
import io.netty.handler.codec.socks.SocksCmdStatus;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;

@ChannelHandler.Sharable
public final class SocksServerConnectHandler extends SimpleChannelInboundHandler<SocksCmdRequest> {

	private final String userName;
	private final String passcode;

	private final Bootstrap b = new Bootstrap();
	private final URI bridgeServiceUri;

	public SocksServerConnectHandler(URI bridgeServiceUri, String userName, String passcode) {
		this.bridgeServiceUri = bridgeServiceUri;
		this.userName = userName;
		this.passcode = passcode;
	}

	@Override
	public void channelRead0(final ChannelHandlerContext ctx, final SocksCmdRequest request) throws Exception {
		if(request.host().equals("127.0.0.1")||request.host().equals("localhost")){
			System.err.println("Not able to establish bridge. Inform proxy client.");
			ctx.channel().writeAndFlush(new SocksCmdResponse(SocksCmdStatus.FAILURE, request.addressType()));
			SocksServerUtils.closeOnFlush(ctx.channel());
		}
		
		Promise<Channel> promise = ctx.executor().newPromise();
		promise.addListener(new GenericFutureListener<Future<Channel>>() {
			@Override
			public void operationComplete(final Future<Channel> future) throws Exception {
				final Channel outboundChannel = future.getNow();
				if (future.isSuccess()) {
					if (SocksServer.isDebug) {
						System.out.println("Bridge is established. Inform proxy client.");
					}
					SocksCmdResponse resp = new SocksCmdResponse(SocksCmdStatus.SUCCESS, request.addressType());
					resp.setProtocolVersion(request.protocolVersion());
					ctx.channel().writeAndFlush(resp).addListener(new ChannelFutureListener() {
						@Override
						public void operationComplete(ChannelFuture channelFuture) {
							ChannelPipeline pipeline = ctx.pipeline();
							pipeline.remove(SocksServerConnectHandler.this);
							// ctx.pipeline().addLast(new StringByteCodec());
							pipeline.addLast(new WebSocketRelayHandler(outboundChannel));
						}
					});
				} else {
					System.err.println("Not able to establish bridge. Inform proxy client.");
					ctx.channel().writeAndFlush(new SocksCmdResponse(SocksCmdStatus.FAILURE, request.addressType()));
					SocksServerUtils.closeOnFlush(ctx.channel());
				}
			}
		});
		final Channel inboundChannel = ctx.channel();
		
		// Add authentication headers
		HttpHeaders authHeader = new DefaultHttpHeaders();
		authHeader.add(SowsAuthHelper.HEADER_SOWS_USER, this.userName);
		byte[] nonce = SowsAuthHelper.randomBytes(16);
		String seed = SowsAuthHelper.base64(nonce);
		authHeader.add(SowsAuthHelper.HEADER_SOWS_SEED, seed);
		byte[] sha1 = SowsAuthHelper.sha1((this.passcode + seed).getBytes(CharsetUtil.US_ASCII));
		String token = SowsAuthHelper.base64(sha1);
		authHeader.add(SowsAuthHelper.HEADER_SOWS_TOKEN, token);
		
		// initiating websocket client handler
		final WebSocketClientHandler handler = new WebSocketClientHandler(
				WebSocketClientHandshakerFactory.newHandshaker(bridgeServiceUri, WebSocketVersion.V13, null, false,
						authHeader),
				promise, request.host(), request.port(), inboundChannel);
		b.group(inboundChannel.eventLoop()).channel(NioSocketChannel.class)
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000).option(ChannelOption.SO_KEEPALIVE, true)
				.handler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel ch) {
						ChannelPipeline p = ch.pipeline();
						p.addLast(new HttpClientCodec(), new HttpObjectAggregator(8192), handler);
					}
				});
		if (SocksServer.isDebug) {
			System.out.println("Try to connect to bridge service.");
		}
		b.connect(bridgeServiceUri.getHost(), bridgeServiceUri.getPort()).addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (future.isSuccess()) {
					// Connection established use handler provided results
					if (SocksServer.isDebug) {
						System.out.printf("Brige service connection is established. host=%s,port=%d \r\n",
								bridgeServiceUri.getHost(), bridgeServiceUri.getPort());
					}
				} else {
					// Close the connection if the connection attempt has
					// failed.
					System.err.printf("Not able to connect bridge service! host=%s,port=%d \r\n",
							bridgeServiceUri.getHost(), bridgeServiceUri.getPort());
					ctx.channel().writeAndFlush(new SocksCmdResponse(SocksCmdStatus.FAILURE, request.addressType()));
					SocksServerUtils.closeOnFlush(ctx.channel());
				}
			}
		});

	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		cause.printStackTrace();
		SocksServerUtils.closeOnFlush(ctx.channel());
	}
}
