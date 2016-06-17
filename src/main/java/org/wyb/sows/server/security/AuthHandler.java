package org.wyb.sows.server.security;

public interface AuthHandler {
	
	
	public final static int UNKNOWN = -999;
	
	public final static int OK = 0;
	
	public final static int INVALID_USER=1;
	
	public final static int INVALID_TOKEN = 2;
	
	public final static int INVALID_USERNAME=-1;
	
	public final static int INVALID_PASS = -2;
	
	public final static int USER_ALREADY_EXISTS = 3;
	
	public int login(String user,String token,String seed);
	
	public int addUser(String user,String pass);
	
	public int deleteUser(String user);
	
	public int resetPassword(String user,String pass);
	
	public int hasUser(String user);
	

}
