package com.analytics.infrastructure.persistence.repository;

import com.analytics.infrastructure.persistence.entity.EventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for event analytics queries.
 * 
 * Uses cursor-based pagination for efficient large dataset traversal.
 */
@Repository
public interface EventRepository extends JpaRepository<EventEntity, UUID> {
    
    /**
     * Cursor-based pagination query.
     * 
     * Instead of OFFSET (which scans all skipped rows),
     * we use WHERE eventId > cursor for O(log n) performance.
     */
    @Query("SELECT e FROM EventEntity e WHERE " +
           "(:cursor IS NULL OR e.eventId > :cursor) AND " +
           "(:userId IS NULL OR e.userId = :userId) AND " +
           "(:eventType IS NULL OR e.eventType = :eventType) AND " +
           "e.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY e.eventId ASC")
    List<EventEntity> findEventsCursor(
            @Param("cursor") UUID cursor,
            @Param("userId") UUID userId,
            @Param("eventType") String eventType,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            Pageable pageable
    );
    
    /**
     * Count query for total results.
     */
    @Query("SELECT COUNT(e) FROM EventEntity e WHERE " +
           "(:userId IS NULL OR e.userId = :userId) AND " +
           "(:eventType IS NULL OR e.eventType = :eventType) AND " +
           "e.timestamp BETWEEN :startTime AND :endTime")
    long countEvents(
            @Param("userId") UUID userId,
            @Param("eventType") String eventType,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );
    
    /**
     * Aggregation query for event counts by type.
     * 
     * This is an expensive query that should be cached.
     */
    @Query("SELECT e.eventType, COUNT(e) FROM EventEntity e WHERE " +
           "e.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY e.eventType " +
           "ORDER BY COUNT(e) DESC")
    List<Object[]> aggregateEventsByType(
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );
    
    /**
     * Time-series aggregation query.
     * 
     * Groups events by hour for time-series visualization.
     */
    @Query(value = "SELECT " +
           "DATE_TRUNC('hour', timestamp) as hour, " +
           "COUNT(*) as count " +
           "FROM events " +
           "WHERE timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY hour " +
           "ORDER BY hour ASC",
           nativeQuery = true)
    List<Object[]> aggregateEventsByHour(
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );
}
