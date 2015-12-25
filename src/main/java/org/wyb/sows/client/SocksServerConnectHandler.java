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
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.socks.SocksCmdRequest;
import io.netty.handler.codec.socks.SocksCmdResponse;
import io.netty.handler.codec.socks.SocksCmdStatus;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;

@ChannelHandler.Sharable
public final class SocksServerConnectHandler extends SimpleChannelInboundHandler<SocksCmdRequest> {

    private final Bootstrap b = new Bootstrap();
    private final URI bridgeServiceUri;
    
    
    public SocksServerConnectHandler(URI bridgeServiceUri){
    	this.bridgeServiceUri = bridgeServiceUri;

    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final SocksCmdRequest request) throws Exception {
        Promise<Channel> promise = ctx.executor().newPromise();
        promise.addListener(
            new GenericFutureListener<Future<Channel>>() {
            @Override
            public void operationComplete(final Future<Channel> future) throws Exception {
                final Channel outboundChannel = future.getNow();
                if (future.isSuccess()) {
                	System.out.println("Bridge is established. Inform proxy client.");
                    ctx.channel().writeAndFlush(new SocksCmdResponse(SocksCmdStatus.SUCCESS, request.addressType()))
                            .addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture channelFuture) {
                                	ChannelPipeline pipeline = ctx.pipeline();
                                	pipeline.remove(SocksServerConnectHandler.this);
                                    //ctx.pipeline().addLast(new StringByteCodec());
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
        final WebSocketClientHandler handler =
                new WebSocketClientHandler(
                        WebSocketClientHandshakerFactory.newHandshaker(
                        		bridgeServiceUri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders()),promise,request.host(),request.port(),inboundChannel);
        b.group(inboundChannel.eventLoop())
         .channel(NioSocketChannel.class)
         .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
         .option(ChannelOption.SO_KEEPALIVE, true)
         .handler(new ChannelInitializer<SocketChannel>() {
             @Override
             protected void initChannel(SocketChannel ch) {
                 ChannelPipeline p = ch.pipeline();
                 p.addLast(
                         new HttpClientCodec(),
                         new HttpObjectAggregator(8192),
                         handler);
             }
         });
        System.out.println("Connect to bridge service.");
        b.connect(bridgeServiceUri.getHost(), bridgeServiceUri.getPort()).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    // Connection established use handler provided results
                	System.out.println("Bridge service connection is established. host="+bridgeServiceUri.getHost()+"port="+bridgeServiceUri.getPort());
                } else {
                    // Close the connection if the connection attempt has failed.
                	System.err.println("Not able to connect bridge service! host="+bridgeServiceUri.getHost()+"port="+bridgeServiceUri.getPort());
                    ctx.channel().writeAndFlush(
                            new SocksCmdResponse(SocksCmdStatus.FAILURE, request.addressType()));
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
