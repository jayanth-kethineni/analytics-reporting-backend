import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const cacheHitRate = new Rate('cache_hits');
const latencyTrend = new Trend('latency_ms');

export const options = {
    stages: [
        { duration: '30s', target: 30 },
        { duration: '2m', target: 30 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        'http_req_duration': ['p(95)<2000'], // Target SLA
        'errors': ['rate<0.02'],
    },
};

// Mix of query patterns
const queryPatterns = [
    // Pattern 1: Recent events (should hit cache)
    () => `?pageSize=50`,
    
    // Pattern 2: User-specific (less likely to hit cache)
    () => `?userId=123e4567-e89b-12d3-a456-426614174000&pageSize=20`,
    
    // Pattern 3: Event type filter
    () => `?eventType=PAGE_VIEW&pageSize=30`,
    
    // Pattern 4: Pagination (cursor-based)
    () => `?cursor=${generateUUID()}&pageSize=25`,
];

export default function () {
    const pattern = queryPatterns[Math.floor(Math.random() * queryPatterns.length)];
    const url = `http://localhost:8082/api/v1/analytics/events${pattern()}`;
    
    const res = http.get(url);
    
    const success = check(res, {
        'status is 200': (r) => r.status === 200,
        'has events': (r) => r.json('events') !== undefined,
    });
    
    errorRate.add(!success);
    
    if (success && res.json()) {
        const body = res.json();
        cacheHitRate.add(body.cached === true);
        latencyTrend.add(body.queryTimeMs || 0);
    }
    
    sleep(0.2);
}

function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}
