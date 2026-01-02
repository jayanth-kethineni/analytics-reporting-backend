package com.analytics.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Cache service for query results.
 * 
 * Uses Redis with circuit breaker for resilience.
 * 
 * Caching Strategy:
 * - Fast queries (< 1s): Cache for 5 minutes
 * - Aggregated queries: Cache for 1 hour
 * - Reports: Cache for 2 hours
 * 
 * Why Redis?
 * - Fast (1-2ms lookup)
 * - Shared across service instances
 * - TTL support (automatic eviction)
 * - LRU eviction when memory full
 * 
 * Failure Handling:
 * - Circuit breaker prevents cascading failures
 * - Falls back to database on Redis failure
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryCacheService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * Get cached query result.
     * 
     * Circuit breaker prevents Redis failures from blocking queries.
     */
    @CircuitBreaker(name = "redis", fallbackMethod = "getCacheFallback")
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String cached = redisTemplate.opsForValue().get(key);
            
            if (cached == null) {
                log.debug("Cache miss for key: {}", key);
                return Optional.empty();
            }
            
            T value = objectMapper.readValue(cached, type);
            log.debug("Cache hit for key: {}", key);
            return Optional.of(value);
            
        } catch (Exception e) {
            log.error("Error reading from cache: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Store query result in cache.
     */
    @CircuitBreaker(name = "redis", fallbackMethod = "setCacheFallback")
    public void set(String key, Object value, long ttlSeconds) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
            log.debug("Cached result for key: {} (TTL: {}s)", key, ttlSeconds);
            
        } catch (Exception e) {
            log.error("Error writing to cache: {}", e.getMessage());
            // Don't throw - cache write failure shouldn't fail the query
        }
    }
    
    /**
     * Invalidate cache entry.
     */
    @CircuitBreaker(name = "redis", fallbackMethod = "invalidateCacheFallback")
    public void invalidate(String key) {
        try {
            redisTemplate.delete(key);
            log.debug("Invalidated cache for key: {}", key);
            
        } catch (Exception e) {
            log.error("Error invalidating cache: {}", e.getMessage());
        }
    }
    
    /**
     * Generate cache key from query parameters.
     */
    public String generateCacheKey(String prefix, Object... params) {
        StringBuilder key = new StringBuilder(prefix);
        for (Object param : params) {
            key.append(":").append(param != null ? param.toString() : "null");
        }
        return key.toString();
    }
    
    // Fallback methods (circuit breaker)
    
    private <T> Optional<T> getCacheFallback(String key, Class<T> type, Exception e) {
        log.warn("Redis circuit breaker open, falling back to database");
        return Optional.empty();
    }
    
    private void setCacheFallback(String key, Object value, long ttlSeconds, Exception e) {
        log.warn("Redis circuit breaker open, skipping cache write");
    }
    
    private void invalidateCacheFallback(String key, Exception e) {
        log.warn("Redis circuit breaker open, skipping cache invalidation");
    }
}
