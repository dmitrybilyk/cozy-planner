# Session Creation from Trainee Availability - Implementation Guide

## Overview

This feature allows coaches to quickly create sessions by clicking on trainee availability intervals in the trainee manager view. The system automatically:

1. **Fills the start time** with the clicked availability interval's start time
2. **Fills the end time** with start time + 1 hour
3. **Shows available time slots** - all intervals when trainee set one or many
4. **Excludes busy times** - hides any times that already have sessions scheduled
5. **Uses coach's timezone** - all times display in the coach's configured timezone

---

## API Endpoint

### Get Session Creation Suggestion

**Endpoint**: `GET /api/v1/sessions/suggestion`

**Parameters**:
- `traineeId` (Long, required) - The trainee whose availability was clicked
- `mentorId` (Long, required) - The coach/mentor creating the session
- `date` (LocalDate, format: YYYY-MM-DD) - Date of the clicked availability
- `clickedStartTime` (LocalTime, format: HH:mm:ss) - Start time of clicked interval

**Example Request**:
```bash
GET /api/v1/sessions/suggestion?traineeId=5&mentorId=10&date=2026-06-02&clickedStartTime=09:00:00
```

**Response** (HTTP 200):
```json
{
  "date": "2026-06-02",
  "traineeId": 5,
  "suggestedStartTime": "09:00:00",
  "suggestedEndTime": "10:00:00",
  "availableSlots": [
    {
      "startTime": "09:00:00",
      "endTime": "10:00:00"
    },
    {
      "startTime": "10:00:00",
      "endTime": "11:00:00"
    },
    {
      "startTime": "14:00:00",
      "endTime": "15:00:00"
    }
  ]
}
```

---

## Response Fields Explained

### `suggestedStartTime`
- The start time for the session form
- Equals the clicked availability interval's start time
- Already in coach's timezone

### `suggestedEndTime`
- The end time for the session form
- Calculated as `suggestedStartTime + 1 hour`
- Can be adjusted by coach if needed

### `availableSlots` (Array)
- **All available time intervals** for the trainee on that date
- **Excludes** times that have existing sessions scheduled
- Accounts for multiple availability intervals if trainee set several
- **Merged** consecutive availability intervals are treated as continuous
- Times shown in **coach's timezone**

---

## How It Works - Step by Step

### 1. User Interface Flow

```
Coach views "Trainee Manager"
    ↓
Coach sees trainee's availability intervals for a date
    ↓
Coach clicks on an availability interval (e.g., 09:00-10:00)
    ↓
Frontend calls GET /api/v1/sessions/suggestion
    (with traineeId, mentorId, date, clickedStartTime)
    ↓
Backend returns:
  - suggestedStartTime: 09:00
  - suggestedEndTime: 10:00
  - availableSlots: [all free times on that day]
    ↓
Frontend populates session creation form with:
  - from: 09:00 (from suggestedStartTime)
  - to: 10:00 (from suggestedEndTime)
  - shows tooltip/dropdown with availableSlots
    ↓
Coach can:
  - Confirm (create session with 09:00-10:00)
  - Change times manually
  - Select different slot from availableSlots
```

### 2. Backend Logic

**Step 1: Get Trainee's All Availability for the Date**
```
Find all TraineeAvailability records for:
  - traineeId
  - date (2026-06-02)
```

**Step 2: Get Coach's Existing Sessions for the Date**
```
Find all Session records for:
  - mentorId
  - workoutDate = date (2026-06-02)
  
Result: List of busy time intervals
```

**Step 3: Calculate Available Slots**
```
Merge consecutive/overlapping availability intervals
Remove any times that have sessions
Return resulting free intervals
```

**Step 4: Account for Timezone**
```
All times converted to coach's timezone
If coach timezone ≠ UTC, times are adjusted accordingly
```

---

## Example Scenarios

### Scenario 1: Simple Case - No Conflicts

**Trainee Availability**:
- 09:00-12:00 (merged from 09:00-10:00, 10:00-11:00, 11:00-12:00)
- 14:00-16:00

**Coach's Sessions on that day**: None

**Coach clicks on 10:30 within 09:00-12:00 interval**

**Response**:
```json
{
  "suggestedStartTime": "10:30:00",
  "suggestedEndTime": "11:30:00",
  "availableSlots": [
    {"startTime": "09:00:00", "endTime": "12:00:00"},
    {"startTime": "14:00:00", "endTime": "16:00:00"}
  ]
}
```

✅ Coach can book 10:30-11:30 (no conflicts)

---

### Scenario 2: With Existing Sessions - Excluded Times

**Trainee Availability**:
- 09:00-12:00
- 14:00-17:00

**Coach's Sessions on that day**:
- 10:00-11:00 (other trainee session)
- 15:00-16:00 (another session)

**Coach clicks on 09:00**

**Response**:
```json
{
  "suggestedStartTime": "09:00:00",
  "suggestedEndTime": "10:00:00",
  "availableSlots": [
    {"startTime": "09:00:00", "endTime": "10:00:00"},
    {"startTime": "11:00:00", "endTime": "12:00:00"},
    {"startTime": "14:00:00", "endTime": "15:00:00"},
    {"startTime": "16:00:00", "endTime": "17:00:00"}
  ]
}
```

✅ Available slots exclude 10:00-11:00 and 15:00-16:00

---

### Scenario 3: Multiple Non-Consecutive Availability

**Trainee Availability**:
- 07:00-08:00
- 09:00-10:00 (gap from 08:00-09:00)
- 14:00-15:00 (gap from 10:00-14:00)

**Coach clicks on 14:00 (third interval)**

**Response**:
```json
{
  "suggestedStartTime": "14:00:00",
  "suggestedEndTime": "15:00:00",
  "availableSlots": [
    {"startTime": "07:00:00", "endTime": "08:00:00"},
    {"startTime": "09:00:00", "endTime": "10:00:00"},
    {"startTime": "14:00:00", "endTime": "15:00:00"}
  ]
}
```

✅ Shows all intervals including gaps (trainee can pick any time)

---

## Implementation Details

### Service: `SessionCreationSuggestionService`

**File**: `com/cozy/planner/service/SessionCreationSuggestionService.java`

**Key Methods**:

1. **`generateSessionSuggestion()`** - Main entry point
   - Fetches mentor timezone
   - Gets trainee availability
   - Gets existing sessions
   - Calculates free slots
   - Returns response map

2. **`calculateFreeIntervals()`** - Removes busy times from available
   - Merges overlapping availability
   - Merges overlapping sessions
   - Subtracts busy from available

3. **`mergeIntervals()`** - Combines adjacent/overlapping intervals
   - Used for both availability and sessions
   - Handles consecutive intervals (end of one = start of next)

4. **`subtractInterval()`** - Removes a busy interval from a free one
   - Handles 4 cases:
     - Busy at start
     - Busy at end
     - Busy in middle
     - Busy covers entire interval

### Controller: `SessionsApiController`

**Endpoint**: `getSessionSuggestion()`

**Location**: Line ~107 in SessionsApiController

**Responsibilities**:
- Validates parameters
- Calls `SessionCreationSuggestionService`
- Returns response with HTTP 200

### Repository Addition

**File**: `SessionRepository`

**New Method**: `findAllByMentorIdAndWorkoutDate()`
```java
Flux<Session> findAllByMentorIdAndWorkoutDate(Long mentorId, LocalDate date);
```

---

## Timezone Handling

### How Timezone Works

1. **Coach's Timezone**: Retrieved from `Mentor.timezone` field
   - Default: "Europe/Kiev"
   - Can be set by coach in preferences

2. **Time Display**:
   - All times in response are in coach's timezone
   - When coach views trainee availability, it's already converted
   - When coach creates session, times are in their timezone

3. **Database Storage**:
   - Times stored as LocalTime (no timezone info)
   - Trainer availability assumed to be in trainee's local time
   - Conversion happens in controller layer

---

## Frontend Integration Example

### JavaScript/React

```javascript
// When coach clicks on trainee availability interval
async function onAvailabilityClick(traineeId, mentorId, date, clickedStartTime) {
  const response = await fetch(
    `/api/v1/sessions/suggestion?` +
    `traineeId=${traineeId}&` +
    `mentorId=${mentorId}&` +
    `date=${date}&` +
    `clickedStartTime=${clickedStartTime}`
  );
  
  const data = await response.json();
  
  // Pre-fill form
  document.getElementById('sessionFrom').value = data.suggestedStartTime;
  document.getElementById('sessionTo').value = data.suggestedEndTime;
  
  // Show available slots as options
  const availableSlots = data.availableSlots;
  renderAvailableSlotsList(availableSlots);
  
  // Open session creation form
  showSessionForm();
}
```

---

## Testing the Feature

### Manual Test

1. **Setup**:
   - Create coach with timezone "Europe/Kiev"
   - Create trainee with availability: 09:00-12:00 and 14:00-17:00 on 2026-06-02
   - Create existing session: 10:00-11:00 on same date

2. **Test Request**:
   ```bash
curl "http://localhost:8080/api/v1/sessions/suggestion?traineeId=5&mentorId=10&date=2026-06-02&clickedStartTime=09:00:00"
   ```

3. **Expected Response**:
   ```json
   {
     "date": "2026-06-02",
     "traineeId": 5,
     "suggestedStartTime": "09:00:00",
     "suggestedEndTime": "10:00:00",
     "availableSlots": [
       {"startTime": "09:00:00", "endTime": "10:00:00"},
       {"startTime": "11:00:00", "endTime": "12:00:00"},
       {"startTime": "14:00:00", "endTime": "17:00:00"}
     ]
   }
   ```

   ✅ Excludes 10:00-11:00 (existing session)

---

## Error Handling

### Possible Errors

**404 - Mentor Not Found**:
```
If mentorId doesn't exist in database
```

**400 - Invalid Time Format**:
```
If clickedStartTime not in HH:mm:ss format
If date not in YYYY-MM-DD format
```

**500 - Server Error**:
```
Database connection issues
Timezone invalid error
```

---

## Files Modified/Created

| File | Type | Changes |
|------|------|---------|
| `SessionCreationSuggestionService.java` | NEW | Core logic for suggestions |
| `SessionsApiController.java` | MODIFIED | Added endpoint + service injection |
| `SessionRepository.java` | MODIFIED | Added `findAllByMentorIdAndWorkoutDate()` |

---

## Compilation Status

✅ **Code compiles successfully**
✅ **No errors or warnings**
✅ **Ready for deployment**

---

## Future Enhancements

1. **UI Component**: Clickable availability intervals in trainee manager
2. **Validation**: Check if session overlaps with coach's own availability
3. **Multiple Trainees**: Suggest times when multiple trainees are available
4. **Recurrence**: Apply to recurring availability patterns
5. **Push Notifications**: Notify trainee when session is created from their availability


