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

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;


public final class WebSocketServerIndexPage {

    final static Logger logger = LoggerFactory.getLogger(WebSocketServerIndexPage.class);
    public static ByteBuf getContent(String webSocketLocation) {
    	String content = "<html></html>";
    	Optional<InputStream> opt = Optional.ofNullable(WebSocketServerIndexPage.class.getResourceAsStream("/index.html"));
    	if(opt.isPresent()){
    		try {
				content = IOUtils.toString(opt.get());
			} catch (IOException e) {
				logger.error("Cannot read index.html!",e);
			}
    	}
        return Unpooled.copiedBuffer(content, CharsetUtil.UTF_8);
    }

}
