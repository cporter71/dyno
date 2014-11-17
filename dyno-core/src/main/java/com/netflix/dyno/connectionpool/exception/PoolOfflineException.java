package com.netflix.dyno.connectionpool.exception;

import com.netflix.dyno.connectionpool.HostConnectionPool;

public class PoolOfflineException extends DynoConnectException {

	private static final long serialVersionUID = -345340994112630363L;
	private final HostConnectionPool<?> hostPool; 
	
	public PoolOfflineException(HostConnectionPool<?> hostPool, String message) {
		super(message);
		this.hostPool = hostPool;
	}
	
	public HostConnectionPool<?> getHostPool() {
		return hostPool;
	}
}