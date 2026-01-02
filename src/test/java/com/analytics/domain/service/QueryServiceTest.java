package com.analytics.domain.service;

import com.analytics.domain.model.EventQueryRequest;
import com.analytics.domain.model.EventQueryResponse;
import com.analytics.infrastructure.cache.QueryCacheService;
import com.analytics.infrastructure.persistence.entity.EventEntity;
import com.analytics.infrastructure.persistence.repository.EventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QueryService.
 * 
 * Tests caching behavior and pagination logic.
 * Had some issues with cursor pagination initially - these tests helped catch bugs.
 */
@ExtendWith(MockitoExtension.class)
class QueryServiceTest {
    
    @Mock
    private EventRepository eventRepository;
    
    @Mock
    private QueryCacheService cacheService;
    
    private MeterRegistry meterRegistry;
    private QueryService queryService;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        queryService = new QueryService(eventRepository, cacheService, meterRegistry);
    }
    
    @Test
    void testQueryEvents_CacheHit() {
        // Given
        EventQueryRequest request = EventQueryRequest.builder()
                .pageSize(10)
                .build();
        
        EventQueryResponse cachedResponse = EventQueryResponse.builder()
                .events(new ArrayList<>())
                .hasMore(false)
                .totalCount(0)
                .cached(true)
                .build();
        
        when(cacheService.get(anyString(), eq(EventQueryResponse.class)))
                .thenReturn(Optional.of(cachedResponse));
        
        // When
        EventQueryResponse result = queryService.queryEvents(request);
        
        // Then
        assertNotNull(result);
        assertTrue(result.isCached());
        
        // Verify repository was NOT called (cache hit)
        verify(eventRepository, never()).findEventsCursor(any(), any(), any(), any(), any(), any());
    }
    
    @Test
    void testQueryEvents_CacheMiss() {
        // Given
        EventQueryRequest request = EventQueryRequest.builder()
                .pageSize(10)
                .build();
        
        List<EventEntity> events = createTestEvents(10);
        
        when(cacheService.get(anyString(), eq(EventQueryResponse.class)))
                .thenReturn(Optional.empty());
        when(eventRepository.findEventsCursor(any(), any(), any(), any(), any(), any()))
                .thenReturn(events);
        when(eventRepository.countEvents(any(), any(), any(), any()))
                .thenReturn(100L);
        
        // When
        EventQueryResponse result = queryService.queryEvents(request);
        
        // Then
        assertNotNull(result);
        assertFalse(result.isCached());
        assertEquals(10, result.getEvents().size());
        assertEquals(100L, result.getTotalCount());
        
        // Verify repository was called
        verify(eventRepository).findEventsCursor(any(), any(), any(), any(), any(), any());
        
        // Verify result was cached
        verify(cacheService).set(anyString(), any(), anyLong());
    }
    
    @Test
    void testQueryEvents_Pagination_HasMore() {
        // Given
        EventQueryRequest request = EventQueryRequest.builder()
                .pageSize(10)
                .build();
        
        // Return 11 events (pageSize + 1) to indicate more results
        List<EventEntity> events = createTestEvents(11);
        
        when(cacheService.get(anyString(), eq(EventQueryResponse.class)))
                .thenReturn(Optional.empty());
        when(eventRepository.findEventsCursor(any(), any(), any(), any(), any(), any()))
                .thenReturn(events);
        when(eventRepository.countEvents(any(), any(), any(), any()))
                .thenReturn(100L);
        
        // When
        EventQueryResponse result = queryService.queryEvents(request);
        
        // Then
        assertNotNull(result);
        assertTrue(result.isHasMore());
        assertEquals(10, result.getEvents().size()); // Should trim to pageSize
        assertNotNull(result.getNextCursor());
    }
    
    @Test
    void testQueryEvents_Pagination_NoMore() {
        // Given
        EventQueryRequest request = EventQueryRequest.builder()
                .pageSize(10)
                .build();
        
        // Return exactly 10 events (no more results)
        List<EventEntity> events = createTestEvents(10);
        
        when(cacheService.get(anyString(), eq(EventQueryResponse.class)))
                .thenReturn(Optional.empty());
        when(eventRepository.findEventsCursor(any(), any(), any(), any(), any(), any()))
                .thenReturn(events);
        when(eventRepository.countEvents(any(), any(), any(), any()))
                .thenReturn(10L);
        
        // When
        EventQueryResponse result = queryService.queryEvents(request);
        
        // Then
        assertNotNull(result);
        assertFalse(result.isHasMore());
        assertNull(result.getNextCursor());
    }
    
    private List<EventEntity> createTestEvents(int count) {
        List<EventEntity> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(EventEntity.builder()
                    .eventId(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .eventType("TEST")
                    .source("test")
                    .timestamp(Instant.now())
                    .build());
        }
        return events;
    }
}
