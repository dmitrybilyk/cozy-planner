function localDateStr(d) {
    d = d || new Date();
    return `${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')}`;
}
function snapToMonday(date) {
    const d = new Date(date);
    const day = d.getDay();
    const diff = d.getDate() - day + (day === 0 ? -6 : 1);
    d.setDate(diff);
    return d;
}
function availabilityApp() {
    const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    const today = localDateStr();
    return {
        traineeId: null,
        selectedDate: today,
        days: [],
        slotsByDate: {},
        pickStart: null,
        daySlots: Array.from({length: 29}, (_, i) => `${(7+Math.floor(i/2)).toString().padStart(2,'0')}:${i%2===0?'00':'30'}`),
        saved: false,
        me: {},
        sessions: [],

        get sessionsLink() {
            return this.me.inviteToken ? '/trainee/' + this.me.inviteToken + '/sessions' : '#';
        },

        get sessionsTabLabel() {
            const profile = this.me.mentorProfile || 'sport';
            const labels = { sport: 'Тренування', studying: 'Уроки', psychology: 'Сесії', other: 'Сесії' };
            return labels[profile] || 'Сесії';
        },

        get mentorDative() {
            const profile = this.me.mentorProfile || 'sport';
            const labels = { sport: 'тренеру', studying: 'репетитору', psychology: 'терапевту', other: 'виконавцю' };
            return labels[profile] || 'тренеру';
        },

        get currentSlots() {
            return this.slotsByDate[this.selectedDate] || [];
        },

        async init() {
            const res = await fetch('/api/v1/me');
            if (!res.ok) { window.location.href = '/signin'; return; }
            const me = await res.json();
            if (!me.traineeId) { window.location.href = '/signin'; return; }
            this.traineeId = me.traineeId;
            this.me = me;

            const now = new Date();
            const start = snapToMonday(now);
            for (let i = 0; i < 14; i++) {
                const d = new Date(start);
                d.setDate(start.getDate() + i);
                const ds = localDateStr(d);
                this.days.push({ dateStr: ds, weekday: days[(d.getDay() + 6) % 7], dayNum: d.getDate() });
            }

            const urlParams = new URLSearchParams(window.location.search);
            const dateParam = urlParams.get('date');
            if (dateParam && this.days.some(d => d.dateStr === dateParam)) {
                this.selectedDate = dateParam;
            }

            await this.loadSessions();
            await this.loadAvailability();
            this.connectWebSocket();
        },

        connectWebSocket() {
            const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${wsProtocol}//${window.location.host}/api/v1/ws`;
            const ws = new WebSocket(wsUrl);
            
            ws.onmessage = (event) => {
                if (event.data === 'availability_changed') {
                    this.loadAvailability();
                }
            };
            
            ws.onclose = () => {
                setTimeout(() => this.connectWebSocket(), 3000);
            };
            
            ws.onerror = () => {
                ws.close();
            };
            
            this.ws = ws;
        },

        selectDate(date) {
            this.selectedDate = date;
            this.pickStart = null;
        },

        slotToMin(t) {
            const [h, m] = t.split(':').map(Number);
            return h * 60 + m;
        },

        pickSlot(t) {
            if (this.isBlockedBySession(t)) return;
            const slots = this.slotsByDate[this.selectedDate] || [];
            if (this.pickStart === null) {
                if (this.isBlockedBySession(t)) return;
                this.pickStart = t;
            } else {
                const sm = this.slotToMin(this.pickStart);
                const tm = this.slotToMin(t);
                if (tm <= sm) {
                    this.pickStart = t;
                    return;
                }
                slots.push({ startTime: this.pickStart, endTime: t });
                slots.sort((a, b) => this.slotToMin(a.startTime) - this.slotToMin(b.startTime));
                this.slotsByDate[this.selectedDate] = slots;
                this.pickStart = null;
            }
        },

        getSlotClass(t) {
            if (this.isBlockedBySession(t)) return 'bg-red-900/20 text-red-400/50 line-through cursor-not-allowed';
            const tm = this.slotToMin(t);
            const slots = this.slotsByDate[this.selectedDate] || [];
            for (const s of slots) {
                const ss = this.slotToMin(s.startTime);
                const se = this.slotToMin(s.endTime);
                if (tm >= ss && tm < se) return 'bg-blue-600/30 text-blue-300';
            }
            if (this.pickStart) {
                const pm = this.slotToMin(this.pickStart);
                if (tm === pm) return 'bg-yellow-500 text-black font-black ring-2 ring-yellow-300';
                if (tm > pm) return 'bg-blue-500/20 text-blue-300';
            }
            return 'bg-[#262626] text-gray-400 hover:bg-[#333]';
        },

        removeSlot(i) {
            const slots = this.slotsByDate[this.selectedDate] || [];
            slots.splice(i, 1);
            this.slotsByDate[this.selectedDate] = slots;
        },

        async loadSessions() {
            const res = await fetch('/api/v1/trainee/sessions');
            if (res.ok) this.sessions = await res.json();
        },

        isBlockedBySession(t) {
            const tm = this.slotToMin(t);
            const daySessions = this.sessions.filter(s => s.date === this.selectedDate && s.confirmationStatus !== 'REJECTED');
            for (const s of daySessions) {
                const ss = this.slotToMin(s.time);
                const se = this.slotToMin(s.endTime || s.time);
                if (tm >= ss && tm < se) return true;
            }
            return false;
        },

        async loadAvailability() {
            const start = this.days[0].dateStr;
            const end = this.days[this.days.length - 1].dateStr;
            const res = await fetch(`/api/v1/trainees/${this.traineeId}/availability?startDate=${start}&endDate=${end}`);
            if (res.ok) {
                const data = await res.json();
                const grouped = {};
                for (const item of data) {
                    if (!grouped[item.date]) grouped[item.date] = [];
                    grouped[item.date].push({ startTime: item.startTime, endTime: item.endTime });
                }
                for (const date in grouped) {
                    grouped[date].sort((a, b) => this.slotToMin(a.startTime) - this.slotToMin(b.startTime));
                }
                this.slotsByDate = grouped;
            }
        },

        async saveAvailability() {
            const allEntries = [];
            const datesToDelete = [];

            for (const date in this.slotsByDate) {
                const slots = this.slotsByDate[date];
                if (slots.length === 0) {
                    datesToDelete.push(date);
                } else {
                    for (const s of slots) {
                        allEntries.push({ date, startTime: s.startTime, endTime: s.endTime });
                    }
                }
            }

            try {
                if (allEntries.length > 0) {
                    const res = await fetch('/api/v1/trainee/availability', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(allEntries)
                    });
                    if (!res.ok) throw new Error('POST failed');
                }
                
                if (datesToDelete.length > 0) {
                    const datesParam = datesToDelete.join(',');
                    const res = await fetch(`/api/v1/trainee/availability?dates=${datesParam}`, { method: 'DELETE' });
                    if (!res.ok) throw new Error('DELETE failed');
                }
                
                this.saved = true;
                setTimeout(() => this.saved = false, 3000);
            } catch (e) {
                console.error('Save failed:', e);
            }
        }
    }
}
