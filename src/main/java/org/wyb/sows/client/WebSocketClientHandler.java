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
//The MIT License
//
//Copyright (c) 2009 Carl Bystršm
//
//Permission is hereby granted, free of charge, to any person obtaining a copy
//of this software and associated documentation files (the "Software"), to deal
//in the Software without restriction, including without limitation the rights
//to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
//copies of the Software, and to permit persons to whom the Software is
//furnished to do so, subject to the following conditions:
//
//The above copyright notice and this permission notice shall be included in
//all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
//THE SOFTWARE.

package org.wyb.sows.client;

import org.wyb.sows.websocket.SowsConnectCmd;
import org.wyb.sows.websocket.SowsStatusType;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;


//read from websocket and write to socks
public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;
    
    private final Promise<Channel> promise;
    
    private final Channel relayChannel;
    
    private final String targetHost;
    private final int targetPort;
    
    private boolean remoteConnect = false;
    
    private String userName;
    private String passcode;
    
    public WebSocketClientHandler(WebSocketClientHandshaker handshaker,Promise<Channel> promise,String targetHost,int targetPort,Channel relayChannel,String userName,String passcode) {
        this.handshaker = handshaker;
        this.promise = promise;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.relayChannel = relayChannel;
        this.userName = userName;
        this.passcode = passcode;
    }
    


    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
        handshakeFuture.addListener(new GenericFutureListener<Future<Void>>(){
			@Override
			public void operationComplete(Future<Void> future) throws Exception {
				// send out target host and port to remote service.
				if(future.isSuccess()){
					SowsConnectCmd cmd = new SowsConnectCmd();
					cmd.setHost(targetHost);
					cmd.setPort(targetPort);
					cmd.setUserName(userName);
					cmd.setPasscode(passcode);
					if(SocksServer.isDebug){
						System.out.printf("Send remote connection request: %s \r\n",cmd);
					}
					WebSocketFrame frame = new TextWebSocketFrame(cmd.encode());
					ctx.writeAndFlush(frame);
				}else{
					promise.setFailure(new Exception("Bridge service handshake failed!"));
				}
			}


        	
        });
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("WebSocket Client disconnected!");
        if (relayChannel.isActive()) {
            SocksServerUtils.closeOnFlush(relayChannel);
        }
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            handshaker.finishHandshake(ch, (FullHttpResponse) msg);
            handshakeFuture.setSuccess();
            if(SocksServer.isDebug){
            	System.out.println("Websocket hand shake success.");
            }
            return;
        }

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus=" + response.getStatus() +
                            ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')');
        }
        
        WebSocketFrame frame = (WebSocketFrame) msg;
        
        if(frame instanceof BinaryWebSocketFrame){
        	BinaryWebSocketFrame binFrame = (BinaryWebSocketFrame) frame;
        	if (relayChannel.isActive()) {
        		if(binFrame.isFinalFragment()){
        			relayChannel.writeAndFlush(binFrame.content().retain());
        		}else{
        			relayChannel.write(binFrame.content().retain());
        		}
            }
        }
        else if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            if(!remoteConnect){
            	String remoteConnectionStatus = textFrame.text();
            	
            	if(SowsStatusType.SUCCESS == SowsStatusType.valueOf(remoteConnectionStatus)){
            		System.out.printf("Remote connection is established. %s:%d\r\n",targetHost,targetPort);
                	remoteConnect = true;
                	promise.setSuccess(ctx.channel());
            	}else{
            		//throw new Exception("Remote connection error! Msg:"+remoteConnectionStatus);
            		System.err.printf("Remote connection failed. %s:%d\r\n",targetHost,targetPort);
            		ch.close();
            	}
            }else{
            	System.err.println("Unknow text command.");
            }
        } else if (frame instanceof PongWebSocketFrame) {
            System.out.println("WebSocket Client received pong");
        } else if (frame instanceof CloseWebSocketFrame) {
        	if(SocksServer.isDebug){
        		System.out.println("WebSocket Client received closing");
        	}
            ch.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }
}
