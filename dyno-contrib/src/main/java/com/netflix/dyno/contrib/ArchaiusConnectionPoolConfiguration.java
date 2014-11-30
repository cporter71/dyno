package com.netflix.dyno.contrib;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.dyno.connectionpool.ErrorRateMonitorConfig;
import com.netflix.dyno.connectionpool.RetryPolicy.RetryPolicyFactory;
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.dyno.connectionpool.impl.RetryNTimes;
import com.netflix.dyno.connectionpool.impl.RunOnce;

public class ArchaiusConnectionPoolConfiguration extends ConnectionPoolConfigurationImpl {

	private static final Logger Logger = LoggerFactory.getLogger(ArchaiusConnectionPoolConfiguration.class);
	
	private static final String DynoPrefix = "dyno.";
	
	private final DynamicIntProperty maxConnsPerHost;
	private final DynamicIntProperty maxTimeoutWhenExhausted;
	private final DynamicIntProperty maxFailoverCount;
	private final DynamicIntProperty connectTimeout;
//	private final DynamicIntProperty socketTimeout;
	private final DynamicIntProperty poolShutdownDelay;
	private final DynamicBooleanProperty localDcAffinity;
	
	private final LoadBalancingStrategy loadBalanceStrategy;
	private final ErrorRateMonitorConfig errorRateConfig;
	private final RetryPolicyFactory retryPolicyFactory;
	
	public ArchaiusConnectionPoolConfiguration(String name) {
		super(name);
		
		String propertyPrefix = DynoPrefix + name; 
		
		maxConnsPerHost = DynamicPropertyFactory.getInstance().getIntProperty(propertyPrefix + ".connection.maxConnsPerHost", super.getMaxConnsPerHost());
		maxTimeoutWhenExhausted = DynamicPropertyFactory.getInstance().getIntProperty(propertyPrefix + ".connection.maxTimeoutWhenExhausted", super.getMaxTimeoutWhenExhausted());
		maxFailoverCount = DynamicPropertyFactory.getInstance().getIntProperty(propertyPrefix + ".connection.maxFailoverCount", super.getMaxFailoverCount());
		connectTimeout = DynamicPropertyFactory.getInstance().getIntProperty(propertyPrefix + ".connection.connectTimeout", super.getConnectTimeout());
//		socketTimeout = DynamicPropertyFactory.getInstance().getIntProperty(propertyPrefix + ".connection.socketTimeout", super.getSocketTimeout());
		poolShutdownDelay = DynamicPropertyFactory.getInstance().getIntProperty(propertyPrefix + ".connection.poolShutdownDelay", super.getPoolShutdownDelay());
		localDcAffinity = DynamicPropertyFactory.getInstance().getBooleanProperty(propertyPrefix + ".connection.localDcAffinity", super.localDcAffinity());

		
		loadBalanceStrategy = parseLBStrategy(propertyPrefix);
		errorRateConfig = parseErrorRateMonitorConfig(propertyPrefix);
		retryPolicyFactory = parseRetryPolicyFactory(propertyPrefix);
	}

	
	@Override
	public String getName() {
		return super.getName();
	}

	@Override
	public int getMaxConnsPerHost() {
		return maxConnsPerHost.get();
	}

	@Override
	public int getMaxTimeoutWhenExhausted() {
		return maxTimeoutWhenExhausted.get();
	}

	@Override
	public int getMaxFailoverCount() {
		return maxFailoverCount.get();
	}


	@Override
	public int getConnectTimeout() {
		return connectTimeout.get();
	}

//	@Override
//	public int getSocketTimeout() {
//		return socketTimeout.get();
//	}

	@Override
	public RetryPolicyFactory getRetryPolicyFactory() {
		return retryPolicyFactory;
	}

	@Override
	public int getPoolShutdownDelay() {
		return poolShutdownDelay.get();
	}

	@Override
	public boolean localDcAffinity() {
		return localDcAffinity.get();
	}
	
	@Override
	public LoadBalancingStrategy getLoadBalancingStrategy() {
		return loadBalanceStrategy;
	}

	
	private LoadBalancingStrategy parseLBStrategy(String propertyPrefix) {
		
		LoadBalancingStrategy defaultConfig = super.getLoadBalancingStrategy();
		
		String cfg = 
				DynamicPropertyFactory.getInstance().getStringProperty(propertyPrefix + ".lbStrategy", defaultConfig.name()).get();
		
		LoadBalancingStrategy lb = null;
		try { 
			lb = LoadBalancingStrategy.valueOf(cfg);
		} catch (Exception e) {
			Logger.warn("Unable to parse LoadBalancingStrategy: " + cfg + ", switching to default: " + defaultConfig.name());
			lb = defaultConfig;
		}

		return lb;
	}
	
	private ErrorRateMonitorConfig parseErrorRateMonitorConfig(String propertyPrefix) {
		String errorRateConfig = DynamicPropertyFactory.getInstance().getStringProperty(propertyPrefix + ".errorRateConfig", null).get();
		try { 
		if (errorRateConfig == null) {
			return null;
		}
		
		// Classic format that is supported is json. 
		
		JSONObject json = new JSONObject(errorRateConfig);
		
		int window = json.getInt("window");
		int frequency = json.getInt("frequency");
		int suppress = json.getInt("suppress");
		
		ErrorRateMonitorConfigImpl configImpl = new ErrorRateMonitorConfigImpl(window, frequency, suppress);

		JSONArray thresholds = json.getJSONArray("thresholds");
		for (int i=0; i<thresholds.length(); i++) {
			
			JSONObject tConfig = thresholds.getJSONObject(i);
			
			int rps = tConfig.getInt("rps");
			int seconds = tConfig.getInt("seconds");
			int coverage = tConfig.getInt("coverage");
			
			configImpl.addThreshold(rps, seconds, coverage);
		}
		
		return configImpl;
		
		} catch (Exception e) {
			Logger.warn("Failed to parse error rate config: " + errorRateConfig, e);
		}
		
		return new ErrorRateMonitorConfigImpl();
	}
	
	private RetryPolicyFactory parseRetryPolicyFactory(String propertyPrefix) {
		
		String retryPolicy = DynamicPropertyFactory.getInstance().getStringProperty(propertyPrefix + ".retryPolicy", "RunOnce").get();
		
		if (retryPolicy.equals("RunOnce")) {
			return new RunOnce.RetryFactory();
		}
		
		if (retryPolicy.startsWith("RetryNTimes")) {
				
			String[] parts = retryPolicy.split(":");
			
			if (parts.length < 2) {
				return new RunOnce.RetryFactory();
			}
			
			try { 
				
				int n = Integer.parseInt(parts[1]);
				boolean allowFallback = false;
				if (parts.length == 3) {
					allowFallback = Boolean.parseBoolean(parts[2]);
				}
				return new RetryNTimes.RetryFactory(n, allowFallback);
				
			} catch (Exception e) {
				return new RunOnce.RetryFactory();
			}
		}
		
		return new RunOnce.RetryFactory();
	}
	

}
