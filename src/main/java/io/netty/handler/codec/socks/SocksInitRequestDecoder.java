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
package io.netty.handler.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.socks.SocksInitRequestDecoder.State;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes {@link ByteBuf}s into {@link SocksInitRequest}.
 * Before returning SocksRequest decoder removes itself from pipeline.
 */
public class SocksInitRequestDecoder extends ReplayingDecoder<State> {
    private static final String name = "SOCKS_INIT_REQUEST_DECODER";

    /**
     * @deprecated Will be removed at the next minor version bump.
     */
    @Deprecated
    public static String getName() {
        return name;
    }

    private final List<SocksAuthScheme> authSchemes = new ArrayList<SocksAuthScheme>();
    private SocksProtocolVersion version;
    private byte authSchemeNum;
    private SocksRequest msg = SocksCommonUtils.UNKNOWN_SOCKS_REQUEST;

    public SocksInitRequestDecoder() {
        super(State.CHECK_PROTOCOL_VERSION);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) throws Exception {
        switch (state()) {
            case CHECK_PROTOCOL_VERSION: {
                version = SocksProtocolVersion.valueOf(byteBuf.readByte());
                if (version != SocksProtocolVersion.SOCKS5) {
                	if(version == SocksProtocolVersion.SOCKS4a){
                		checkpoint(State.READ_SOCKS4_REQUEST);
                		byte cmdCode = byteBuf.readByte();
                		if(SocksCmdType.CONNECT != SocksCmdType.valueOf(cmdCode))break;
                		short port = byteBuf.readShort();
                		short ip1 = byteBuf.readUnsignedByte();
                		short ip2 = byteBuf.readUnsignedByte();
                		short ip3 = byteBuf.readUnsignedByte();
                		short ip4 = byteBuf.readUnsignedByte();
                		//ignore user id
                		byte b = byteBuf.readByte();
                		while(b != 0x00){
                			b = byteBuf.readByte();
                		}
                		String host = ip1+"."+ip2+"."+ip3+"."+ip4;
                		msg = new SocksCmdRequest(SocksCmdType.CONNECT,SocksAddressType.IPv4,host,port);
                		msg.setProtocolVersion(SocksProtocolVersion.SOCKS4a);
                		break;
                	}else{
                		break;
                	}
                    
                }else{
                	checkpoint(State.READ_AUTH_SCHEMES);
                }
                
            }
            case READ_AUTH_SCHEMES: {
                authSchemes.clear();
                authSchemeNum = byteBuf.readByte();
                for (int i = 0; i < authSchemeNum; i++) {
                    authSchemes.add(SocksAuthScheme.valueOf(byteBuf.readByte()));
                }
                msg = new SocksInitRequest(authSchemes);
                break;
            }
		default:
			break;
        }
        ctx.pipeline().remove(this);
        out.add(msg);
    }

    enum State {
        CHECK_PROTOCOL_VERSION,
        READ_AUTH_SCHEMES,
        READ_SOCKS4_REQUEST
    }
}
