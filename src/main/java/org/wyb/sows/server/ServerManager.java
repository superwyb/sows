package org.wyb.sows.server;

import org.wyb.sows.server.security.SimpleAuthHandler;
import org.wyb.sows.websocket.SowsAuthHelper;

import io.netty.util.CharsetUtil;

public class ServerManager {

	public static void main(String[] args) {
		if(args.length == 0){
			help();
			return;
		}
		String cmd = args[0];
		switch(cmd){
		case "adduser":
			if(args.length<3){
				help();
				return;
			}
			String[] params = new String[2];
			System.arraycopy(args, 1, params, 0, 2);
			addUser(params);
			break;
		default:
			help();
		}
	}
	
	public static void addUser(String[] params){
		SimpleAuthHandler auth = new SimpleAuthHandler();
		String user = params[0];
		String password = params[1];
		byte[] sha1 = SowsAuthHelper.sha1((password).getBytes(CharsetUtil.US_ASCII));
		String passcode = SowsAuthHelper.base64(sha1);
		int result = auth.addUser(user, passcode);
		if(result == SimpleAuthHandler.OK){
			System.out.println(String.format("User %s is added.",user));
		}else{
			System.err.println(String.format("Error %d",result));
		}
	}
	
	public static void help(){
		System.out.println("Server Manager Help");
		System.out.println();
		System.out.println("adduser username password");
	}

}
