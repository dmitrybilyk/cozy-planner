// planner-utils.js
// Pure helper/display functions: time math, formatting, card classes, availability checks, UI helpers.
(function () {
  'use strict';

  // These methods are mixed into the main app object.
  // Inside each method, `this` refers to the Alpine app state.
  window.plannerModules = window.plannerModules || {};

  Object.assign(window.plannerModules, {

    // ── Time helpers ─────────────────────────────────────────────────────────

    slotToMin(t) {
      const [h, m] = t.split(':').map(Number);
      return h * 60 + m;
    },

    minToTime(min) {
      const h = Math.floor(min / 60);
      const m = min % 60;
      return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}`;
    },

    convertTz(timeStr, dateStr, fromTz, toTz) {
      if (!timeStr) return '';
      toTz = toTz || Intl.DateTimeFormat().resolvedOptions().timeZone;
      if (!fromTz || fromTz === toTz) return timeStr;
      const d = dateStr || new Date().toISOString().slice(0, 10);
      const [h, m] = timeStr.split(':').map(Number);
      if (isNaN(h) || isNaN(m)) return timeStr;
      const tzOff = (tz) => {
        const dt = new Date(d + 'T' + timeStr + ':00');
        const f = new Intl.DateTimeFormat('en', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false });
        const p = f.formatToParts(dt);
        const v = (t) => parseInt(p.find(x => x.type === t)?.value || '0');
        return (Date.UTC(v('year'), v('month') - 1, v('day'), v('hour'), v('minute')) - dt.getTime()) / 60000;
      };
      let totalMin = h * 60 + m + (tzOff(toTz) - tzOff(fromTz));
      if (totalMin < 0) totalMin += 1440;
      if (totalMin >= 1440) totalMin -= 1440;
      return `${String(Math.floor(totalMin / 60)).padStart(2, '0')}:${String(totalMin % 60).padStart(2, '0')}`;
    },

    isWeekend(dateStr) {
      const d = new Date(dateStr + 'T12:00:00');
      const day = d.getDay();
      return day === 0 || day === 6;
    },

    isDayOff(dateStr) {
      return this.dayOffs.includes(dateStr);
    },

    isTimePassed() {
      if (this.sessionForm.startTime === null) return this.sessionForm.date <= this.todayStr;
      if (this.sessionForm.date < this.todayStr) return true;
      if (this.sessionForm.date === this.todayStr) {
        return new Date(`${this.sessionForm.date}T${this.sessionForm.startTime}`) <= new Date();
      }
      return false;
    },

    // ── Display / lookup helpers ──────────────────────────────────────────────

    formatDisplayDate(str) {
      const d = new Date(str);
      const months = ['січня', 'лютого', 'березня', 'квітня', 'травня', 'червня', 'липня', 'серпня', 'вересня', 'жовтня', 'листопада', 'грудня'];
      const fullDays = ['Понеділок', 'Вівторок', 'Середа', 'Четвер', 'П\'ятниця', 'Субота', 'Неділя'];
      const dayIdx = (d.getDay() + 6) % 7;
      return `${fullDays[dayIdx]}, ${d.getDate()} ${months[d.getMonth()]}`;
    },

    formatFeedbackDate(dateStr) {
      if (!dateStr) return '';
      const d = new Date(dateStr);
      const WD = ['Нд','Пн','Вт','Ср','Чт','Пт','Сб'];
      const MON = ['Січ','Лют','Бер','Кві','Тра','Чер','Лип','Сер','Вер','Жов','Лис','Гру'];
      const time = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
      return WD[d.getDay()] + ' ' + d.getDate() + ' ' + MON[d.getMonth()] + ', ' + time;
    },

    getTraineeName(id) {
      return (this.trainees.find(at => at.id == id)?.name) || 'Unknown';
    },

    getLocationName(id) {
      return this.locations.find(l => l.id == id)?.name || '';
    },

    getLocationColor(id) {
      return this.locations.find(l => l.id == id)?.color || '#3b82f6';
    },

    getLocName(id) {
      return this.getLocationName(id);
    },

    getLocColor(id) {
      return id ? this.getLocationColor(id) : null;
    },

    getTraineeConfirmStatus(traineeId, session) {
      const confirmed = (session.confirmedTraineeIds || '').split(',').map(s => s.trim()).filter(Boolean).map(Number);
      const rejected = (session.rejectedTraineeIds || '').split(',').map(s => s.trim()).filter(Boolean).map(Number);
      if (confirmed.includes(traineeId)) return 'confirmed';
      if (rejected.includes(traineeId)) return 'rejected';
      return 'none';
    },

    getConfirmationBadgeClass(status) {
      if (status === 'CONFIRMED') return 'bg-green-900/30 text-green-400';
      if (status === 'REJECTED') return 'bg-red-900/30 text-red-400';
      if (status === 'PENDING') return 'bg-yellow-900/30 text-yellow-400';
      return 'bg-gray-900/30 text-gray-400';
    },

    getConfirmationLabel(status) {
      if (status === 'CONFIRMED') return '✅ Підтв.';
      if (status === 'REJECTED') return '❌ Відмінено';
      if (status === 'PENDING') return '⏳ Очікує';
      return '';
    },

    allTraineesConfirmed(session) {
      const ids = session.traineeIds || [];
      if (ids.length === 0) return false;
      return ids.every(id => this.getTraineeConfirmStatus(id, session) === 'confirmed');
    },

    someTraineesConfirmed(session) {
      const ids = session.traineeIds || [];
      if (ids.length === 0) return false;
      return ids.some(id => this.getTraineeConfirmStatus(id, session) === 'confirmed') && !this.allTraineesConfirmed(session);
    },

    isTraineeTelegramConnected(traineeId) {
      const t = this.trainees.find(a => a.id == traineeId);
      return t && t.telegramConnected;
    },

    sessionHasTg(session) {
      if (this.mentorTg.connected) return true;
      return (session.traineeIds || []).some(id => this.isTraineeTelegramConnected(id));
    },

    getSessionIcons(session, index) {
      const count = (session.traineeIds || []).length;
      return Array(count).fill('😊');
    },

    getTraineeNamesText(ids) {
      return ids.map(id => this.getTraineeName(id)).filter(n => n).join(', ');
    },

    sessionDuration(session) {
      if (!session || !session.time || !session.endTime) return '';
      const diff = this.slotToMin(session.endTime) - this.slotToMin(session.time);
      const h = Math.floor(diff / 60);
      const m = diff % 60;
      if (h > 0 && m > 0) return `${h} год ${m} хв`;
      if (h > 0) return `${h} год`;
      return `${m} хв`;
    },

    getDurationLabel() {
      if (!this.sessionForm.startTime || !this.sessionForm.endTime) return '';
      const diff = this.slotToMin(this.sessionForm.endTime) - this.slotToMin(this.sessionForm.startTime);
      const h = Math.floor(diff / 60);
      const m = diff % 60;
      if (h > 0 && m > 0) return `${h} год ${m} хв`;
      if (h > 0) return `${h} год`;
      return `${m} хв`;
    },

    // ── Now-line / countdown helpers ─────────────────────────────────────────

    toUtcMin(timeStr, dateStr, tz) {
      if (!timeStr || !dateStr) return 0;
      const [h, m] = timeStr.split(':').map(Number);
      const [y, mo, d] = dateStr.split('-').map(Number);
      if (isNaN(h) || isNaN(m) || isNaN(y) || isNaN(mo) || isNaN(d)) return 0;
      const naiveUtc = Date.UTC(y, mo - 1, d, h, m);
      const f = new Intl.DateTimeFormat('en', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false });
      const p = f.formatToParts(new Date(naiveUtc));
      const v = (t) => parseInt(p.find(x => x.type === t)?.value || '0');
      const actualUtc = Date.UTC(v('year'), v('month') - 1, v('day'), v('hour'), v('minute'));
      return (naiveUtc - (actualUtc - naiveUtc)) / 60000;
    },

    sessionStartMin(session) {
      const [h, m] = (session.time || '0:00').split(':').map(Number);
      return h * 60 + (m || 0);
    },

    sessionEndMin(session) {
      const [h, m] = (session.endTime || session.time || '0:00').split(':').map(Number);
      return h * 60 + (m || 0);
    },

    nowCountdown(session, dateOrArray) {
      this.nowTick;
      if (!this.showNowLineAfter(session, dateOrArray)) return '';
      const dz = this.timezone;
      const allSessions = this._agendaSessionsForDate(session.date);
      const sessionStartUtc = this.toUtcMin(session.time, session.date, dz);
      const sorted = allSessions
        .filter(s => s.id !== session.id)
        .slice()
        .sort((a, b) => this.toUtcMin(a.time, a.date, dz) - this.toUtcMin(b.time, b.date, dz));
      const nextSession = sorted.find(s => this.toUtcMin(s.time, s.date, dz) > sessionStartUtc);
      if (!nextSession) return '';
      return this._formatCountdown(this.toUtcMin(nextSession.time, nextSession.date, dz));
    },

    nowCountdownBefore(session) {
      this.nowTick;
      if (!this.showNowLineBefore(session)) return '';
      const dz = this.timezone;
      const sessionStartUtc = this.toUtcMin(session.time, session.date, dz);
      return this._formatCountdown(sessionStartUtc);
    },

    showNowLineBefore(session) {
      try {
        if (!session.time || !session.date) return false;
        this.nowTick;
        const nowUtc = Date.now() / 60000;
        const dz = this.timezone;
        const dayStart = this.toUtcMin('00:00', session.date, dz);
        if (nowUtc < dayStart || nowUtc >= dayStart + 1440) return false;
        const sessionStartUtc = this.toUtcMin(session.time, session.date, dz);
        if (nowUtc >= sessionStartUtc) return false;
        const allSessions = this._agendaSessionsForDate(session.date);
        const hasPrev = allSessions
          .filter(s => s.id !== session.id)
          .some(s => this.toUtcMin(s.time, s.date, dz) < sessionStartUtc);
        return !hasPrev;
      } catch (e) {
        console.error(`[nowline-before] ERROR session ${session.id}:`, e);
        return false;
      }
    },

    showNowLineAfter(session, dateOrArray) {
      try {
        if (!session.time || !session.date) return false;
        this.nowTick;
        const nowUtc = Date.now() / 60000;
        const dz = this.timezone;
        const dayStart = this.toUtcMin('00:00', session.date, dz);
        if (nowUtc < dayStart || nowUtc >= dayStart + 1440) return false;
        const sessionStartUtc = this.toUtcMin(session.time, session.date, dz);
        const end = session.endTime ? this.toUtcMin(session.endTime, session.date, dz) : sessionStartUtc + 60;
        if (nowUtc < end) return false;
        const allSessions = this._agendaSessionsForDate(session.date);
        const sorted = allSessions
          .filter(s => s.id !== session.id)
          .slice()
          .sort((a, b) => this.toUtcMin(a.time, a.date, dz) - this.toUtcMin(b.time, b.date, dz));
        const nextSession = sorted.find(s => this.toUtcMin(s.time, s.date, dz) > sessionStartUtc);
        if (!nextSession) return false;
        const nextStart = this.toUtcMin(nextSession.time, nextSession.date, dz);
        return end <= nowUtc && nowUtc < nextStart;
      } catch (e) {
        console.error(`[nowline] ERROR session ${session.id}:`, e);
        return false;
      }
    },

    _agendaSessionsForDate(date) {
      const result = this.sessions.filter(s => s.date === date);
      console.log(`[agenda] sessionsForDate(${date}) → ${result.length} sessions (total ${this.sessions.length})`);
      return result;
    },

    _formatCountdown(targetUtc) {
      const diffMs = targetUtc * 60000 - Date.now();
      if (diffMs <= 0) return '';
      const h = Math.floor(diffMs / 3600000);
      const m = Math.floor((diffMs % 3600000) / 60000);
      const s = Math.floor((diffMs % 60000) / 1000);
      if (h > 0) return `● ${h} г ${m} хв ${s} с`;
      if (m > 0) return `● ${m} хв ${s} с`;
      return `● ${s} с`;
    },

    // ── Card / UI helpers ────────────────────────────────────────────────────

    getCardBorderClass(session) {
      if (this.isSessionNow(session)) return 'bg-[#1c1c1c] ring-4 ring-red-500/30 border-red-500/20 shadow-lg shadow-red-500/10';
      if (session.confirmationStatus === 'CONFIRMED') return 'bg-green-900/20 border-green-500';
      if (session.confirmationStatus === 'REJECTED') return 'bg-red-900/10 border-red-500/30 opacity-70';
      if (session.confirmationStatus === 'PENDING' && session.createdBy === 'TRAINEE') return 'bg-yellow-900/10 border-yellow-500/30';
      if (session.confirmationStatus === 'PENDING') {
        const confirmed = (session.confirmedTraineeIds || '').split(',').filter(Boolean).length;
        const total = (session.traineeIds || []).length;
        if (confirmed > 0) return 'bg-yellow-900/15 border-yellow-600/40';
        return 'bg-yellow-900/10 border-yellow-500/20';
      }
      return 'bg-[#1c1c1c] border border-[#333]';
    },

    getAgendaCardClass(session) {
      if (this.isSessionNow(session)) return 'bg-[#1c1c1c] ring-4 ring-red-500/30 border-red-500/20 shadow-lg shadow-red-500/10';
      if (session.confirmationStatus === 'CONFIRMED') return 'bg-green-900/20 border-green-500';
      if (session.confirmationStatus === 'REJECTED') return 'bg-red-900/10 border-red-500/30 opacity-70';
      if (session.confirmationStatus === 'PENDING' && session.createdBy === 'TRAINEE') return 'bg-yellow-900/10 border-yellow-500/30';
      if (session.confirmationStatus === 'PENDING') {
        const confirmed = (session.confirmedTraineeIds || '').split(',').filter(Boolean).length;
        const total = (session.traineeIds || []).length;
        if (confirmed > 0) return 'bg-yellow-900/15 border-yellow-600/40';
        return 'bg-yellow-900/10 border-yellow-500/20';
      }
      return 'bg-[#1c1c1c] border border-[#333]';
    },

    isExpanded(id) {
      return this.expandedIds.includes(id);
    },

    isCollapsed(id) {
      return this.collapsedIds.indexOf(id) >= 0;
    },

    toggleExpand(id) {
      const idx = this.expandedIds.indexOf(id);
      if (idx >= 0) {
        this.expandedIds.splice(idx, 1);
      } else {
        this.expandedIds.push(id);
      }
    },

    toggleCollapse(id) {
      const idx = this.collapsedIds.indexOf(id);
      if (idx >= 0) {
        this.collapsedIds.splice(idx, 1);
      } else {
        this.collapsedIds.push(id);
      }
    },

    targetIndex(id) {
      return this.filteredSessions.findIndex(w => w.id === id);
    },

    handleCardClick(session, index) {
      if (this.touchJustDragged) return;
      if (session.date < this.todayStr) return;
      this.editSession(session);
    },

    toggleCompactView() {
      this.compactView = !this.compactView;
      if (this.compactView) {
        this.expandedIds = [];
      } else {
        this.collapsedIds = [];
      }
    },

    dragStart(event, index) {
      this.draggedIndex = index;
    },

    dragEnd() {
      this.draggedIndex = null;
    },

    handleMouseScroll(e) {
      document.getElementById('calendar-container').scrollLeft += e.deltaY;
    },

    // ── Availability display helpers ─────────────────────────────────────────

    hasSessionOn(date) {
      if (date < addDays(this.todayStr, -14)) return false;
      return (this.sessionCounts[date] || 0) > 0;
    },

    traineeHasFutureAvailability(id) {
      return this.futureDays.some(d => this.getFutureSlots(id, d.dateStr).length > 0);
    },

    isAvailable(traineeId, date) {
      const key = traineeId + '|' + date;
      return this.availabilityMap[key] !== undefined ? this.availabilityMap[key] : null;
    },

    getFutureSlots(traineeId, dateStr) {
      const slots = this.availabilityMap[traineeId + '|' + dateStr];
      if (!slots) return [];
      if (slots.some(s => s.startTime === 'all_day')) return [{startTime: 'all_day', endTime: 'all_day'}];
      if (dateStr !== this.todayStr) return slots;
      const now = new Date();
      const currentMin = now.getHours() * 60 + now.getMinutes();
      return slots.filter(s => this.slotToMin(s.endTime) > currentMin);
    },

    getAvailLabel(slots) {
      if (!slots || slots.length === 0) return '';
      return slots.map(s => s.startTime === 'all_day' ? 'Весь день' : s.startTime.slice(0,5) + '-' + s.endTime.slice(0,5)).join(', ');
    },

    getAvailPopupRanges() {
      const date = this.sessionForm.date;
      const ranges = this.coachRangesByDate?.[date] || [];
      const groups = {};
      for (const r of ranges) {
        if (!r.startTime && !r.endTime) continue;
        const key = r.locationId ?? '__none__';
        if (!groups[key]) groups[key] = { locationId: r.locationId, ranges: [] };
        groups[key].ranges.push(r);
      }
      return Object.values(groups).map(g => ({
        ...g,
        locationName: g.locationId ? this.getLocationName(g.locationId) : null,
        locationColor: g.locationId ? this.getLocationColor(g.locationId) : null
      }));
    },

    _traineeAvailConflicts(traineeId, startTime, date) {
      if (!startTime || !date) return false;
      const slots = this.isAvailable(traineeId, date);
      if (!slots || slots.length === 0) return false;
      if (slots.some(s => s.startTime === 'all_day')) return false;
      return !slots.some(s => startTime >= s.startTime.slice(0, 5) && startTime < s.endTime.slice(0, 5));
    },

    _isSlotInAllTraineeAvail(fromMin, toMin, date, traineeIds) {
      const ids = traineeIds ?? this.sessionForm.traineeIds ?? [];
      for (const tId of ids) {
        const slots = this.availabilityMap[tId + '|' + date];
        if (slots && slots.length > 0) {
          if (slots.some(s => s.startTime === 'all_day')) continue;
          const ok = slots.some(s => {
            const ss = this.slotToMin(s.startTime);
            const se = this.slotToMin(s.endTime);
            return fromMin >= ss && toMin <= se;
          });
          if (!ok) return false;
        }
      }
      return true;
    },

    computeValidSessionSlots(date, { locationId: locIdOverride, traineeIds: tIdsOverride, ignoreAvail = false } = {}) {
      if (!date) return [];
      const step = this.availStep || 30;
      const allSlots = this.timeSlots15;
      const we = this.workEnd || '21:00';
      const busyMinRanges = this.getBusyMinuteRanges(date);
      const dateRanges = this.coachRangesByDate?.[date] || [];
      const hasAvail = !ignoreAvail && dateRanges.length > 0;
      const locId = locIdOverride !== undefined ? locIdOverride : this.sessionForm.locationId;
      const tIds = tIdsOverride !== undefined ? tIdsOverride : undefined;
      if (hasAvail && this.locations.length > 0 && !locId) return [];
      const now = new Date();
      const isToday = date === localDateStr(now);
      const todayMin = now.getHours() * 60 + now.getMinutes();

      return allSlots.filter(t => {
        if (t >= we) return false;
        const tm = this.slotToMin(t);
        if (isToday && tm <= todayMin) return false;
        if (hasAvail) {
          const filteredRanges = locId ? dateRanges.filter(r => r.locationId == locId || r.locationId == null) : dateRanges;
          if (filteredRanges.length === 0) return false;
          const inRange = filteredRanges.some(r =>
            tm >= this.slotToMin(r.startTime) && tm + step <= this.slotToMin(r.endTime)
          );
          if (!inRange) return false;
        }
        for (const [bs, be] of busyMinRanges) {
          if (tm < be && tm + step > bs) return false;
        }
        if (!this._isSlotInAllTraineeAvail(tm, tm + step, date, tIds)) return false;
        return true;
      });
    },

    computeValidSessionEndSlots(date, startTime, { locationId: locIdOverride, traineeIds: tIdsOverride, ignoreAvail = false } = {}) {
      if (!date || !startTime) return [];
      const allSlots = this.timeSlots15;
      const busyMinRanges = this.getBusyMinuteRanges(date);
      const dateRanges = this.coachRangesByDate?.[date] || [];
      const hasAvail = !ignoreAvail && dateRanges.length > 0;
      const locId = locIdOverride !== undefined ? locIdOverride : this.sessionForm.locationId;
      const tIds = tIdsOverride !== undefined ? tIdsOverride : undefined;
      if (hasAvail && this.locations.length > 0 && !locId) return [];
      const sm = this.slotToMin(startTime);
      const step = this.availStep || 30;

      return allSlots.filter(t => {
        const tm = this.slotToMin(t);
        if (tm <= sm) return false;
        if (hasAvail) {
          const filteredRanges = locId ? dateRanges.filter(r => r.locationId == locId || r.locationId == null) : dateRanges;
          if (filteredRanges.length === 0) return false;
          const inRange = filteredRanges.some(r =>
            tm <= this.slotToMin(r.endTime) && sm >= this.slotToMin(r.startTime)
          );
          if (!inRange) return false;
        }
        for (const [bs, be] of busyMinRanges) {
          if (tm > bs && sm < be) return false;
        }
        if (!this._isSlotInAllTraineeAvail(sm, tm, date, tIds)) return false;
        return true;
      });
    },

    getBusyMinuteRanges(date) {
      const ranges = [];
      const allSessions = this.sessions || [];
      for (const s of allSessions) {
        if (s.date && s.date !== date) continue;
        if (this.editingSessionId && s.id === this.editingSessionId) continue;
        const st = s.startTime || s.time;
        const et = s.endTime || st;
        if (!st) continue;
        ranges.push([this.slotToMin(st), this.slotToMin(et)]);
      }
      return ranges;
    },

    // ── Fetch with timeout ───────────────────────────────────────────────────

    async _fetchWithTimeout(url, options = {}, timeoutMs = 10000) {
      const ctrl = new AbortController();
      const id = setTimeout(() => ctrl.abort(), timeoutMs);
      try {
        return await fetch(url, { ...options, signal: ctrl.signal });
      } finally {
        clearTimeout(id);
      }
    },

  });
}());
