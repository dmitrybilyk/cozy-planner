function app() {
    const MON = ['Січ','Лют','Бер','Кві','Тра','Чер','Лип','Сер','Вер','Жов','Лис','Гру'];
    const WD = ['Пн','Вт','Ср','Чт','Пт','Сб','Нд'];
    const today = (d=>`${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')}`)(new Date);

    return {
        mentorId: null, shareToken: null, shareUrl: '', copied: false, todayCopied: false, imgCopiedWeek: false, imgCopiedDay: false,
        curDate: today, days: [], grid: [],
        cells: {}, sess: {}, locs: [], curLoc: null,
        ws: null,
        busySession: null,

        init() {
            const now = new Date;
            for (let i = 0; i < 14; i++) {
                const d = new Date(now); d.setDate(now.getDate() + i);
                const ds = `${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')}`;
                this.days.push({ ds, wd: WD[(d.getDay()+6)%7], num: d.getDate(), mo: MON[d.getMonth()], isToday: ds === today });
            }
            for (let h = 6; h < 22; h++) {
                const cells = [{mm:h*60},{mm:h*60+30}];
                this.grid.push({ lbl: `${String(h).padStart(2,'0')}:00`, cells });
            }
            this.load();
            this.connectWebSocket();
        },

        getCurCells() { return this.cells[this.curDate] || []; },

        cellAt(mm) { return this.getCurCells().find(x => x.mm === mm); },

        inSess(mm) {
            return (this.sess[this.curDate] || []).some(s => {
                const [sh,sm] = s.startTime.split(':').map(Number);
                const [eh,em] = s.endTime.split(':').map(Number);
                return mm >= sh*60+sm && mm < eh*60+em;
            });
        },

        title(mm) {
            return `${String(Math.floor(mm/60)).padStart(2,'0')}:${String(mm%60).padStart(2,'0')}`;
        },

        hasDaySlots(ds) { return (this.cells[ds] || []).length > 0; },

        sessionAt(mm) {
            return (this.sess[this.curDate] || []).find(s => {
                const [sh,sm] = s.startTime.split(':').map(Number);
                const [eh,em] = s.endTime.split(':').map(Number);
                return mm >= sh*60+sm && mm < eh*60+em;
            }) || null;
        },

        async tap(mm) {
            const busy = this.sessionAt(mm);
            if (busy) { this.busySession = busy; return; }
            const arr = this.getCurCells();
            const idx = arr.findIndex(x => x.mm === mm);
            if (idx >= 0) {
                arr.splice(idx, 1);
            } else {
                arr.push({ mm, locId: this.curLoc ? Number(this.curLoc) : null });
            }
            this.cells[this.curDate] = [...arr];
            await this.quickSaveDate(this.curDate);
        },

        async quickSaveDate(date) {
            this._quickSaveCount = (this._quickSaveCount || 0) + 1;
            const arr = this.cells[date] || [];
            console.log(`[coach] quickSaveDate ${date} cells=${arr.length}`);
            try {
                if (!arr.length) {
                    const r = await fetch(`/api/v1/coach/availability?dates=${date}`, { method: 'DELETE' });
                    if (!r.ok) console.error(`[coach] DELETE fail ${date} status=${r.status}`);
                    else console.log(`[coach] DELETE ok ${date}`);
                    return;
                }
                const sorted = [...arr].sort((a,b) => a.mm - b.mm);
                const merged = [];
                let cs = sorted[0].mm, ce = sorted[0].mm + 30, cl = sorted[0].locId;
                for (let i = 1; i < sorted.length; i++) {
                    if (sorted[i].mm === ce && sorted[i].locId === cl) {
                        ce = sorted[i].mm + 30;
                    } else {
                        merged.push({ date, startTime: this.minStr(cs), endTime: this.minStr(ce), locationId: cl || null });
                        cs = sorted[i].mm; ce = sorted[i].mm + 30; cl = sorted[i].locId;
                    }
                }
                merged.push({ date, startTime: this.minStr(cs), endTime: this.minStr(ce), locationId: cl || null });
                const r = await fetch('/api/v1/coach/availability', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(merged) });
                if (!r.ok) console.error(`[coach] POST fail status=${r.status}`, merged);
                else {
                    console.log(`[coach] POST ok ${date} merged=${merged.length}`);
                    if (!this.shareToken) await this.mkToken();
                }
            } catch(e) { console.error('[coach] quickSaveDate error', e); }
            finally { this._quickSaveCount--; }
        },

        cellStyle(mm) {
            if (this.inSess(mm)) return 'background:#dc2626';
            const c = this.cellAt(mm);
            if (!c) return 'background:#2a2a2a';
            if (c.locId != null && this.locs.length) {
                const l = this.locs.find(x => Number(x.id) === Number(c.locId));
                if (l && l.color) return `background:${l.color}`;
            }
            return 'background:#3b82f6';
        },

        sel(d) { this.curDate = d; },

        async load() {
            const r = await fetch('/api/v1/me');
            if (!r.ok) { window.location.href = '/signin'; return; }
            const me = await r.json();
            if (!me.mentor || me.mentor.id < 0) { window.location.href = '/signin'; return; }
            this.mentorId = me.mentor.id;
            this.shareToken = me.mentor.shareToken;
            if (this.shareToken) this.shareUrl = window.location.origin + '/shared/' + this.shareToken;

            const sd = this.days[0].ds, ed = this.days[this.days.length-1].ds;
            const [lr, ar, sr] = await Promise.all([
                fetch(`/api/v1/mentors/${this.mentorId}/locations`),
                fetch(`/api/v1/coach/availability?startDate=${sd}&endDate=${ed}`),
                fetch(`/api/v1/coach/sessions?startDate=${sd}&endDate=${ed}`)
            ]);
            if (lr.ok) this.locs = await lr.json();

            if (ar.ok) {
                const g = {};
                for (const i of await ar.json()) {
                    if (!g[i.date]) g[i.date] = [];
                    const [sh,sm] = i.startTime.split(':').map(Number);
                    const [eh,em] = i.endTime.split(':').map(Number);
                    let t = sh*60+sm, end = eh*60+em;
                    while (t < end) {
                        g[i.date].push({ mm: t, locId: i.locationId });
                        t += 30;
                    }
                }
                this.cells = g;
            }

            if (sr.ok) {
                const g = {};
                for (const i of await sr.json()) {
                    if (!g[i.date]) g[i.date] = [];
                    g[i.date].push(i);
                }
                this.sess = g;
            }
        },

        minStr(m) { return `${String(Math.floor(m/60)).padStart(2,'0')}:${String(m%60).padStart(2,'0')}`; },

        connectWebSocket() {
            const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${wsProtocol}//${window.location.host}/api/v1/ws`;
            const ws = new WebSocket(wsUrl);
            ws.onmessage = (event) => {
                console.log('[coach] ws event:', event.data, 'quickSaveCount:', this._quickSaveCount);
                if (event.data === 'coach_availability_changed' && !this._quickSaveCount) {
                    console.log('[coach] reloading from ws');
                    this.load();
                }
                if (event.data === 'session_changed' || event.data === 'location_changed') {
                    console.log('[coach] reloading from ws (session/location)');
                    this.load();
                }
            };
            ws.onclose = () => setTimeout(() => this.connectWebSocket(), 3000);
            ws.onerror = () => ws.close();
            this.ws = ws;
        },

        async mkToken() {
            if (this.shareToken) return;
            const r = await fetch('/api/v1/coach/share-token', { method:'POST' });
            if (r.ok) { const d = await r.json(); this.shareToken = d.shareToken; this.shareUrl = window.location.origin + '/shared/' + this.shareToken; }
        },

        async copyLink() {
            try { await navigator.clipboard.writeText(this.shareUrl); this.copied = true; setTimeout(() => this.copied = false, 2000); }
            catch(e) { prompt('Скопіюйте:', this.shareUrl); }
        },
        async copyTodayLink() {
            const url = this.shareUrl + '?date=' + this.curDate;
            try { await navigator.clipboard.writeText(url); this.todayCopied = true; setTimeout(() => this.todayCopied = false, 2000); }
            catch(e) { prompt('Скопіюйте:', url); }
        },
        async copyImageWeek() {
            if (!this.shareToken) await this.mkToken();
            if (!this.shareToken) return;
            const imgUrl = window.location.origin + '/api/v1/shared/' + this.shareToken + '/image';
            try { await navigator.clipboard.writeText(imgUrl); this.imgCopiedWeek = true; setTimeout(() => this.imgCopiedWeek = false, 3000); }
            catch(e) { prompt('Скопіюйте посилання на картинку:', imgUrl); }
        },
        async copyImageDay() {
            if (!this.shareToken) await this.mkToken();
            if (!this.shareToken) return;
            const imgUrl = window.location.origin + '/api/v1/shared/' + this.shareToken + '/image?date=' + this.curDate;
            try { await navigator.clipboard.writeText(imgUrl); this.imgCopiedDay = true; setTimeout(() => this.imgCopiedDay = false, 3000); }
            catch(e) { prompt('Скопіюйте посилання на картинку:', imgUrl); }
        }
    }
}
