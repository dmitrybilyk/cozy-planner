# Frontend Integration Guide: Session Creation from Trainee Availability

## 🎯 Overview

This guide explains how to integrate the trainee availability clicking feature into your frontend (React, Vue, or any framework).

---

## 📤 API Response You'll Receive

When coach clicks on a trainee availability interval, call this endpoint:

```
GET /api/v1/sessions/suggestion
    ?traineeId=5
    &mentorId=10
    &date=2026-06-02
    &clickedStartTime=09:00:00
```

**Response**:
```json
{
  "date": "2026-06-02",
  "traineeId": 5,
  "suggestedStartTime": "09:00:00",
  "suggestedEndTime": "10:00:00",
  "availableSlots": [
    {"startTime": "09:00:00", "endTime": "10:00:00"},
    {"startTime": "10:00:00", "endTime": "11:00:00"},
    {"startTime": "11:00:00", "endTime": "12:00:00"},
    {"startTime": "14:00:00", "endTime": "15:00:00"}
  ]
}
```

---

## 🔧 React Component Example

```javascript
import { useState } from 'react';

export function TraineeAvailabilityViewer({ traineeId, mentorId, startDate, endDate }) {
  const [availabilities, setAvailabilities] = useState([]);
  const [suggestion, setSuggestion] = useState(null);
  const [loading, setLoading] = useState(false);

  // Step 1: Fetch trainee availability intervals
  useEffect(() => {
    fetch(`/api/v1/mentors/${mentorId}/availability?startDate=${startDate}&endDate=${endDate}`)
      .then(r => r.json())
      .then(data => {
        const traineeData = data.find(t => t.traineeId === traineeId);
        setAvailabilities(traineeData?.slots || []);
      });
  }, [traineeId, mentorId, startDate, endDate]);

  // Step 2: Handle click on availability interval
  async function onAvailabilityClick(date, startTime) {
    setLoading(true);
    try {
      const response = await fetch(
        `/api/v1/sessions/suggestion?` +
        `traineeId=${traineeId}&` +
        `mentorId=${mentorId}&` +
        `date=${date}&` +
        `clickedStartTime=${startTime}`
      );
      
      const data = await response.json();
      setSuggestion(data);
      
      // Open session creation form with pre-filled data
      openSessionForm(data);
    } catch (error) {
      console.error('Error getting session suggestion:', error);
    } finally {
      setLoading(false);
    }
  }

  // Step 3: Display availability intervals
  return (
    <div className="availability-list">
      <h3>Trainee Availability</h3>
      {availabilities.map((slot, idx) => (
        <div
          key={idx}
          className="availability-slot"
          onClick={() => onAvailabilityClick(slot.date, slot.startTime)}
          style={{ cursor: 'pointer' }}
        >
          <span className="date">{slot.date}</span>
          <span className="time">
            {slot.startTime} - {slot.endTime}
          </span>
          <button className="create-session-btn">Create Session</button>
        </div>
      ))}
    </div>
  );
}

// Step 4: Session form with pre-filled data
function SessionForm({ suggestion }) {
  const [formData, setFormData] = useState({
    from: suggestion?.suggestedStartTime || '',
    to: suggestion?.suggestedEndTime || '',
    title: '',
  });

  const handleSlotSelect = (slot) => {
    setFormData({
      ...formData,
      from: slot.startTime,
      to: slot.endTime,
    });
  };

  return (
    <div className="session-form">
      <h3>Create Session</h3>
      
      {/* Pre-filled time fields */}
      <div className="form-group">
        <label>From</label>
        <input
          type="time"
          value={formData.from}
          onChange={(e) => setFormData({ ...formData, from: e.target.value })}
        />
      </div>

      <div className="form-group">
        <label>To</label>
        <input
          type="time"
          value={formData.to}
          onChange={(e) => setFormData({ ...formData, to: e.target.value })}
        />
      </div>

      {/* Available slots dropdown */}
      <div className="form-group">
        <label>Available Times</label>
        <select onChange={(e) => {
          const slot = suggestion.availableSlots[e.target.value];
          if (slot) handleSlotSelect(slot);
        }}>
          <option value="">Select from available...</option>
          {suggestion?.availableSlots?.map((slot, idx) => (
            <option key={idx} value={idx}>
              {slot.startTime} - {slot.endTime}
            </option>
          ))}
        </select>
      </div>

      {/* Rest of form fields */}
      <div className="form-group">
        <label>Title</label>
        <input
          type="text"
          value={formData.title}
          onChange={(e) => setFormData({ ...formData, title: e.target.value })}
          placeholder="e.g., Tennis Practice"
        />
      </div>

      <button onClick={() => createSession(formData)}>
        Create Session
      </button>
    </div>
  );
}
```

---

## 🟦 Vue.js Component Example

```vue
<template>
  <div class="trainee-availability">
    <h3>Trainee Availability</h3>
    
    <div 
      v-for="slot in availabilities" 
      :key="slot.id"
      class="availability-slot"
      @click="onSlotClick(slot.date, slot.startTime)"
    >
      <span class="date">{{ slot.date }}</span>
      <span class="time">{{ slot.startTime }} - {{ slot.endTime }}</span>
      <button class="btn">Create Session</button>
    </div>

    <!-- Session Form Modal -->
    <div v-if="suggestion" class="modal">
      <div class="modal-content">
        <h4>Create Session</h4>
        
        <div class="form-group">
          <label>From</label>
          <input v-model="form.from" type="time" />
        </div>

        <div class="form-group">
          <label>To</label>
          <input v-model="form.to" type="time" />
        </div>

        <div class="form-group">
          <label>Available Times</label>
          <select @change="onSlotSelect">
            <option value="">Select...</option>
            <option v-for="(slot, idx) in suggestion.availableSlots" :key="idx" :value="idx">
              {{ slot.startTime }} - {{ slot.endTime }}
            </option>
          </select>
        </div>

        <button @click="createSession">Create</button>
        <button @click="closeSuggestion">Cancel</button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      availabilities: [],
      suggestion: null,
      form: { from: '', to: '', title: '' }
    };
  },
  methods: {
    async onSlotClick(date, startTime) {
      const response = await fetch(
        `/api/v1/sessions/suggestion?` +
        `traineeId=${this.traineeId}&` +
        `mentorId=${this.mentorId}&` +
        `date=${date}&` +
        `clickedStartTime=${startTime}`
      );
      
      this.suggestion = await response.json();
      this.form = {
        from: this.suggestion.suggestedStartTime,
        to: this.suggestion.suggestedEndTime,
        title: ''
      };
    },
    onSlotSelect(event) {
      const idx = event.target.value;
      if (idx) {
        const slot = this.suggestion.availableSlots[idx];
        this.form.from = slot.startTime;
        this.form.to = slot.endTime;
      }
    },
    async createSession() {
      // Call your session creation endpoint
      await fetch('/api/v1/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          traineeIds: [this.traineeId],
          mentorId: this.mentorId,
          date: this.suggestion.date,
          time: this.form.from,
          endTime: this.form.to,
          title: this.form.title,
        })
      });
      this.closeSuggestion();
    },
    closeSuggestion() {
      this.suggestion = null;
      this.form = { from: '', to: '', title: '' };
    }
  }
};
</script>
```

---

## 🌐 Vanilla JavaScript Example

```javascript
class TraineeAvailabilityManager {
  constructor(traineeId, mentorId) {
    this.traineeId = traineeId;
    this.mentorId = mentorId;
    this.currentSuggestion = null;
  }

  // Fetch and display availabilities
  async loadAvailabilities(startDate, endDate) {
    const response = await fetch(
      `/api/v1/mentors/${this.mentorId}/availability?` +
      `startDate=${startDate}&endDate=${endDate}`
    );
    const allData = await response.json();
    const traineeData = allData.find(t => t.traineeId === this.traineeId);
    
    this.renderAvailabilities(traineeData?.slots || []);
  }

  renderAvailabilities(slots) {
    const container = document.getElementById('availability-list');
    container.innerHTML = slots.map((slot, idx) => `
      <div class="availability-slot" data-slot-idx="${idx}">
        <span class="date">${slot.date}</span>
        <span class="time">${slot.startTime} - ${slot.endTime}</span>
        <button onclick="manager.onSlotClick('${slot.date}', '${slot.startTime}')">
          Create Session
        </button>
      </div>
    `).join('');
  }

  // Get session suggestion when slot clicked
  async onSlotClick(date, startTime) {
    const response = await fetch(
      `/api/v1/sessions/suggestion?` +
      `traineeId=${this.traineeId}&` +
      `mentorId=${this.mentorId}&` +
      `date=${date}&` +
      `clickedStartTime=${startTime}`
    );

    this.currentSuggestion = await response.json();
    this.showSessionForm();
  }

  showSessionForm() {
    const suggestion = this.currentSuggestion;
    
    // Pre-fill times
    document.getElementById('sessionFrom').value = suggestion.suggestedStartTime;
    document.getElementById('sessionTo').value = suggestion.suggestedEndTime;
    
    // Populate available slots
    const slotsSelect = document.getElementById('availableSlotsSelect');
    slotsSelect.innerHTML = suggestion.availableSlots.map((slot, idx) => 
      `<option value="${idx}">${slot.startTime} - ${slot.endTime}</option>`
    ).join('');
    
    // Show modal/form
    document.getElementById('sessionFormModal').style.display = 'block';
  }

  // User selects different available slot
  onAvailableSlotSelect(event) {
    const idx = event.target.value;
    if (idx >= 0) {
      const slot = this.currentSuggestion.availableSlots[idx];
      document.getElementById('sessionFrom').value = slot.startTime;
      document.getElementById('sessionTo').value = slot.endTime;
    }
  }

  // Create the session
  async createSession() {
    const response = await fetch('/api/v1/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        traineeIds: [this.traineeId],
        mentorId: this.mentorId,
        date: this.currentSuggestion.date,
        time: document.getElementById('sessionFrom').value,
        endTime: document.getElementById('sessionTo').value,
        title: document.getElementById('sessionTitle').value,
      })
    });

    if (response.ok) {
      this.closeForm();
      // Refresh sessions list if needed
    }
  }

  closeForm() {
    document.getElementById('sessionFormModal').style.display = 'none';
    this.currentSuggestion = null;
  }
}

// Usage
const manager = new TraineeAvailabilityManager(traineeId, mentorId);
manager.loadAvailabilities('2026-06-01', '2026-06-30');
```

---

## 🎨 HTML Template

```html
<div id="availability-list" class="availability-container">
  <!-- Populated by JavaScript -->
</div>

<!-- Session Form Modal -->
<div id="sessionFormModal" class="modal">
  <div class="modal-content">
    <h3>Create Session from Availability</h3>
    
    <label>From:</label>
    <input id="sessionFrom" type="time" readonly />
    
    <label>To:</label>
    <input id="sessionTo" type="time" readonly />
    
    <label>Available Times:</label>
    <select id="availableSlotsSelect" onchange="manager.onAvailableSlotSelect(event)">
      <option value="">Select...</option>
    </select>
    
    <label>Title:</label>
    <input id="sessionTitle" type="text" placeholder="Session title" />
    
    <button onclick="manager.createSession()">Create</button>
    <button onclick="manager.closeForm()">Cancel</button>
  </div>
</div>
```

---

## 📝 CSS Styling

```css
.availability-slot {
  padding: 12px;
  margin: 8px 0;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: background 0.2s;
}

.availability-slot:hover {
  background: #f5f5f5;
}

.availability-slot .date {
  font-weight: bold;
  min-width: 100px;
}

.availability-slot .time {
  color: #555;
}

.availability-slot .create-session-btn {
  margin-left: auto;
  padding: 6px 12px;
  background: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.availability-slot .create-session-btn:hover {
  background: #45a049;
}

.modal {
  display: none;
  position: fixed;
  z-index: 1000;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  background: rgba(0,0,0,0.4);
}

.modal-content {
  background: white;
  margin: 10% auto;
  padding: 20px;
  border: 1px solid #888;
  border-radius: 8px;
  max-width: 400px;
}

.form-group {
  margin: 12px 0;
  display: flex;
  flex-direction: column;
}

.form-group label {
  margin-bottom: 6px;
  font-weight: bold;
}

.form-group input,
.form-group select {
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}
```

---

## ✅ Implementation Checklist

- [ ] Install/update your frontend IDE with latest code
- [ ] Call `/api/v1/sessions/suggestion` endpoint on availability click
- [ ] Pre-fill session form with `suggestedStartTime` and `suggestedEndTime`
- [ ] Populate available slots dropdown with `availableSlots` array
- [ ] Allow user to select different time from dropdown
- [ ] Call existing session creation endpoint (`POST /api/v1/sessions`)
- [ ] Close form and refresh sessions list after creation
- [ ] Test with multiple availability scenarios
- [ ] Verify timezone display matches coach's timezone

---

## 🧪 Developer Testing Tips

1. **Use Browser DevTools**:
   - Network tab: Check endpoint calls and responses
   - Console: Log suggestion data
   - Elements: Inspect form pre-fill

2. **Test Different Scenarios**:
   - Click different intervals
   - Select alternative slots from dropdown
   - Change times manually in form

3. **Timezone Testing**:
   - Create coach with UTC timezone
   - Create trainee availability
   - Verify times display correctly

---

**Implementation Status**: ✅ Backend Ready
**Frontend Status**: Ready to implement using examples above
**Testing**: Use provided scenarios


