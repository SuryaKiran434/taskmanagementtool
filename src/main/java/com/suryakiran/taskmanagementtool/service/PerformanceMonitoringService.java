package com.suryakiran.taskmanagementtool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Service for monitoring application performance metrics.
 * Implements Observer pattern for performance monitoring.
 */
@Service
public class PerformanceMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitoringService.class);
    
    private final ConcurrentHashMap<String, LongAdder> requestCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> responseTimeAccumulators = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> minResponseTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> maxResponseTimes = new ConcurrentHashMap<>();

    /**
     * Records the start time of an operation
     * @param operationName the name of the operation
     * @return the start time in milliseconds
     */
    public long startOperation(String operationName) {
        return System.currentTimeMillis();
    }

    /**
     * Records the completion of an operation and calculates metrics
     * @param operationName the name of the operation
     * @param startTime the start time returned by startOperation
     */
    public void endOperation(String operationName, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        
        // Update counters
        requestCounters.computeIfAbsent(operationName, k -> new LongAdder()).increment();
        
        // Update response time metrics
        responseTimeAccumulators.computeIfAbsent(operationName, k -> new AtomicLong(0))
                .addAndGet(duration);
        
        // Update min response time
        minResponseTimes.computeIfAbsent(operationName, k -> new AtomicLong(Long.MAX_VALUE))
                .updateAndGet(current -> Math.min(current, duration));
        
        // Update max response time
        maxResponseTimes.computeIfAbsent(operationName, k -> new AtomicLong(0))
                .updateAndGet(current -> Math.max(current, duration));
        
        // Log slow operations
        if (duration > 1000) { // Log operations taking more than 1 second
            logger.warn("Slow operation detected: {} took {}ms", operationName, duration);
        }
    }

    /**
     * Gets performance statistics for an operation
     * @param operationName the name of the operation
     * @return performance statistics
     */
    public OperationStats getOperationStats(String operationName) {
        LongAdder counter = requestCounters.get(operationName);
        AtomicLong accumulator = responseTimeAccumulators.get(operationName);
        AtomicLong minTime = minResponseTimes.get(operationName);
        AtomicLong maxTime = maxResponseTimes.get(operationName);
        
        if (counter == null || counter.sum() == 0) {
            return new OperationStats(operationName, 0, 0, 0, 0);
        }
        
        long count = counter.sum();
        long totalTime = accumulator.get();
        long avgTime = totalTime / count;
        long min = minTime.get();
        long max = maxTime.get();
        
        return new OperationStats(operationName, count, avgTime, min, max);
    }

    /**
     * Gets all operation statistics
     * @return map of operation names to their statistics
     */
    public java.util.Map<String, OperationStats> getAllOperationStats() {
        java.util.Map<String, OperationStats> stats = new ConcurrentHashMap<>();
        
        requestCounters.keySet().forEach(operationName -> 
            stats.put(operationName, getOperationStats(operationName)));
        
        return stats;
    }

    /**
     * Resets all performance metrics
     */
    public void resetMetrics() {
        requestCounters.clear();
        responseTimeAccumulators.clear();
        minResponseTimes.clear();
        maxResponseTimes.clear();
        logger.info("Performance metrics have been reset");
    }

    /**
     * Logs current performance summary
     */
    public void logPerformanceSummary() {
        logger.info("=== Performance Summary ===");
        getAllOperationStats().values().forEach(stats -> 
            logger.info("{}: {} requests, avg: {}ms, min: {}ms, max: {}ms", 
                stats.operationName, stats.requestCount, stats.averageResponseTime, 
                stats.minResponseTime, stats.maxResponseTime));
        logger.info("==========================");
    }

    /**
     * Inner class to hold operation statistics
     */
    public static class OperationStats {
        public final String operationName;
        public final long requestCount;
        public final long averageResponseTime;
        public final long minResponseTime;
        public final long maxResponseTime;

        public OperationStats(String operationName, long requestCount, 
                           long averageResponseTime, long minResponseTime, long maxResponseTime) {
            this.operationName = operationName;
            this.requestCount = requestCount;
            this.averageResponseTime = averageResponseTime;
            this.minResponseTime = minResponseTime;
            this.maxResponseTime = maxResponseTime;
        }
    }
}
