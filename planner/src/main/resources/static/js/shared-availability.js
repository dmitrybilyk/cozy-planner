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
        rangesByDate: {},
        dayOffs: [],
        availStep: 30,
        ws: null,
        deferredInstallPrompt: null,
        isStandalone: window.matchMedia('(display-mode: standalone)').matches,
        today: '',
        mentorTimezone: '',

        isDayOff(dateStr) { return this.dayOffs.includes(dateStr); },

        get selectedRanges() {
            return (this.rangesByDate[this.selectedDate] || []).slice().sort((a, b) => a.startTime.localeCompare(b.startTime));
        },

        get locations() {
            const seen = {};
            for (const date in this.rangesByDate) {
                for (const r of this.rangesByDate[date]) {
                    if (r.locName && !seen[r.locName]) {
                        seen[r.locName] = { name: r.locName, color: r.locColor || '#3b82f6' };
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
            if (!d) return '';
            const dayNames = ['Неділя','Понеділок','Вівторок','Середа','Четвер','П\'ятниця','Субота'];
            const weekdayIndex = WD.indexOf(d.weekday) + 1;
            if (weekdayIndex > 0 && weekdayIndex <= 7) {
                return dayNames[weekdayIndex % 7] + ', ' + d.dayNum + ' ' + d.month;
            }
            return d.weekday + ', ' + d.dayNum + ' ' + d.month;
        },

        _pickDate() {
            if (this.selectedDate && this.rangesByDate[this.selectedDate]?.length) return;
            if (this.rangesByDate[this.today]?.length) { this.selectedDate = this.today; return; }
            const sorted = Object.keys(this.rangesByDate).sort();
            if (sorted.length) { this.selectedDate = sorted[0]; return; }
            this.selectedDate = this.today;
        },

        async init() {
            if (_deferredInstall) this.deferredInstallPrompt = _deferredInstall;
            const pathParts = window.location.pathname.split('/');
            this.shareToken = pathParts[pathParts.length - 1];

            const params = new URLSearchParams(window.location.search);
            this.selectedDate = params.get('date') || '';

            this.today = localDateStr();
            this._buildDays();

            await this.loadData();
            this._pickDate();

            this.connectWebSocket();
            document.addEventListener('visibilitychange', () => {
                if (!document.hidden) this._reload();
            });
        },

        _reload() {
            this.loadData().then(() => { this._pickDate(); });
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
                if (availData.availStep) this.availStep = availData.availStep;
                if (availData.mentorTimezone) {
                    this.mentorTimezone = availData.mentorTimezone;
                    this.today = localDateStrTz(this.mentorTimezone);
                    this.days.forEach(d => d.isToday = d.dateStr === this.today);
                }

                const g = {};
                for (const item of slots) {
                    if (!g[item.date]) g[item.date] = [];
                    g[item.date].push({
                        startTime: item.startTime,
                        endTime: item.endTime,
                        locId: item.locationId,
                        locColor: item.locationColor,
                        locName: item.locationName
                    });
                }

                this.rangesByDate = g;
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
