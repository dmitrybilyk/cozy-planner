let _deferredInstall = null;
window.addEventListener('beforeinstallprompt', (e) => { e.preventDefault(); _deferredInstall = e; });

function localDateStr(d) {
    d = d || new Date();
    return `${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')}`;
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
        saved: false,
        savedMessage: '',
        error: '',
        todayStr: today,
        showAllFuture: false,
        form: { title: '', description: '', date: today, time: '', endTime: '', locationId: '' },
        _bookingSlot: null,
        _prevTab: null,
        selectedModalSlots: [],
        locations: [],
        confirmModal: { show: false, sessionId: null, title: '', message: '' },
        sessionInfo: { show: false, id: null, title: '', description: '', date: '', time: '', endTime: '', mentorName: '', status: '', createdBy: '' },
        compactView: true,
        expandedIds: [],
        tabLabels: {},
        pageTitle: 'Мої сесії',
        coachTimezone: 'Europe/Kiev',
        timezone: 'Europe/Kiev',
        nowTick: 0,
        nowTickInterval: null,
        deferredInstallPrompt: null,
        showIosInstall: false,
        isStandalone: window.matchMedia('(display-mode: standalone)').matches,
        notifications: [],
        unreadCount: 0,
        feedbackModal: { show: false, sessionId: null, sessionTitle: null, text: '', tags: [], rating: 0 },
        conversation: [],
        feedbackUnreadCount: 0,
        feedbackSending: false,
        TRAINEE_FEEDBACK_TAGS: ['Дякую!', 'Корисно', 'Хочу більше', 'Важко', 'Відмінний тренер', 'Є запитання'],

        get timeSlots15() {
            const ws = this.mentorWorkStart || '09:00';
            const we = this.mentorWorkEnd || '21:00';
            const step = this.mentorAvailStep || 30;
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

        // coach schedule
        coachSelectedDate: today,
        coachCellsByDate: {},
        coachBusyByDate: {},
        mentorWorkStart: '06:00',
        mentorWorkEnd: '21:00',

        // trainee availability state
        traineeId: null,
        days: [],
        availDate: today,
        availRanges: [],
        mentorAvailStep: 30,
        availFreeAllDay: false,
        availRangesByDate: {},
        availFreeAllDayByDate: {},
        availDirtyDates: new Set(),
        availSaving: false,
        availSaved: false,

        get availDirty() { return this.availDirtyDates.size > 0; },

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

        isPastSession(s) {
            const now = new Date();
            const todayStr = localDateStr(now);
            if (s.date > todayStr) return false;
            if (s.date < todayStr) return true;
            return !(s.endTime && s.endTime > now.toTimeString().slice(0,5));
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

        get coachSlotDates() {
            return this.sortedMentorDays.map(d => d.date);
        },

        get coachSlotsOnDate() {
            const day = this.sortedMentorDays.find(d => d.date === this.coachSelectedDate);
            return day ? day.slots : [];
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

        get grid() {
            const rows = [];
            for (let h = 6; h < 22; h++) {
                const lbl = `${String(h).padStart(2,'0')}:00`;
                const cells = [
                    { mm: h * 60 },
                    { mm: h * 60 + 30 }
                ];
                rows.push({ lbl, cells });
            }
            return rows;
        },

        get validModalStartSlots() {
            const step = this.mentorAvailStep || 30;
            const slot = this._bookingSlot;
            let startMm, endMm;
            if (slot) {
                const ss = this.convertTz(slot.startTime, slot.date, this.coachTimezone, this.timezone);
                const se = this.convertTz(slot.endTime, slot.date, this.coachTimezone, this.timezone);
                const [sh, sm] = ss.split(':').map(Number);
                const [eh, em] = se.split(':').map(Number);
                startMm = sh * 60 + sm;
                endMm = eh * 60 + em;
            } else {
                const ws = this.convertTz(this.mentorWorkStart || '06:00', this.form.date, this.coachTimezone, this.timezone);
                const we = this.convertTz(this.mentorWorkEnd || '21:00', this.form.date, this.coachTimezone, this.timezone);
                const [wh, wm] = ws.split(':').map(Number);
                const [eh, em] = we.split(':').map(Number);
                startMm = wh * 60 + wm;
                endMm = eh * 60 + em;
            }
            const slots = [];
            for (let m = startMm; m < endMm; m += 15) {
                const h = String(Math.floor(m / 60)).padStart(2, '0');
                const min = String(m % 60).padStart(2, '0');
                slots.push(`${h}:${min}`);
            }
            if (step === 60) return slots.filter(t => t.endsWith(':00'));
            if (step === 30) return slots.filter(t => t.endsWith(':00') || t.endsWith(':30'));
            return slots;
        },

        get validModalEndSlots() {
            if (!this.form.time) return [];
            const step = this.mentorAvailStep || 30;
            const [sh, sm] = this.form.time.split(':').map(Number);
            const startMm = sh * 60 + sm;
            const slot = this._bookingSlot;
            let endBoundary;
            if (slot) {
                const se = this.convertTz(slot.endTime, slot.date, this.coachTimezone, this.timezone);
                const [eh, em] = se.split(':').map(Number);
                endBoundary = eh * 60 + em;
            } else {
                const we = this.convertTz(this.mentorWorkEnd || '21:00', this.form.date, this.coachTimezone, this.timezone);
                const [eh, em] = we.split(':').map(Number);
                endBoundary = eh * 60 + em;
            }
            const slots = [];
            for (let m = startMm + 15; m <= endBoundary; m += 15) {
                const h = String(Math.floor(m / 60)).padStart(2, '0');
                const min = String(m % 60).padStart(2, '0');
                slots.push(`${h}:${min}`);
            }
            if (step === 60) return slots.filter(t => t.endsWith(':00'));
            if (step === 30) return slots.filter(t => t.endsWith(':00') || t.endsWith(':30'));
            return slots;
        },

        async init() {
            this.initAudioContext();
            if (_deferredInstall) this.deferredInstallPrompt = _deferredInstall;
            let me = embeddedTrainee;
            if (!me || !me.traineeId) {
                const res = await fetch('/api/v1/me');
                if (!res.ok) { window.location.href = '/signin'; return; }
                me = await res.json();
            }
            if (!me.traineeId) { window.location.href = '/signin'; return; }
            this.traineeId = me.traineeId;
            this.me = me;
            this.coachTimezone = me.mentorTimezone || 'Europe/Kiev';
            this.timezone = me.timezone || 'Europe/Kiev';
            this.locations = me.locations || [];
            this.mentorAvailStep = me.mentorAvailStep || 30;
            this.nowTickInterval = setInterval(() => { this.nowTick++; }, 30000);

            const profile = me.mentorProfile || 'sport';
            const tabLabelSets = {
                sport:      { sessions: 'Тренування', schedule: 'Доступність тренера',    availability: 'Моя доступність', no_sessions: 'У тебе ще немає тренувань.',  past_sessions: '— Минулі тренування —'  },
                studying:   { sessions: 'Заняття',    schedule: 'Доступність репетитора', availability: 'Моя доступність', no_sessions: 'У тебе ще немає занять.',     past_sessions: '— Минулі заняття —'     },
                psychology: { sessions: 'Зустрічі',   schedule: 'Доступність психолога',  availability: 'Моя доступність', no_sessions: 'У тебе ще немає зустрічей.', past_sessions: '— Минулі зустрічі —'   },
                other:      { sessions: 'Зустрічі',   schedule: 'Доступність виконавця',  availability: 'Моя доступність', no_sessions: 'У тебе ще немає зустрічей.', past_sessions: '— Минулі зустрічі —'   },
                massage:    { sessions: 'Сеанси',      schedule: 'Доступність масажиста',  availability: 'Моя доступність', no_sessions: 'У тебе ще немає сеансів.',   past_sessions: '— Минулі сеанси —'     },
                manicure:   { sessions: 'Сеанси',      schedule: 'Доступність майстра',    availability: 'Моя доступність', no_sessions: 'У тебе ще немає сеансів.',   past_sessions: '— Минулі сеанси —'     },
                medicine:   { sessions: 'Прийоми',     schedule: 'Доступність лікаря',     availability: 'Моя доступність', no_sessions: 'У тебе ще немає прийомів.', past_sessions: '— Минулі прийоми —'    }
            };
            this.tabLabels = tabLabelSets[profile] || tabLabelSets.sport;
            this.pageTitle = 'Мої ' + this.tabLabels.sessions.toLowerCase();

            const now = new Date();
            for (let i = 0; i < 14; i++) {
                const d = new Date(now);
                d.setDate(now.getDate() + i);
                const ds = localDateStr(d);
                this.days.push({ dateStr: ds, weekday: WD[(d.getDay()+6)%7], dayNum: d.getDate(), month: MON[d.getMonth()], isToday: ds === today, isWeekend: d.getDay() === 0 || d.getDay() === 6 });
            }

            await this.loadSessions();
            if (this.me.mentorShareToken) this.loadMentorAvailability();
            await this.loadAvailability();
            await this.loadNotifications();
            await this.loadFeedbackReceived();
            this.connectWebSocket();
            this.listenForSWMessages();
            this.requestNotificationPermission();
            this.registerPush();
            setInterval(() => this.loadNotifications(), 30000);
            document.addEventListener('visibilitychange', () => {
                if (!document.hidden) { this.loadSessions(); this.loadAvailability(); if (this.me.mentorShareToken) this.loadMentorAvailability(); this.loadNotifications(); }
            });
            this.$watch('showModal', (val) => {
                if (val) history.pushState({modal: true}, '');
                if (!val) this._bookingSlot = null;
            });
            window.addEventListener('popstate', () => {
                if (this.showModal) this.closeModal();
            });
            this.$watch('form.time', () => {
                const slots = this.validModalEndSlots;
                if (slots.length > 0) {
                    if (!slots.includes(this.form.endTime)) {
                        this.form.endTime = slots[0];
                    }
                } else {
                    this.form.endTime = '';
                }
            });
        },

        listenForSWMessages() {
            if ('serviceWorker' in navigator) {
                navigator.serviceWorker.addEventListener('message', (event) => {
                    if (event.data?.type === 'session_changed') {
                        this.loadSessions();
                        this.loadNotifications();
                    }
                    if (event.data.action) {
                        console.log('[sw] notification action result:', event.data.action, 'success:', event.data.success, 'status:', event.data.statusCode);
                        if (event.data.success === false) {
                            this.savedMessage = event.data.action === 'confirm' ? 'Помилка підтвердження' : 'Помилка відхилення';
                            this.saved = true;
                            setTimeout(() => this.saved = false, 5000);
                        }
                    }
                });
            }
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
                    if (this.availSaving) return;
                    this.loadAvailability();
                }
                if (event.data === 'trainee_changed') this.refreshMe();
                if (event.data === 'coach_availability_changed' || event.data === 'location_changed') {
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
                const body = (msg.message || '') + ' — натисніть, щоб відкрити додаток';
                const options = {
                    body: body,
                    tag: 'cozy-notification',
                    icon: '/favicon.svg'
                };
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
                let appKey;
                try { appKey = this.base64UrlToUint8Array(vapidKey); } catch (e) { console.log('push: invalid vapid key'); return; }
                const sub = await reg.pushManager.subscribe({
                    userVisibleOnly: true,
                    applicationServerKey: appKey
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
            try {
                const res = await fetch('/api/v1/trainee/sessions');
                if (res.ok) { this.sessions = await res.json(); }
            } catch(e) { console.error('[sessions] load error', e); }
        },

        openCreateModal() {
            const traineeName = this.me.name || '';
            this.form = { title: 'Тренування', description: '', date: today, time: '', endTime: '', locationId: '' };
            this._bookingSlot = null;
            this._prevTab = this.tab;
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
                this._prevTab = null;
                this.showModal = false;
                this.saved = true;
                this.savedMessage = 'Сесію створено! ' + (this.me.mentorName || 'Тренер') + ' отримає сповіщення.';
                setTimeout(() => this.saved = false, 4000);
                await this.loadSessions();
            } else { const err = await res.json().catch(() => ({reason: 'Помилка при створенні сесії'})); this.error = err.reason || 'Помилка при створенні сесії'; }
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
                if (me.traineeId) this.me = me;
            }
        },

        async doRequestConfirm() {
            const id = this.confirmModal.sessionId;
            this.closeRequestConfirm();
            if (!id) return;
            this.error = '';
            const res = await fetch(`/api/v1/trainee/sessions/${id}/request-coach-confirmation`, { method: 'POST' });
            if (res.ok) { this.saved = true; this.savedMessage = 'Запит на підтвердження надіслано ' + (this.me.mentorName || 'майстру') + '!'; setTimeout(() => this.saved = false, 4000); await this.loadSessions(); }
            else { this.error = 'Помилка при надсиланні запиту'; }
        },

        openFeedbackModal(sessionId, sessionTitle) {
            this.feedbackModal = { show: true, sessionId: sessionId || null, sessionTitle: sessionTitle || null, text: '', tags: [], rating: 0 };
        },

        closeFeedbackModal() {
            this.feedbackModal = { show: false, sessionId: null, sessionTitle: null, text: '', tags: [], rating: 0 };
        },

        toggleFeedbackTag(tag) {
            const idx = this.feedbackModal.tags.indexOf(tag);
            if (idx >= 0) this.feedbackModal.tags.splice(idx, 1);
            else this.feedbackModal.tags.push(tag);
        },

        async sendFeedback() {
            if (this.feedbackSending) return;
            this.feedbackSending = true;
            try {
                const body = {
                    fromTraineeId: this.traineeId,
                    toMentorId: null,
                    sessionId: this.feedbackModal.sessionId,
                    sessionTitle: this.feedbackModal.sessionTitle,
                    text: this.feedbackModal.text.trim() || null,
                    tags: this.feedbackModal.tags.length ? this.feedbackModal.tags.join(',') : null,
                    rating: this.feedbackModal.rating > 0 ? this.feedbackModal.rating : null
                };
                // Resolve mentor id from me data
                if (this.me.mentorId) body.toMentorId = this.me.mentorId;
                const res = await fetch('/api/v1/feedback', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body)
                });
                if (res.ok) {
                    this.closeFeedbackModal();
                    this.saved = true;
                    this.savedMessage = 'Відгук надіслано!';
                    setTimeout(() => this.saved = false, 3000);
                    await this.loadFeedbackReceived();
                } else {
                    this.error = 'Помилка при надсиланні відгуку';
                    setTimeout(() => this.error = '', 3000);
                }
            } catch(e) {
                this.error = 'Помилка при надсиланні відгуку';
                setTimeout(() => this.error = '', 3000);
            } finally {
                this.feedbackSending = false;
            }
        },

        async loadFeedbackReceived() {
            if (!this.traineeId || !this.me?.mentorId) return;
            try {
                const res = await fetch(`/api/v1/feedback/conversation?mentorId=${this.me.mentorId}&traineeId=${this.traineeId}`);
                if (res.ok) {
                    this.conversation = (await res.json()).sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
                    this.feedbackUnreadCount = this.conversation.filter(f => f.fromMentorId && !f.isRead).length;
                }
            } catch(e) {}
        },

        async markFeedbackRead(id) {
            await fetch(`/api/v1/feedback/${id}/read`, { method: 'POST' });
            await this.loadFeedbackReceived();
        },

        formatFeedbackDate(dateStr) {
            if (!dateStr) return '';
            const d = new Date(dateStr);
            const time = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
            return WD[(d.getDay() + 6) % 7] + ' ' + d.getDate() + ' ' + MON[d.getMonth()] + ', ' + time;
        },

        getCardClass(session) {
            if (this.isSessionNow(session)) return 'bg-[#1c1c1c] ring-4 ring-red-500/30 border-red-500/20 shadow-lg shadow-red-500/10';
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

        toggleCompactView() {
            this.compactView = !this.compactView;
        },

        isExpanded(sessionId) {
            return this.expandedIds.includes(sessionId);
        },

        toggleExpand(session) {
            const idx = this.expandedIds.indexOf(session.id);
            if (idx >= 0) {
                this.expandedIds.splice(idx, 1);
            } else {
                this.expandedIds.push(session.id);
            }
        },

        getTraineeConfirmStatus(traineeId, session) {
            const confirmed = (session.confirmedTraineeIds || '').split(',').map(Number).filter(Boolean);
            const rejected = (session.rejectedTraineeIds || '').split(',').map(Number).filter(Boolean);
            if (confirmed.includes(traineeId)) return 'confirmed';
            if (rejected.includes(traineeId)) return 'rejected';
            return 'none';
        },

        get nowMinutes() {
            this.nowTick;
            const now = new Date();
            return now.getHours() * 60 + now.getMinutes();
        },

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

        isSessionNow(session) {
            if (!session.time || !session.date) return false;
            this.nowTick;
            const nowUtc = Date.now() / 60000;
            const dayStart = this.toUtcMin('00:00', session.date, this.coachTimezone);
            if (nowUtc < dayStart || nowUtc >= dayStart + 1440) return false;
            const start = this.toUtcMin(session.time, session.date, this.coachTimezone);
            const end = session.endTime ? this.toUtcMin(session.endTime, session.date, this.coachTimezone) : start + 60;
            return nowUtc >= start && nowUtc < end;
        },

        getTraineeName(traineeId, session) {
            const names = session.traineeNames || {};
            return names[traineeId] || 'Unknown';
        },

        getTraineeNamesText(session) {
            return (session.traineeIds || []).map(id => this.getTraineeName(id, session)).join(', ');
        },

        formatDateCompact(dateStr) {
            const d = new Date(dateStr + 'T12:00:00');
            return WD[(d.getDay() + 6) % 7] + ' ' + d.getDate() + ' ' + MON[d.getMonth()];
        },

        async toggleTraineeConfirm(session) {
            const status = this.getTraineeConfirmStatus(this.traineeId, session);
            if (status === 'confirmed') {
                await fetch(`/api/v1/trainee/sessions/${session.id}/reject`, { method: 'POST' });
            } else {
                await fetch(`/api/v1/trainee/sessions/${session.id}/confirm`, { method: 'POST' });
            }
            await this.loadSessions();
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
                const res = await fetch(`/api/v1/shared/${this.me.mentorShareToken}?startDate=${start}&endDate=${end}&timezone=${encodeURIComponent(this.timezone)}`);
                console.log('[trainee] shared fetch status:', res.status);
                    if (res.ok) {
                        const data = await res.json();
                        console.log('[trainee] shared data slots count:', (data.slots || []).length);
                        this.mentorSlots = data.slots || [];
                        this.coachDayOffs = data.dayOffDates || [];
                        if (data.mentorTimezone) this.coachTimezone = data.mentorTimezone;
                        if (data.workStart) this.mentorWorkStart = data.workStart;
                        if (data.workEnd) this.mentorWorkEnd = data.workEnd;
                        const g = {};
                        for (const item of this.mentorSlots) {
                        if (!g[item.date]) g[item.date] = [];
                        const [sh, sm] = item.startTime.split(':').map(Number);
                        const [eh, em] = item.endTime.split(':').map(Number);
                        let color = item.locationColor || this.getMentorLocationColor(item.locationId);
                        if (!color && item.locationName) color = this.locationNameColor(item.locationName);
                        if (!color) color = '#3b82f6';
                        const step = 30;
                        let t = sh * 60 + sm, end = eh * 60 + em;
                        while (t < end) {
                            g[item.date].push({ mm: t, locId: item.locationId, locColor: color, locName: item.locationName });
                            t += step;
                        }
                    }
                    this.coachCellsByDate = g;
                    const busy = {};
                    for (const bs of data.busySlots || []) {
                        if (!busy[bs.date]) busy[bs.date] = [];
                        const [sh, sm] = bs.startTime.split(':').map(Number);
                        const [eh, em] = bs.endTime.split(':').map(Number);
                        const bstep = 30;
                        let t = sh * 60 + sm, end = eh * 60 + em;
                        while (t < end) {
                            busy[bs.date].push(t);
                            t += bstep;
                        }
                    }
                    this.coachBusyByDate = busy;
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

        bookSlot(slot) {
            this._bookingSlot = slot;
            const coachStart = slot.startTime;
            const coachEnd = slot.endTime;
            const start = this.convertTz(coachStart, slot.date, this.coachTimezone, this.timezone);
            const end = this.convertTz(coachEnd, slot.date, this.coachTimezone, this.timezone);
            const [sh, sm] = start.split(':').map(Number);
            const [eh, em] = end.split(':').map(Number);
            const step = this.mentorAvailStep || 30;
            const slotStartMm = sh * 60 + sm;
            const slotEndMm = eh * 60 + em;
            const startMm = Math.ceil(slotStartMm / step) * step;
            if (startMm >= slotEndMm) return;
            let endMm = startMm + step;
            if (endMm > slotEndMm) endMm = slotEndMm;
            const startH = String(Math.floor(startMm / 60)).padStart(2, '0');
            const startM = String(startMm % 60).padStart(2, '0');
            const endH = String(Math.floor(endMm / 60)).padStart(2, '0');
            const endM = String(endMm % 60).padStart(2, '0');
            this.form = { title: 'Тренування', description: '', date: slot.date, time: `${startH}:${startM}`, endTime: `${endH}:${endM}`, locationId: slot.locationId || '' };
            this._prevTab = this.tab;
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
            const busyMms = this.coachBusyByDate[this.coachSelectedDate] || [];
            if (busyMms.includes(mm)) return;
            const slot = this.mentorSlots.find(s => {
                if (s.date !== this.coachSelectedDate) return false;
                const [sh, sm] = s.startTime.split(':').map(Number);
                const [eh, em] = s.endTime.split(':').map(Number);
                return mm >= sh * 60 + sm && mm < eh * 60 + em;
            });
            if (!slot) return;
            this._bookingSlot = slot;
            const [sh, sm] = slot.startTime.split(':').map(Number);
            const [eh, em] = slot.endTime.split(':').map(Number);
            const step = this.mentorAvailStep || 30;
            const slotStartMm = sh * 60 + sm;
            const slotEndMm = eh * 60 + em;
            const startMm = Math.ceil(slotStartMm / step) * step;
            if (startMm >= slotEndMm) return;
            const startH = String(Math.floor(startMm / 60)).padStart(2, '0');
            const startM = String(startMm % 60).padStart(2, '0');
            const startTime = `${startH}:${startM}`;
            let endMm = startMm + step;
            if (endMm > slotEndMm) endMm = slotEndMm;
            const endH = String(Math.floor(endMm / 60)).padStart(2, '0');
            const endM = String(endMm % 60).padStart(2, '0');
            const endTime = `${endH}:${endM}`;
            const locationId = c.locId || slot.locationId || '';
            this.form = { title: 'Тренування', description: '', date: this.coachSelectedDate, time: startTime, endTime, locationId };
            this._prevTab = this.tab;
            this.tab = 'sessions';
            this.showModal = true;
        },

        closeModal() {
            if (this._prevTab) this.tab = this._prevTab;
            this._prevTab = null;
            this.showModal = false;
        },

        slotSlides(startTime, endTime) {
            const [sh, sm] = startTime.split(':').map(Number);
            const [eh, em] = endTime.split(':').map(Number);
            const step = 30;
            const slots = [];
            for (let t = sh * 60 + sm; t < eh * 60 + em; t += step) slots.push(t);
            return slots;
        },

        pickModalTime(mm) {
            if (this.isPastMinute(mm)) return;
            if (!this.isCoachSlotOnModalDate(mm)) return;
            const step = 30;
            const sel = this.selectedModalSlots;
            if (sel.length === 0) {
                this.selectedModalSlots = [mm];
            } else {
                const sorted = [...sel].sort((a, b) => a - b);
                const min = sorted[0], max = sorted[sorted.length - 1];
                if (mm === max + step) {
                    this.selectedModalSlots = [...sorted, mm];
                } else if (mm === min - step) {
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
                const last = sorted[sorted.length - 1] + step;
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

        isCoachSlotOnModalDate(mm) {
            if (Object.keys(this.coachCellsByDate).length === 0) return true;
            const cells = this.coachCellsByDate[this.form.date];
            if (!cells) return true;
            return cells.some(c => c.mm === mm);
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

        availHasRanges(ds) {
            const fr = this.availFreeAllDayByDate[ds];
            if (fr === true) return true;
            const rr = this.availRangesByDate[ds];
            return rr && rr.length > 0;
        },

        availSel(d) {
            this.availRangesByDate[this.availDate] = [...this.availRanges];
            this.availFreeAllDayByDate[this.availDate] = this.availFreeAllDay;
            this.availDate = d;
            const cached = this.availRangesByDate[d];
            this.availRanges = cached ? [...cached] : [];
            if (this.availRangesByDate[d]) {
                for (const r of this.availRanges) r._new = false;
            }
            this.availFreeAllDay = this.availFreeAllDayByDate[d] !== undefined ? this.availFreeAllDayByDate[d] : false;
            this.availSortRanges();
            this.availSaved = false;
        },

        availAddRange() {
            const defStart = this.mentorWorkStart || '09:00';
            const defEnd = this.mentorWorkEnd || '18:00';
            this.availRanges.forEach(r => r._new = false);
            this.availRanges.unshift({ startTime: defStart, endTime: defEnd, _new: true });
            this.availSortRanges();
            this.availMarkDirty();
        },

        availRemoveRange(i) {
            this.availRanges.splice(i, 1);
            this.availMarkDirty();
        },

        onTraineeAvailStartChange(i) {
            this.availMarkDirty();
            const r = this.availRanges[i];
            if (!r || !r.startTime) return;
            if (!r.endTime || r.endTime <= r.startTime) {
                const next = this.timeSlots15.find(t => t > r.startTime);
                r.endTime = next || '';
            }
            this.availSortRanges();
        },

        slotToMin(t) {
            const [h, m] = t.split(':').map(Number);
            return h * 60 + m;
        },

        startSlots(excludeIndex) {
            const we = this.mentorWorkEnd || '21:00';
            const curRange = this.availRanges[excludeIndex];
            return this.timeSlots15.filter(t => {
                if (t >= we) return false;
                const tm = this.slotToMin(t);
                if (curRange && curRange.startTime && t === curRange.startTime) return true;
                for (let i = 0; i < this.availRanges.length; i++) {
                    if (i === excludeIndex) continue;
                    const r = this.availRanges[i];
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
            const curRange = this.availRanges[excludeIndex];
            return this.timeOptionsAfter(startTime).filter(t => {
                const tm = this.slotToMin(t);
                if (curRange && curRange.endTime && t === curRange.endTime) return true;
                for (let i = 0; i < this.availRanges.length; i++) {
                    if (i === excludeIndex) continue;
                    const r = this.availRanges[i];
                    if (!r.startTime || !r.endTime) continue;
                    const rs = this.slotToMin(r.startTime);
                    const re = this.slotToMin(r.endTime);
                    if (sm < re && tm > rs) return false;
                }
                return true;
            });
        },

        availSortRanges() {
            this.availRanges = [...this.availRanges].sort((a, b) => {
                if (!a.startTime) return -1;
                if (!b.startTime) return 1;
                return a.startTime.localeCompare(b.startTime);
            });
        },

        availMarkDirty() {
            const s = new Set(this.availDirtyDates);
            s.add(this.availDate);
            this.availDirtyDates = s;
            this.availSaved = false;
        },

        onAvailFreeAllDayToggle() {
            if (this.availFreeAllDay) {
                this.availRanges = [];
            } else {
                if (this.availRanges.length === 0) {
                    const defStart = this.mentorWorkStart || '09:00';
                    const defEnd = this.mentorWorkEnd || '18:00';
                    this.availRanges = [{ startTime: defStart, endTime: defEnd, locationId: null }];
                }
            }
            this.availMarkDirty();
        },

        getLocName(id) { return id ? (this.locations.find(l => Number(l.id) === Number(id))?.name || '') : ''; },
        getLocColor(id) { return id ? (this.locations.find(l => Number(l.id) === Number(id))?.color || null) : null; },

        async loadAvailability() {
            if (!this.traineeId) return;
            if (this.availDirtyDates.size > 0) return;
            const start = this.days[0].dateStr;
            const end = this.days[this.days.length - 1].dateStr;
            const url = `/api/v1/availability/ranges?userId=${this.traineeId}&userType=TRAINEE&startDate=${start}&endDate=${end}`;
            const res = await fetch(url);
            if (this.availDirtyDates.size > 0) {
                return;
            }
            const rangesByDate = {};
            const freeAllDayByDate = {};
            for (const d of this.days) {
                rangesByDate[d.dateStr] = [];
                freeAllDayByDate[d.dateStr] = this.availFreeAllDayByDate?.[d.dateStr] ?? false;
            }
            if (res.ok) {
                let items;
                try { items = await res.json(); } catch (e) { items = []; }
                for (const item of items) {
                    if (item.freeAllDay) {
                        freeAllDayByDate[item.date] = true;
                        if (!rangesByDate[item.date]) rangesByDate[item.date] = [];
                        continue;
                    }
                    if (!rangesByDate[item.date]) rangesByDate[item.date] = [];
                    rangesByDate[item.date].push({
                        startTime: item.startTime || '',
                        endTime: item.endTime || '',
                        locationId: item.locationId
                    });
                    freeAllDayByDate[item.date] = false;
                }
            }
            this.availRangesByDate = rangesByDate;
            this.availFreeAllDayByDate = freeAllDayByDate;
            this.availRanges = [...(rangesByDate[this.availDate] || [])];
            this.availFreeAllDay = freeAllDayByDate[this.availDate] !== undefined ? freeAllDayByDate[this.availDate] : false;
            this.availDirtyDates.clear();
        },

        availMergeRanges(ranges) {
            if (!ranges || ranges.length < 2) return ranges || [];
            const sorted = [...ranges].sort((a, b) => {
                if (a.startTime !== b.startTime) return a.startTime < b.startTime ? -1 : 1;
                return a.endTime < b.endTime ? -1 : 1;
            });
            const merged = [];
            for (const r of sorted) {
                const prev = merged[merged.length - 1];
                const sameLoc = prev && (prev.locationId === r.locationId || (!prev.locationId && !r.locationId));
                if (prev && sameLoc && r.startTime <= prev.endTime) {
                    if (r.endTime > prev.endTime) prev.endTime = r.endTime;
                } else {
                    merged.push({ ...r });
                }
            }
            return merged;
        },

        async availSaveAll() {
            if (this.availSaving) return;
            this.availRanges = this.availMergeRanges(this.availRanges);
            this.availSaving = true;
            this.availRangesByDate[this.availDate] = [...this.availRanges];
            this.availFreeAllDayByDate[this.availDate] = this.availFreeAllDay;
            try {
                let allOk = true;
                for (const date of this.availDirtyDates) {
                    const isFreeAllDay = this.availFreeAllDayByDate[date] !== undefined ? this.availFreeAllDayByDate[date] : false;
                    if (isFreeAllDay) {
                        const res = await fetch('/api/v1/availability/ranges', {
                            method: 'PUT',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                                userId: this.traineeId,
                                userType: 'TRAINEE',
                                date,
                                ranges: [],
                                freeAllDay: true
                            })
                        });
                        if (!res.ok) allOk = false;
                        continue;
                    }
                    const ranges = this.availRangesByDate[date] || [];
                    const res = await fetch('/api/v1/availability/ranges', {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            userId: this.traineeId,
                            userType: 'TRAINEE',
                            date,
                            ranges: ranges.map(r => ({
                                startTime: r.startTime,
                                endTime: r.endTime,
                                locationId: null
                            }))
                        })
                    });
                    if (!res.ok) allOk = false;
                }
                if (allOk) {
                    this.availDirtyDates.clear();
                    this.availSaved = true;
                    setTimeout(() => this.availSaved = false, 3000);
                } else {
                    this.availSaved = true;
                    setTimeout(() => this.availSaved = false, 3000);
                }
            } catch(e) { this.availSaved = true; setTimeout(() => this.availSaved = false, 3000); }
            finally { this.availSaving = false; this.loadAvailability(); }
        },

        timeOptionsAfter(fromTime) {
            if (!fromTime) return this.timeSlots15;
            return this.timeSlots15.filter(t => t > fromTime);
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
            const busyMms = this.coachBusyByDate[this.coachSelectedDate] || [];
            if (busyMms.includes(mm)) return 'background:#dc2626; pointer-events:none';
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
        },

        async installPwa() {
            const isIos = /iphone|ipad|ipod/i.test(navigator.userAgent) && !window.MSStream;
            if (isIos) { this.showIosInstall = true; return; }
            const prompt = this.deferredInstallPrompt || _deferredInstall;
            if (prompt) {
                prompt.prompt();
                const result = await prompt.userChoice;
                this.deferredInstallPrompt = null;
                _deferredInstall = null;
            }
        },

        async toggleSessionReminder() {
            const newVal = !this.me.sessionReminderEnabled;
            try {
                const res = await fetch(`/api/v1/trainees/${this.traineeId}/session-reminder`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ enabled: newVal })
                });
                if (res.ok) this.me = { ...this.me, sessionReminderEnabled: newVal };
            } catch (e) { /* ignore */ }
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
        }
    }
}
