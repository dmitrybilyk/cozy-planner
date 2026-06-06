# Availability Interval Merging Feature

## Overview

This feature automatically merges consecutive availability intervals when a coach or trainee saves their availability. This improves data quality and user experience by preventing fragmented time slots.

## How It Works

### For Coaches
When a coach adds availability intervals:
- **Same date + Same location**: Consecutive intervals (back-to-back) are merged into a single interval
- **Same date + No location**: Consecutive intervals with null location IDs are merged
- **Different locations**: Intervals are NOT merged (kept separate)
- **Gaps**: Intervals with time gaps are NOT merged

**Example:**
- Input: `09:00-10:00` (location 1), `10:00-11:00` (location 1), `11:00-12:00` (location 1)
- Output: `09:00-12:00` (location 1) — single merged interval

### For Trainees
When a trainee adds availability intervals:
- **Same date**: All consecutive (back-to-back) intervals are merged
- Trainees don't have location constraints, so merging is simpler
- **Gaps**: Intervals with time gaps are NOT merged

**Example:**
- Input: `09:00-10:00`, `10:00-11:00`, `11:00-12:00`
- Output: `09:00-12:00` — single merged interval

## Implementation Details

### Service Layer
**File**: `com/cozy/planner/service/AvailabilityMergeService.java`

Core methods:
- `mergeCoachIntervals(List<CoachSlot>)` — Merges coach intervals
- `mergeTraineeIntervals(List<TraineeSlot>)` — Merges trainee intervals

Algorithm:
1. Group intervals by date
2. For coaches: also group by location within each date
3. Sort by start time
4. Iterate through sorted intervals and merge consecutive ones

### Controllers

**Coach Availability** (`com/cozy/planner/controllers/CoachAvailabilityController.java`)
- Endpoint: `POST /api/v1/coach/availability`
- The `setCoachAvailability` method now:
  1. Receives list of slot entries
  2. Converts to `CoachSlot` records
  3. Calls `mergeCoachIntervals()`
  4. Converts merged slots back to `MentorAvailability` entities
  5. Saves to database

**Trainee Availability** (`com/cozy/planner/controllers/AvailabilityController.java`)
- Endpoints: 
  - `POST /api/v1/trainee/availability`
  - `POST /api/v1/trainees/{traineeId}/availability`
- The `saveAvailabilityInternal` method now:
  1. Receives list of slot entries
  2. Converts to `TraineeSlot` records
  3. Calls `mergeTraineeIntervals()`
  4. Converts merged slots back to `TraineeAvailability` entities
  5. Saves to database

## Testing the Feature

### Coach Scenario
```bash
# Send availability with consecutive same-location intervals
curl -X POST http://localhost:8080/api/v1/coach/availability \
  -H "Content-Type: application/json" \
  -d '[
    {"date": "2026-06-02", "startTime": "09:00", "endTime": "10:00", "locationId": 1},
    {"date": "2026-06-02", "startTime": "10:00", "endTime": "11:00", "locationId": 1},
    {"date": "2026-06-02", "startTime": "11:00", "endTime": "12:00", "locationId": 1}
  ]'

# Result: One interval 09:00-12:00 is saved instead of three
```

### Trainee Scenario
```bash
# Send trainee availability with consecutive intervals
curl -X POST http://localhost:8080/api/v1/trainee/availability \
  -H "Content-Type: application/json" \
  -d '[
    {"date": "2026-06-02", "startTime": "09:00", "endTime": "10:00"},
    {"date": "2026-06-02", "startTime": "10:00", "endTime": "11:00"},
    {"date": "2026-06-02", "startTime": "11:00", "endTime": "12:00"}
  ]'

# Result: One interval 09:00-12:00 is saved instead of three
```

## Edge Cases Handled

1. **Empty input**: Returns empty result
2. **Single interval**: Returns as-is
3. **Multiple dates**: Merges are done per-date independently
4. **Multiple locations**: Merges only within same location (coach only)
5. **Null locations**: Treated as a separate group, consecutive null-location intervals are merged
6. **Time gaps**: NOT merged (preserves user's intent for gaps)
7. **Overlapping intervals**: Each is processed independently; the input is assumed to be valid

## Data Flow

```
User submits availability intervals
         ↓
Controller receives List<SlotEntry>
         ↓
Convert to CoachSlot/TraineeSlot
         ↓
Call AvailabilityMergeService.merge*Intervals()
         ↓
Merge logic processes:
  - Group by date
  - (Coach only) Group by location
  - Sort by start time
  - Merge consecutive intervals
         ↓
Convert back to entity objects (MentorAvailability/TraineeAvailability)
         ↓
Delete old records for affected dates
         ↓
Save merged records to database
         ↓
Broadcast availability_changed event
```

## Notes

- The merging happens ON SAVE, not on retrieval
- Database stores the merged intervals (not the original fragmented ones)
- This is a one-way operation; the original input intervals are not stored separately
- The feature respects timezone handling for coaches (if implemented)
- Event broadcasting (`availability_changed`) notifies other parts of the application (e.g., WebSocket listeners)

