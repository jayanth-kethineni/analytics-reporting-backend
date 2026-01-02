package com.analytics.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Request model for event queries.
 * 
 * Supports filtering, sorting, and cursor-based pagination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventQueryRequest {
    
    private UUID userId;
    private String eventType;
    private Instant startTime;
    private Instant endTime;
    
    // Cursor-based pagination
    private UUID cursor;
    private Integer pageSize;
    
    // Defaults
    public Integer getPageSize() {
        if (pageSize == null || pageSize <= 0) {
            return 100;
        }
        return Math.min(pageSize, 1000); // Max 1000 per page
    }
    
    public Instant getStartTime() {
        if (startTime == null) {
            // Default: last 7 days
            return Instant.now().minusSeconds(7 * 24 * 3600);
        }
        return startTime;
    }
    
    public Instant getEndTime() {
        if (endTime == null) {
            return Instant.now();
        }
        return endTime;
    }
}
