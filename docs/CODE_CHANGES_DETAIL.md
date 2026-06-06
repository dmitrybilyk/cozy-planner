# Code Changes: Immediate UI Updates

## Quick Reference: What Was Changed

### 1. Coach Availability Controller

**File**: `planner/src/main/java/com/cozy/planner/controllers/CoachAvailabilityController.java`

**Method**: `setCoachAvailability()` (Line 86-137)

**Before**:
```java
@PostMapping("/api/v1/coach/availability")
public Mono<ResponseEntity<Void>> setCoachAvailability(@RequestBody List<SlotEntry> entries,
                                                       ServerWebExchange exchange) {
    // ... merge logic ...
    .then()
    .then(Mono.fromRunnable(() -> eventService.broadcast("coach_availability_changed")))
    .then(Mono.just(ResponseEntity.ok().<Void>build()));
}
```

**After**:
```java
@PostMapping("/api/v1/coach/availability")
public Mono<ResponseEntity<List<Map<String, Object>>>> setCoachAvailability(@RequestBody List<SlotEntry> entries,
                                                        ServerWebExchange exchange) {
    // ... merge logic ...
    return Flux.fromIterable(uniqueDates)
            .flatMap(date -> availabilityRepository.findByMentorIdAndDate(mentorId, date))
            .flatMap(availabilityRepository::delete)
            .thenMany(Flux.fromIterable(toSave))
            .flatMap(availabilityRepository::save)
            .collectList()  // ← Collect saved results
            .flatMap(savedList -> {
                eventService.broadcast("coach_availability_changed");
                List<Map<String, Object>> response = savedList.stream()
                        .map(s -> {
                            Map<String, Object> slot = new HashMap<>();
                            slot.put("id", s.getId());
                            slot.put("date", s.getDate().toString());
                            slot.put("startTime", s.getStartTime().toString());
                            slot.put("endTime", s.getEndTime().toString());
                            slot.put("locationId", s.getLocationId());
                            return slot;
                        })
                        .toList();
                return Mono.just(ResponseEntity.ok(response));  // ← Return response with data
            });
}
```

**Key Changes**:
1. Return type: `Mono<ResponseEntity<Void>>` → `Mono<ResponseEntity<List<Map<String, Object>>>>`
2. Added: `.collectList()` to gather saved entities
3. Added: Response mapping loop to build response objects
4. Changed: `Mono.just(ResponseEntity.ok().build())` → `Mono.just(ResponseEntity.ok(response))`

---

### 2. Trainee Availability Controller - Method Signatures

**File**: `planner/src/main/java/com/cozy/planner/controllers/AvailabilityController.java`

**Line 151-163**:

**Before**:
```java
@PostMapping(path = {"/api/v1/trainees/{traineeId}/availability"})
public Mono<ResponseEntity<Void>> setAvailabilityById(@PathVariable Long traineeId,
                                                       @RequestBody List<SlotEntry> entries,
                                                       ServerWebExchange exchange) {
    return saveAvailabilityInternal(traineeId, entries, baseUrl(exchange));
}

@PostMapping(path = {"/api/v1/trainee/availability"})
public Mono<ResponseEntity<Void>> setAvailability(@RequestBody List<SlotEntry> entries,
                                                     ServerWebExchange exchange) {
    return getTraineeId(exchange)
            .flatMap(traineeId -> saveAvailabilityInternal(traineeId, entries, baseUrl(exchange)));
}
```

**After**:
```java
@PostMapping(path = {"/api/v1/trainees/{traineeId}/availability"})
public Mono<ResponseEntity<List<Map<String, Object>>>> setAvailabilityById(@PathVariable Long traineeId,
                                                            @RequestBody List<SlotEntry> entries,
                                                            ServerWebExchange exchange) {
    return saveAvailabilityInternal(traineeId, entries, baseUrl(exchange));
}

@PostMapping(path = {"/api/v1/trainee/availability"})
public Mono<ResponseEntity<List<Map<String, Object>>>> setAvailability(@RequestBody List<SlotEntry> entries,
                                                         ServerWebExchange exchange) {
    return getTraineeId(exchange)
            .flatMap(traineeId -> saveAvailabilityInternal(traineeId, entries, baseUrl(exchange)));
}
```

**Changes**:
- Return type: `Mono<ResponseEntity<Void>>` → `Mono<ResponseEntity<List<Map<String, Object>>>>`

---

### 3. Trainee Availability Controller - Core Logic

**File**: `planner/src/main/java/com/cozy/planner/controllers/AvailabilityController.java`

**Method**: `saveAvailabilityInternal()` (Line 187-237)

**Before**:
```java
private Mono<ResponseEntity<Void>> saveAvailabilityInternal(Long traineeId, List<SlotEntry> entries, String baseUrl) {
    // ... merge logic ...
    return traineeRepository.findById(traineeId)
            .defaultIfEmpty(Trainee.builder().mentorId(-1L).name("невідомий").build())
            .flatMap(trainee -> Flux.fromIterable(uniqueDates)
                    .flatMap(date -> availabilityRepository.findByTraineeIdAndDate(traineeId, date))
                    .flatMap(availabilityRepository::delete)
                    .thenMany(Flux.fromIterable(toSave))
                    .flatMap(availabilityRepository::save)
                    .then()
                    .then(Mono.fromRunnable(() -> eventService.broadcast("availability_changed")))
                    .then(createAvailabilityNotification(trainee.getMentorId(), trainee.getName(), baseUrl))
                    .then(Mono.just(ResponseEntity.ok().<Void>build())));
}
```

**After**:
```java
private Mono<ResponseEntity<List<Map<String, Object>>>> saveAvailabilityInternal(Long traineeId, List<SlotEntry> entries, String baseUrl) {
    // ... merge logic ...
    return traineeRepository.findById(traineeId)
            .defaultIfEmpty(Trainee.builder().mentorId(-1L).name("невідомий").build())
            .flatMap(trainee -> Flux.fromIterable(uniqueDates)
                    .flatMap(date -> availabilityRepository.findByTraineeIdAndDate(traineeId, date))
                    .flatMap(availabilityRepository::delete)
                    .thenMany(Flux.fromIterable(toSave))
                    .flatMap(availabilityRepository::save)
                    .collectList()  // ← Collect saved results
                    .flatMap(savedList -> {
                        eventService.broadcast("availability_changed");
                        List<Map<String, Object>> response = savedList.stream()
                                .map(s -> {
                                    Map<String, Object> slot = new HashMap<>();
                                    slot.put("id", s.getId());
                                    slot.put("date", s.getDate().toString());
                                    slot.put("startTime", s.getStartTime().toString());
                                    slot.put("endTime", s.getEndTime().toString());
                                    return slot;
                                })
                                .toList();
                        return createAvailabilityNotification(trainee.getMentorId(), trainee.getName(), baseUrl)
                                .then(Mono.just(ResponseEntity.ok(response)));  // ← Return response with data
                    }));
}
```

**Key Changes**:
1. Return type: `Mono<ResponseEntity<Void>>` → `Mono<ResponseEntity<List<Map<String, Object>>>>`
2. Changed: `.then()` → `.collectList()` to gather saved entities
3. Changed: `.then(Mono.fromRunnable(...))` → `.flatMap(savedList -> { eventService.broadcast(...) })`
4. Added: Response mapping loop (same as coach)
5. Changed: Final response from `.then(Mono.just(ResponseEntity.ok().build()))` to include data

---

## Side-by-Side Comparison

### Response Building Pattern

**Generic Pattern Used in Both Controllers**:
```java
// After collecting saved entities
.collectList()
.flatMap(savedList -> {
    // Broadcast event
    eventService.broadcast("event_name");
    
    // Build response objects
    List<Map<String, Object>> response = savedList.stream()
            .map(s -> {
                Map<String, Object> slot = new HashMap<>();
                slot.put("id", s.getId());
                slot.put("date", s.getDate().toString());
                slot.put("startTime", s.getStartTime().toString());
                slot.put("endTime", s.getEndTime().toString());
                // Coach only: slot.put("locationId", s.getLocationId());
                return slot;
            })
            .toList();
    
    // Return response with data
    return Mono.just(ResponseEntity.ok(response));
})
```

---

## Reactive Stream Flow

### Before (Void Response)
```
save() → save() → save() 
    → then() 
    → broadcast() 
    → ResponseEntity.ok().build() 
    → HTTP 200 (empty)
```

### After (Data Response)
```
save() → save() → save() 
    → collectList()  ← NEW: Collect saved entities
    → map entities to Map objects  ← NEW: Build response
    → broadcast()
    → ResponseEntity.ok(data)  ← CHANGED: Return data
    → HTTP 200 (with merged data)
```

---

## Imports (All Already Present)

Both controllers already had:
- `java.util.HashMap`
- `java.util.List`
- `java.util.Map`
- `org.springframework.http.ResponseEntity`
- `reactor.core.publisher.Flux`
- `reactor.core.publisher.Mono`

No new imports were needed.

---

## Compilation Results

✅ **CoachAvailabilityController** - Compiles successfully
✅ **AvailabilityController** - Compiles successfully
✅ **No errors or warnings related to changes**

---

## Summary of Changes

| Component | Before | After | Impact |
|-----------|--------|-------|--------|
| **Coach Response Type** | `Void` | `List<Map>` | Breaking change |
| **Trainee Response Type** | `Void` | `List<Map>` | Breaking change |
| **Response Data** | None | Merged intervals | Frontend can use immediately |
| **API Calls Needed** | 2 (save + fetch) | 1 (save with response) | 50% reduction |
| **Database Changed** | No | No | Fully compatible |
| **Event Broadcasting** | Yes | Yes | Unchanged |

---

## Testing the Changes

### Verify with cURL

**Coach Availability**:
```bash
curl -X POST http://localhost:8080/api/v1/coach/availability \
  -H "Content-Type: application/json" \
  -d '[
    {"date": "2026-06-02", "startTime": "09:00", "endTime": "10:00", "locationId": 1},
    {"date": "2026-06-02", "startTime": "10:00", "endTime": "11:00", "locationId": 1}
  ]'

# Expected Response: 200 OK with list of merged slots
[
  {
    "id": 1,
    "date": "2026-06-02",
    "startTime": "09:00:00",
    "endTime": "11:00:00",
    "locationId": 1
  }
]
```

**Trainee Availability**:
```bash
curl -X POST http://localhost:8080/api/v1/trainee/availability \
  -H "Content-Type: application/json" \
  -d '[
    {"date": "2026-06-02", "startTime": "14:00", "endTime": "15:00"},
    {"date": "2026-06-02", "startTime": "15:00", "endTime": "16:00"}
  ]'

# Expected Response: 200 OK with list of merged slots
[
  {
    "id": 2,
    "date": "2026-06-02",
    "startTime": "14:00:00",
    "endTime": "16:00:00"
  }
]
```

---

**Implementation Status**: ✅ COMPLETE
**Code Changes**: ✅ VERIFIED
**Compilation**: ✅ SUCCESSFUL

