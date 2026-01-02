package com.analytics.domain.service;

import com.analytics.domain.model.EventQueryRequest;
import com.analytics.domain.model.EventQueryResponse;
import com.analytics.infrastructure.cache.QueryCacheService;
import com.analytics.infrastructure.persistence.entity.EventEntity;
import com.analytics.infrastructure.persistence.repository.EventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Query service for analytics data.
 * 
 * Query Flow:
 * 1. Generate cache key from query parameters
 * 2. Check cache (Redis)
 * 3. If cache miss, query database
 * 4. Store result in cache
 * 5. Return result
 * 
 * Performance Optimization:
 * - Cursor-based pagination (O(log n) vs O(n))
 * - Redis caching (1-2ms vs 100-500ms database query)
 * - Query timeout (10 seconds)
 * - Async job for heavy queries (>5 seconds)
 * 
 * Caching Strategy:
 * - Fast queries (<1s): Cache for 5 minutes
 * - Aggregated queries: Cache for 1 hour
 * - Reports: Cache for 2 hours
 * 
 * Tradeoff: Freshness vs Performance
 * - Cached data may be stale (up to TTL)
 * - Acceptable for analytics (not real-time)
 * - Can invalidate cache on data updates if needed
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryService {
    
    private final EventRepository eventRepository;
    private final QueryCacheService cacheService;
    private final MeterRegistry meterRegistry;
    
    @Value("${app.query.timeout-seconds:10}")
    private int queryTimeoutSeconds;
    
    @Value("${app.cache.ttl.fast-query:300}")
    private long fastQueryTtl;
    
    /**
     * Query events with caching.
     * 
     * Uses cursor-based pagination for efficient large dataset traversal.
     */
    @Transactional(readOnly = true, timeout = 10)
    public EventQueryResponse queryEvents(EventQueryRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Generate cache key
            String cacheKey = cacheService.generateCacheKey(
                    "query:events",
                    request.getUserId(),
                    request.getEventType(),
                    request.getStartTime(),
                    request.getEndTime(),
                    request.getCursor(),
                    request.getPageSize()
            );
            
            // Check cache
            Optional<EventQueryResponse> cached = cacheService.get(cacheKey, EventQueryResponse.class);
            
            if (cached.isPresent()) {
                log.debug("Cache hit for query: {}", cacheKey);
                
                Counter.builder("query.cache")
                        .tag("result", "hit")
                        .register(meterRegistry)
                        .increment();
                
                EventQueryResponse response = cached.get();
                response.setCached(true);
                return response;
            }
            
            // Cache miss - query database
            log.debug("Cache miss for query: {}", cacheKey);
            
            Counter.builder("query.cache")
                    .tag("result", "miss")
                    .register(meterRegistry)
                    .increment();
            
            long startTime = System.currentTimeMillis();
            
            // Execute query with cursor-based pagination
            List<EventEntity> events = eventRepository.findEventsCursor(
                    request.getCursor(),
                    request.getUserId(),
                    request.getEventType(),
                    request.getStartTime(),
                    request.getEndTime(),
                    PageRequest.of(0, request.getPageSize() + 1) // +1 to check if more results
            );
            
            // Determine if more results exist
            boolean hasMore = events.size() > request.getPageSize();
            if (hasMore) {
                events = events.subList(0, request.getPageSize());
            }
            
            // Get next cursor (last event ID)
            UUID nextCursor = hasMore && !events.isEmpty() 
                    ? events.get(events.size() - 1).getEventId() 
                    : null;
            
            // Count total results (expensive, cache this separately)
            long totalCount = eventRepository.countEvents(
                    request.getUserId(),
                    request.getEventType(),
                    request.getStartTime(),
                    request.getEndTime()
            );
            
            long queryTime = System.currentTimeMillis() - startTime;
            
            EventQueryResponse response = EventQueryResponse.builder()
                    .events(events)
                    .nextCursor(nextCursor)
                    .hasMore(hasMore)
                    .totalCount(totalCount)
                    .cached(false)
                    .queryTimeMs(queryTime)
                    .build();
            
            // Cache result
            cacheService.set(cacheKey, response, fastQueryTtl);
            
            // Record metrics
            sample.stop(Timer.builder("query.latency")
                    .tag("type", "events")
                    .tag("cached", "false")
                    .register(meterRegistry));
            
            Counter.builder("query.executed")
                    .tag("type", "events")
                    .register(meterRegistry)
                    .increment();
            
            log.info("Query executed: {} events, {} ms", events.size(), queryTime);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error executing query: {}", e.getMessage(), e);
            
            Counter.builder("query.executed")
                    .tag("type", "events")
                    .tag("result", "error")
                    .register(meterRegistry)
                    .increment();
            
            throw new RuntimeException("Query execution failed", e);
        }
    }
    
    /**
     * Aggregate events by type.
     * 
     * This is an expensive query that should be cached for longer.
     */
    @Transactional(readOnly = true, timeout = 10)
    public List<Object[]> aggregateEventsByType(EventQueryRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String cacheKey = cacheService.generateCacheKey(
                    "query:aggregate:type",
                    request.getStartTime(),
                    request.getEndTime()
            );
            
            // Check cache
            Optional<List> cached = cacheService.get(cacheKey, List.class);
            
            if (cached.isPresent()) {
                log.debug("Cache hit for aggregation query");
                
                Counter.builder("query.cache")
                        .tag("result", "hit")
                        .tag("type", "aggregation")
                        .register(meterRegistry)
                        .increment();
                
                return (List<Object[]>) cached.get();
            }
            
            // Cache miss - execute expensive query
            log.debug("Cache miss for aggregation query");
            
            long startTime = System.currentTimeMillis();
            
            List<Object[]> result = eventRepository.aggregateEventsByType(
                    request.getStartTime(),
                    request.getEndTime()
            );
            
            long queryTime = System.currentTimeMillis() - startTime;
            
            // Cache for 1 hour (aggregation data changes slowly)
            cacheService.set(cacheKey, result, 3600);
            
            // Record metrics
            sample.stop(Timer.builder("query.latency")
                    .tag("type", "aggregation")
                    .tag("cached", "false")
                    .register(meterRegistry));
            
            log.info("Aggregation query executed: {} ms", queryTime);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error executing aggregation query: {}", e.getMessage(), e);
            throw new RuntimeException("Aggregation query failed", e);
        }
    }
    
    /**
     * Time-series aggregation by hour.
     * 
     * Used for time-series visualizations.
     */
    @Transactional(readOnly = true, timeout = 10)
    public List<Object[]> aggregateEventsByHour(EventQueryRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String cacheKey = cacheService.generateCacheKey(
                    "query:aggregate:hour",
                    request.getStartTime(),
                    request.getEndTime()
            );
            
            // Check cache
            Optional<List> cached = cacheService.get(cacheKey, List.class);
            
            if (cached.isPresent()) {
                log.debug("Cache hit for time-series query");
                return (List<Object[]>) cached.get();
            }
            
            // Cache miss - execute query
            long startTime = System.currentTimeMillis();
            
            List<Object[]> result = eventRepository.aggregateEventsByHour(
                    request.getStartTime(),
                    request.getEndTime()
            );
            
            long queryTime = System.currentTimeMillis() - startTime;
            
            // Cache for 1 hour
            cacheService.set(cacheKey, result, 3600);
            
            // Record metrics
            sample.stop(Timer.builder("query.latency")
                    .tag("type", "timeseries")
                    .tag("cached", "false")
                    .register(meterRegistry));
            
            log.info("Time-series query executed: {} ms", queryTime);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error executing time-series query: {}", e.getMessage(), e);
            throw new RuntimeException("Time-series query failed", e);
        }
    }
}
