# Immediate Response: Merged Availability Intervals

## Overview

After saving availability intervals, the server now returns the **merged intervals immediately** in the response. This allows the UI to update instantly without requiring a separate fetch request.

---

## Response Format

### Coach Availability Save
**Endpoint**: `POST /api/v1/coach/availability`

**Request**:
```json
[
  {"date": "2026-06-02", "startTime": "09:00", "endTime": "10:00", "locationId": 1},
  {"date": "2026-06-02", "startTime": "10:00", "endTime": "11:00", "locationId": 1},
  {"date": "2026-06-02", "startTime": "11:00", "endTime": "12:00", "locationId": 1}
]
```

**Response** (HTTP 200):
```json
[
  {
    "id": 101,
    "date": "2026-06-02",
    "startTime": "09:00:00",
    "endTime": "12:00:00",
    "locationId": 1
  }
]
```

**Key Points**:
- Input: 3 individual 1-hour slots
- Output: 1 merged 3-hour slot (with database ID)
- Immediate display ready
- No additional fetch needed

---

### Trainee Availability Save
**Endpoint**: `POST /api/v1/trainee/availability` or `POST /api/v1/trainees/{traineeId}/availability`

**Request**:
```json
[
  {"date": "2026-06-02", "startTime": "14:00", "endTime": "15:00"},
  {"date": "2026-06-02", "startTime": "15:00", "endTime": "16:00"},
  {"date": "2026-06-02", "startTime": "16:00", "endTime": "17:00"}
]
```

**Response** (HTTP 200):
```json
[
  {
    "id": 201,
    "date": "2026-06-02",
    "startTime": "14:00:00",
    "endTime": "17:00:00"
  }
]
```

**Key Points**:
- Input: 3 individual 1-hour slots
- Output: 1 merged 3-hour slot (with database ID)
- Immediate display ready
- No additional fetch needed

---

## Data Flow: Before vs After

### Before (Old Behavior)
```
Frontend sends intervals
         ↓
Server merges & saves
         ↓
Server responds: 200 OK (empty body)
         ↓
Frontend needs to request GET /api/v1/coach/availability
         ↓
Backend fetches from database
         ↓
Frontend updates UI
         
Total: 2 API calls + delay
```

### After (New Behavior)
```
Frontend sends intervals
         ↓
Server merges & saves
         ↓
Server responds: 200 OK {merged intervals + IDs}
         ↓
Frontend uses response to update UI immediately
         
Total: 1 API call + no delay
```

---

## UI Integration Example

### React/Frontend Pseudo-code

**Before**:
```javascript
// Two API calls needed
await saveAvailability(slots);  // Returns 200 OK
const updated = await getAvailability();  // Additional fetch
ui.updateCalendar(updated);
```

**After**:
```javascript
// Single API call, immediate response
const merged = await saveAvailability(slots);  // Returns merged data
ui.updateCalendar(merged);  // Use response directly
```

---

## Response Structure Details

### Coach Slots Response
Each slot object contains:
- `id`: Database record ID (Long)
- `date`: Date in ISO format (YYYY-MM-DD)
- `startTime`: Time in HH:mm:ss format
- `endTime`: Time in HH:mm:ss format
- `locationId`: Associated location ID (can be null)

### Trainee Slots Response
Each slot object contains:
- `id`: Database record ID (Long)
- `date`: Date in ISO format (YYYY-MM-DD)
- `startTime`: Time in HH:mm:ss format
- `endTime`: Time in HH:mm:ss format

---

## Empty Response Scenarios

**When**: Caller sends empty list of slots
**Response**: HTTP 200 with empty array `[]`
```json
[]
```

---

## Error Handling

**Invalid Input**: HTTP 400 (request validation fails)
**Unauthorized**: HTTP 403 (not authenticated)
**Not Found**: HTTP 404 (trainer/trainee not found)
**Success with merge**: HTTP 200 with merged data

---

## Merging Examples in Response

### Example 1: Multiple Consecutive Slots (Merged)
```json
Request:
[
  {"date": "2026-06-02", "startTime": "09:00", "endTime": "10:00", "locationId": 1},
  {"date": "2026-06-02", "startTime": "10:00", "endTime": "11:00", "locationId": 1}
]

Response: 
[
  {"id": 1, "date": "2026-06-02", "startTime": "09:00:00", "endTime": "11:00:00", "locationId": 1}
]
```

### Example 2: With Gap (Not Merged)
```json
Request:
[
  {"date": "2026-06-02", "startTime": "09:00", "endTime": "10:00", "locationId": 1},
  {"date": "2026-06-02", "startTime": "10:30", "endTime": "11:30", "locationId": 1}
]

Response:
[
  {"id": 1, "date": "2026-06-02", "startTime": "09:00:00", "endTime": "10:00:00", "locationId": 1},
  {"id": 2, "date": "2026-06-02", "startTime": "10:30:00", "endTime": "11:30:00", "locationId": 1}
]
```

### Example 3: Different Dates (Merged Per Date)
```json
Request:
[
  {"date": "2026-06-02", "startTime": "09:00", "endTime": "10:00", "locationId": 1},
  {"date": "2026-06-02", "startTime": "10:00", "endTime": "11:00", "locationId": 1},
  {"date": "2026-06-03", "startTime": "09:00", "endTime": "10:00", "locationId": 1},
  {"date": "2026-06-03", "startTime": "10:00", "endTime": "11:00", "locationId": 1}
]

Response:
[
  {"id": 1, "date": "2026-06-02", "startTime": "09:00:00", "endTime": "11:00:00", "locationId": 1},
  {"id": 2, "date": "2026-06-03", "startTime": "09:00:00", "endTime": "11:00:00", "locationId": 1}
]
```

---

## Event Broadcasting

In addition to the response payload:
- `coach_availability_changed` event is broadcast for coach updates
- `availability_changed` event is broadcast for trainee updates
- WebSocket listeners are notified in real-time
- Notification to mentor is sent (for trainee changes)

---

## Backward Compatibility

⚠️ **Breaking Change**: Response format changed from `void` to `List<Map<String, Object>>`

**Migration Steps for Frontend**:
1. Update response handling to process list of merged slots
2. Use `id` field to track saved slots
3. Display merged intervals immediately
4. Remove redundant `GET` calls after save

---

## Performance Benefits

| Metric | Before | After |
|--------|--------|-------|
| API Calls | 2 | 1 |
| Network Latency | 2x RTT | 1x RTT |
| Time to Display | ~500ms + fetch | ~250ms |
| User Perception | "saving..." then fetch refresh | Instant update |

---

## Implementation Details

### CoachAvailabilityController
- Return type changed: `Mono<ResponseEntity<Void>>` → `Mono<ResponseEntity<List<Map<String, Object>>>>`
- Method: `setCoachAvailability()` (line 86-137)

### AvailabilityController
- Return type changed: `Mono<ResponseEntity<Void>>` → `Mono<ResponseEntity<List<Map<String, Object>>>>`
- Methods: 
  - `setAvailability()` (line 158-163)
  - `setAvailabilityById()` (line 151-156)
- Helper: `saveAvailabilityInternal()` (line 187-237)

### Response Building
```java
List<Map<String, Object>> response = savedList.stream()
    .map(s -> {
        Map<String, Object> slot = new HashMap<>();
        slot.put("id", s.getId());
        slot.put("date", s.getDate().toString());
        slot.put("startTime", s.getStartTime().toString());
        slot.put("endTime", s.getEndTime().toString());
        slot.put("locationId", s.getLocationId());  // coach only
        return slot;
    })
    .toList();
return Mono.just(ResponseEntity.ok(response));
```

---

**Implementation Status**: ✅ COMPLETE
**Compilation Status**: ✅ SUCCESSFUL
**API Breaking Change**: ⚠️ YES - Update frontend accordingly

