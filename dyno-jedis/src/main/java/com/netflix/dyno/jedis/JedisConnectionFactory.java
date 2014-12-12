package com.netflix.dyno.jedis;

import java.net.SocketException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.NotImplementedException;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

import com.netflix.dyno.connectionpool.AsyncOperation;
import com.netflix.dyno.connectionpool.Connection;
import com.netflix.dyno.connectionpool.ConnectionContext;
import com.netflix.dyno.connectionpool.ConnectionFactory;
import com.netflix.dyno.connectionpool.ConnectionObservor;
import com.netflix.dyno.connectionpool.ConnectionPoolConfiguration;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostConnectionPool;
import com.netflix.dyno.connectionpool.ListenableFuture;
import com.netflix.dyno.connectionpool.Operation;
import com.netflix.dyno.connectionpool.OperationMonitor;
import com.netflix.dyno.connectionpool.OperationResult;
import com.netflix.dyno.connectionpool.exception.DynoConnectException;
import com.netflix.dyno.connectionpool.exception.DynoException;
import com.netflix.dyno.connectionpool.exception.FatalConnectionException;
import com.netflix.dyno.connectionpool.exception.ThrottledException;
import com.netflix.dyno.connectionpool.impl.ConnectionContextImpl;
import com.netflix.dyno.connectionpool.impl.OperationResultImpl;

public class JedisConnectionFactory implements ConnectionFactory<Jedis> {

	private final OperationMonitor opMonitor; 
	
	public JedisConnectionFactory(OperationMonitor monitor) {
		this.opMonitor = monitor;
	}
	
	@Override
	public Connection<Jedis> createConnection(HostConnectionPool<Jedis> pool, ConnectionObservor connectionObservor) 
			throws DynoConnectException, ThrottledException {
		return new JedisConnection(pool);
	}

	public class JedisConnection implements Connection<Jedis> {

		private final HostConnectionPool<Jedis> hostPool;
		private final Jedis jedisClient; 
		private final ConnectionContextImpl context = new ConnectionContextImpl();
		
		private DynoConnectException lastDynoException;
		
		public JedisConnection(HostConnectionPool<Jedis> hostPool) {
			this.hostPool = hostPool;
			Host host = hostPool.getHost();
			ConnectionPoolConfiguration configuration = hostPool.getConnectionPoolConfiguration();
			int connectTimeout = configuration.getConnectTimeout();
			int socketTimeout = configuration.getSocketTimeout();

			jedisClient = new Jedis(host.getHostName(), host.getPort(), connectTimeout, socketTimeout);

			// // TODO: Dynomite node should support AUTH
			// if (host.isPasswordSet()) {
			// jedisClient.auth(host.getPassword());
			// }
		}
		
		@Override
		public <R> OperationResult<R> execute(Operation<Jedis, R> op) throws DynoException {
			
			long startTime = System.nanoTime()/1000;
			String opName = op.getName();

			OperationResultImpl<R> opResult = null;
			
			try { 
				
				if (!jedisClient.isConnected()) {
					open();
				}
				
				R result = op.execute(jedisClient, null);
				opMonitor.recordSuccess(opName);
				opResult = new OperationResultImpl<R>(opName, result, opMonitor);
				return opResult;
				
			} catch (JedisConnectionException ex) {
				
				opMonitor.recordFailure(opName, ex.getMessage());
				if (ex.getCause() instanceof SocketException) {
					SocketException se = (SocketException) ex.getCause();
					if (!se.getMessage().equalsIgnoreCase("timeout")) {
						close();
					}
				}
				
				lastDynoException = (DynoConnectException) new FatalConnectionException(ex).setAttempt(1);
				lastDynoException.setHost(hostPool.getHost());
				throw lastDynoException;

			} catch (RuntimeException ex) {
				opMonitor.recordFailure(opName, ex.getMessage());
				lastDynoException = (DynoConnectException) new FatalConnectionException(ex).setAttempt(1);
				lastDynoException.setHost(hostPool.getHost());
				throw lastDynoException;
				
			} finally {
				long duration = System.nanoTime()/1000 - startTime;
				if (opResult != null) {
					opResult.setLatency(duration, TimeUnit.MICROSECONDS);
				}
			}
		}

		@Override
		public <R> ListenableFuture<OperationResult<R>> executeAsync(AsyncOperation<Jedis, R> op) throws DynoException {
			throw new NotImplementedException();
		}

		@Override
		public void close() {
			if (jedisClient != null) {
				try {
					jedisClient.quit();
				} catch (Exception e) {
				}
				try {
					jedisClient.disconnect();
				} catch (Exception e) {
				}
			}
		}

		@Override
		public Host getHost() {
			return hostPool.getHost();
		}

		@Override
		public void open() throws DynoException {
			jedisClient.connect();
		}

		@Override
		public DynoConnectException getLastException() {
			return lastDynoException;
		}

		@Override
		public HostConnectionPool<Jedis> getParentConnectionPool() {
			return hostPool;
		}

		@Override
		public void execPing() {
			String result = jedisClient.ping();
			if (result == null || result.isEmpty()) {
				throw new DynoConnectException("Unsuccessful ping, got empty result");
			}
		}

		@Override
		public ConnectionContext getContext() {
			return context;
		}
		
		public Jedis getClient() {
			return jedisClient;
		}
	}
}
