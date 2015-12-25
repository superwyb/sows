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

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;

/**
 */
public class HexDumpProxyInitializer extends ChannelInitializer<SocketChannel> {

	private final Channel inboundChannel;
	private final WebSocketServerHandshaker handshaker;

    public HexDumpProxyInitializer(Channel inboundChannel,WebSocketServerHandshaker handshaker) {
        this.inboundChannel = inboundChannel;
        this.handshaker = handshaker;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new HexDumpProxyBackendHandler(inboundChannel,handshaker));
    }
}
