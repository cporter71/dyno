package com.netflix.dyno.connectionpool.exception;

public class NoAvailableHostsException extends DynoConnectException {

	public NoAvailableHostsException(String message) {
		super(message);
	}

	public NoAvailableHostsException(String message, Long hostToken) {
		super(message);
		if (hostToken != null) {
			setHostToken(hostToken);
		}
	}	
}
