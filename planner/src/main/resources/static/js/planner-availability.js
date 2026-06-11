// planner-availability.js
// Coach availability (ranges, dates, save/copy) and Locations (CRUD, maps, free-time copy).
(function () {
    'use strict';
    window.plannerModules = window.plannerModules || {};

    // ── Availability Data Loading ──────────────────────────────────────────────

    Object.assign(window.plannerModules, {

        async fetchAvailability() {
            if (!this.mentorId) return;
            const start = this.days[0].dateStr;
            const end = this.days[this.days.length - 1].dateStr;
            const res = await fetch(`/api/v1/mentors/${this.mentorId}/availability?startDate=${start}&endDate=${end}`);
            if (res.ok) {
                const data = await res.json();
                const map = {};
                for (const entry of data) {
                    for (const slot of entry.slots) {
                        const key = entry.traineeId + '|' + slot.date;
                        if (!map[key]) map[key] = [];
                        map[key].push({ startTime: slot.startTime, endTime: slot.endTime });
                    }
                }
                this.availabilityMap = map;
            }
        },

        async fetchDayOffs() {
            if (!this.mentorId) return;
            const start = this.days[0].dateStr;
            const end = this.days[this.days.length - 1].dateStr;
            const res = await fetch(`/api/v1/coach/day-off?startDate=${start}&endDate=${end}`);
            if (res.ok) this.dayOffs = await res.json();
        },

        // ── Coach Availability UI ──────────────────────────────────────────────

        async loadCoachAvailability() {
            if (this.coachLoaded) return;
            const now = new Date();
            const WD = ['Пн','Вт','Ср','Чт','Пт','Сб','Нд'];
            const MON = ['Січ','Лют','Бер','Кві','Тра','Чер','Лип','Сер','Вер','Жов','Лис','Гру'];
            this.coachAvailDays = [];
            for (let i = 0; i < 14; i++) {
                const d = new Date(now); d.setDate(now.getDate() + i);
                const ds = localDateStr(d);
                this.coachAvailDays.push({ ds, wd: WD[(d.getDay()+6)%7], num: d.getDate(), mo: MON[d.getMonth()], isToday: ds === this.todayStr, isWeekend: d.getDay() === 0 || d.getDay() === 6 });
            }
            this.coachAvailDate = this.todayStr;
            await this.loadCoachAvailabilityData();
            this.coachDirtyDates.clear();
            this.coachLoaded = true;
            this.coachAvailSel(this.coachAvailDate);
        },

        async loadCoachAvailabilityData() {
            const sd = this.days[0]?.dateStr;
            const ed = this.days[this.days.length - 1]?.dateStr;
            if (!sd || !ed) return;
            try {
                const [ar, sr] = await Promise.all([
                    fetch(`/api/v1/coach/availability?startDate=${sd}&endDate=${ed}`),
                    fetch(`/api/v1/coach/sessions?startDate=${sd}&endDate=${ed}`),
                ]);
                if (ar.ok) {
                    const apiData = await ar.json();
                    const g = {};
                    for (const i of apiData) {
                        if (!g[i.date]) g[i.date] = [];
                        g[i.date].push({ startTime: this.coachNormTime(i.startTime), endTime: this.coachNormTime(i.endTime), locationId: i.locationId });
                    }
                    this.coachRangesByDate = g;
                }
                if (sr.ok) {
                    const apiData = await sr.json();
                    const g = {};
                    for (const i of apiData) {
                        if (!g[i.date]) g[i.date] = [];
                        g[i.date].push(i);
                    }
                    this.coachSess = g;
                }
            } catch(e) {
                console.error('[coach] load error', e);
            }
        },

        async coachSaveAll() {
            if (this.coachSaving) return;
            this.coachSaving = true;
            try {
                for (const date of this.coachDirtyDates) {
                    const ranges = this.coachRangesByDate[date] || [];
                    const body = [];
                    for (const r of ranges) {
                        if (r.startTime && r.endTime) {
                            body.push({ date, startTime: r.startTime, endTime: r.endTime, locationId: r.locationId || null });
                        }
                    }
                    if (body.length === 0) {
                        await fetch(`/api/v1/coach/availability?dates=${date}`, { method: 'DELETE' });
                    } else {
                        await fetch('/api/v1/coach/availability', {
                            method:'POST',
                            headers:{'Content-Type':'application/json'},
                            body:JSON.stringify(body)
                        });
                    }
                }
                this.coachDirtyDates.clear();
                for (const date of Object.keys(this.coachRangesByDate)) {
                    for (const r of this.coachRangesByDate[date]) r._new = false;
                }
                if (!this.shareToken) await this.mkShareToken();
            } catch(e) { console.error('[coach] saveAll error', e); }
            finally { this.coachSaving = false; }
        },

        // ── Coach Availability Range Helpers ──────────────────────────────────

        coachHasRanges(date) { return (this.coachRangesByDate[date] || []).length > 0; },
        coachNormTime(t) { return t ? t.replace(/:00$/, '') : t; },

        coachAvailSel(ds) {
            this.coachAvailDate = ds;
            this.coachRanges = this.coachRangesByDate[ds] || [];
            if (!this.coachRangesByDate[ds]) {
                this.coachRangesByDate[ds] = this.coachRanges;
            } else {
                for (const r of this.coachRanges) r._new = false;
            }
            this.coachSortRanges();
        },

        coachSortRanges() {
            this.coachRanges.sort((a, b) => (a.startTime || '').localeCompare(b.startTime || ''));
        },

        coachAddRange() {
            this.coachRanges.forEach(r => r._new = false);
            const defaultLocId = this.locations.length > 0 ? this.locations[0].id : null;
            this.coachRanges.unshift({ startTime: '', endTime: '', locationId: defaultLocId, _new: true });
            this.coachDirtyDates.add(this.coachAvailDate);
        },

        coachRemoveRange(i) {
            this.coachRanges.splice(i, 1);
            this.coachDirtyDates.add(this.coachAvailDate);
        },

        onCoachAvailStartChange(i) {
            this.coachSortRanges();
            this.coachDirtyDates.add(this.coachAvailDate);
        },

        coachMarkDirty() { this.coachDirtyDates.add(this.coachAvailDate); },

        timeOptionsAfter(startTime) {
            if (!startTime) return this.timeSlots15;
            return this.timeSlots15.filter(t => t > startTime);
        },

        coachStartSlots(excludeIndex) {
            const we = this.workEnd || '21:00';
            const result = this.timeSlots15.filter(t => {
                if (t >= we) return false;
                const tm = this.slotToMin(t);
                for (let i = 0; i < this.coachRanges.length; i++) {
                    if (i === excludeIndex) continue;
                    const r = this.coachRanges[i];
                    if (!r.startTime || !r.endTime) continue;
                    const rs = this.slotToMin(r.startTime);
                    const re = this.slotToMin(r.endTime);
                    if (tm >= rs && tm < re) return false;
                }
                return true;
            });
            return result;
        },

        coachEndSlots(startTime, excludeIndex) {
            if (!startTime) return this.timeOptionsAfter(startTime);
            const sm = this.slotToMin(startTime);
            const afterOpts = this.timeOptionsAfter(startTime);
            const result = afterOpts.filter(t => {
                const tm = this.slotToMin(t);
                for (let i = 0; i < this.coachRanges.length; i++) {
                    if (i === excludeIndex) continue;
                    const r = this.coachRanges[i];
                    if (!r.startTime || !r.endTime) continue;
                    const rs = this.slotToMin(r.startTime);
                    const re = this.slotToMin(r.endTime);
                    if (sm < re && tm > rs) return false;
                }
                return true;
            });
            return result;
        },

        // ── Coach Availability Copy / Share ────────────────────────────────────

        coachCopyLink() {
            if (!this.shareToken) return this.mkShareToken().then(() => this.coachCopyLink());
            if (!this.shareUrl) return;
            navigator.clipboard.writeText(this.shareUrl).then(() => {
                this.coachCopied = true;
                setTimeout(() => this.coachCopied = false, 2000);
            }).catch(e => prompt('Скопіюйте:', this.shareUrl));
        },

        coachCopyImageWeek() {
            if (!this.shareToken) return this.mkShareToken().then(() => this.coachCopyImageWeek());
            if (!this.shareToken) return;
            const imgUrl = window.location.origin + '/api/v1/shared/' + this.shareToken + '/image?date=' + this.coachAvailDate;
            navigator.clipboard.writeText(imgUrl).then(() => {
                this.coachImgCopiedWeek = true;
                setTimeout(() => this.coachImgCopiedWeek = false, 3000);
            }).catch(e => prompt('Скопіюйте посилання на картинку:', imgUrl));
        },

        // ── Locations CRUD ─────────────────────────────────────────────────────

        async fetchLocations() {
            if (!this.mentorId) return;
            const res = await fetch(`/api/v1/mentors/${this.mentorId}/locations`);
            if (res.ok) this.locations = await res.json();
        },

        async saveLocation() {
            const method = this.editingLocationId ? 'PUT' : 'POST';
            const url = this.editingLocationId ? `/api/v1/locations/${this.editingLocationId}` : '/api/v1/locations';
            await fetch(url, { method: method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...this.locationForm, mentorId: this.mentorId }) });
            this.locationForm = { name: '', description: '', color: '#3b82f6', googleMapsUrl: '' }; this.editingLocationId = null; this.showLocationFormModal = false;
            await this.fetchLocations();
        },

        askDeleteLocation(id) { this.confirmData = { show: true, title: this.labels.confirm_delete_title || 'Видалити?', message: this.labels.confirm_delete_location || 'Локація зникне з усіх тренувань.', onConfirm: async () => { await fetch(`/api/v1/locations/${id}`, { method: 'DELETE' }); await this.fetchLocations(); await this.fetchData(); } }; },
        openCreateLocationForm() { this.editingLocationId = null; this.locationForm = { name: '', description: '', color: '#3b82f6', googleMapsUrl: '' }; this.showLocationFormModal = true; },
        editLocation(l) { this.editingLocationId = l.id; this.locationForm = { name: l.name, description: l.description || '', color: l.color || '#3b82f6', googleMapsUrl: l.googleMapsUrl || '' }; this.showLocationFormModal = true; },
        copyGoogleMapsUrl(url) {
            if (!url) return;
            navigator.clipboard.writeText(url).catch(() => {});
        },

        // ── Free Time Copy ─────────────────────────────────────────────────────

        copyFreeTime(date, slots) {
            const dateStr = this.formatDisplayDate(date);
            const lines = slots.map(s => {
                const loc = s.locationId ? this.getLocationName(s.locationId) : '';
                return `• ${s.startStr} — ${s.endStr}${loc ? ' · ' + loc : ''}`;
            }).join('\n');
            const text = `${dateStr}\n${lines}`;
            navigator.clipboard.writeText(text).then(() => {
                this.toast.show = true;
                this.toast.message = 'Скопійовано!';
                setTimeout(() => { this.toast.show = false; }, 2500);
            }).catch(() => {});
        },

        copyFreeTimeAsPicture(date, slots) {
            const dateStr = this.formatDisplayDate(date);
            const scale = 2;
            const padX = 44, padTop = 40, padBot = 44;
            const slotGap = 20;
            const dateSize = 32, timeSize = 48, locSize = 28;

            const cvt = document.createElement('canvas');
            const ctx = cvt.getContext('2d');

            // measure max width needed
            ctx.font = `bold ${dateSize}px system-ui, sans-serif`;
            let maxW = ctx.measureText(dateStr).width;
            for (const s of slots) {
                ctx.font = `bold ${timeSize}px system-ui, sans-serif`;
                const tw = ctx.measureText(`${s.startStr} — ${s.endStr}`).width;
                if (tw > maxW) maxW = tw;
                if (s.locationId) {
                    ctx.font = `${locSize}px system-ui, sans-serif`;
                    const lw = ctx.measureText(this.getLocationName(s.locationId)).width;
                    if (lw > maxW) maxW = lw;
                }
            }
            const w = maxW + padX * 2;

            // calculate total height
            let h = padTop + dateSize + 16;
            for (let i = 0; i < slots.length; i++) {
                h += timeSize;
                if (slots[i].locationId) h += 8 + locSize;
                if (i < slots.length - 1) h += slotGap;
            }
            h += padBot;

            cvt.width = Math.round(w * scale);
            cvt.height = Math.round(h * scale);
            ctx.scale(scale, scale);

            // rounded-corner background
            ctx.fillStyle = '#121212';
            const r = 20;
            ctx.beginPath();
            ctx.moveTo(r, 0); ctx.lineTo(w - r, 0); ctx.quadraticCurveTo(w, 0, w, r);
            ctx.lineTo(w, h - r); ctx.quadraticCurveTo(w, h, w - r, h);
            ctx.lineTo(r, h); ctx.quadraticCurveTo(0, h, 0, h - r);
            ctx.lineTo(0, r); ctx.quadraticCurveTo(0, 0, r, 0);
            ctx.closePath(); ctx.fill();

            // date header
            ctx.font = `bold ${dateSize}px system-ui, sans-serif`;
            ctx.fillStyle = '#9ca3af';
            ctx.fillText(dateStr, padX, padTop + Math.round(dateSize * 0.8));

            // slots (y = top of each row, baseline = y + fontSize*0.8)
            let y = padTop + dateSize + 16;
            for (let i = 0; i < slots.length; i++) {
                const s = slots[i];
                ctx.font = `bold ${timeSize}px system-ui, sans-serif`;
                ctx.fillStyle = '#34d399';
                ctx.fillText(`${s.startStr} — ${s.endStr}`, padX, y + Math.round(timeSize * 0.8));
                y += timeSize;
                if (s.locationId) {
                    y += 8;
                    ctx.font = `${locSize}px system-ui, sans-serif`;
                    ctx.fillStyle = this.getLocationColor(s.locationId) || '#6b7280';
                    ctx.fillText(this.getLocationName(s.locationId), padX, y + Math.round(locSize * 0.8));
                    y += locSize;
                }
                if (i < slots.length - 1) y += slotGap;
            }

            cvt.toBlob(blob => {
                if (!blob) return;
                const download = () => {
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url; a.download = 'free-time.png'; a.click();
                    setTimeout(() => URL.revokeObjectURL(url), 1000);
                };
                navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]).then(() => {
                    this.toast.show = true;
                    this.toast.message = 'Картинку скопійовано';
                    setTimeout(() => { this.toast.show = false; }, 2500);
                }).catch(() => download());
            }, 'image/png');
        },

    });
}());
