package org.wyb.sows.websocket;

import java.io.UnsupportedEncodingException;

public abstract class SowsCmd {
	
	public abstract String encode() throws UnsupportedEncodingException;
	
	public abstract void decode(String msg) throws UnsupportedEncodingException;

}
