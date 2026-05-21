function localDateStr(d) {
    d = d || new Date();
    return `${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')}`;
}
const MON = ['Січ','Лют','Бер','Кві','Тра','Чер','Лип','Сер','Вер','Жов','Лис','Гру'];
const WD = ['Пн','Вт','Ср','Чт','Пт','Сб','Нд'];

function sharedAvailabilityApp() {
    const today = localDateStr();
    return {
        shareToken: '',
        mentorName: '',
        loading: true,
        hasData: false,
        selectedDate: today,
        days: [],
        cellsByDate: {},
        dayOffs: [],
        mentorWorkStart: '06:00',
        mentorWorkEnd: '21:00',
        grid: [],
        ws: null,
        singleDay: false,
        cardGrid: [],

        isDayOff(dateStr) { return this.dayOffs.includes(dateStr); },

        get coachCurCells() {
            return this.cellsByDate[this.selectedDate] || [];
        },
        cellAt(mm) {
            return this.coachCurCells.find(x => x.mm === mm);
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
            return this.selectedDate === today;
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
            const ws = parseInt((this.mentorWorkStart || '06:00').split(':')[0]);
            const we = parseInt((this.mentorWorkEnd || '21:00').split(':')[0]);
            for (let h = ws; h < we; h++) {
                const cells = [{mm:h*60},{mm:h*60+30}];
                this.grid.push({ lbl: `${String(h).padStart(2,'0')}:00`, cells });
                this.cardGrid.push({ lbl: `${String(h).padStart(2,'0')}:00`, cells: [{mm:h*60},{mm:h*60+30}] });
            }
        },

        async init() {
            const pathParts = window.location.pathname.split('/');
            this.shareToken = pathParts[pathParts.length - 1];

            const params = new URLSearchParams(window.location.search);
            const dateParam = params.get('date');
            if (dateParam) this.singleDay = true;

            const now = new Date();
            for (let i = 0; i < 14; i++) {
                const d = new Date(now);
                d.setDate(now.getDate() + i);
                const ds = localDateStr(d);
                this.days.push({ dateStr: ds, weekday: WD[(d.getDay()+6)%7], dayNum: d.getDate(), month: MON[d.getMonth()], isToday: ds === today, isWeekend: d.getDay() === 0 || d.getDay() === 6 });
            }

            this.buildWorkGrid();

            await this.loadData();
            if (dateParam && this.days.some(d => d.dateStr === dateParam)) {
                this.selectedDate = dateParam;
            } else if (this.singleDay) {
                this.selectedDate = today;
            }
            this.connectWebSocket();
            document.addEventListener('visibilitychange', () => {
                if (!document.hidden) this.loadData();
            });
        },

        connectWebSocket() {
            const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${wsProtocol}//${window.location.host}/api/v1/ws`;
            const ws = new WebSocket(wsUrl);
            ws.onmessage = (event) => {
                console.log('[shared] ws event:', event.data);
                if (event.data === 'coach_availability_changed' || event.data === 'location_changed' || event.data === 'session_changed') {
                    console.log('[shared] reloading data');
                    this.loadData();
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
                this.buildWorkGrid();

                const g = {};
                for (const item of slots) {
                    if (!g[item.date]) g[item.date] = [];
                    const [sh, sm] = item.startTime.split(':').map(Number);
                    const [eh, em] = item.endTime.split(':').map(Number);
                    let t = sh * 60 + sm, end = eh * 60 + em;
                    while (t < end) {
                        g[item.date].push({ mm: t, locId: item.locationId, locColor: item.locationColor, locName: item.locationName });
                        t += 30;
                    }
                }

                this.cellsByDate = g;
                this.hasData = Object.keys(g).length > 0;
                if (this.hasData) {
                    this.selectedDate = Object.keys(g).sort()[0];
                }
                this.loading = false;
            } catch (e) {
                console.error(e);
                this.loading = false;
            }
        }
    }
}
