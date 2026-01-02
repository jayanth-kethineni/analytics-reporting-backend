package com.analytics.api;

import com.analytics.domain.model.EventQueryRequest;
import com.analytics.domain.model.EventQueryResponse;
import com.analytics.domain.service.AsyncJobProcessor;
import com.analytics.domain.service.QueryService;
import com.analytics.infrastructure.persistence.entity.AsyncJobEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for analytics queries.
 * 
 * Endpoints:
 * - GET /api/v1/analytics/events - Query events with pagination
 * - GET /api/v1/analytics/aggregate/type - Aggregate by event type
 * - GET /api/v1/analytics/aggregate/hour - Time-series aggregation
 * - POST /api/v1/analytics/jobs - Submit async job
 * - GET /api/v1/analytics/jobs/{jobId} - Get job status
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    
    private final QueryService queryService;
    private final AsyncJobProcessor asyncJobProcessor;
    
    /**
     * Query events with cursor-based pagination.
     * 
     * GET /api/v1/analytics/events?userId=xxx&eventType=xxx&cursor=xxx&pageSize=100
     * 
     * Query Parameters:
     * - userId (optional): Filter by user ID
     * - eventType (optional): Filter by event type
     * - startTime (optional): Start of time range (ISO 8601)
     * - endTime (optional): End of time range (ISO 8601)
     * - cursor (optional): Cursor for pagination
     * - pageSize (optional): Page size (default: 100, max: 1000)
     * 
     * Response:
     * - events: List of events
     * - nextCursor: Cursor for next page
     * - hasMore: Whether more results exist
     * - totalCount: Total number of results
     * - cached: Whether result was cached
     * - queryTimeMs: Query execution time
     */
    @GetMapping("/events")
    public ResponseEntity<EventQueryResponse> queryEvents(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(required = false) Integer pageSize) {
        
        log.info("Query events: userId={}, eventType={}, cursor={}", userId, eventType, cursor);
        
        EventQueryRequest request = EventQueryRequest.builder()
                .userId(userId)
                .eventType(eventType)
                .cursor(cursor)
                .pageSize(pageSize)
                .build();
        
        EventQueryResponse response = queryService.queryEvents(request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Aggregate events by type.
     * 
     * GET /api/v1/analytics/aggregate/type?startTime=xxx&endTime=xxx
     * 
     * Returns event counts grouped by event type.
     */
    @GetMapping("/aggregate/type")
    public ResponseEntity<List<Object[]>> aggregateByType(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        
        log.info("Aggregate by type: startTime={}, endTime={}", startTime, endTime);
        
        EventQueryRequest request = EventQueryRequest.builder().build();
        
        List<Object[]> result = queryService.aggregateEventsByType(request);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Aggregate events by hour (time-series).
     * 
     * GET /api/v1/analytics/aggregate/hour?startTime=xxx&endTime=xxx
     * 
     * Returns event counts grouped by hour.
     */
    @GetMapping("/aggregate/hour")
    public ResponseEntity<List<Object[]>> aggregateByHour(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        
        log.info("Aggregate by hour: startTime={}, endTime={}", startTime, endTime);
        
        EventQueryRequest request = EventQueryRequest.builder().build();
        
        List<Object[]> result = queryService.aggregateEventsByHour(request);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Submit async job for heavy query.
     * 
     * POST /api/v1/analytics/jobs
     * 
     * Request body:
     * {
     *   "queryType": "EVENTS|AGGREGATE_TYPE|AGGREGATE_HOUR",
     *   "request": { ... }
     * }
     * 
     * Response:
     * {
     *   "jobId": "uuid"
     * }
     */
    @PostMapping("/jobs")
    public ResponseEntity<Map<String, UUID>> submitAsyncJob(
            @Valid @RequestBody Map<String, Object> payload) {
        
        String queryType = (String) payload.get("queryType");
        EventQueryRequest request = (EventQueryRequest) payload.get("request");
        
        log.info("Submit async job: queryType={}", queryType);
        
        UUID jobId = asyncJobProcessor.submitAsyncJob(queryType, request);
        
        return ResponseEntity.ok(Map.of("jobId", jobId));
    }
    
    /**
     * Get async job status and result.
     * 
     * GET /api/v1/analytics/jobs/{jobId}
     * 
     * Response:
     * {
     *   "jobId": "uuid",
     *   "status": "PENDING|RUNNING|COMPLETED|FAILED",
     *   "result": "...",
     *   "errorMessage": "...",
     *   "executionTimeMs": 1234
     * }
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<AsyncJobEntity> getJobStatus(@PathVariable UUID jobId) {
        log.info("Get job status: jobId={}", jobId);
        
        AsyncJobEntity job = asyncJobProcessor.getJobStatus(jobId);
        
        return ResponseEntity.ok(job);
    }
    
    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
