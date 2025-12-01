package io.brokr.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async configuration for enterprise-level performance.
 * Optimized for metrics collection and heavy concurrent operations.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Thread pool for metrics collection operations.
     * Sized for enterprise clusters with many topics and consumer groups.
     */
    @Bean(name = "metricsCollectionExecutor")
    public Executor metricsCollectionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core pool size: enough threads for parallel cluster processing
        executor.setCorePoolSize(10);
        
        // Max pool size: handle peak load with many clusters
        executor.setMaxPoolSize(50);
        
        // Queue capacity: buffer for pending tasks
        executor.setQueueCapacity(200);
        
        // Thread name prefix for monitoring
        executor.setThreadNamePrefix("metrics-collect-");
        
        // Rejection policy: caller runs if queue is full (prevents task loss)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // Keep alive time for idle threads
        executor.setKeepAliveSeconds(60);
        
        // Allow core threads to timeout
        executor.setAllowCoreThreadTimeOut(false);
        
        executor.initialize();
        log.info("Metrics collection executor initialized: core={}, max={}, queue={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        // Default async executor for general async operations
        // Optimized for enterprise-scale with replay jobs and other async tasks
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);  // Increased for concurrent replay jobs
        executor.setMaxPoolSize(50);   // Increased for peak load
        executor.setQueueCapacity(500); // Increased queue for enterprise load
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(false);
        executor.initialize();
        log.info("Default async executor initialized: core={}, max={}, queue={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        return executor;
    }
    
    /**
     * Dedicated thread pool for replay job execution.
     * Separate pool to isolate replay operations from other async tasks.
     */
    @Bean(name = "replayJobExecutor")
    public Executor replayJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Sized for max concurrent replay jobs (configurable, default 5)
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);  // Allow burst capacity
        executor.setQueueCapacity(100); // Queue pending jobs
        
        executor.setThreadNamePrefix("replay-job-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setKeepAliveSeconds(300); // Keep threads alive longer for long-running jobs
        executor.setAllowCoreThreadTimeOut(false);
        
        executor.initialize();
        log.info("Replay job executor initialized: core={}, max={}, queue={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }
    
    /**
     * Dedicated thread pool for Kafka admin operations (consumer group offsets, topic descriptions).
     * Controls parallelism for batch Kafka API calls to prevent overwhelming the broker.
     */
    @Bean(name = "kafkaOperationsExecutor")
    public Executor kafkaOperationsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core pool: sized for typical batch operations
        executor.setCorePoolSize(5);
        
        // Max pool: limit concurrent Kafka API calls to prevent broker overload
        executor.setMaxPoolSize(20);
        
        // Larger queue to buffer operations during peak load
        // This prevents task rejection while still controlling parallelism
        executor.setQueueCapacity(200);
        
        executor.setThreadNamePrefix("kafka-ops-");
        
        // Use DiscardOldestPolicy for Kafka operations:
        // - Newer offset requests are more valuable than stale ones
        // - This provides graceful degradation under load
        // - Unlike CallerRunsPolicy, won't block web request threads
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        
        executor.initialize();
        log.info("Kafka operations executor initialized: core={}, max={}, queue={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }
}

