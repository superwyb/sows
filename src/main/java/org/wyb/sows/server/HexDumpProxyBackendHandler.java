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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;

public class HexDumpProxyBackendHandler extends ChannelInboundHandlerAdapter {

    private final Channel inboundChannel;

    private final WebSocketServerHandshaker handshaker;
    
    
    public HexDumpProxyBackendHandler(Channel inboundChannel,WebSocketServerHandshaker handshaker) {
        this.inboundChannel = inboundChannel;
        this.handshaker = handshaker;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
    	//System.out.println("Binary send");
        inboundChannel.writeAndFlush(new BinaryWebSocketFrame(((ByteBuf) msg))).addListener(new ChannelFutureListener() {
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

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
    	System.out.println("Hex Dump channel inactive");
    	closeOnFlush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    	// if exception occurred, close on flush hex channel first.
    	// then it will trigger channelInactive to close websocket gracefully.
    	System.err.println("Hex Dump connection error!");
        cause.printStackTrace();
        HexDumpProxyBackendHandler.closeOnFlush(ctx.channel());
    }
    
    public void closeOnFlush() {
        if (inboundChannel.isActive()) {
        	System.out.println("Send close web socket frame.");
        	handshaker.close(inboundChannel,new CloseWebSocketFrame());
            //ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
    
    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
