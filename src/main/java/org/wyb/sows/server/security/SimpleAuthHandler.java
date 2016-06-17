package org.wyb.sows.server.security;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import org.wyb.sows.websocket.SowsAuthHelper;

import io.netty.util.CharsetUtil;

public class SimpleAuthHandler implements AuthHandler {
	
	private final static String USER_FILE_PATH = "./config/users.txt";

	@Override
	public int login(String user, String token, String seed) {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(USER_FILE_PATH));
			String lookupValue = user + ";";
			String line = null;
			while ((line = br.readLine()) != null) {
				if (line.isEmpty())
					continue;
				if (line.startsWith(lookupValue)) {
					String[] params = line.split(";");
					String passcode = params[1];
					byte[] sha1 = SowsAuthHelper.sha1((passcode + seed).getBytes(CharsetUtil.US_ASCII));
					String token2 = SowsAuthHelper.base64(sha1);
					return token2.equals(token) ? OK : INVALID_PASS;
				}
			}
			return INVALID_USER;
		} catch (IOException e) {
			return UNKNOWN;
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					return UNKNOWN;
				}
			}
		}
	}

	@Override
	public int addUser(String user, String passcode) {
		if(hasUser(user) == OK)return USER_ALREADY_EXISTS;
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(new FileWriter(USER_FILE_PATH,true));
			pw.println(String.format("%s;%s;", user,passcode));
			pw.flush();
			return OK;
		} catch (IOException e) {
			return UNKNOWN;
		}finally{
			if(pw != null){
				pw.close();
			}
		}
	}

	@Override
	public int deleteUser(String user) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int resetPassword(String user, String passcode) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int hasUser(String user) {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(USER_FILE_PATH));
			String lookupValue = user + ";";
			String line = null;
			while ((line = br.readLine()) != null) {
				if (line.isEmpty())
					continue;
				if (line.startsWith(lookupValue)) {
					return OK;
				}
			}
			return INVALID_USER;
		} catch (IOException e) {
			return UNKNOWN;
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					return UNKNOWN;
				}
			}
		}

	}
}
