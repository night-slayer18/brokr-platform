package io.brokr.kafka.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.brokr.core.model.BrokerJmxMetrics;
import io.brokr.core.model.KafkaCluster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.management.*;
import javax.management.remote.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for managing JMX connections to Kafka brokers.
 * Thread-safe with connection pooling and per-broker locking to prevent race conditions.
 */
@Service
@Slf4j
public class JmxConnectionService {
    
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int CACHE_EXPIRY_MINUTES = 5;
    
    // Connection cache with automatic expiry to prevent stale connections
    private final Cache<String, JMXConnector> connectionCache;
    
    // Per-broker locks to prevent concurrent connection attempts
    // Using Cache to avoid memory leak from accumulated locks for old brokers
    private final Cache<String, ReentrantLock> brokerLocks;
    
    public JmxConnectionService() {
        this.connectionCache = Caffeine.newBuilder()
                .expireAfterAccess(CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
                .removalListener((key, connector, cause) -> closeQuietly((JMXConnector) connector))
                .build();
                
        this.brokerLocks = Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();
    }
    
    /**
     * Collect JMX metrics from a broker.
     * Uses caching and locking to prevent race conditions.
     */
    public BrokerJmxMetrics collectMetrics(KafkaCluster cluster, String host, int jmxPort) {
        if (!cluster.isJmxEnabled() || cluster.getJmxPort() == null) {
            log.debug("JMX not enabled for cluster: {}", cluster.getName());
            return null;
        }
        
        String cacheKey = buildCacheKey(host, jmxPort);
        ReentrantLock lock = brokerLocks.get(cacheKey, k -> new ReentrantLock());
        
        // Acquire lock with timeout to prevent deadlocks
        boolean acquired = false;
        try {
            acquired = lock.tryLock(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("Timeout acquiring lock for JMX connection to {}:{}", host, jmxPort);
                return null;
            }
            
            return collectMetricsInternal(cluster, host, jmxPort, cacheKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for JMX lock", e);
            return null;
        } finally {
            if (acquired) {
                lock.unlock();
            }
        }
    }
    
    /**
     * Test JMX connection to a cluster.
     */
    public boolean testConnection(KafkaCluster cluster, String host, int jmxPort) {
        JMXConnector connector = null;
        try {
            connector = createConnectionWithTimeout(cluster, host, jmxPort);
            MBeanServerConnection mbs = connector.getMBeanServerConnection();
            // Simple test - get domain count
            mbs.getDomains();
            return true;
        } catch (Exception e) {
            log.debug("JMX connection test failed for {}:{}: {}", host, jmxPort, e.getMessage());
            return false;
        } finally {
            closeQuietly(connector);
        }
    }
    
    private BrokerJmxMetrics collectMetricsInternal(KafkaCluster cluster, String host, int jmxPort, String cacheKey) {
        JMXConnector connector = null;
        try {
            // Try to reuse cached connection
            connector = connectionCache.getIfPresent(cacheKey);
            
            if (connector == null) {
                connector = createConnectionWithTimeout(cluster, host, jmxPort);
                connectionCache.put(cacheKey, connector);
            }
            
            return collectKafkaBrokerMetrics(connector.getMBeanServerConnection());
        } catch (Exception e) {
            // Only log warning if it's not just a connection refused (common during startup)
            if (!(e instanceof IOException)) {
                log.warn("Failed to collect JMX metrics from {}:{}: {}", host, jmxPort, e.getMessage());
            } else {
                log.debug("Failed to connect to JMX at {}:{}: {}", host, jmxPort, e.getMessage());
            }
            
            // Remove failed connection from cache
            connectionCache.invalidate(cacheKey);
            // If we created a new connection that failed and wasn't put in cache yet (or was invalid), close it
            if (connector != null) {
                // Check if it's the one in cache (unlikely if we just invalidated, but good practice)
                if (connectionCache.getIfPresent(cacheKey) != connector) {
                    closeQuietly(connector);
                }
            }
            return null;
        }
    }
    
    private JMXConnector createConnectionWithTimeout(KafkaCluster cluster, String host, int jmxPort) throws IOException, TimeoutException, ExecutionException, InterruptedException {
        CompletableFuture<JMXConnector> future = CompletableFuture.supplyAsync(() -> {
            try {
                return createConnection(cluster, host, jmxPort);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });

        try {
            return future.get(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }
    }

    private JMXConnector createConnection(KafkaCluster cluster, String host, int jmxPort) throws IOException {
        String jmxUrl = String.format("service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi", host, jmxPort);
        JMXServiceURL serviceUrl = new JMXServiceURL(jmxUrl);
        
        Map<String, Object> env = new HashMap<>();
        
        // Configure authentication if enabled
        if (cluster.isJmxAuthentication() && cluster.getJmxUsername() != null) {
            String[] credentials = { cluster.getJmxUsername(), cluster.getJmxPassword() };
            env.put(JMXConnector.CREDENTIALS, credentials);
        }
        
        // Configure SSL if enabled
        if (cluster.isJmxSsl()) {
            // SSL is configured via system properties for JMX
            // javax.net.ssl.trustStore, javax.net.ssl.trustStorePassword, etc.
            env.put("com.sun.jndi.rmi.factory.socket", javax.rmi.ssl.SslRMIClientSocketFactory.class);
        }
        
        // Set connection timeout properties (as a backup to our CompletableFuture timeout)
        env.put("jmx.remote.x.request.waiting.timeout", String.valueOf(CONNECTION_TIMEOUT_MS));
        env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(CONNECTION_TIMEOUT_MS));
        
        return JMXConnectorFactory.connect(serviceUrl, env);
    }
    
    private BrokerJmxMetrics collectKafkaBrokerMetrics(MBeanServerConnection mbs) throws Exception {
        BrokerJmxMetrics metrics = new BrokerJmxMetrics();
        
        // OS metrics (CPU)
        try {
            ObjectName osName = new ObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
            
            // CPU - try different attributes depending on JVM
            try {
                Object cpuLoad = mbs.getAttribute(osName, "ProcessCpuLoad");
                if (cpuLoad instanceof Number) {
                    metrics.setCpuUsagePercent(((Number) cpuLoad).doubleValue() * 100);
                }
            } catch (AttributeNotFoundException e) {
                // Not available on all JVMs
            }
        } catch (Exception e) {
            log.debug("Failed to collect CPU metrics: {}", e.getMessage());
        }
        
        // JVM Heap Memory (not system RAM!)
        try {
            ObjectName memoryName = new ObjectName(ManagementFactory.MEMORY_MXBEAN_NAME);
            javax.management.openmbean.CompositeData heapMemory = 
                (javax.management.openmbean.CompositeData) mbs.getAttribute(memoryName, "HeapMemoryUsage");
            
            if (heapMemory != null) {
                Long used = (Long) heapMemory.get("used");
                Long max = (Long) heapMemory.get("max");
                metrics.setMemoryUsedBytes(used);
                metrics.setMemoryMaxBytes(max > 0 ? max : (Long) heapMemory.get("committed"));
            }
        } catch (Exception e) {
            log.debug("Failed to collect JVM heap metrics: {}", e.getMessage());
        }
        
        // Kafka-specific metrics
        try {
            // Bytes In/Out per second
            metrics.setBytesInPerSecond(getKafkaMetricValue(mbs, 
                "kafka.server:type=BrokerTopicMetrics,name=BytesInPerSec", "OneMinuteRate"));
            metrics.setBytesOutPerSecond(getKafkaMetricValue(mbs,
                "kafka.server:type=BrokerTopicMetrics,name=BytesOutPerSec", "OneMinuteRate"));
            
            // Messages In per second
            metrics.setMessagesInPerSecond(getKafkaMetricValue(mbs,
                "kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec", "OneMinuteRate"));
            
            // Request rate
            metrics.setRequestsPerSecond(getKafkaMetricValue(mbs,
                "kafka.network:type=RequestMetrics,name=RequestsPerSec,request=Produce", "OneMinuteRate"));
        } catch (Exception e) {
            log.debug("Failed to collect Kafka metrics: {}", e.getMessage());
        }
        
        // Disk metrics (log directory size)
        try {
            Object diskUsed = mbs.getAttribute(
                new ObjectName("kafka.log:type=Log,name=Size"), "Value");
            if (diskUsed instanceof Number) {
                metrics.setDiskUsedBytes(((Number) diskUsed).longValue());
            }
        } catch (Exception e) {
            log.debug("Failed to collect disk metrics: {}", e.getMessage());
        }
        
        return metrics;
    }
    
    private Long getKafkaMetricValue(MBeanServerConnection mbs, String objectName, String attribute) {
        try {
            Object value = mbs.getAttribute(new ObjectName(objectName), attribute);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (Exception e) {
            // Metric not available
        }
        return null;
    }
    
    private String buildCacheKey(String host, int port) {
        return host + ":" + port;
    }
    
    private void closeQuietly(JMXConnector connector) {
        if (connector != null) {
            try {
                connector.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
    
    /**
     * Shutdown the service and close all connections.
     */
    public void shutdown() {
        connectionCache.invalidateAll();
        // Caffeine invalidation happens asynchronously usually, but explicit cleanUp forces it
        connectionCache.cleanUp();
    }
    
}
