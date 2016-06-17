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

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.Properties;

import org.apache.log4j.PropertyConfigurator;
import org.wyb.sows.websocket.SowsAuthHelper;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.CharsetUtil;

public final class SocksServer {

	final static int PORT = Integer.parseInt(System.getProperty("port", "1080"));
	final static String URI = System.getProperty("uri", "ws://127.0.0.1:8080/websocket");
	public static boolean isDebug = false;

	public static void main(String[] args) throws Exception {
		File configFile = new File("./config/sows.config");
		if (!configFile.exists()) {
			System.err.println("Cannot find config file: ./config/sows.config");
			System.exit(-1);
		}
		PropertyConfigurator.configure( "./config/serverlog.config" );
		Properties props = new Properties();
		props.load(new FileInputStream(configFile));
		String uri = props.getProperty("sows.uri");
		int port = Integer.parseInt(props.getProperty("socks.port"));
		String userName = props.getProperty("sows.user");
		String password = props.getProperty("sows.pass");
		byte[] sha1 = SowsAuthHelper.sha1((password).getBytes(CharsetUtil.US_ASCII));
		String passcode = SowsAuthHelper.base64(sha1);
		isDebug = Boolean.parseBoolean(props.getProperty("debug", "false"));
		URI bridgeServiceUri = new URI(uri);
		EventLoopGroup bossGroup = new NioEventLoopGroup(1);
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
					// .handler(new LoggingHandler(LogLevel.INFO))
					.childHandler(new SocksServerInitializer(bridgeServiceUri, userName, passcode));
			System.out.printf("Socks server is listening on port %d. Remote URI is %s \r\n", port, uri);
			b.bind(port).sync().channel().closeFuture().sync();
			
		} finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
	}
}
