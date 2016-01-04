package org.wyb.sows.websocket;

public enum SowsStatusType {
	SUCCESS("SUCCESS"),FAILED("FAILED"),UNKNOWN("UNKNOWN");
	
	private final String str;
	SowsStatusType(String str){
		this.str = str;
	}
	
	public String stringValue(){
		return str;
	}

}
