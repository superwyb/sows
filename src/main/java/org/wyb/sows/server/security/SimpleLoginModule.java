package org.wyb.sows.server.security;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

public class SimpleLoginModule implements LoginModule {

	private final static String USER = "javax.security.auth.login.name";
	private final static String PASS = "javax.security.auth.login.password";
	private final static String FILE = "file";


	private Map<String, String> sharedState;
	private String credentialFilePath;
	private CallbackHandler callbackHandler;

	@SuppressWarnings("unchecked")
	@Override
	public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState,
			Map<String, ?> options) {
		this.sharedState = (Map<String, String>) sharedState;
		credentialFilePath = (String) options.get(FILE);
		this.callbackHandler = callbackHandler;

	}

	@Override
	public boolean login() throws LoginException {
		File f = new File(credentialFilePath);
		if (!f.exists())
			return false;
		NameCallback nameCB = new NameCallback("username: ");
		PasswordCallback passCB = new PasswordCallback("password:", false);
		Callback[] callbacks = {nameCB,passCB};
		try {
			callbackHandler.handle(callbacks);
		} catch (IOException | UnsupportedCallbackException e1) {
			e1.printStackTrace();
		}

		sharedState.put(USER, nameCB.getName());
		sharedState.put(PASS, new String(passCB.getPassword()));
		if (sharedState.containsKey(USER) && sharedState.containsKey(PASS)) {
			String userName = (String) sharedState.get(USER);
			String password = (String) sharedState.get(PASS);
			if (userName.isEmpty() || password.isEmpty()) {
				throw new FailedLoginException("Empty username or password.");
			}
			try {
				if (lookupUserAndPass(f, userName, password)) {
					return true;
				} else {
					throw new FailedLoginException("Invalid username or password.");
				}
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}

		} else {
			throw new FailedLoginException("Username or password is not provided.");
		}
	}

	private boolean lookupUserAndPass(File file, String userName, String password) throws IOException {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(file));
			String value = userName + ";" + password + ";";
			String line = null;
			while ((line = br.readLine()) != null) {
				if (line.isEmpty())
					continue;
				if (value.equals(line))
					return true;
			}
			return false;
		} finally {
			if (br != null) {
				br.close();
			}
		}
	}

	@Override
	public boolean commit() throws LoginException {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean abort() throws LoginException {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean logout() throws LoginException {
		// TODO Auto-generated method stub
		return true;
	}

}
