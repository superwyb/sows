package org.wyb.sows.server.security;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

public class PassiveCallbackHandler implements CallbackHandler {

	private String userName;
	private String password;

	public PassiveCallbackHandler(String userName, String password) {
		this.userName = userName;
		this.password = password;
	}

	@Override
	public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException { // do
																								// some
		for (int i = 0; i < callbacks.length; i++) {
			if (callbacks[i] instanceof NameCallback) {
				NameCallback nc = (NameCallback) callbacks[i];
				nc.setName(this.userName);
			} else if (callbacks[i] instanceof PasswordCallback) {
				PasswordCallback pc = (PasswordCallback) callbacks[i];
				pc.setPassword(this.password.toCharArray());
			} else {
				throw (new UnsupportedCallbackException(callbacks[i], "Callback handler not support"));
			}
		}
	}

}
