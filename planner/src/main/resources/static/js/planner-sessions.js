// planner-sessions.js
// Session management: fetching, creating, editing, saving, calendar navigation, plan modal.
(function () {
  'use strict';

  // These methods are mixed into the main app object.
  // Inside each method, `this` refers to the Alpine app state.
  window.plannerModules = window.plannerModules || {};

  Object.assign(window.plannerModules, {

    // ── Data fetching ────────────────────────────────────────────────────────

    async fetchData() {
      if (this._fetchPromise) {
        console.log('[fetchData] concurrent call detected, waiting for in-progress fetch');
        await this._fetchPromise;
      }
      this._fetchPromise = this._doFetch();
      try {
        await this._fetchPromise;
      } finally {
        this._fetchPromise = null;
      }
    },

    async _doFetch() {
      const today = this.todayStr;
      if (!this.mentorId) { console.warn('[agenda] no mentorId'); this.agendaReady = true; return; }
      console.log(`[fetchData] calling... selectedDate=${this.selectedDate}, sessions before=${this.sessions.length}`);
      this._noMoreFutureSessions = false;
      this.loadedDates = {};
      this.agendaAvailByDate = {};
      const pastStart = addDays(today, -3);
      for (let i = 0; i < 6; i++) {
        this.loadedDates[addDays(pastStart, i)] = true;
      }
      const end = addDays(today, 2);
      try {
        const [res] = await Promise.all([
          fetch(`/api/v1/sessions?mentorId=${this.mentorId}&startDate=${pastStart}&endDate=${end}`),
          this.loadAgendaAvailability(pastStart, end)
        ]);
        if (res.ok) {
          this.sessions = (await res.json()).sort((a, b) => a.time.localeCompare(b.time));
          console.log(`[fetchData] received ${this.sessions.length} sessions for ${pastStart}..${end}`);
          for (const w of this.sessions) {
            w.color = this.getLocationColor(w.locationId);
          }
        } else {
          console.warn(`[fetchData] HTTP ${res.status} for mentorId=${this.mentorId}`);
        }
      } catch (e) {
        console.error('[agenda] fetch error:', e);
      }
      if (this.selectedDate && !this.loadedDates[this.selectedDate]) {
        console.log(`[fetchData] selectedDate ${this.selectedDate} not in loadedDates, calling loadDaySessions`);
        await this.loadDaySessions(this.selectedDate);
      } else {
        console.log(`[fetchData] selectedDate ${this.selectedDate} in loadedDates: ${!!this.loadedDates[this.selectedDate]}, sessions for date: ${this.sessions.filter(s => s.date === this.selectedDate).length}`);
      }
      await this.loadCoachAvailabilityData();
      this.agendaReady = true;
    },

    async selectDay(dateStr) {
      this.selectedDate = dateStr;
      this.activeTab = 'feed';
      this.$nextTick(() => this.scrollToActive());
      if (!this.loadedDates[dateStr]) {
        await this.loadDaySessions(dateStr);
      } else if (!this.agendaAvailByDate[dateStr]) {
        await this.loadAgendaAvailability(dateStr, dateStr);
      }
    },

    async loadDaySessions(dateStr) {
      if (!this.mentorId || this.loadedDates[dateStr]) { console.log(`[loadDaySessions] skipped for ${dateStr}: mentorId=${!!this.mentorId}, alreadyLoaded=${!!this.loadedDates[dateStr]}`); return; }
      console.log(`[loadDaySessions] loading ${dateStr}, sessions before=${this.sessions.length}`);
      this.loadingMore = true;
      try {
        const [res] = await Promise.all([
          fetch(`/api/v1/sessions?mentorId=${this.mentorId}&startDate=${dateStr}&endDate=${dateStr}`),
          this.loadAgendaAvailability(dateStr, dateStr)
        ]);
        if (res.ok) {
          const daySessions = (await res.json()).sort((a, b) => a.time.localeCompare(b.time));
          console.log(`[loadDaySessions] received ${daySessions.length} sessions for ${dateStr}`);
          for (const w of daySessions) {
            w.color = this.getLocationColor(w.locationId);
          }
          const removed = this.sessions.filter(s => s.date !== dateStr).length;
          this.sessions = [...this.sessions.filter(s => s.date !== dateStr), ...daySessions];
          this.loadedDates[dateStr] = true;
          console.log(`[loadDaySessions] sessions after=${this.sessions.length}, removed ${removed} for other dates, kept ${this.sessions.length - removed}, date ${dateStr} now has ${daySessions.length}`);
        } else {
          console.warn(`[loadDaySessions] HTTP ${res.status} for ${dateStr}`);
        }
      } catch (e) {
        console.error('Failed to load sessions for', dateStr, e);
      } finally {
        this.loadingMore = false;
      }
    },

    async loadFutureSessions() {
      this.agendaReady = false;
      this._noMoreFutureSessions = false;
      const today = this.todayStr;
      this.loadedDates = {};
      this.agendaAvailByDate = {};
      for (let i = 0; i < 6; i++) {
        this.loadedDates[addDays(today, i)] = true;
      }
      const end = addDays(today, 5);
      try {
        const [res] = await Promise.all([
          fetch(`/api/v1/sessions?mentorId=${this.mentorId}&startDate=${today}&endDate=${end}`),
          this.loadAgendaAvailability(today, end)
        ]);
        if (res.ok) {
          const newSessions = (await res.json()).sort((a, b) => a.time.localeCompare(b.time));
          if (newSessions.length === 0) this._noMoreFutureSessions = true;
          for (const w of newSessions) {
            w.color = this.getLocationColor(w.locationId);
            const existing = this.sessions.findIndex(s => s.id === w.id);
            if (existing >= 0) {
              this.sessions[existing] = w;
            } else {
              this.sessions.push(w);
            }
          }
        }
      } catch (e) {
        console.error('[agenda] fetch error:', e);
      } finally {
        this.agendaReady = true;
      }
    },

    async loadMoreAgenda() {
      if (!this.hasMoreDays || this._noMoreFutureSessions) return;
      this.loadingMore = true;
      const beforeCount = this.sessions.filter(s => s.date > this.todayStr).length;
      await Promise.all([
        this.loadNextDays(7),
        this.loadPrevDays(7)
      ]);
      if (this.sessions.filter(s => s.date > this.todayStr).length === beforeCount) {
        this._noMoreFutureSessions = true;
      }
      this.loadingMore = false;
    },

    async loadPastSessions() {
      this._pastSessionPage = 0;
      this.pastSessions = [];
      this.hasMorePastSessions = true;
      await this.loadMorePastSessions();
    },

    async loadMorePastSessions() {
      if (!this.hasMorePastSessions) return;
      this.loadingMorePast = true;
      const daysBack = this._pastSessionPage === 0 ? 3 : 7;
      const startDate = addDays(this.todayStr, -(this._pastSessionPage + 1) * daysBack + 1);
      const endDate = addDays(this.todayStr, -this._pastSessionPage * daysBack);
      try {
        const res = await fetch(`/api/v1/sessions?mentorId=${this.mentorId}&startDate=${startDate}&endDate=${endDate}`);
        if (res.ok) {
          const data = await res.json();
          const sorted = data.sort((a, b) => b.date.localeCompare(a.date) || b.time.localeCompare(a.time));
          for (const w of sorted) {
            w.color = this.getLocationColor(w.locationId);
            if (!this.pastSessions.find(s => s.id === w.id)) this.pastSessions.push(w);
          }
          this.hasMorePastSessions = sorted.length >= daysBack * 2;
        } else {
          this.hasMorePastSessions = false;
        }
      } catch (e) {
        console.error('Failed to load past sessions:', e);
        this.hasMorePastSessions = false;
      } finally {
        this.loadingMorePast = false;
        this._pastSessionPage++;
      }
    },

    async loadNextDays(n) {
      const loaded = Object.keys(this.loadedDates).sort();
      const lastLoaded = loaded[loaded.length - 1];
      if (!lastLoaded) return;
      const startDate = addDays(lastLoaded, 1);
      const endDate = addDays(lastLoaded, n);
      try {
        const [res] = await Promise.all([
          fetch(`/api/v1/sessions?mentorId=${this.mentorId}&startDate=${startDate}&endDate=${endDate}`),
          this.loadAgendaAvailability(startDate, endDate)
        ]);
        if (res.ok) {
          const newSessions = (await res.json()).sort((a, b) => a.time.localeCompare(b.time));
          for (const w of newSessions) {
            w.color = this.getLocationColor(w.locationId);
            const existing = this.sessions.findIndex(s => s.id === w.id);
            if (existing >= 0) {
              this.sessions[existing] = w;
            } else {
              this.sessions.push(w);
            }
          }
          let d = new Date(startDate + 'T12:00:00');
          const end = new Date(endDate + 'T12:00:00');
          while (d <= end) {
            this.loadedDates[localDateStr(d)] = true;
            d.setDate(d.getDate() + 1);
          }
        }
      } catch (e) {
        console.error('[agenda] loadNextDays error:', e);
      }
    },

    async loadPrevDays(n) {
      const loaded = Object.keys(this.loadedDates).sort();
      const firstLoaded = loaded[0];
      if (!firstLoaded) return;
      const startDate = addDays(firstLoaded, -n);
      const endDate = addDays(firstLoaded, -1);
      try {
        const [res] = await Promise.all([
          fetch(`/api/v1/sessions?mentorId=${this.mentorId}&startDate=${startDate}&endDate=${endDate}`),
          this.loadAgendaAvailability(startDate, endDate)
        ]);
        if (res.ok) {
          const newSessions = (await res.json()).sort((a, b) => a.time.localeCompare(b.time));
          for (const w of newSessions) {
            w.color = this.getLocationColor(w.locationId);
            const existing = this.sessions.findIndex(s => s.id === w.id);
            if (existing >= 0) {
              this.sessions[existing] = w;
            } else {
              this.sessions.push(w);
            }
          }
          let d = new Date(startDate + 'T12:00:00');
          const end = new Date(endDate + 'T12:00:00');
          while (d <= end) {
            this.loadedDates[localDateStr(d)] = true;
            d.setDate(d.getDate() + 1);
          }
        }
      } catch (e) {
        console.error('[agenda] loadPrevDays error:', e);
      }
    },

    async fetchSessionCounts() {
      if (!this.mentorId) return;
      console.log(`[fetchSessionCounts] calling... mentorId=${this.mentorId}`);
      const startDate = this.days[0].dateStr;
      const endDate = this.days[this.days.length - 1].dateStr;
      const res = await fetch(`/api/v1/sessions/counts?mentorId=${this.mentorId}&startDate=${startDate}&endDate=${endDate}`);
      if (res.ok) {
        this.sessionCounts = await res.json();
        const count = Object.keys(this.sessionCounts).length;
        console.log(`[fetchSessionCounts] received ${count} dates, sample:`, JSON.stringify(Object.fromEntries(Object.entries(this.sessionCounts).slice(0, 5))));
      } else {
        console.warn(`[fetchSessionCounts] HTTP ${res.status} for mentorId=${this.mentorId}`);
      }
    },

    // ── Calendar navigation ──────────────────────────────────────────────────

    scrollToToday() {
      this.selectedDate = this.todayStr;
      this.$nextTick(() => this.scrollToActive());
    },

    scrollToWeekStart() {
      const el = document.getElementById('calendar-container');
      const todayBtn = document.getElementById('date-btn-' + this.todayStr);
      if (!el || !todayBtn) {
        this.scrollToActive();
        return;
      }

      const todayIdx = this.days.findIndex(d => d.dateStr === this.todayStr);
      if (todayIdx === -1) {
        this.scrollToActive();
        return;
      }

      let mondayIdx = todayIdx;
      while (mondayIdx > 0 && !this.days[mondayIdx].isMonday) {
        mondayIdx--;
      }

      const mondayBtn = document.getElementById('date-btn-' + this.days[mondayIdx].dateStr);
      if (mondayBtn) {
        el.scrollTo({
          left: mondayBtn.offsetLeft - 16,
          behavior: 'smooth'
        });
      }
    },

    scrollToActive() {
      const el = document.getElementById('calendar-container');
      const activeBtn = document.getElementById('date-btn-' + this.selectedDate);
      if (el && activeBtn) {
        const posInContainer = activeBtn.offsetLeft - el.offsetLeft;
        el.scrollTo({
          left: posInContainer - el.offsetWidth / 2 + activeBtn.offsetWidth / 2,
          behavior: 'smooth'
        });
      }
    },

    goToToday() {
      this.selectedDate = this.todayStr;
      this.activeTab = 'feed';
      this.$nextTick(() => {
        const el = document.getElementById('calendar-container');
        if (el) el.scrollLeft = 0;
      });
    },

    goToDay(date) {
      this.selectedDate = date;
      this.activeTab = 'feed';
      setTimeout(() => this.scrollToActive(), 150);
    },

    showMorePast() {
      this.pastDaysToShow += 14;
      this.$nextTick(() => {
        if (this.selectedDate) this.scrollToActive();
      });
    },

    sessionCountOn(dateStr) {
      return this.sessionCounts[dateStr] || 0;
    },

    sessionCountClass(dateStr) {
      const count = this.sessionCounts[dateStr] || 0;
      if (count > 2) return 'bg-blue-500';
      if (count > 0) return 'bg-blue-400';
      return '';
    },

    generateDays() {
      const start = this.snapToMonday(new Date());
      start.setDate(start.getDate() - 14);
      const days = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Нд'];
      const fullDays = ['Понеділок', 'Вівторок', 'Середа', 'Четвер', 'П\'ятниця', 'Субота', 'Неділя'];
      const shortMonths = ['Січ','Лют','Бер','Кві','Тра','Чер','Лип','Сер','Вер','Жов','Лис','Гру'];
      for (let i = 0; i < 77; i++) {
        const dayIdx = (start.getDay() + 6) % 7;
        const ds = localDateStr(start);
        this.days.push({ dateStr: ds, dayNum: start.getDate(), weekday: days[dayIdx], weekdayFull: fullDays[dayIdx], isMonday: start.getDay() === 1, shortMonth: shortMonths[start.getMonth()], isToday: ds === this.todayStr, isWeekend: start.getDay() === 0 || start.getDay() === 6 });
        start.setDate(start.getDate() + 1);
      }
    },

    snapToMonday(date) {
      const d = new Date(date);
      const day = d.getDay();
      const diff = d.getDate() - day + (day === 0 ? -6 : 1);
      d.setDate(diff);
      return d;
    },

    // ── Session modal ────────────────────────────────────────────────────────

    openSessionModal() {
      this.traineeSearch = '';
      this.editingSessionId = null;
      this.originalSessionData = null;
      this.sessionValidationErrors = null;
      this.freeSlotContext = null;
      const workEnd = this.workEnd || '21:00';
      const [weh, wem] = workEnd.split(':').map(Number);
      const now = new Date();
      const currentMinutes = now.getHours() * 60 + now.getMinutes();
      const workEndMinutes = weh * 60 + wem;
      let defaultDate = this.selectedDate < this.todayStr ? this.todayStr : this.selectedDate;
      if (!defaultDate || defaultDate < this.todayStr) defaultDate = this.todayStr;
      if (defaultDate === this.todayStr && currentMinutes >= workEndMinutes) {
        const tomorrow = new Date(now);
        tomorrow.setDate(tomorrow.getDate() + 1);
        defaultDate = localDateStr(tomorrow);
      }
      this.sessionForm = { title: this.labels.session_title_default || 'Тренування', description: '', date: defaultDate, startTime: null, endTime: null, traineeIds: [], locationId: this.defaultLocationId(defaultDate), recurring: false, recurringCount: 8 };
      this.showModal = true;
      this.scrollDateIntoView();
      this.$nextTick(() => this.onSessionStartChange());
    },

    closeModal() {
      if (this._prevTab) {
        this.activeTab = this._prevTab;
        this._prevTab = null;
      }
      this.showModal = false;
      this.sessionSaving = false;
      this.sessionValidationErrors = null;
      this.freeSlotContext = null;
    },

    editSession(w) {
      if (w.date < this.todayStr) return;
      this.traineeSearch = '';
      this.editingSessionId = w.id;
      this.originalSessionData = { date: w.date, time: w.time, endTime: w.endTime, traineeIds: [...(w.traineeIds || [])], title: w.title, description: w.description || '', locationId: w.locationId || null };
      this.sessionValidationErrors = null;
      this.freeSlotContext = null;
      this.sessionForm = { title: w.title, description: w.description || '', date: w.date, startTime: w.time, endTime: w.endTime || null, traineeIds: [...(w.traineeIds || [])], locationId: w.locationId || null, recurring: false };
      this.showModal = true;
      this.scrollDateIntoView();
      this.$nextTick(() => this._validateFormTime());
    },

    copySession(w) {
      this.editingSessionId = null;
      this.originalSessionData = null;
      let date = w.date;
      if (date < this.todayStr) date = this.todayStr;
      let st = w.time, et = w.endTime || this.nextSlot(w.time);
      if (date === this.todayStr && new Date(`${date}T${st}`) <= new Date()) {
        const dur = this.slotToMin(et) - this.slotToMin(st);
        st = this.getNearestSlot();
        const em = this.slotToMin(st) + Math.max(dur, 30);
        const eh = Math.floor(em / 60) % 24;
        et = `${eh.toString().padStart(2, '0')}:${em % 60 === 0 ? '00' : '30'}`;
      }
      this.sessionForm = {
        title: w.title,
        description: w.description || '',
        date,
        startTime: st,
        endTime: et,
        traineeIds: [...(w.traineeIds || [])],
        locationId: w.locationId || null,
        recurring: false
      };
      this.showModal = true;
      this.$nextTick(() => {
        // temporarily use source session id so getBusyMinuteRanges skips it
        this.editingSessionId = w.id;
        this._validateFormTime();
        this.editingSessionId = null;
      });
    },

    copySessionById(id) {
      const w = this.pastSessions.find(s => s.id === id) || this.sessions.find(s => s.id === id);
      if (!w) return;
      this.copySession(w);
    },

    copyFromHistory(session) {
      this.editingSessionId = null;
      this.originalSessionData = null;
      let st = session.time, et = session.endTime || this.nextSlot(session.time);
      let date = this.selectedDate;
      if (date < this.todayStr) date = this.todayStr;
      if (date === this.todayStr && new Date(`${date}T${st}`) <= new Date()) {
        st = this.getNearestSlot();
        const dur = this.slotToMin(et) - this.slotToMin(st);
        const em = this.slotToMin(st) + Math.max(dur, 30);
        const eh = Math.floor(em / 60) % 24;
        et = `${eh.toString().padStart(2, '0')}:${em % 60 === 0 ? '00' : '30'}`;
      }
      this.sessionForm = {
        title: session.title,
        description: session.description || '',
        date,
        startTime: st,
        endTime: et,
        traineeIds: [...(session.traineeIds || [])],
        locationId: session.locationId || null,
        recurring: false
      };
      this.showModal = true;
      this.$nextTick(() => {
        this.editingSessionId = session.id;
        this._validateFormTime();
        this.editingSessionId = null;
      });
    },

    openSessionFromFreeSlot(slot, date) {
      this.traineeSearch = '';
      this.editingSessionId = null;
      this.originalSessionData = null;
      this.sessionValidationErrors = null;
      const d = date || this.selectedDate;
      this.freeSlotContext = { startStr: slot.startStr, endStr: slot.endStr };
      this.sessionForm = {
        title: this.labels.session_title_default || 'Тренування',
        description: '',
        date: d,
        startTime: slot.startStr,
        endTime: null,
        traineeIds: [],
        locationId: slot.locationId || this.defaultLocationId(d),
        recurring: false,
        recurringCount: 8
      };
      this.showModal = true;
      this.scrollDateIntoView();
      this.$nextTick(() => this.onSessionStartChange());
    },

    createFromAvailability(traineeId, date, startTime, endTime) {
      this._prevTab = this.activeTab;
      this.activeTab = 'feed';
      this.traineeSearch = '';
      this.editingSessionId = null;
      this.sessionValidationErrors = null;
      if (startTime === 'all_day') {
        const trainee = this.trainees.find(t => t.id === traineeId);
        const traineeName = trainee ? trainee.name : '';
        this.sessionForm = { title: (this.labels.session_title_default || 'Тренування') + (traineeName ? ' — ' + traineeName : ''), description: '', date, startTime: null, endTime: null, traineeIds: [traineeId], locationId: this.defaultLocationId(date), recurring: false };
        this.showModal = true;
        this.scrollDateIntoView();
        this.$nextTick(() => {
          const slots = this.validStartSlots;
          if (slots.length > 0) {
            const st = slots[0];
            const min = this.slotToMin(st) + (this.availStep || 60);
            const et = String(Math.floor(min / 60)).padStart(2, '0') + ':' + String(min % 60).padStart(2, '0');
            this.sessionForm.startTime = st;
            this.sessionForm.endTime = et;
          } else {
            const first = this.timeSlots15?.[0] || '08:00';
            this.sessionForm.startTime = first;
            const min = this.slotToMin(first) + (this.availStep || 60);
            this.sessionForm.endTime = String(Math.floor(min / 60)).padStart(2, '0') + ':' + String(min % 60).padStart(2, '0');
          }
        });
        return;
      }
      this.$nextTick(() => {
        const trainee = this.trainees.find(t => t.id === traineeId);
        const traineeName = trainee ? trainee.name : '';
        const st = startTime;
        const et = endTime;
        this.sessionForm = { title: (this.labels.session_title_default || 'Тренування') + (traineeName ? ' — ' + traineeName : ''), description: '', date: date, startTime: st.slice(0,5), endTime: et.slice(0,5), traineeIds: [traineeId], locationId: this.defaultLocationId(date), recurring: false };
        this.showModal = true;
        this.scrollDateIntoView();
      });
    },

    createSessionForTrainee(trainee) {
      this._prevTab = this.activeTab;
      this.activeTab = 'feed';
      this.traineeSearch = '';
      this.editingSessionId = null;
      this.originalSessionData = null;
      this.sessionForm = { title: (this.labels.session_title_default || 'Тренування') + ' — ' + (trainee.name || ''), description: '', date: this.selectedDate, startTime: null, endTime: null, traineeIds: [trainee.id], locationId: this.defaultLocationId(this.selectedDate), recurring: false };
      this.showModal = true;
    },

    // ── Session form helpers ─────────────────────────────────────────────────

    onSessionDateChange(date) {
      const prevStart = this.sessionForm.startTime;
      const prevEnd = this.sessionForm.endTime;
      this.sessionForm.date = date;
      this.sessionForm.startTime = prevStart;
      this.sessionForm.endTime = prevEnd;
      this.sessionForm._prevStartTime = null;
      this.sessionForm._prevEndTime = null;
      this.sessionForm.locationId = this.defaultLocationId(date);
      this.$nextTick(() => this._validateFormTime());
    },

    onSessionStartChange() {
      this.sessionForm._prevStartTime = null;
      this.sessionForm._prevEndTime = null;
      const slots = this.validEndSlots;
      if (slots.length > 0) {
        this.sessionForm.endTime = slots[0];
      } else {
        this.sessionForm.endTime = null;
      }
    },

    onSessionLocationChange() {
      this.$nextTick(() => this._validateFormTime());
    },

    _validateFormTime() {
      if (!this.sessionForm.startTime) return;
      const validStarts = this.validStartSlots;
      if (!validStarts.includes(this.sessionForm.startTime)) {
        this.sessionForm._prevStartTime = this.sessionForm.startTime;
        this.sessionForm._prevEndTime = this.sessionForm.endTime;
        this.sessionForm.startTime = null;
        this.sessionForm.endTime = null;
        return;
      }
      if (!this.sessionForm.endTime) { this.onSessionStartChange(); return; }
      const validEnds = this.validEndSlots;
      if (!validEnds.includes(this.sessionForm.endTime)) {
        this.sessionForm._prevEndTime = this.sessionForm.endTime;
        this.onSessionStartChange();
      }
    },

    getSessionValidationErrors() {
      const errors = [];
      const { startTime, endTime, date, traineeIds } = this.sessionForm;
      if (!startTime || !endTime || traineeIds.length === 0) return errors;
      if (this.editingSessionId && this.originalSessionData) {
        const sameDate = date === this.originalSessionData.date;
        const sameTime = startTime === this.originalSessionData.time;
        const sameEndTime = endTime === this.originalSessionData.endTime;
        const sameTrainees = this.originalSessionData.traineeIds &&
          traineeIds.length === this.originalSessionData.traineeIds.length &&
          traineeIds.every(id => this.originalSessionData.traineeIds.includes(id));
        if (sameDate && sameTime && sameEndTime && sameTrainees) return errors;
      }
      const sm = this.slotToMin(startTime);
      const em = this.slotToMin(endTime);
      if (this.dayOffs.includes(date)) {
        errors.push(this.labels.err_coach_day_off || 'Цей день є вихідним для майстра');
      }
      if (this.workStart && this.workEnd) {
        const [wsh, wsm] = this.workStart.split(':').map(Number);
        const [weh, wem] = this.workEnd.split(':').map(Number);
        const ws = wsh * 60 + wsm;
        const we = weh * 60 + wem;
        if (sm < ws || em > we) {
          errors.push((this.labels.err_coach_work_hours || 'Час сесії має бути в межах {ws} — {we}').replace('{ws}', this.workStart).replace('{we}', this.workEnd));
        }
      }
      const ranges = this.coachRangesByDate?.[date];
      if (ranges && ranges.length > 0) {
        const locId = this.sessionForm.locationId;
        const filteredRanges = locId ? ranges.filter(r => r.locationId == locId || r.locationId == null) : ranges;
        if (filteredRanges.length > 0) {
          let within = false;
          for (const r of filteredRanges) {
            const rs = this.slotToMin(r.startTime);
            const re = this.slotToMin(r.endTime);
            if (sm >= rs && em <= re) { within = true; break; }
          }
          if (!within) {
            errors.push(this.labels.err_coach_avail || 'Час сесії не входить в години доступності майстра');
          }
        }
      }
      const coachSessions = this.coachSess[date] || [];
      for (const s of coachSessions) {
        if (this.editingSessionId && s.id === this.editingSessionId) continue;
        const st = s.startTime || s.time;
        const et = s.endTime || st;
        if (!st) continue;
        const [ssh, ssm] = st.split(':').map(Number);
        const [seh, sem] = et.split(':').map(Number);
        const sStart = ssh * 60 + ssm;
        const sEnd = seh * 60 + sem;
        if (sm < sEnd && em > sStart) {
          errors.push(this.labels.err_coach_conflict || 'Тренер уже має сесію на цей час');
          break;
        }
      }
      for (const aId of traineeIds) {
        const slots = this.availabilityMap[aId + '|' + date];
        if (slots && slots.length > 0) {
          if (slots.some(s => s.startTime === 'all_day')) continue;
          const ok = slots.some(s => {
            const ss = this.slotToMin(s.startTime);
            const se = this.slotToMin(s.endTime);
            return sm >= ss && em <= se;
          });
          if (!ok) {
            const trainee = this.trainees.find(t => t.id === aId);
            errors.push((this.labels.err_trainee_avail || 'Час сесії не входить в години доступності учня {name}').replace('{name}', trainee?.name || ''));
          }
        }
      }
      if (traineeIds.length > 0) {
        const checked = new Set();
        for (const aId of traineeIds) {
          if (checked.has(aId)) continue;
          for (const s of this.sessions) {
            if (this.editingSessionId && s.id === this.editingSessionId) continue;
            if (s.date !== date) continue;
            if (!s.traineeIds || !s.traineeIds.includes(aId)) continue;
            const st = s.time || s.startTime;
            const et = s.endTime || st;
            if (!st) continue;
            const [ssh, ssm] = st.split(':').map(Number);
            const [seh, sem] = et.split(':').map(Number);
            const sStart = ssh * 60 + ssm;
            const sEnd = seh * 60 + sem;
            if (sm < sEnd && em > sStart) {
              const trainee = this.trainees.find(t => t.id === aId);
              errors.push((this.labels.err_trainee_conflict || 'Учень {name} уже має сесію на цей час').replace('{name}', trainee?.name || ''));
              checked.add(aId);
              break;
            }
          }
        }
      }
      return errors;
    },

    defaultLocationId(date) {
      if (this.locations.length === 0) return null;
      const ranges = this.coachRangesByDate?.[date] || [];
      if (ranges.length === 0) return this.locations[0].id;
      const availLocIds = new Set(ranges.map(r => r.locationId));
      if (availLocIds.has(null)) return this.locations[0].id;
      const firstAvail = this.locations.find(l => availLocIds.has(l.id));
      return firstAvail ? firstAvail.id : this.locations[0].id;
    },

    exportToGCal(session) {
      const d = session.date.replace(/-/g, '');
      const st = session.time.replace(/:/g, '') + '00';
      const et = (session.endTime || session.time).replace(/:/g, '') + '00';
      const text = encodeURIComponent(session.title || '');
      const dates = d + 'T' + st + '/' + d + 'T' + et;
      const loc = encodeURIComponent(this.getLocationName(session.locationId) || '');
      const names = (session.traineeIds || []).map(id => this.getTraineeName(id)).filter(Boolean).join(', ');
      const details = encodeURIComponent((session.description || '') + (names ? '\\n' + names : ''));
      const url = 'https://calendar.google.com/calendar/render?action=TEMPLATE&text=' + text + '&dates=' + dates + '&details=' + details + '&location=' + loc;
      window.open(url, '_blank');
    },

    // ── Save / delete ────────────────────────────────────────────────────────

    async saveSession() {
      if (this.sessionSaving) return;
      const { startTime, endTime, date, title, description, traineeIds, locationId } = this.sessionForm;
      if (!startTime || !endTime) return;
      if (date < this.todayStr) return;
      if (this.locations.length > 0 && !locationId) return;
      const errors = this.getSessionValidationErrors();
      if (errors.length > 0) { this.sessionValidationErrors = errors; return; }
      this.sessionValidationErrors = null;
      if (this.editingSessionId && this.originalSessionData) {
        const sameDate = date === this.originalSessionData.date;
        const sameTime = startTime === this.originalSessionData.time;
        const sameEndTime = endTime === this.originalSessionData.endTime;
        const sameTrainees = this.originalSessionData.traineeIds &&
          traineeIds.length === this.originalSessionData.traineeIds.length &&
          traineeIds.every(id => this.originalSessionData.traineeIds.includes(id));
        if (sameDate && sameTime && sameEndTime && sameTrainees && title === this.originalSessionData.title && description === this.originalSessionData.description && locationId === this.originalSessionData.locationId) {
          this._prevTab = null; this.showModal = false; return;
        }
      }
      this.sessionSaving = true;
      try {
        const newTime = startTime;
        if (this.editingSessionId && this.originalSessionData) {
          if (date !== this.originalSessionData.date || newTime !== this.originalSessionData.time) {
            await fetch(`/api/v1/sessions/${this.editingSessionId}`, { method: 'DELETE' });
          }
        }
        const isNew = !this.editingSessionId || (date !== this.originalSessionData?.date || newTime !== this.originalSessionData?.time);
        const payload = {
          id: (this.editingSessionId && date === this.originalSessionData?.date && newTime === this.originalSessionData?.time) ? this.editingSessionId : null,
          title, description, date, mentorId: this.mentorId,
          time: newTime, endTime,
          traineeIds, locationId,
          recurring: false,
          recurringCount: null
        };
        console.log(`[saveSession] creating session: date=${payload.date}, time=${payload.time}, endTime=${payload.endTime}, editingId=${payload.id}`);
        const res = await fetch('/api/v1/sessions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
        console.log(`[saveSession] save done, status=${res.status}`);
        this._prevTab = null; this.showModal = false; await this.fetchData(); this.$nextTick(() => this.scrollToActive());
      } finally {
        this.sessionSaving = false;
      }
    },

    askDeleteSession() {
      this.confirmData = { show: true, title: this.labels.confirm_delete_title || 'Видалити?', message: this.labels.confirm_delete_session || 'Дію неможливо скасувати.', onConfirm: async () => { await fetch(`/api/v1/sessions/${this.editingSessionId}`, { method: 'DELETE' }); this._prevTab = null; this.showModal = false; await this.fetchData(); this.$nextTick(() => this.scrollToActive()); } };
    },

    async drop(event, targetIndex) {
      this.draggedIndex = null;
    },

    scrollDateIntoView() {
      this.$nextTick(() => {
        const btn = this.$refs.dateScroller?.querySelector('[data-date="' + this.sessionForm.date + '"]');
        if (btn) btn.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
      });
    },

    getNearestSlot() {
      const now = new Date();
      const totalMin = Math.max(now.getHours() * 60 + now.getMinutes(), 7 * 60);
      const step = this.availStep || 30;
      const nextSlot = Math.ceil(totalMin / step) * step;
      const nh = Math.floor(nextSlot / 60) % 24;
      const nm = nextSlot % 60;
      const mm = nm === 0 ? '00' : String(nm).padStart(2, '0');
      return `${nh.toString().padStart(2, '0')}:${mm}`;
    },

    // ── Touch drag (swipe to delete/copy) ───────────────────────────────────

    initTouchDrag() {
      const container = document.querySelector('main') || document.body;
      if (!container) return;
      if (this._touchDragCleanup) return;

      let timer = null;
      let drag = null;

      const onStart = (e) => {
        const card = e.target.closest('[data-session-id]');
        if (!card) return;
        const id = parseInt(card.dataset.sessionId);
        const session = this.sessions.find(w => w.id === id);
        if (!session) return;
        const t = e.touches[0];
        this.touchJustDragged = false;
        drag = { session, card, startX: t.clientX, startY: t.clientY, armed: false, ghost: null };
        timer = setTimeout(() => { if (drag) drag.armed = true; }, 400);
      };

      const onMove = (e) => {
        if (!drag) return;
        const t = e.touches[0];
        const dx = t.clientX - drag.startX;
        if (dx >= -20 && dx <= 20) return;
        e.preventDefault();
        if (!drag.ghost) {
          const g = drag.card.cloneNode(true);
          g.style.position = 'fixed';
          g.style.width = drag.card.offsetWidth + 'px';
          g.style.pointerEvents = 'none';
          g.style.zIndex = '9999';
          g.style.transform = 'scale(0.95) rotate(1deg)';
          g.style.transition = 'none';
          const overlay = document.createElement('div');
          overlay.style.cssText = 'position:absolute;inset:0;display:flex;align-items:center;border-radius:24px;opacity:0;pointer-events:none;z-index:20;font-size:15px;font-weight:800;color:#fff;transition:none';
          if (dx < 0) {
            overlay.style.background = 'linear-gradient(90deg,rgba(220,38,38,0.93),rgba(220,38,38,0.5) 55%,transparent)';
            overlay.style.justifyContent = 'flex-start';
            overlay.style.paddingLeft = '20px';
            overlay.innerHTML = '<svg style="width:22px;height:22px;margin-right:8px;flex-shrink:0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg>Видалити';
          } else {
            overlay.style.background = 'linear-gradient(270deg,rgba(34,197,94,0.93),rgba(34,197,94,0.5) 55%,transparent)';
            overlay.style.justifyContent = 'flex-end';
            overlay.style.paddingRight = '20px';
            overlay.innerHTML = 'Копіювати<svg style="width:22px;height:22px;margin-left:8px;flex-shrink:0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"/></svg>';
          }
          g.appendChild(overlay);
          document.body.appendChild(g);
          drag.ghost = g;
          drag._overlay = overlay;
        }
        drag.ghost.style.left = (t.clientX - drag.ghost.offsetWidth / 2) + 'px';
        drag.ghost.style.top = (t.clientY - 30) + 'px';
        const intensity = Math.min(Math.abs(dx) / 120, 1);
        drag._overlay.style.opacity = intensity;
      };

      const onEnd = async (e) => {
        clearTimeout(timer);
        if (!drag) return;
        const dx = e.changedTouches[0].clientX - drag.startX;
        if (dx < -80) {
          if (drag.ghost) drag.ghost.remove();
          drag.ghost = null;
          this.touchJustDragged = true;
          setTimeout(() => { this.touchJustDragged = false; }, 400);
          this.editingSessionId = drag.session.id;
          this.confirmData = {
            show: true,
            title: this.labels.confirm_delete_title || 'Видалити?',
            message: this.labels.confirm_delete_session || 'Дію неможливо скасувати.',
            onConfirm: async () => {
              await fetch(`/api/v1/sessions/${this.editingSessionId}`, { method: 'DELETE' });
              this.editingSessionId = null;
              await this.fetchData();
            }
          };
        } else if (dx > 80) {
          if (drag.ghost) drag.ghost.remove();
          drag.ghost = null;
          this.touchJustDragged = true;
          setTimeout(() => { this.touchJustDragged = false; }, 400);
          this.copySession(drag.session);
        }
        if (drag.ghost) drag.ghost.remove();
        drag = null;
      };

      container.addEventListener('touchstart', onStart, { passive: true });
      container.addEventListener('touchmove', onMove, { passive: false });
      container.addEventListener('touchend', onEnd, { passive: true });

      this._touchDragCleanup = () => {
        container.removeEventListener('touchstart', onStart);
        container.removeEventListener('touchmove', onMove);
        container.removeEventListener('touchend', onEnd);
      };
    },

    // ── Plan sessions modal ──────────────────────────────────────────────────

    openPlanSessions(trainee) {
      const firstDate = addDays(this.todayStr, 1);
      const locId = this.defaultLocationId(firstDate);
      const firstSlot = { date: firstDate, startTime: null, endTime: null, locationId: locId };
      this.planModal = { show: true, traineeId: trainee.id, traineeName: trainee.name, slots: [firstSlot], saving: false, error: '', weeklyCount: 8 };
      this.$nextTick(() => this.onPlanDateChange(firstSlot));
    },

    addPlanSlot() {
      const last = this.planModal.slots[this.planModal.slots.length - 1];
      const nextDate = last ? addDays(last.date, 7) : addDays(this.todayStr, 1);
      const locId = last?.locationId || this.defaultLocationId(nextDate);
      const newSlot = { date: nextDate, startTime: null, endTime: null, locationId: locId };
      this.planModal.slots.push(newSlot);
      this.$nextTick(() => this.onPlanDateChange(newSlot));
    },

    removePlanSlot(idx) {
      this.planModal.slots.splice(idx, 1);
    },

    fillWeeklyPlan() {
      const first = this.planModal.slots[0];
      if (!first || !first.date) return;
      const count = Math.max(2, Math.min(52, this.planModal.weeklyCount || 8));
      const slots = [];
      for (let i = 0; i < count; i++) {
        const date = addDays(first.date, i * 7);
        slots.push({ date, startTime: first.startTime, endTime: first.endTime, locationId: first.locationId || this.defaultLocationId(date) });
      }
      this.planModal.slots = slots;
    },

    planStartSlots(slot) {
      return this.computeValidSessionSlots(slot.date, this._planSlotOpts(slot));
    },

    planEndSlots(slot) {
      return this.computeValidSessionEndSlots(slot.date, slot.startTime, this._planSlotOpts(slot));
    },

    onPlanDateChange(slot) {
      slot.startTime = null;
      slot.endTime = null;
      slot.locationId = this.defaultLocationId(slot.date);
      const starts = this.planStartSlots(slot);
      if (starts.length) {
        slot.startTime = starts[0];
        const ends = this.planEndSlots(slot);
        slot.endTime = ends.length ? ends[0] : null;
      }
    },

    onPlanStartChange(slot) {
      const ends = this.planEndSlots(slot);
      slot.endTime = ends.length ? ends[0] : null;
    },

    _planSlotOpts(slot) {
      return { locationId: slot.locationId, traineeIds: [this.planModal.traineeId] };
    },

    async savePlanSessions() {
      const { traineeId, slots } = this.planModal;
      const valid = slots.filter(s => s.date && s.startTime && s.endTime && s.date >= this.todayStr);
      if (valid.length === 0) { this.planModal.error = 'Оберіть дату та час для хоча б одного тренування'; return; }
      this.planModal.saving = true;
      this.planModal.error = '';
      try {
        const baseTitle = (this.labels.session_title_default || 'Тренування') + ' — ' + this.planModal.traineeName;
        const created = [];
        for (const slot of valid) {
          const payload = {
            title: baseTitle,
            description: '',
            date: slot.date,
            time: slot.startTime,
            endTime: slot.endTime,
            traineeIds: [traineeId],
            mentorId: this.mentorId,
            locationId: slot.locationId || null,
            recurring: false,
            suppressNotification: true
          };
          const res = await fetch('/api/v1/sessions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
          if (res.ok) {
            const s = await res.json();
            created.push({ id: s.id, date: slot.date, time: slot.startTime, title: baseTitle });
          }
        }
        if (created.length > 0) {
          await fetch('/api/v1/sessions/batch-notify', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ mentorId: this.mentorId, traineeIds: [traineeId], sessions: created })
          });
        }
        this.planModal.show = false;
        await this.fetchData();
      } catch (e) {
        this.planModal.error = 'Помилка збереження';
      } finally {
        this.planModal.saving = false;
      }
    },

    async loadAgendaAvailability(startDate, endDate) {
      try {
        const res = await fetch(`/api/v1/coach/availability?startDate=${startDate}&endDate=${endDate}`);
        if (!res.ok) return;
        const data = await res.json();
        const updated = Object.assign({}, this.agendaAvailByDate);
        for (const item of data) {
          if (!updated[item.date]) updated[item.date] = [];
          const norm = t => t ? t.substring(0, 5) : t;
          updated[item.date].push({ startTime: norm(item.startTime), endTime: norm(item.endTime), locationId: item.locationId });
        }
        this.agendaAvailByDate = updated;
      } catch(e) {}
    },

    async reloadAgendaAvailability() {
      const dates = Object.keys(this.loadedDates).sort();
      if (!dates.length) return;
      this.agendaAvailByDate = {};
      await this.loadAgendaAvailability(dates[0], dates[dates.length - 1]);
    },

    toggleTraineeFilter(id) {
      if (id === null) this.selectedTraineeFilters = [];
      else {
        const idx = this.selectedTraineeFilters.indexOf(id);
        if (idx > -1) this.selectedTraineeFilters.splice(idx, 1);
        else this.selectedTraineeFilters.push(id);
      }
    },

  });
}());
