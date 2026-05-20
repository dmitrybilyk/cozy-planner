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
function addDays(dateStr, n) {
    const d = new Date(dateStr + 'T12:00:00');
    d.setDate(d.getDate() + n);
    return localDateStr(d);
}
function traineeApp() {
    const WD = ['Пн','Вт','Ср','Чт','Пт','Сб','Нд'];
    const MON = ['Січ','Лют','Бер','Кві','Тра','Чер','Лип','Сер','Вер','Жов','Лис','Гру'];
    const today = localDateStr();
    return {
        tab: 'sessions',
        sessions: [],
        me: {},
        mentorSlots: [],
        mentorScheduleLoading: false,
        coachDayOffs: [],
        showModal: false,
        saving: false,
        _dirtyDates: new Set(),

        get hasUnsavedChanges() { return this._dirtyDates.size > 0; },

        saved: false,
        savedMessage: '',
        error: '',
        todayStr: today,
        showAllFuture: false,
        form: { title: '', description: '', date: today, time: '10:00', endTime: '11:00', locationId: '' },
        locations: [],
        confirmModal: { show: false, sessionId: null, title: '', message: '' },
        formPickStart: null,
        selectedModalSlots: [],
        sessionInfo: { show: false, id: null, title: '', description: '', date: '', time: '', endTime: '', mentorName: '', status: '', createdBy: '' },
        tabLabels: {},
        pageTitle: 'Мої сесії',
        notifications: [],
        unreadCount: 0,

        // coach schedule
        coachSelectedDate: today,
        coachCellsByDate: {},

        // availability state
        traineeId: null,
        selectedDate: today,
        days: [],
        cellsByDate: {},
        grid: [],

        getCurCells() {
            return this.cellsByDate[this.selectedDate] || [];
        },
        cellAt(mm) {
            return this.getCurCells().find(x => x.mm === mm);
        },

        get sortedSessions() {
            const now = new Date();
            const todayStr = localDateStr(now);
            const future = [];
            const past = [];
            for (const s of this.sessions) {
                if (s.date > todayStr || (s.date === todayStr && (!s.endTime || s.endTime > now.toTimeString().slice(0,5)))) {
                    future.push(s);
                } else {
                    past.push(s);
                }
            }
            future.sort((a, b) => (a.date + a.time).localeCompare(b.date + b.time));
            past.sort((a, b) => (b.date + (b.endTime || b.time)).localeCompare(a.date + (a.endTime || a.time)));
            if (!this.showAllFuture) {
                const cutoff = addDays(todayStr, 3);
                return [...future.filter(s => s.date <= cutoff), ...past];
            }
            return [...future, ...past];
        },

        get hasMoreSessions() {
            const todayStr = localDateStr(new Date());
            const cutoff = addDays(todayStr, 3);
            return this.sessions.some(s => s.date > cutoff);
        },

        hasSessionOnDate(dateStr) {
            return this.sessions.some(s => s.date === dateStr && s.confirmationStatus !== 'REJECTED');
        },

        get coachLocations() {
            const seen = {};
            for (const date in this.coachCellsByDate) {
                for (const cell of this.coachCellsByDate[date]) {
                    if (cell.locName && !seen[cell.locName]) {
                        seen[cell.locName] = { name: cell.locName, color: cell.locColor || '#3b82f6' };
                    }
                }
            }
            return Object.values(seen).sort((a, b) => a.name.localeCompare(b.name));
        },

        get sortedMentorDays() {
            const grouped = {};
            for (const slot of this.mentorSlots) {
                if (!grouped[slot.date]) grouped[slot.date] = [];
                grouped[slot.date].push(slot);
            }
            return Object.keys(grouped).sort().map(date => ({
                date,
                slots: grouped[date].sort((a, b) => a.startTime.localeCompare(b.startTime))
            }));
        },

        async init() {
            this.initAudioContext();
            let me = embeddedTrainee;
            if (!me || !me.traineeId) {
                const res = await fetch('/api/v1/me');
                if (!res.ok) { window.location.href = '/signin'; return; }
                me = await res.json();
            }
            if (!me.traineeId) { window.location.href = '/signin'; return; }
            this.traineeId = me.traineeId;
            this.me = me;
            this.locations = me.locations || [];

            const profile = me.mentorProfile || 'sport';
            const tabLabelSets = {
                sport: { sessions: 'Тренування', schedule: 'Доступність тренера', availability: 'Моя доступність', no_sessions: 'У тебе ще немає тренувань.', past_sessions: '— Минулі тренування —' },
                studying: { sessions: 'Уроки', schedule: 'Доступність репетитора', availability: 'Моя доступність', no_sessions: 'У тебе ще немає уроків.', past_sessions: '— Минулі уроки —' },
                psychology: { sessions: 'Сесії', schedule: 'Доступність терапевта', availability: 'Моя доступність', no_sessions: 'У тебе ще немає сесій.', past_sessions: '— Минулі сесії —' },
                other: { sessions: 'Сесії', schedule: 'Доступність виконавця', availability: 'Моя доступність', no_sessions: 'У тебе ще немає сесій.', past_sessions: '— Минулі сесії —' }
            };
            this.tabLabels = tabLabelSets[profile] || tabLabelSets.sport;
            this.pageTitle = 'Мої ' + this.tabLabels.sessions.toLowerCase();

            const now = new Date();
            const start = snapToMonday(now);
            for (let i = 0; i < 31; i++) {
                const d = new Date(start);
                d.setDate(start.getDate() + i);
                const ds = localDateStr(d);
                this.days.push({ dateStr: ds, weekday: WD[(d.getDay()+6)%7], dayNum: d.getDate(), month: MON[d.getMonth()], isToday: ds === today, isWeekend: d.getDay() === 0 || d.getDay() === 6 });
            }

            for (let h = 6; h < 22; h++) {
                const cells = [{mm:h*60},{mm:h*60+30}];
                this.grid.push({ lbl: `${String(h).padStart(2,'0')}:00`, cells });
            }

            await this.loadSessions();
            if (this.me.mentorShareToken) this.loadMentorAvailability();
            await this.loadAvailability();
            await this.loadNotifications();
            this.connectWebSocket();
            this.requestNotificationPermission();
            this.registerPush();
            setInterval(() => this.loadNotifications(), 30000);
            document.addEventListener('visibilitychange', () => {
                if (!document.hidden) { this.loadSessions(); this.loadAvailability(); if (this.me.mentorShareToken) this.loadMentorAvailability(); this.loadNotifications(); }
            });
        },

        connectWebSocket() {
            const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${wsProtocol}//${window.location.host}/api/v1/ws`;
            const ws = new WebSocket(wsUrl);
            ws.onmessage = (event) => {
                console.log('[trainee] ws event:', event.data);
                if (event.data.startsWith('{')) {
                    try {
                        const msg = JSON.parse(event.data);
                        if (msg.type === 'notification') {
                            this.showBrowserNotification(msg);
                            this.loadNotifications();
                        }
                    } catch (e) {}
                    return;
                }
                if (event.data === 'session_changed') {
                    if (this.saving) return;
                    this.loadSessions();
                    if (this.me.mentorShareToken) this.loadMentorAvailability();
                }
                if (event.data === 'availability_changed') {
                    if (this.saving) return;
                    this.loadAvailability();
                }
                if (event.data === 'trainee_changed') this.refreshMe();
                if (event.data === 'coach_availability_changed' || event.data === 'location_changed') {
                    console.log('[trainee] coach_avail/location_changed, mentorShareToken:', this.me.mentorShareToken);
                    if (this.me.mentorShareToken) this.loadMentorAvailability();
                }
            };
            ws.onopen = () => {
                this.loadNotifications();
            };
            ws.onclose = () => setTimeout(() => this.connectWebSocket(), 3000);
            ws.onerror = () => ws.close();
            this.ws = ws;
        },

        // ---- NOTIFICATIONS ----
        async loadNotifications() {
            const res = await fetch('/api/v1/notifications');
            if (res.ok) {
                this.notifications = await res.json();
                this.unreadCount = this.notifications.filter(n => !n.isRead).length;
            }
        },
        async markNotificationRead(id) {
            await fetch('/api/v1/notifications/' + id + '/read', { method: 'POST' });
            await this.loadNotifications();
        },
        async markAllNotificationsRead() {
            await fetch('/api/v1/notifications/read-all', { method: 'POST' });
            await this.loadNotifications();
        },
        showBrowserNotification(msg) {
            if (Notification.permission === 'granted') {
                const options = {
                    body: msg.message || '',
                    tag: 'cozy-notification',
                    icon: '/favicon.svg'
                };
                if (msg.actionType && msg.sessionId) {
                    options.tag = 'cozy-actionable-' + msg.sessionId;
                    options.data = { sessionId: msg.sessionId, actionType: msg.actionType };
                    options.actions = [
                        { action: 'confirm', title: '✅ Підтвердити' },
                        { action: 'reject', title: '❌ Відхилити' }
                    ];
                }
                if ('serviceWorker' in navigator && navigator.serviceWorker.controller) {
                    navigator.serviceWorker.ready.then(reg => {
                        reg.showNotification(msg.title || 'Сповіщення', options);
                    });
                } else {
                    new Notification(msg.title || 'Сповіщення', options);
                }
            }
            this.playNotificationSound();
        },
        playNotificationSound() {
            try {
                const ctx = this.audioCtx;
                if (ctx.state === 'suspended') ctx.resume();
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.frequency.value = 880;
                gain.gain.value = 0.3;
                osc.start();
                gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.25);
                osc.stop(ctx.currentTime + 0.25);
                setTimeout(() => {
                    const osc2 = ctx.createOscillator();
                    const gain2 = ctx.createGain();
                    osc2.connect(gain2);
                    gain2.connect(ctx.destination);
                    osc2.frequency.value = 1100;
                    gain2.gain.value = 0.2;
                    osc2.start();
                    gain2.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.5 + 0.2);
                    osc2.stop(ctx.currentTime + 0.5 + 0.2);
                }, 350);
            } catch (e) {}
            if (navigator.vibrate) navigator.vibrate([200, 100, 200]);
        },
        requestNotificationPermission() {
            if ('Notification' in window && Notification.permission === 'default') {
                Notification.requestPermission();
            }
        },
        async registerPush() {
            if (!('serviceWorker' in navigator) || !('PushManager' in window)) return;
            try {
                const vapidRes = await fetch('/api/v1/push/vapid-key');
                if (!vapidRes.ok) return;
                const {vapidKey} = await vapidRes.json();
                if (!vapidKey) return;
                const reg = await navigator.serviceWorker.register('/sw.js');
                const sub = await reg.pushManager.subscribe({
                    userVisibleOnly: true,
                    applicationServerKey: this.base64UrlToUint8Array(vapidKey)
                });
                const getKey = (name) => {
                    const k = sub.getKey(name);
                    if (!k) return null;
                    const bytes = String.fromCharCode(...new Uint8Array(k));
                    return btoa(bytes).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
                };
                await fetch('/api/v1/push/subscribe', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({
                        endpoint: sub.endpoint,
                        authKey: getKey('auth'),
                        p256dhKey: getKey('p256dh')
                    })
                });
            } catch (e) { console.log('push registration:', e); }
        },
        base64UrlToUint8Array(base64url) {
            const pad = base64url.length % 4 === 3 ? '=' : base64url.length % 4 === 2 ? '==' : '';
            const raw = atob(base64url + pad);
            const arr = new Uint8Array(raw.length);
            for (let i = 0; i < raw.length; i++) arr[i] = raw.charCodeAt(i);
            return arr;
        },
        initAudioContext() {
            try {
                this.audioCtx = new (window.AudioContext || window.webkitAudioContext)();
            } catch (e) {}
        },
        async loadSessions() {
            const res = await fetch('/api/v1/trainee/sessions');
            if (res.ok) { this.sessions = await res.json(); }
        },

        openCreateModal() {
            const traineeName = this.me.name || '';
            this.form = { title: traineeName ? 'Сесія — ' + traineeName : 'Сесія', description: '', date: today, time: '', endTime: '', locationId: '' };
            this.formPickStart = null;
            this.selectedModalSlots = [];
            this.showModal = true;
        },

        async createSession() {
            this.error = '';
            const res = await fetch('/api/v1/trainee/sessions', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ title: this.form.title, description: this.form.description, date: this.form.date, time: this.form.time, endTime: this.form.endTime, locationId: this.form.locationId })
            });
            if (res.ok) {
                this.showModal = false;
                this.saved = true;
                this.savedMessage = 'Сесію створено! ' + (this.me.mentorName || 'Тренер') + ' отримає сповіщення.';
                setTimeout(() => this.saved = false, 4000);
                await this.loadSessions();
            } else { this.error = 'Помилка при створенні сесії'; }
        },

        handleTraineeConfirmAction(session) {
            if (session.createdBy === 'COACH' && session.confirmationStatus === 'PENDING') {
                this.confirmSession(session.id);
            }
        },

        async confirmSession(id) {
            this.error = '';
            const res = await fetch(`/api/v1/trainee/sessions/${id}/confirm`, { method: 'POST' });
            if (res.ok) { this.saved = true; this.savedMessage = 'Сесію підтверджено!'; setTimeout(() => this.saved = false, 3000); await this.loadSessions(); }
            else { this.error = 'Помилка підтвердження'; }
        },

        async rejectSession(id) {
            this.error = '';
            const res = await fetch(`/api/v1/trainee/sessions/${id}/reject`, { method: 'POST' });
            if (res.ok) { this.saved = true; this.savedMessage = 'Сесію відхилено'; setTimeout(() => this.saved = false, 3000); await this.loadSessions(); }
            else { this.error = 'Помилка'; }
        },

        exportToGCal(session) {
            const d = session.date.replace(/-/g, '');
            const st = session.time.replace(/:/g, '') + '00';
            const et = (session.endTime || session.time).replace(/:/g, '') + '00';
            const text = encodeURIComponent(session.title || '');
            const dates = d + 'T' + st + '/' + d + 'T' + et;
            const details = encodeURIComponent(session.description || '');
            const url = 'https://calendar.google.com/calendar/render?action=TEMPLATE&text=' + text + '&dates=' + dates + '&details=' + details;
            window.open(url, '_blank');
        },

        openRequestConfirm(session) {
            this.confirmModal = { show: true, sessionId: session.id, title: 'Запитати підтвердження?', message: (this.me.mentorName || 'Тренер') + ' отримає сповіщення про необхідність підтвердити сесію.' };
        },

        closeRequestConfirm() {
            this.confirmModal = { show: false, sessionId: null, title: '', message: '' };
        },

        async refreshMe() {
            const res = await fetch('/api/v1/me');
            if (res.ok) {
                const me = await res.json();
                this.me = me;
            }
        },

        async doRequestConfirm() {
            const id = this.confirmModal.sessionId;
            this.closeRequestConfirm();
            if (!id) return;
            this.error = '';
            const res = await fetch(`/api/v1/trainee/sessions/${id}/request-coach-confirmation`, { method: 'POST' });
            if (res.ok) { this.saved = true; this.savedMessage = 'Запит на підтвердження надіслано ' + (this.me.mentorName || 'тренеру') + '!'; setTimeout(() => this.saved = false, 4000); await this.loadSessions(); }
            else { this.error = 'Помилка при надсиланні запиту'; }
        },

        getCardClass(session) {
            if (session.confirmationStatus === 'CONFIRMED') return 'bg-green-900/20 border-green-500/50';
            if (session.confirmationStatus === 'REJECTED') return 'bg-red-900/20 border-red-500/30 opacity-60';
            if (session.confirmationStatus === 'PENDING') return 'bg-yellow-900/10 border-yellow-500/30';
            return 'bg-[#1c1c1c] border-[#333]';
        },

        getStatusClass(status) {
            if (status === 'CONFIRMED') return 'bg-green-900/30 text-green-400';
            if (status === 'REJECTED') return 'bg-red-900/30 text-red-400';
            if (status === 'PENDING') return 'bg-yellow-900/30 text-yellow-400';
            return 'bg-gray-900/30 text-gray-400';
        },

        getStatusLabel(status) {
            if (status === 'CONFIRMED') return '✅ Підтверджено';
            if (status === 'REJECTED') return '❌ Відхилено';
            if (status === 'PENDING') return '⏳ Очікує';
            return '—';
        },

        getSessionIcons(session) {
            const count = (session.traineeIds || []).length;
            return Array(count).fill('😊');
        },

        // ---- COACH SCHEDULE ----
        locColors: ['#3b82f6', '#22c55e', '#f59e0b', '#ec4899', '#8b5cf6', '#06b6d4', '#f97316', '#84cc16'],

        getMentorLocationColor(locId) {
            if (locId == null) return null;
            const loc = (this.me.locations || []).find(l => Number(l.id) === Number(locId));
            return loc?.color || null;
        },

        locationNameColor(name) {
            let hash = 0;
            for (let i = 0; i < name.length; i++) {
                hash = name.charCodeAt(i) + ((hash << 5) - hash);
            }
            return this.locColors[Math.abs(hash) % this.locColors.length];
        },

        async loadMentorAvailability() {
            this.mentorScheduleLoading = true;
            const start = localDateStr();
            const endDate = new Date();
            endDate.setDate(endDate.getDate() + 13);
            const end = localDateStr(endDate);
            console.log('[trainee] loadMentorAvailability', start, end, 'token:', this.me.mentorShareToken);
            try {
                const res = await fetch(`/api/v1/shared/${this.me.mentorShareToken}?startDate=${start}&endDate=${end}`);
                console.log('[trainee] shared fetch status:', res.status);
                    if (res.ok) {
                        const data = await res.json();
                        console.log('[trainee] shared data slots count:', (data.slots || []).length);
                        this.mentorSlots = data.slots || [];
                        this.coachDayOffs = data.dayOffDates || [];
                        const g = {};
                        for (const item of this.mentorSlots) {
                        if (!g[item.date]) g[item.date] = [];
                        const [sh, sm] = item.startTime.split(':').map(Number);
                        const [eh, em] = item.endTime.split(':').map(Number);
                        let color = item.locationColor || this.getMentorLocationColor(item.locationId);
                        if (!color && item.locationName) color = this.locationNameColor(item.locationName);
                        if (!color) color = '#3b82f6';
                        let t = sh * 60 + sm, end = eh * 60 + em;
                        while (t < end) {
                            g[item.date].push({ mm: t, locId: item.locationId, locColor: color, locName: item.locationName });
                            t += 30;
                        }
                    }
                    this.coachCellsByDate = g;
                    console.log('[trainee] coachCellsByDate dates:', Object.keys(this.coachCellsByDate));
                } else {
                    console.error('[trainee] shared fetch not ok', await res.text());
                }
            } catch(e) { console.error('[trainee] loadMentorAvailability error', e); }
            this.mentorScheduleLoading = false;
            console.log('[trainee] coachCellsByDate loaded', Object.keys(this.coachCellsByDate).length, 'dates');
        },

        showCoachSchedule() {
            this.tab = 'schedule';
            if (this.me.mentorShareToken) this.loadMentorAvailability();
        },

        slotSlides(startTime, endTime) {
            const [sh, sm] = startTime.split(':').map(Number);
            const [eh, em] = endTime.split(':').map(Number);
            const slots = [];
            for (let t = sh * 60 + sm; t < eh * 60 + em; t += 30) slots.push(t);
            return slots;
        },

        bookSlot(slot) {
            this.form = { title: 'Сесія', description: '', date: slot.date, time: slot.startTime, endTime: slot.endTime, locationId: slot.locationId || '' };
            this.selectedModalSlots = this.slotSlides(slot.startTime, slot.endTime);
            this.tab = 'sessions';
            this.showModal = true;
        },

        traineeSessionOnDate(dateStr, mm) {
            return this.sessions.some(s => {
                if (s.date !== dateStr) return false;
                if (s.confirmationStatus === 'REJECTED') return false;
                const [sh, sm] = s.time.split(':').map(Number);
                const [eh, em] = (s.endTime || s.time).split(':').map(Number);
                return mm >= sh * 60 + sm && mm < eh * 60 + em;
            });
        },

        bookSlotFromGrid(mm) {
            const c = this.coachCellAt(mm);
            if (!c) return;
            if (this.traineeSessionOnDate(this.coachSelectedDate, mm)) return;
            const slot = this.mentorSlots.find(s => {
                if (s.date !== this.coachSelectedDate) return false;
                const [sh, sm] = s.startTime.split(':').map(Number);
                const [eh, em] = s.endTime.split(':').map(Number);
                return mm >= sh * 60 + sm && mm < eh * 60 + em;
            });
            let startTime, endTime, locationId;
            if (slot) {
                startTime = slot.startTime;
                endTime = slot.endTime;
                locationId = slot.locationId || '';
            } else {
                const h = String(Math.floor(mm / 60)).padStart(2, '0');
                const m = String(mm % 60).padStart(2, '0');
                startTime = `${h}:${m}`;
                endTime = `${Number(m) + 30 < 60 ? `${h}:${String(Number(m) + 30).padStart(2, '0')}` : `${String(Number(h) + 1).padStart(2, '0')}:00`}`;
                locationId = c.locId || '';
            }
            this.form = { title: 'Сесія', description: '', date: this.coachSelectedDate, time: startTime, endTime, locationId };
            this.selectedModalSlots = this.slotSlides(startTime, endTime);
            this.tab = 'sessions';
            this.showModal = true;
        },

        selectDate(dateStr) {
            this.selectedDate = dateStr;
        },

        tap(mm) {
            if (this.isPastMinuteOnDate(this.selectedDate, mm)) return;
            const session = this.findSessionAt(mm);
            if (session) { this.tapSession(mm); return; }
            const arr = [...(this.cellsByDate[this.selectedDate] || [])];
            const idx = arr.findIndex(x => x.mm === mm);
            if (idx >= 0) { arr.splice(idx, 1); } else { arr.push({ mm }); }
            this.cellsByDate[this.selectedDate] = arr;
            this._dirtyDates.add(this.selectedDate);
        },

        tapSession(mm) {
            const session = this.findSessionAt(mm);
            if (!session) return;
            const location = session.location || {};
            const names = session.traineeNames || {};
            this.sessionInfo = {
                show: true,
                id: session.id,
                title: session.title,
                description: session.description,
                date: session.date,
                time: session.time,
                endTime: session.endTime,
                mentorName: session.mentorName,
                status: session.confirmationStatus,
                createdBy: session.createdBy,
                locationName: location.name || '',
                locationColor: location.color || '',
                attendees: Object.values(names)
            };
        },

        findSessionAt(mm) {
            return this.sessions.find(s => {
                if (s.date !== this.selectedDate) return false;
                if (s.confirmationStatus === 'REJECTED') return false;
                const [sh, sm] = s.time.split(':').map(Number);
                const [eh, em] = (s.endTime || s.time).split(':').map(Number);
                return mm >= sh * 60 + sm && mm < eh * 60 + em;
            });
        },

        cellStyle(mm) {
            if (this.isPastMinuteOnDate(this.selectedDate, mm)) return 'background:#1a1a1a; pointer-events:none';
            if (this.inSess(mm)) return 'background:#dc2626';
            const c = this.cellAt(mm);
            if (!c) return 'background:#2a2a2a';
            return 'background:#3b82f6';
        },

        inSess(mm) {
            return this.sessions.some(s => {
                if (s.date !== this.selectedDate) return false;
                if (s.confirmationStatus === 'REJECTED') return false;
                const [sh, sm] = s.time.split(':').map(Number);
                const [eh, em] = (s.endTime || s.time).split(':').map(Number);
                return mm >= sh * 60 + sm && mm < eh * 60 + em;
            });
        },

        title(mm) {
            return `${String(Math.floor(mm/60)).padStart(2,'0')}:${String(mm%60).padStart(2,'0')}`;
        },

        minStr(m) {
            return `${String(Math.floor(m/60)).padStart(2,'0')}:${String(m%60).padStart(2,'0')}`;
        },

        async loadAvailability() {
            if (!this.traineeId) return;
            const start = this.days[0].dateStr;
            const end = this.days[this.days.length - 1].dateStr;
            const res = await fetch(`/api/v1/trainees/${this.traineeId}/availability?startDate=${start}&endDate=${end}`);
            if (res.ok) {
                const data = await res.json();
                const g = {};
                for (const item of data) {
                    if (!g[item.date]) g[item.date] = [];
                    const [sh, sm] = item.startTime.split(':').map(Number);
                    const [eh, em] = item.endTime.split(':').map(Number);
                    let t = sh * 60 + sm, end = eh * 60 + em;
                    while (t < end) {
                        g[item.date].push({ mm: t });
                        t += 30;
                    }
                }
                this.cellsByDate = g;
                this._dirtyDates.clear();
            }
        },

        async saveAll() {
            if (this.saving) return;
            this.saving = true;
            try {
                const all = [];
                for (const date of this._dirtyDates) {
                    const arr = this.cellsByDate[date] || [];
                    if (!arr.length) continue;
                    const sorted = [...arr].sort((a,b) => a.mm - b.mm);
                    let cs = sorted[0].mm, ce = sorted[0].mm + 30;
                    for (let i = 1; i < sorted.length; i++) {
                        if (sorted[i].mm === ce) {
                            ce = sorted[i].mm + 30;
                        } else {
                            all.push({ date, startTime: this.minStr(cs), endTime: this.minStr(ce) });
                            cs = sorted[i].mm; ce = sorted[i].mm + 30;
                        }
                    }
                    all.push({ date, startTime: this.minStr(cs), endTime: this.minStr(ce) });
                }
                const del = [...this._dirtyDates].filter(d => !this.cellsByDate[d]?.length);
                if (all.length) {
                    const res = await fetch(`/api/v1/trainees/${this.traineeId}/availability`, { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(all) });
                    if (!res.ok) throw Error('POST fail');
                }
                if (del.length) {
                    const res = await fetch(`/api/v1/trainees/${this.traineeId}/availability?dates=${del.join(',')}`, { method:'DELETE' });
                    if (!res.ok) throw Error('DEL fail');
                }
                this._dirtyDates.clear();
                this.saved = true;
                this.savedMessage = 'Збережено!';
                setTimeout(() => this.saved = false, 3000);
            } catch(e) { console.error(e); }
            finally { this.saving = false; }
        },

        slotToMin(t) {
            if (!t) return 0;
            const [h, m] = t.split(':').map(Number);
            return h * 60 + m;
        },

        isCoachSlotOnModalDate(mm) {
            if (Object.keys(this.coachCellsByDate).length === 0) return true;
            const cells = this.coachCellsByDate[this.form.date];
            if (!cells) return true;
            return cells.some(c => c.mm === mm);
        },

        pickModalTime(mm) {
            if (this.isPastMinute(mm)) return;
            if (!this.isCoachSlotOnModalDate(mm)) return;
            const sel = this.selectedModalSlots;
            if (sel.length === 0) {
                this.selectedModalSlots = [mm];
            } else {
                const sorted = [...sel].sort((a, b) => a - b);
                const min = sorted[0], max = sorted[sorted.length - 1];
                if (mm === max + 30) {
                    this.selectedModalSlots = [...sorted, mm];
                } else if (mm === min - 30) {
                    this.selectedModalSlots = [mm, ...sorted];
                } else if (mm >= min && mm <= max) {
                    this.selectedModalSlots = [mm];
                } else {
                    this.selectedModalSlots = [mm];
                }
            }
            const sorted = [...this.selectedModalSlots].sort((a, b) => a - b);
            if (sorted.length) {
                const sh = String(Math.floor(sorted[0] / 60)).padStart(2, '0');
                const sm = String(sorted[0] % 60).padStart(2, '0');
                const last = sorted[sorted.length - 1] + 30;
                const eh = String(Math.floor(last / 60)).padStart(2, '0');
                const em = String(last % 60).padStart(2, '0');
                this.form.time = `${sh}:${sm}`;
                this.form.endTime = `${eh}:${em}`;
                const cells = this.coachCellsByDate[this.form.date] || [];
                const c = cells.find(x => x.mm === sorted[0]);
                if (c && c.locId != null) {
                    this.form.locationId = c.locId;
                }
            } else {
                this.form.time = '';
                this.form.endTime = '';
            }
        },

        modalCellStyle(mm) {
            if (this.isPastMinute(mm)) return 'background:#1a1a1a; pointer-events:none';
            if (this.isSlotBlockedByCoachSession(mm)) return 'background:#1a1a1a; pointer-events:none';
            if (!this.isCoachSlotOnModalDate(mm)) return 'background:#1a1a1a; pointer-events:none';
            if (this.selectedModalSlots.includes(mm)) return 'background:#3b82f6';
            return 'background:#2a2a2a';
        },

        isPastMinute(mm) {
            if (this.form.date !== today) return false;
            const now = new Date();
            const currentMin = now.getHours() * 60 + now.getMinutes();
            return mm < currentMin;
        },

        isPastSession(session) {
            const todayStr = localDateStr();
            if (session.date < todayStr) return true;
            if (session.date > todayStr) return false;
            const now = new Date();
            const currentMin = now.getHours() * 60 + now.getMinutes();
            const [eh, em] = (session.endTime || session.time || '0:00').split(':').map(Number);
            return eh * 60 + em <= currentMin;
        },

        isSlotBlockedByCoachSession(mm) {
            if (!this.form.date) return false;
            return this.sessions.some(s => {
                if (s.date !== this.form.date) return false;
                if (s.confirmationStatus === 'REJECTED') return false;
                const [sh, sm] = s.time.split(':').map(Number);
                const [eh, em] = (s.endTime || s.time).split(':').map(Number);
                return mm >= sh * 60 + sm && mm < eh * 60 + em;
            });
        },

        get coachCurCells() {
            return this.coachCellsByDate[this.coachSelectedDate] || [];
        },

        coachCellAt(mm) {
            return this.coachCurCells.find(x => x.mm === mm);
        },

        coachCellStyle(mm) {
            if (this.isPastMinuteOnDate(this.coachSelectedDate, mm)) return 'background:#1a1a1a; pointer-events:none';
            if (this.traineeSessionOnDate(this.coachSelectedDate, mm)) return 'background:#dc2626; pointer-events:none';
            const c = this.coachCellAt(mm);
            if (!c) return 'background:#2a2a2a';
            if (c.locColor) return `background:${c.locColor}`;
            return 'background:#3b82f6';
        },

        isPastMinuteOnDate(dateStr, mm) {
            if (dateStr !== today) return false;
            const now = new Date();
            const currentMin = now.getHours() * 60 + now.getMinutes();
            return mm < currentMin;
        }
    }
}
