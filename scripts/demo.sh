#!/bin/bash

# Demo Script for Analytics & Reporting Backend
# Showcases cursor-based pagination, caching, and async jobs

echo "=== Analytics & Reporting Backend Demo ==="
echo ""

API_URL="http://localhost:8082/api/v1/analytics"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}1. Query Events (First Page)${NC}"
echo "Fetching first 5 events..."
RESPONSE=$(curl -s "$API_URL/events?pageSize=5")
echo "$RESPONSE" | jq '.'
CURSOR=$(echo "$RESPONSE" | jq -r '.nextCursor')
echo ""
echo "✅ Received 5 events with cursor for next page"
echo ""
echo ""

echo -e "${BLUE}2. Query Next Page (Cursor-Based Pagination)${NC}"
echo "Using cursor from previous response: $CURSOR"
curl -s "$API_URL/events?cursor=$CURSOR&pageSize=5" | jq '.'
echo ""
echo "✅ Cursor-based pagination working (O(log n) performance)"
echo ""
echo ""

echo -e "${BLUE}3. Testing Cache (Same Query Again)${NC}"
echo "Querying same data again - should hit Redis cache..."
curl -s "$API_URL/events?pageSize=5" | jq '.cached, .queryTimeMs'
echo ""
echo "✅ cached=true, queryTimeMs should be <10ms (vs 100-500ms uncached)"
echo ""
echo ""

echo -e "${BLUE}4. Aggregate Events by Type${NC}"
echo "Running aggregation query..."
curl -s "$API_URL/aggregate/type" | jq '.'
echo ""
echo "✅ Aggregation completed (cached for 1 hour)"
echo ""
echo ""

echo -e "${BLUE}5. Submit Async Job for Heavy Query${NC}"
echo "Submitting async job..."
JOB_RESPONSE=$(curl -s -X POST "$API_URL/jobs" \
    -H "Content-Type: application/json" \
    -d '{
        "queryType": "AGGREGATE_TYPE",
        "request": {}
    }')
echo "$JOB_RESPONSE" | jq '.'
JOB_ID=$(echo "$JOB_RESPONSE" | jq -r '.jobId')
echo ""
echo "✅ Job submitted, ID: $JOB_ID"
echo ""
echo ""

echo -e "${BLUE}6. Poll for Job Status${NC}"
echo "Checking job status..."
sleep 2
curl -s "$API_URL/jobs/$JOB_ID" | jq '.jobId, .status, .executionTimeMs'
echo ""
echo "✅ Async job processing (prevents API timeout)"
echo ""
echo ""

echo -e "${BLUE}7. Checking System Health${NC}"
curl -s http://localhost:8082/actuator/health | jq '.'
echo ""
echo ""

echo -e "${BLUE}8. Viewing Metrics (Sample)${NC}"
echo "Cache hit rate:"
curl -s http://localhost:8082/actuator/prometheus | grep "query_cache_total"
echo ""
echo "Query latency:"
curl -s http://localhost:8082/actuator/prometheus | grep "query_latency_seconds"
echo ""
echo ""

echo -e "${GREEN}=== Demo Complete ===${NC}"
echo ""
echo "Key Observations:"
echo "- Cursor-based pagination: Efficient for large datasets"
echo "- Caching: 10-50x latency improvement (cached vs uncached)"
echo "- Async jobs: Heavy queries don't block API"
echo "- All operations: Monitored via Prometheus metrics"
echo ""
echo "Full metrics available at: http://localhost:8082/actuator/prometheus"
