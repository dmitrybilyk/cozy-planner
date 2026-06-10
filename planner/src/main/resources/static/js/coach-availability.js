let _deferredInstall = null;
window.addEventListener('beforeinstallprompt', (e) => { e.preventDefault(); _deferredInstall = e; });

function rangeApp() {
    const MON = ['Січ','Лют','Бер','Кві','Тра','Чер','Лип','Сер','Вер','Жов','Лис','Гру'];
    const WD = ['Пн','Вт','Ср','Чт','Пт','Сб','Нд'];
    const today = (d=>`${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')}`)(new Date);

    return {
        mentorId: null, shareToken: null, shareUrl: '', copied: false,
        curDate: today, todayStr: today, days: [],
        ranges: [], freeAllDay: true,
        rangesByDate: {}, freeAllDayByDate: {},
        locs: [], trainees: [],
        mentorProfile: 'sport',
        dayOffs: [],
        deferredInstallPrompt: null,
        isStandalone: window.matchMedia('(display-mode: standalone)').matches,
        saving: false,
        workStart: '08:00',
        workEnd: '21:00',
        availStep: 30,
        get timeSlots15() {
            const ws = this.workStart || '08:00';
            const we = this.workEnd || '21:00';
            const step = this.availStep || 30;
            return Array.from({length: 96}, (_, i) => {
                const h = String(Math.floor(i / 4)).padStart(2, '0');
                const m = String([0, 15, 30, 45][i % 4]).padStart(2, '0');
                return `${h}:${m}`;
            }).filter(t => t >= ws && t <= we)
              .filter(t => {
                  if (step === 60) return t.endsWith(':00');
                  if (step === 30) return t.endsWith(':00') || t.endsWith(':30');
                  return true;
              });
        },

        slotToMin(t) {
            if (!t) return 0;
            const [h, m] = t.split(':').map(Number);
            return h * 60 + m;
        },

        get isDayOff() { return this.dayOffs.includes(this.curDate); },
        _dirtyDates: new Set(),
        _dirtyDayOffs: [],

        get dirty() { return this._dirtyDates.size > 0 || this._dirtyDayOffs.length > 0; },

        init() {
            if (_deferredInstall) this.deferredInstallPrompt = _deferredInstall;
            const now = new Date;
            for (let i = 0; i < 14; i++) {
                const d = new Date(now); d.setDate(now.getDate() + i);
                const ds = `${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')}`;
                this.days.push({ ds, wd: WD[(d.getDay()+6)%7], num: d.getDate(), mo: MON[d.getMonth()], isToday: ds === today, isWeekend: d.getDay() === 0 || d.getDay() === 6 });
            }
            this.load();
            this.connectWebSocket();
        },

        get mentorTitle() {
            const titles = { sport:'Моя доступність', studying:'Моя доступність', psychology:'Моя доступність', other:'Моя доступність' };
            return titles[this.mentorProfile] || titles.sport;
        },

        hasRanges(ds) {
            const fr = this.freeAllDayByDate[ds];
            if (fr === true) return false;
            const rr = this.rangesByDate[ds];
            return rr && rr.length > 0;
        },

        sel(d) {
            console.log('[avail] sel switching from', this.curDate, 'to', d, 'current ranges:', JSON.parse(JSON.stringify(this.ranges)));
            this.rangesByDate[this.curDate] = [...this.ranges];
            this.freeAllDayByDate[this.curDate] = this.freeAllDay;
            this.curDate = d;
            const cached = this.rangesByDate[d];
            this.ranges = cached ? [...cached] : [];
            this.freeAllDay = this.freeAllDayByDate[d] !== undefined ? this.freeAllDayByDate[d] : false;
            console.log('[avail] sel done, new ranges:', JSON.parse(JSON.stringify(this.ranges)));
        },

        addRange() {
            this.ranges.push({ startTime: '09:15', endTime: '17:45', locationId: null });
            this.markDirty();
        },

        removeRange(i) {
            this.ranges.splice(i, 1);
            this.markDirty();
        },

        onAvailStartChange(i) {
            const r = this.ranges[i];
            if (r && r.startTime) r._new = false;
            this.markDirty();
        },

        markDirty() {
            console.log('[avail] markDirty date:', this.curDate, 'prev size:', this._dirtyDates.size);
            this._dirtyDates.add(this.curDate);
        },

        onFreeAllDayToggle() {
            if (this.freeAllDay) {
                this.ranges = [];
            } else {
                if (this.ranges.length === 0) {
                    this.ranges.push({ startTime: '09:15', endTime: '17:45', locationId: null });
                }
            }
            this._dirtyDates.add(this.curDate);
        },

        toggleDayOff() {
            const wasDayOff = this.isDayOff;
            if (wasDayOff) {
                this.dayOffs = this.dayOffs.filter(d => d !== this.curDate);
                this._dirtyDayOffs.push({ date: this.curDate, action: 'remove' });
            } else {
                this.dayOffs.push(this.curDate);
                this.ranges = [];
                this.freeAllDay = true;
                this._dirtyDayOffs.push({ date: this.curDate, action: 'add' });
                this._dirtyDates.delete(this.curDate);
            }
        },

        getLocName(id) { return id ? (this.locs.find(l => Number(l.id) === Number(id))?.name || '') : ''; },
        getLocColor(id) { return id ? (this.locs.find(l => Number(l.id) === Number(id))?.color || null) : null; },
        timeOptionsAfter(fromTime) {
            if (!fromTime) return this.timeSlots15;
            return this.timeSlots15.filter(t => t > fromTime);
        },

        startSlots(excludeIndex) {
            const we = this.workEnd || '21:00';
            return this.timeSlots15.filter(t => {
                if (t >= we) return false;
                const tm = this.slotToMin(t);
                for (let i = 0; i < this.ranges.length; i++) {
                    if (i === excludeIndex) continue;
                    const r = this.ranges[i];
                    if (!r.startTime || !r.endTime) continue;
                    const rs = this.slotToMin(r.startTime);
                    const re = this.slotToMin(r.endTime);
                    if (tm >= rs && tm < re) return false;
                }
                return true;
            });
        },

        endSlots(startTime, excludeIndex) {
            if (!startTime) return this.timeOptionsAfter(startTime);
            const sm = this.slotToMin(startTime);
            return this.timeOptionsAfter(startTime).filter(t => {
                const tm = this.slotToMin(t);
                for (let i = 0; i < this.ranges.length; i++) {
                    if (i === excludeIndex) continue;
                    const r = this.ranges[i];
                    if (!r.startTime || !r.endTime) continue;
                    const rs = this.slotToMin(r.startTime);
                    const re = this.slotToMin(r.endTime);
                    if (sm < re && tm > rs) return false;
                }
                return true;
            });
        },

        mergeRanges(raw) {
            if (!raw || raw.length < 2) return raw;
            const sorted = [...raw].sort((a, b) => a.startTime < b.startTime ? -1 : a.startTime > b.startTime ? 1 : 0);
            const out = [];
            for (const r of sorted) {
                const prev = out[out.length - 1];
                const sameLoc = prev && (prev.locationId === r.locationId || (!prev.locationId && !r.locationId));
                if (prev && sameLoc && r.startTime <= prev.endTime) {
                    if (r.endTime > prev.endTime) prev.endTime = r.endTime;
                } else {
                    out.push({ startTime: r.startTime, endTime: r.endTime, locationId: r.locationId });
                }
            }
            return out;
        },

        async saveAll() {
            if (this.saving) return;
            console.log('[avail] saveAll start ranges:', JSON.stringify(this.ranges));
            this.ranges = this.mergeRanges(this.ranges);
            console.log('[avail] saveAll after merge:', JSON.stringify(this.ranges));
            this.saving = true;
            this.rangesByDate[this.curDate] = [...this.ranges];
            this.freeAllDayByDate[this.curDate] = this.freeAllDay;
            try {
                for (const { date, action } of this._dirtyDayOffs) {
                    await fetch('/api/v1/coach/day-off', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ date, action })
                    });
                }
                for (const date of this._dirtyDates) {
                    const isFreeAllDay = this.freeAllDayByDate[date] !== undefined ? this.freeAllDayByDate[date] : false;
                    if (isFreeAllDay) {
                        await fetch('/api/v1/availability/ranges', {
                            method: 'PUT',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                                userId: this.mentorId,
                                userType: 'COACH',
                                date,
                                ranges: []
                            })
                        });
                        continue;
                    }
                    const ranges = this.rangesByDate[date] || [];
                    await fetch('/api/v1/availability/ranges', {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            userId: this.mentorId,
                            userType: 'COACH',
                            date,
                            ranges: ranges.map(r => ({
                                startTime: r.startTime,
                                endTime: r.endTime,
                                locationId: r.locationId || null
                            }))
                        })
                    });
                }
                this._dirtyDates.clear();
                this._dirtyDayOffs = [];
                this.saved = true;
                this.savedMessage = 'Збережено!';
                setTimeout(() => this.saved = false, 3000);
            } catch(e) { console.error('[save] error', e); }
            finally { this.saving = false; this.load(); }
        },

        async load() {
            const me = await (await fetch('/api/v1/me')).json();
            if (!me.mentor || me.mentor.id < 0) { window.location.href = '/signin'; return; }
            this.mentorId = me.mentor.id;
            this.mentorProfile = me.mentor.profile || 'sport';
            this.workStart = me.mentor.workStart || '08:00';
            this.workEnd = me.mentor.workEnd || '21:00';
            this.availStep = me.mentor.availStep || 30;
            this.shareToken = me.mentor.shareToken;
            if (this.shareToken) this.shareUrl = window.location.origin + '/shared/' + this.shareToken;

            const sd = this.days[0].ds, ed = this.days[this.days.length-1].ds;
            const [lr, rr, dr] = await Promise.all([
                fetch(`/api/v1/mentors/${this.mentorId}/locations`),
                fetch(`/api/v1/availability/ranges?userId=${this.mentorId}&userType=COACH&startDate=${sd}&endDate=${ed}`),
                fetch(`/api/v1/coach/day-off?startDate=${sd}&endDate=${ed}`)
            ]);
            if (lr.ok) { this.locs = await lr.json(); }
            if (dr.ok) this.dayOffs = await dr.json();

            const tr = await fetch(`/api/v1/mentors/${this.mentorId}/trainees`);
            if (tr.ok) this.trainees = await tr.json();

            const rangesByDate = {};
            const freeAllDayByDate = {};
            for (const d of this.days) {
                rangesByDate[d.ds] = [];
                freeAllDayByDate[d.ds] = false;
            }
            if (rr.ok) {
                const data = await rr.json();
                console.log('[avail] API raw ranges response:', JSON.parse(JSON.stringify(data)));
                for (const item of data) {
                    if (!rangesByDate[item.date]) rangesByDate[item.date] = [];
                    rangesByDate[item.date].push({
                        startTime: (item.startTime || '').replace(/:00$/, ''),
                        endTime: (item.endTime || '').replace(/:00$/, ''),
                        locationId: item.locationId
                    });
                }
            }
            this.rangesByDate = rangesByDate;
            this.freeAllDayByDate = freeAllDayByDate;
            this.ranges = [...(rangesByDate[this.curDate] || [])];
            this.freeAllDay = freeAllDayByDate[this.curDate] !== undefined ? freeAllDayByDate[this.curDate] : true;
            console.log('[avail] after load, ranges:', JSON.parse(JSON.stringify(this.ranges)), 'date:', this.curDate);
            this._dirtyDates.clear();
            this._dirtyDayOffs = [];
        },

        connectWebSocket() {
            const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${wsProtocol}//${window.location.host}/api/v1/ws`;
            const ws = new WebSocket(wsUrl);
            ws.onmessage = (event) => {
                if (this.saving) return;
                if (event.data === 'coach_availability_changed' || event.data === 'availability_changed' || event.data === 'session_changed' || event.data === 'location_changed') {
                    console.log('[coach] reloading from ws:', event.data);
                    this.load();
                }
            };
            ws.onopen = () => console.log('[coach] ws connected');
            ws.onerror = () => console.log('[coach] ws error');
            this.ws = ws;
        },

        copyLink() {
            if (this.shareUrl) {
                navigator.clipboard.writeText(this.shareUrl).catch(() => {});
                this.copied = true;
                setTimeout(() => this.copied = false, 2000);
            }
        },

        getTraineeName(id) { return (this.trainees.find(at => at.id == id)?.name) || 'Unknown'; },

        async installPwa() {
            const prompt = this.deferredInstallPrompt || _deferredInstall;
            if (prompt) {
                prompt.prompt();
                const result = await prompt.userChoice;
                if (result.outcome === 'accepted') console.log('PWA installed');
                this.deferredInstallPrompt = null;
                _deferredInstall = null;
            }
        }
    };
}
