let _deferredInstall = null;
window.addEventListener('beforeinstallprompt', (e) => { e.preventDefault(); _deferredInstall = e; });

function localDateStr(d) {
    d = d || new Date();
    return `${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')}`;
}
function localDateStrTz(tz, d) {
    d = d || new Date();
    return new Intl.DateTimeFormat('en-CA', { timeZone: tz }).format(d);
}
function datePartsInTz(tz, d) {
    const fmt = new Intl.DateTimeFormat('en', { timeZone: tz, year: 'numeric', month: 'numeric', day: 'numeric', weekday: 'numeric' });
    const parts = fmt.formatToParts(d);
    let year, month, day, weekday;
    for (const p of parts) {
        if (p.type === 'year') year = parseInt(p.value);
        if (p.type === 'month') month = parseInt(p.value);
        if (p.type === 'day') day = parseInt(p.value);
        if (p.type === 'weekday') weekday = parseInt(p.value);
    }
    return { year, month, day, weekday };
}
const MON = ['Січ','Лют','Бер','Кві','Тра','Чер','Лип','Сер','Вер','Жов','Лис','Гру'];
const WD = ['Пн','Вт','Ср','Чт','Пт','Сб','Нд'];
const WD_NUM = {1:'Пн',2:'Вт',3:'Ср',4:'Чт',5:'Пт',6:'Сб',7:'Нд'};

function sharedAvailabilityApp() {
    return {
        shareToken: '',
        mentorName: '',
        loading: true,
        hasData: false,
        selectedDate: '',
        days: [],
        cellsByDate: {},
        dayOffs: [],
        mentorWorkStart: '06:00',
        mentorWorkEnd: '21:00',
        availStep: 30,
        grid: [],
        ws: null,
        singleDay: false,
        cardGrid: [],
        deferredInstallPrompt: null,
        isStandalone: window.matchMedia('(display-mode: standalone)').matches,
        today: '',
        mentorTimezone: '',
        _dataLoaded: false,

        isDayOff(dateStr) { return this.dayOffs.includes(dateStr); },

        get coachCurCells() {
            return this.cellsByDate[this.selectedDate] || [];
        },
        cellAt(mm) {
            return this.coachCurCells.find(x => x.mm === mm);
        },
        fmt(mm) {
            return `${String(Math.floor(mm/60)).padStart(2,'0')}:${String(mm%60).padStart(2,'0')}`;
        },
        get locations() {
            const seen = {};
            for (const date in this.cellsByDate) {
                for (const cell of this.cellsByDate[date]) {
                    if (cell.locName && !seen[cell.locName]) {
                        seen[cell.locName] = { name: cell.locName, color: cell.locColor || '#3b82f6' };
                    }
                }
            }
            return Object.values(seen);
        },
        get isTodaySelected() {
            return this.selectedDate === this.today;
        },
        dayName(dateStr) {
            const d = this.days.find(x => x.dateStr === dateStr);
            return d ? d.weekday + ', ' + d.dayNum + ' ' + d.month : '';
        },
        cellStyle(mm) {
            const c = this.cellAt(mm);
            if (!c) return 'background:#2a2a2a';
            if (c.locColor) return `background:${c.locColor}`;
            return 'background:#3b82f6';
        },
        get cardCurCells() {
            return this.cellsByDate[this.selectedDate] || [];
        },
        cardCellAt(mm) {
            return this.cardCurCells.find(x => x.mm === mm);
        },
        cardCellStyle(mm) {
            const c = this.cardCellAt(mm);
            if (!c) return 'background:#2a2a2a';
            if (c.locColor) return `background:${c.locColor}`;
            return 'background:#3b82f6';
        },

        buildWorkGrid() {
            this.grid = [];
            this.cardGrid = [];
            const step = this.availStep || 30;
            const [sh, sm] = (this.mentorWorkStart || '06:00').split(':').map(Number);
            const [eh, em] = (this.mentorWorkEnd || '21:00').split(':').map(Number);
            const startMin = sh * 60 + sm;
            const endMin = eh * 60 + em;
            for (let m = startMin; m < endMin; m += 60) {
                const hourEnd = Math.min(m + 60, endMin);
                const cells = [];
                for (let t = m; t < hourEnd; t += step) {
                    cells.push({ mm: t });
                }
                if (cells.length) {
                    const rowCells = cells.map(c => ({ mm: c.mm }));
                    this.grid.push({ lbl: `${String(Math.floor(m / 60)).padStart(2,'0')}:00`, cells: rowCells });
                    this.cardGrid.push({ lbl: `${String(Math.floor(m / 60)).padStart(2,'0')}:00`, cells: rowCells.map(c => ({ mm: c.mm })) });
                }
            }
        },

        _pickDate() {
            if (this._dateParam && this.days.some(d => d.dateStr === this._dateParam)) {
                this.selectedDate = this._dateParam;
            } else if (this.singleDay) {
                this.selectedDate = this.today;
            }
            if (!this.cellsByDate[this.selectedDate]?.length) {
                const sorted = Object.keys(this.cellsByDate).sort();
                if (sorted.length) this.selectedDate = sorted[0];
            }
        },

        async init() {
            if (_deferredInstall) this.deferredInstallPrompt = _deferredInstall;
            const pathParts = window.location.pathname.split('/');
            this.shareToken = pathParts[pathParts.length - 1];

            const params = new URLSearchParams(window.location.search);
            this._dateParam = params.get('date');
            if (this._dateParam) this.singleDay = true;

            this.today = localDateStr();
            this._buildDays();
            this.buildWorkGrid();

            await this.loadData();
            this._dataLoaded = true;
            this._pickDate();

            this.connectWebSocket();
            document.addEventListener('visibilitychange', () => {
                if (!document.hidden) this._reload();
            });
        },

        _reload() {
            this.loadData().then(() => {
                this._pickDate();
            });
        },

        _buildDays() {
            const tz = this.mentorTimezone;
            const now = new Date();
            for (let i = 0; i < 14; i++) {
                const d = new Date(now);
                d.setDate(now.getDate() + i);
                const ds = tz ? localDateStrTz(tz, d) : localDateStr(d);
                let weekday, dayNum, month, isWeekend;
                if (tz) {
                    const p = datePartsInTz(tz, d);
                    weekday = WD_NUM[p.weekday] || '';
                    dayNum = p.day;
                    month = MON[p.month - 1] || '';
                    isWeekend = p.weekday === 6 || p.weekday === 7;
                } else {
                    weekday = WD[(d.getDay()+6)%7];
                    dayNum = d.getDate();
                    month = MON[d.getMonth()];
                    isWeekend = d.getDay() === 0 || d.getDay() === 6;
                }
                this.days.push({
                    dateStr: ds,
                    weekday,
                    dayNum,
                    month,
                    isToday: ds === this.today,
                    isWeekend
                });
            }
        },

        connectWebSocket() {
            const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${wsProtocol}//${window.location.host}/api/v1/ws`;
            const ws = new WebSocket(wsUrl);
            ws.onmessage = (event) => {
                console.log('[shared] ws event:', event.data);
                if (event.data === 'coach_availability_changed' || event.data === 'location_changed' || event.data === 'session_changed') {
                    console.log('[shared] reloading data');
                    this._reload();
                }
            };
            ws.onclose = () => setTimeout(() => this.connectWebSocket(), 3000);
            ws.onerror = () => ws.close();
            this.ws = ws;
        },

        async loadData() {
            try {
                const start = this.days[0].dateStr;
                const end = this.days[this.days.length - 1].dateStr;

                const mentorRes = await fetch(`/api/v1/shared/${this.shareToken}/mentor?startDate=${start}&endDate=${end}`);
                if (!mentorRes.ok) { this.loading = false; return; }
                const mentorData = await mentorRes.json();
                this.mentorName = mentorData.name;
                this.dayOffs = mentorData.dayOffDates || [];

                const availRes = await fetch(`/api/v1/shared/${this.shareToken}?startDate=${start}&endDate=${end}`);
                if (!availRes.ok) { this.loading = false; return; }

                const availData = await availRes.json();
                const slots = availData.slots || [];
                this.dayOffs = this.dayOffs.length ? this.dayOffs : (availData.dayOffDates || []);
                if (availData.workStart) this.mentorWorkStart = availData.workStart;
                if (availData.workEnd) this.mentorWorkEnd = availData.workEnd;
                if (availData.availStep) this.availStep = availData.availStep;
                if (availData.mentorTimezone) {
                    this.mentorTimezone = availData.mentorTimezone;
                    this.today = localDateStrTz(this.mentorTimezone);
                    this.days.forEach(d => d.isToday = d.dateStr === this.today);
                }
                this.buildWorkGrid();

                const step = this.availStep || 30;
                const g = {};
                for (const item of slots) {
                    if (!g[item.date]) g[item.date] = [];
                    const [sh, sm] = item.startTime.split(':').map(Number);
                    const [eh, em] = item.endTime.split(':').map(Number);
                    let t = sh * 60 + sm, end = eh * 60 + em;
                    while (t < end) {
                        g[item.date].push({ mm: t, locId: item.locationId, locColor: item.locationColor, locName: item.locationName });
                        t += step;
                    }
                }

                this.cellsByDate = g;
                this.hasData = Object.keys(g).length > 0;
                this.loading = false;
            } catch (e) {
                console.error(e);
                this.loading = false;
            }
        },

        async installPwa() {
            const prompt = this.deferredInstallPrompt || _deferredInstall;
            if (prompt) {
                prompt.prompt();
                const result = await prompt.userChoice;
                this.deferredInstallPrompt = null;
                _deferredInstall = null;
            }
        }
    }
}
