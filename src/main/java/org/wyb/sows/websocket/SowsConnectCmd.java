package org.wyb.sows.websocket;

import java.io.UnsupportedEncodingException;

public class SowsConnectCmd extends SowsCmd{
	
	private String host;
	private int port;
	private String userName;
	private String passcode;
	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}
	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
	}
	public String getUserName() {
		return userName;
	}
	public void setUserName(String userName) {
		this.userName = userName;
	}
	public String getPasscode() {
		return passcode;
	}
	public void setPasscode(String passcode) {
		this.passcode = passcode;
	}

	@Override
	public String encode()  throws UnsupportedEncodingException{
		StringBuffer sb = new StringBuffer();
		sb.append(host!=null?host:"");
		sb.append(":");
		sb.append(port);
		sb.append(";");
		sb.append(userName);
		sb.append("@");
		sb.append(passcode);
		return sb.toString();
	}
	@Override
	public void decode(String msg)  throws UnsupportedEncodingException{
		String[] params = msg.split("\\;");
		String[] targetParams = params[0].split("\\:");
		String[] loginParams = params[1].split("\\@");
		this.host = targetParams[0];
		this.port = Integer.parseInt(targetParams[1]);
		this.userName = loginParams[0];
		this.passcode = loginParams[1];
		
	}
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(host!=null?host:"");
		sb.append(":");
		sb.append(port);
		sb.append(";");
		sb.append(userName);
		return sb.toString();
	}
	
	

}
