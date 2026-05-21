function localDateStr(d) {
    d = d || new Date();
    return `${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')}`;
}
function addDays(dateStr, n) {
    const d = new Date(dateStr + 'T12:00:00');
    d.setDate(d.getDate() + n);
    return localDateStr(d);
}
function calendarApp() {
    const _today = localDateStr();
    return {
        currentView: 'feed', showModal: false, showManageTrainees: false, showManageLocations: false, showAvailabilityOverview: false, showProfile: false,
        editingSessionId: null, editingTraineeId: null, editingLocationId: null, draggedIndex: null, showCreateForm: false,
        selectedDate: _today, todayStr: _today,
        days: [], sessions: [], trainees: [], locations: [], mentorId: null, mentor: { name: '' }, user: {}, labels: {}, mentorProfile: 'sport',
        hours: Array.from({length: 24}, (_, i) => i.toString().padStart(2, '0')),
        halfHours: Array.from({length: 48}, (_, i) => `${Math.floor(i/2).toString().padStart(2,'0')}:${i%2===0?'00':'30'}`),
        daySlots: Array.from({length: 29}, (_, i) => `${(7+Math.floor(i/2)).toString().padStart(2,'0')}:${i%2===0?'00':'30'}`),
        coachGrid: [],
        coachAvailByDate: {},
        selectedTraineeFilters: [], traineeSearch: '',
        sessionForm: { title: '', description: '', date: '', startTime: null, endTime: null, traineeIds: [], locationId: null },
        selectedCoachSlots: [],
        traineeForm: { name: '', description: '', photoBase64: null },
        locationForm: { name: '', description: '', color: '#3b82f6' },
        confirmData: { show: false, title: '', message: '', confirmText: 'Видалити', onConfirm: () => {} },
        notifyModal: { show: false, traineeId: null, traineeName: '', customMessage: '', dayType: 'tomorrow', targetDate: '' },
        mentorTg: { enabled: false, connected: false, username: '', connectLink: '', copied: false },
        inviteUrls: {},
        traineeLinks: {},
        notifyingTrainees: {},
        notifyErrors: {},
        notifySuccess: {},
        _nowTimer: null,
        nowTick: 0,
        loading: false,
        loadingMore: false,
        loadedDates: {},
        sessionCounts: {},
        availabilityMap: {},
        touchDrag: null,
        touchJustDragged: false,
        _touchDragCleanup: null,
        showNotifications: false,
        workStart: '09:00',
        workEnd: '21:00',
        photoUrl: null,
        notifications: [],
        unreadCount: 0,
        dayOffs: [],

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
        async init() {
            console.log('init() starting...');
            this.initAudioContext();
            try {
                const me = await this.fetchMe();
                console.log('fetchMe result:', me, 'mentorId:', this.mentorId);
                if (!me) return;
                
                this.generateDays();
                console.log('generateDays done, days count:', this.days.length);
                
                await this.fetchLocations();
                console.log('fetchLocations done, locations:', this.locations.length);
                
                await this.fetchSessionCounts();
                console.log('fetchSessionCounts done');
                
                await this.fetchTrainees();
                console.log('fetchTrainees done, trainees:', this.trainees.length);
                
                await this.fetchData();
                console.log('fetchData done, sessions:', this.sessions.length);
                
                await this.fetchAvailability();
                console.log('fetchAvailability done');

                await this.loadDayOffs();
                console.log('loadDayOffs done');
                
                await this.fetchMentorTelegramStatus();
                console.log('fetchMentorTelegramStatus done');

                await this.loadNotifications();
                this.requestNotificationPermission();
                this.connectWebSocket();
                this.registerPush();
                setInterval(() => this.loadNotifications(), 30000);
                this.buildCoachGrid();
                this.$watch('sessionForm.date', (value) => {
                    if (value) this.buildCoachGrid(value);
                });
                this.initTouchDrag();
                setTimeout(() => this.scrollToToday(), 200);
                this._nowTimer = setInterval(() => { this.nowTick++; }, 30000);
                console.log('init() complete!');
            } catch (e) {
                console.error('init() error:', e);
            }
        },

        connectWebSocket() {
            const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${wsProtocol}//${window.location.host}/api/v1/ws`;
            const ws = new WebSocket(wsUrl);
            
            ws.onmessage = (event) => {
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
                switch(event.data) {
                    case 'session_changed': this.fetchSessionCounts(); this.fetchData(); break;
                    case 'trainee_changed': this.fetchTrainees(); this.fetchSessionCounts(); this.fetchData(); break;
                    case 'location_changed': this.fetchLocations(); this.fetchSessionCounts(); this.fetchData(); break;
                    case 'availability_changed': this.fetchAvailability(); break;
                    case 'coach_availability_changed': this.loadDayOffs(); this.fetchAvailability(); break;
                    case 'mentor_changed': this.fetchMentorTelegramStatus(); this.fetchTrainees(); break;
                }
            };
            
            ws.onopen = () => {
                this.loadNotifications();
            };
            ws.onclose = () => {
                setTimeout(() => this.connectWebSocket(), 3000);
            };
            ws.onerror = () => {
                ws.close();
            };
            this.ws = ws;
        },

        handleCardClick(session, index) {
            if (this.touchJustDragged) return;
            if (session.date < this.todayStr) return;
            this.editSession(session);
        },

        copySession(w) {
            if (w.date < this.todayStr) return;
            this.editingSessionId = null;
            this.originalSessionData = null;
            let st = w.time, et = w.endTime || this.nextSlot(w.time);
            let copyDate = w.date;
            if (this.isDayOff(copyDate)) {
                const tomorrow = new Date();
                tomorrow.setDate(tomorrow.getDate() + 1);
                copyDate = tomorrow.toISOString().slice(0, 10);
                let tries = 0;
                while (this.isDayOff(copyDate) && tries < 14) {
                    tomorrow.setDate(tomorrow.getDate() + 1);
                    copyDate = tomorrow.toISOString().slice(0, 10);
                    tries++;
                }
            }
            if (w.date === this.todayStr && new Date(`${w.date}T${st}`) <= new Date()) {
                const dur = this.slotToMin(et) - this.slotToMin(st);
                st = this.getNearestSlot();
                const em = this.slotToMin(st) + Math.max(dur, 30);
                const eh = Math.floor(em / 60) % 24;
                et = `${eh.toString().padStart(2, '0')}:${em % 60 === 0 ? '00' : '30'}`;
            }
            this.sessionForm = {
                title: w.title,
                description: w.description || '',
                date: copyDate,
                startTime: st,
                endTime: et,
                traineeIds: [...(w.traineeIds || [])],
                locationId: w.locationId || null
            };
            this.showModal = true;
        },

        exportToGCal(session) {
            const d = session.date.replace(/-/g, '');
            const st = session.time.replace(/:/g, '') + '00';
            const et = (session.endTime || session.time).replace(/:/g, '') + '00';
            const text = encodeURIComponent(session.title || '');
            const dates = d + 'T' + st + '/' + d + 'T' + et;
            const loc = encodeURIComponent(this.getLocationName(session.locationId) || '');
            const names = (session.traineeIds || []).map(id => this.getTraineeName(id)).filter(Boolean).join(', ');
            const details = encodeURIComponent((session.description || '') + (names ? '\\n' + names : ''));
            const url = 'https://calendar.google.com/calendar/render?action=TEMPLATE&text=' + text + '&dates=' + dates + '&details=' + details + '&location=' + loc;
            window.open(url, '_blank');
        },

        buildCoachGrid(dateStr) {
            this.coachGrid = [];
            const startH = parseInt(this.workStart?.split(':')[0] || '9');
            const endH = parseInt(this.workEnd?.split(':')[0] || '21');
            const now = new Date();
            const currentMinutes = now.getHours() * 60 + now.getMinutes();
            const today = this.todayStr;
            for (let h = startH; h < endH; h++) {
                const cells = [];
                for (const offset of [0, 30]) {
                    const mm = h * 60 + offset;
                    let isPast = false;
                    if (dateStr && dateStr < today) {
                        isPast = true;
                    } else if (dateStr === today) {
                        isPast = mm <= currentMinutes;
                    }
                    cells.push({ mm, isPast });
                }
                this.coachGrid.push({ lbl: `${String(h).padStart(2,'0')}:00`, cells });
            }
            this.loadCoachAvail(dateStr);
        },

        async loadCoachAvail(dateStr) {
            if (!dateStr) return;
            try {
                const r = await fetch(`/api/v1/coach/availability?startDate=${dateStr}&endDate=${dateStr}`);
                if (!r.ok) return;
                const data = (await r.json()).filter(x => x.date === dateStr);
                const cells = {};
                for (const item of data) {
                    const [sh, sm] = item.startTime.split(':').map(Number);
                    const [eh, em] = item.endTime.split(':').map(Number);
                    let t = sh * 60 + sm, end = eh * 60 + em;
                    while (t < end) {
                        cells[t] = item.locationId || null;
                        t += 30;
                    }
                }
                this.coachAvailByDate[dateStr] = cells;
            } catch (e) {
                console.error('loadCoachAvail failed', e);
            }
        },

        getCoachAvailLocId(date, mm) {
            const cells = this.coachAvailByDate[date];
            if (!cells) return null;
            return cells[mm] || null;
        },
        initTouchDrag() {
            const container = document.querySelector('main') || document.body;
            if (!container) return;
            if (this._touchDragCleanup) return;

            let timer = null;
            let drag = null;

            const onStart = (e) => {
                const card = e.target.closest('[data-session-id]');
                if (!card) return;
                const id = parseInt(card.dataset.sessionId);
                const session = this.sessions.find(w => w.id === id);
                if (!session) return;
                const t = e.touches[0];
                this.touchJustDragged = false;
                drag = { session, card, startX: t.clientX, startY: t.clientY, armed: false, ghost: null };
                timer = setTimeout(() => { if (drag) drag.armed = true; }, 400);
            };

                const onMove = (e) => {
                if (!drag || !drag.armed) return;
                e.preventDefault();
                const t = e.touches[0];
                if (!drag.ghost) {
                    const g = drag.card.cloneNode(true);
                    g.style.position = 'fixed';
                    g.style.width = drag.card.offsetWidth + 'px';
                    g.style.opacity = '0.85';
                    g.style.pointerEvents = 'none';
                    g.style.zIndex = '9999';
                    g.style.transform = 'scale(0.95) rotate(1deg)';
                    g.style.transition = 'none';
                    document.body.appendChild(g);
                    drag.ghost = g;
                }
                drag.ghost.style.left = (t.clientX - drag.ghost.offsetWidth / 2) + 'px';
                drag.ghost.style.top = (t.clientY - 30) + 'px';
            };

            const onEnd = async (e) => {
                clearTimeout(timer);
                if (!drag) return;
                if (drag.armed && drag.ghost) {
                    this.touchJustDragged = true;
                    setTimeout(() => { this.touchJustDragged = false; }, 400);
                    const el = document.elementFromPoint(e.changedTouches[0].clientX, e.changedTouches[0].clientY);
                    const c = el?.closest('[data-session-id]');
                    if (c) {
                        const tid = parseInt(c.dataset.sessionId);
                        const tw = this.sessions.find(w => w.id === tid);
                        if (tw && tw.id !== drag.session.id) {
                            const ts = drag.session.time, te = drag.session.endTime;
                            drag.session.time = tw.time; drag.session.endTime = tw.endTime;
                            tw.time = ts; tw.endTime = te;
                            await this.saveRaw(drag.session); await this.saveRaw(tw);
                            await this.fetchData();
                        }
                    }
                }
                if (drag.ghost) drag.ghost.remove();
                drag = null;
            };

            container.addEventListener('touchstart', onStart, { passive: true });
            container.addEventListener('touchmove', onMove, { passive: false });
            container.addEventListener('touchend', onEnd, { passive: true });

            this._touchDragCleanup = () => {
                container.removeEventListener('touchstart', onStart);
                container.removeEventListener('touchmove', onMove);
                container.removeEventListener('touchend', onEnd);
            };
        },

        async fetchMe() {
            try {
                const res = await fetch('/api/v1/me');
                if (res.ok) {
                    const data = await res.json();
                    this.user = data.user || {};
                    this.mentorId = data.mentor?.id;
                    this.mentor.name = data.mentor?.name || 'Cozy Planner';
                    this.mentorProfile = data.mentor?.profile || 'sport';
                    this.workStart = data.mentor?.workStart || '09:00';
                    this.workEnd = data.mentor?.workEnd || '21:00';
                    this.photoUrl = data.mentor?.photoUrl || null;
                    this.labels = data.labels || {};
                    return true;
                } else {
                    window.location.href = '/setup';
                    return false;
                }
            } catch {
                window.location.href = '/setup';
                return false;
            }
        },

        get filteredSessions() {
            return this.sessions.filter(w => w.date === this.selectedDate);
        },

        targetIndex(id) {
            return this.filteredSessions.findIndex(w => w.id === id);
        },

        scrollToToday() {
            this.selectedDate = this.todayStr;
            this.$nextTick(() => this.scrollToActive());
        },

        scrollToWeekStart() {
            const el = document.getElementById('calendar-container');
            const todayBtn = document.getElementById('date-btn-' + this.todayStr);
            if (!el || !todayBtn) {
                this.scrollToActive();
                return;
            }
            
            const todayIdx = this.days.findIndex(d => d.dateStr === this.todayStr);
            if (todayIdx === -1) {
                this.scrollToActive();
                return;
            }
            
            let mondayIdx = todayIdx;
            while (mondayIdx > 0 && !this.days[mondayIdx].isMonday) {
                mondayIdx--;
            }
            
            const mondayBtn = document.getElementById('date-btn-' + this.days[mondayIdx].dateStr);
            if (mondayBtn) {
                el.scrollTo({
                    left: mondayBtn.offsetLeft - 16,
                    behavior: 'smooth'
                });
            }
        },

        scrollToActive() {
            const el = document.getElementById('calendar-container');
            const activeBtn = document.getElementById('date-btn-' + this.selectedDate);
            if (el && activeBtn) {
                el.scrollTo({
                    left: activeBtn.offsetLeft - el.offsetWidth / 2 + activeBtn.offsetWidth / 2,
                    behavior: 'smooth'
                });
            }
        },

        goToDay(date) { this.selectedDate = date; this.currentView = 'feed'; setTimeout(() => this.scrollToActive(), 150); },
        toggleTraineeFilter(id) { if (id === null) this.selectedTraineeFilters = []; else { const idx = this.selectedTraineeFilters.indexOf(id); if (idx > -1) this.selectedTraineeFilters.splice(idx, 1); else this.selectedTraineeFilters.push(id); } },

        get agendaGroups() {
            const loadedList = Object.keys(this.loadedDates).sort().filter(d => d >= this.todayStr);
            let filtered = this.sessions.filter(w => loadedList.includes(w.date));
            if (this.selectedTraineeFilters.length > 0) filtered = filtered.filter(w => w.traineeIds.some(id => this.selectedTraineeFilters.includes(id)));
            const groups = filtered.reduce((acc, w) => { if (!acc[w.date]) acc[w.date] = []; acc[w.date].push(w); return acc; }, {});
            return loadedList.map(date => ({ date, items: groups[date] || [] }));
        },

        get hasMoreDays() {
            const futureDays = this.days.filter(d => d.dateStr >= this.todayStr);
            const loadedFuture = Object.keys(this.loadedDates).filter(d => d >= this.todayStr);
            return loadedFuture.length < futureDays.length;
        },

        get futureDays() {
            return this.days.filter(d => d.dateStr >= this.todayStr);
        },

        traineeHasFutureAvailability(id) {
            return this.futureDays.some(d => this.getFutureSlots(id, d.dateStr).length > 0);
        },

        get filteredTrainees() {
            const q = this.traineeSearch.toLowerCase();
            return this.trainees
                .filter(a => !q || a.name.toLowerCase().includes(q))
                .sort((a, b) => a.name.localeCompare(b.name));
        },

        isTimePassed() {
            if (this.sessionForm.startTime === null) return this.sessionForm.date <= this.todayStr;
            if (this.sessionForm.date < this.todayStr) return true;
            if (this.sessionForm.date === this.todayStr) {
                return new Date(`${this.sessionForm.date}T${this.sessionForm.startTime}`) <= new Date();
            }
            return false;
        },

        isSlotPast(t) {
            if (this.sessionForm.date < this.todayStr) return true;
            if (this.sessionForm.date === this.todayStr) {
                const now = new Date();
                const currentMinutes = now.getHours() * 60 + now.getMinutes();
                return this.slotToMin(t) <= currentMinutes;
            }
            return false;
        },

        async fetchSessionCounts() {
            if (!this.mentorId) return;
            const startDate = this.days[0].dateStr;
            const endDate = this.days[this.days.length - 1].dateStr;
            const res = await fetch(`/api/v1/sessions/counts?mentorId=${this.mentorId}&startDate=${startDate}&endDate=${endDate}`);
            if (res.ok) {
                this.sessionCounts = await res.json();
            }
        },

        async fetchData() {
            if (!this.mentorId) return;
            const today = this.todayStr;
            const end = addDays(today, 2);
            const res = await fetch(`/api/v1/sessions?mentorId=${this.mentorId}&startDate=${today}&endDate=${end}`);
            if (res.ok) {
                this.sessions = (await res.json()).sort((a, b) => a.time.localeCompare(b.time));
                for (const w of this.sessions) {
                    w.color = this.getLocationColor(w.locationId);
                }
                this.loadedDates = {};
                for (let i = 0; i < 3; i++) {
                    this.loadedDates[addDays(today, i)] = true;
                }
            }
            if (this.selectedDate && !this.loadedDates[this.selectedDate]) {
                await this.loadDaySessions(this.selectedDate);
            }
        },

        async loadDaySessions(dateStr) {
            if (!this.mentorId || this.loadedDates[dateStr]) return;
            this.loading = true;
            try {
                const res = await fetch(`/api/v1/sessions?mentorId=${this.mentorId}&startDate=${dateStr}&endDate=${dateStr}`);
                if (res.ok) {
                    const daySessions = (await res.json()).sort((a, b) => a.time.localeCompare(b.time));
                    for (const w of daySessions) {
                        w.color = this.getLocationColor(w.locationId);
                    }
                    this.sessions = [...this.sessions.filter(s => s.date !== dateStr), ...daySessions];
                    this.loadedDates[dateStr] = true;
                }
            } catch (e) {
                console.error('Failed to load sessions for', dateStr, e);
            } finally {
                this.loading = false;
            }
        },

        async loadNextDays(n) {
            const loaded = Object.keys(this.loadedDates).sort();
            const lastLoaded = loaded[loaded.length - 1];
            if (!lastLoaded) return;
            const startDate = addDays(lastLoaded, 1);
            const endDate = addDays(lastLoaded, n);
            this.loadingMore = true;
            try {
                const res = await fetch(`/api/v1/sessions?mentorId=${this.mentorId}&startDate=${startDate}&endDate=${endDate}`);
                if (res.ok) {
                    const newSessions = (await res.json()).sort((a, b) => a.time.localeCompare(b.time));
                    for (const w of newSessions) {
                        w.color = this.getLocationColor(w.locationId);
                        const existing = this.sessions.findIndex(s => s.id === w.id);
                        if (existing >= 0) {
                            this.sessions[existing] = w;
                        } else {
                            this.sessions.push(w);
                        }
                    }
                    let d = new Date(startDate + 'T12:00:00');
                    const end = new Date(endDate + 'T12:00:00');
                    while (d <= end) {
                        this.loadedDates[localDateStr(d)] = true;
                        d.setDate(d.getDate() + 1);
                    }
                }
            } finally {
                this.loadingMore = false;
            }
        },

        async fetchTrainees() {
            if (!this.mentorId) return;
            
            try {
                const traineesRes = await fetch(`/api/v1/mentors/${this.mentorId}/trainees`);
                if (traineesRes.ok) {
                    this.trainees = (await traineesRes.json()).sort((a, b) => a.name.localeCompare(b.name));
                }
                
                try {
                    const tgRes = await fetch(`/api/v1/telegram/config`);
                    if (tgRes.ok) {
                        const tgConfig = await tgRes.json();
                        if (tgConfig.enabled) {
                            const tgStatusRes = await fetch(`/api/v1/mentors/${this.mentorId}/trainees-telegram`);
                            if (tgStatusRes.ok) {
                                const tgStatuses = await tgStatusRes.json();
                                const tgMap = {};
                                for (const t of tgStatuses) {
                                    tgMap[t.id] = t;
                                }
                                for (const a of this.trainees) {
                                    const tg = tgMap[a.id];
                                    if (tg) {
                                        a.telegramEnabled = tg.telegramEnabled;
                                        a.telegramConnected = tg.telegramConnected;
                                        a.telegramUsername = tg.telegramUsername;
                                        a.connectLink = tg.connectLink;
                                        a.photoBase64 = tg.photoBase64;
                                        a.weekendReminderEnabled = tg.weekendReminderEnabled;
                                        a.sessionReminderEnabled = tg.sessionReminderEnabled;
                                        a.inviteToken = tg.inviteToken;
                                    } else {
                                        a.telegramEnabled = true;
                                        a.telegramConnected = false;
                                        a.photoBase64 = null;
                                        a.weekendReminderEnabled = false;
                                        a.sessionReminderEnabled = false;
                                        a.inviteToken = null;
                                    }
                                }
                            }
                        } else {
                            for (const a of this.trainees) {
                                a.telegramEnabled = false;
                                a.weekendReminderEnabled = false;
                                a.sessionReminderEnabled = false;
                            }
                        }
                    }
                } catch (e) {
                    console.log('Telegram config fetch failed (probably not configured):', e.message);
                    for (const a of this.trainees) {
                        a.telegramEnabled = false;
                        a.weekendReminderEnabled = false;
                        a.sessionReminderEnabled = false;
                    }
                }
                
                for (const a of this.trainees) {
                    if (!(a.id in this.notifyingTrainees)) this.notifyingTrainees[a.id] = false;
                    if (!(a.id in this.notifySuccess)) this.notifySuccess[a.id] = false;
                    if (!(a.id in this.notifyErrors)) this.notifyErrors[a.id] = null;
                }
                
            } catch (e) {
                console.error('fetchTrainees failed:', e);
            }
        },

        openNotifyModal(trainee) {
            if (trainee.telegramConnected) {
                this.notifyModal = {
                    show: true,
                    traineeId: trainee.id,
                    traineeName: trainee.name,
                    customMessage: '',
                    dayType: 'tomorrow',
                    targetDate: ''
                };
            }
        },
        
        closeNotifyModal() {
            this.notifyModal = {
                show: false,
                traineeId: null,
                traineeName: '',
                customMessage: '',
                dayType: 'tomorrow',
                targetDate: ''
            };
        },
        
        async sendNotifyTrainee() {
            const traineeId = this.notifyModal.traineeId;
            const customMsg = this.notifyModal.customMessage;
            const dayType = this.notifyModal.dayType || 'tomorrow';
            const targetDate = this.notifyModal.targetDate;
            
            if (this.notifyingTrainees[traineeId] === true) return;
            
            this.notifyingTrainees[traineeId] = true;
            this.notifyErrors[traineeId] = null;
            this.notifySuccess[traineeId] = false;
            
            const body = { dayType };
            if (dayType === 'specific_day' && targetDate) {
                body.targetDate = targetDate;
            }
            if (customMsg && customMsg.trim().length > 0) {
                body.customMessage = customMsg;
            }
            
            const res = await fetch(`/api/v1/trainees/${traineeId}/notify-availability`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
            
            if (res.ok) {
                const data = await res.json();
                if (data.success) {
                    this.notifySuccess[traineeId] = true;
                    setTimeout(() => { this.notifySuccess[traineeId] = false; }, 3000);
                    this.closeNotifyModal();
                } else if (data.reason === 'Telegram not connected') {
                    this.notifyErrors[traineeId] = this.labels.error_tg_not_connected || 'Учень не підключив Telegram.';
                } else {
                    this.notifyErrors[traineeId] = data.reason || (this.labels.error_send || 'Помилка відправки');
                }
            } else {
                this.notifyErrors[traineeId] = this.labels.error_server || 'Помилка сервера';
            }
            
            this.notifyingTrainees[traineeId] = false;
        },
        
        async startFresh() {
            if (!confirm('🗑 Видалити всі дані та почати спочатку? Усі учні, сесії, локації та налаштування будуть безповоротно видалені.')) return;
            if (!confirm('Ви впевнені? Цю дію неможливо скасувати.')) return;
            try {
                const res = await fetch('/api/v1/account/reset', { method: 'POST' });
                if (res.ok) {
                    window.location.href = '/signin';
                } else {
                    alert('Помилка при видаленні даних');
                }
            } catch (e) {
                alert('Помилка сервера');
            }
        },

        async fetchMentorTelegramStatus() {
            try {
                const res = await fetch('/api/v1/mentor/telegram/status');
                if (res.ok) {
                    const data = await res.json();
                    this.mentorTg = {
                        enabled: data.enabled,
                        connected: data.connected,
                        username: data.telegramUsername,
                        connectLink: data.connectLink
                    };
                }
            } catch (e) {
                console.error('Failed to fetch mentor telegram status:', e);
            }
        },
        uploadPhoto(event) {
            const file = event.target.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = async (e) => {
                const dataUrl = e.target.result;
                const res = await fetch('/api/v1/mentor/profile', {
                    method: 'PUT',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({photoUrl: dataUrl})
                });
                if (res.ok) this.photoUrl = dataUrl;
            };
            reader.readAsDataURL(file);
        },
        async saveWorkHours() {
            await fetch('/api/v1/mentor/profile', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({workStart: this.workStart, workEnd: this.workEnd})
            });
            this.buildCoachGrid();
        },
        
        async generateMentorTelegramToken() {
            if (this.mentorTg.connectLink && this.mentorTg.connectLink.length > 0) {
                try {
                    await navigator.clipboard?.writeText(this.mentorTg.connectLink);
                    this.mentorTg.copied = true;
                    setTimeout(() => { 
                        this.mentorTg.copied = false; 
                    }, 2000);
                } catch (e) {
                    console.error('Failed to copy mentor telegram link:', e);
                }
                return;
            }
            try {
                const res = await fetch('/api/v1/mentor/telegram/generate-token', { method: 'POST' });
                if (res.ok) {
                    const data = await res.json();
                    if (data.success) {
                        this.mentorTg.connectLink = data.connectLink;
                        if (data.connectLink) {
                            await navigator.clipboard?.writeText(data.connectLink);
                            this.mentorTg.copied = true;
                            setTimeout(() => { 
                                this.mentorTg.copied = false; 
                            }, 2000);
                        }
                    }
                }
            } catch (e) {
                console.error('Failed to generate mentor telegram token:', e);
            }
        },

        async fetchLocations() {
            if (!this.mentorId) return;
            const res = await fetch(`/api/v1/mentors/${this.mentorId}/locations`);
            if (res.ok) this.locations = await res.json();
        },

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

        async loadDayOffs() {
            if (!this.mentorId || this.days.length === 0) return;
            const start = this.days[0].dateStr;
            const end = this.days[this.days.length - 1].dateStr;
            try {
                const res = await fetch(`/api/v1/coach/day-off?startDate=${start}&endDate=${end}`);
                if (res.ok) {
                    this.dayOffs = await res.json();
                }
            } catch (e) {
                console.error('loadDayOffs failed', e);
            }
        },

        isDayOff(dateStr) {
            return this.dayOffs.includes(dateStr);
        },

        async generateInvite(traineeId) {
            if (this.inviteUrls[traineeId]) {
                try { await navigator.clipboard.writeText(this.inviteUrls[traineeId]); }
                catch(e) { prompt('Запросити в TG:', this.inviteUrls[traineeId]); }
                return;
            }
            const res = await fetch(`/api/v1/trainees/${traineeId}/generate-invite`, { method: 'POST' });
            if (res.ok) {
                const data = await res.json();
                this.inviteUrls[traineeId] = data.inviteUrl;
                try { await navigator.clipboard.writeText(data.inviteUrl); }
                catch(e) { prompt('Запросити в TG:', data.inviteUrl); }
            }
        },

        isAvailable(traineeId, date) {
            const key = traineeId + '|' + date;
            return this.availabilityMap[key] !== undefined ? this.availabilityMap[key] : null;
        },
        getFutureSlots(traineeId, dateStr) {
            const slots = this.availabilityMap[traineeId + '|' + dateStr];
            if (!slots) return [];
            if (dateStr !== this.todayStr) return slots;
            const now = new Date();
            const currentMin = now.getHours() * 60 + now.getMinutes();
            return slots.filter(s => this.slotToMin(s.endTime) > currentMin);
        },

        getAvailLabel(slots) {
            if (!slots || slots.length === 0) return '';
            return slots.map(s => s.startTime.slice(0,5) + '-' + s.endTime.slice(0,5)).join(', ');
        },

        toggleTraineeSelection(id) {
            const idx = this.sessionForm.traineeIds.indexOf(id);
            if (idx > -1) {
                this.sessionForm.traineeIds = this.sessionForm.traineeIds.filter(i => i !== id);
            } else {
                this.sessionForm.traineeIds = [...this.sessionForm.traineeIds, id];
            }
            this.updateSessionTitleFromTrainees();
        },
        getTraineeName(id) { return (this.trainees.find(at => at.id == id)?.name) || 'Unknown'; },

        isTraineeConfirmed(traineeId, confirmedIdsStr) {
            if (!confirmedIdsStr) return false;
            return confirmedIdsStr.split(',').map(s => s.trim()).filter(Boolean).includes(String(traineeId));
        },

        getTraineeNamesText(ids) {
            return ids.map(id => this.getTraineeName(id)).filter(n => n).join(', ');
        },
        updateSessionTitleFromTrainees() {
            const base = this.labels.session_title_default || 'Тренування';
            const names = this.getTraineeNamesText(this.sessionForm.traineeIds);
            this.sessionForm.title = names ? base + ' — ' + names : base;
        },
        createSessionForTrainee(trainee) {
            if (this.isDayOff(this.selectedDate)) {
                alert('Не можна створити сесію у вихідний день тренера.');
                return;
            }
            this.showManageTrainees = false;
            this.traineeSearch = '';
            this.editingSessionId = null;
            this.originalSessionData = null;
            this.sessionForm = { title: (this.labels.session_title_default || 'Тренування') + ' — ' + (trainee.name || ''), description: '', date: this.selectedDate, startTime: null, endTime: null, traineeIds: [trainee.id], locationId: null };
            this.selectedCoachSlots = [];
            this.showModal = true;
        },
        editTrainee(a) { this.editingTraineeId = a.id; this.traineeForm = { name: a.name, description: a.description, photoBase64: a.photoBase64 || null }; this.showCreateForm = false; },
         askDeleteTrainee(id) { this.confirmData = { show: true, title: this.labels.confirm_delete_title || 'Видалити?', message: this.labels.confirm_delete_trainee || 'Дані зникнуть.', onConfirm: async () => { await fetch(`/api/v1/trainees/${id}`, { method: 'DELETE' }); await this.fetchTrainees(); await this.fetchData(); } }; },

          async generateTraineeLink(trainee) {
              if (this.traineeLinks[trainee.id]) return;
              let url = null;
              if (trainee.inviteToken) {
                  url = window.location.origin + '/trainee/' + trainee.inviteToken;
              } else {
                  const res = await fetch(`/api/v1/trainees/${trainee.id}/generate-invite`, { method: 'POST' });
                  if (res.ok) {
                      const data = await res.json();
                      const match = data.inviteUrl.match(/start=([^&?#]+)/);
                      const token = match ? match[1] : null;
                      if (token) {
                          url = window.location.origin + '/trainee/' + token;
                      } else {
                          url = data.inviteUrl;
                      }
                  }
              }
              if (url) {
                  try { await navigator.clipboard.writeText(url); }
                  catch(e) { prompt('Посилання учня:', url); return; }
                  this.traineeLinks[trainee.id] = url;
                  setTimeout(() => { delete this.traineeLinks[trainee.id]; }, 3000);
              }
          },

         getCardBorderClass(session) {
             const base = this.isSessionNow(session) ? 'bg-[#1c1c1c] ring-2 ring-red-500/50 shadow-lg shadow-red-500/10' : 'bg-[#1c1c1c] border border-[#333]';
             if (session.confirmationStatus === 'CONFIRMED') return 'bg-green-900/20 border-green-500';
             if (session.confirmationStatus === 'REJECTED') return 'bg-red-900/10 border-red-500/30 opacity-70';
             if (session.confirmationStatus === 'PENDING' && session.createdBy === 'TRAINEE') return 'bg-yellow-900/10 border-yellow-500/30';
             return base;
         },

         getAgendaCardClass(session) {
             const base = this.isSessionNow(session) ? 'bg-[#1c1c1c] ring-2 ring-red-500/50 shadow-lg shadow-red-500/10' : 'bg-[#1c1c1c] border border-[#333]';
             if (session.confirmationStatus === 'CONFIRMED') return 'bg-green-900/20 border-green-500';
             if (session.confirmationStatus === 'REJECTED') return 'bg-red-900/10 border-red-500/30 opacity-70';
             if (session.confirmationStatus === 'PENDING' && session.createdBy === 'TRAINEE') return 'bg-yellow-900/10 border-yellow-500/30';
             return base;
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

         async confirmSession(sessionId) {
             await fetch(`/api/v1/sessions/${sessionId}/confirm`, { method: 'POST' });
             await this.fetchData();
         },

          async rejectSession(sessionId) {
              await fetch(`/api/v1/sessions/${sessionId}/reject`, { method: 'POST' });
              await this.fetchData();
          },

          handleConfirmAction(session) {
              if (session.createdBy === 'COACH' && session.confirmationStatus === 'NONE') {
                  this.requestTraineeConfirmation(session);
              } else if (session.createdBy === 'TRAINEE' && session.confirmationStatus === 'PENDING') {
                  this.confirmSession(session.id);
              }
          },

          requestTraineeConfirmation(session) {
             this.confirmData = {
                 show: true,
                 title: 'Запитати підтвердження?',
                 message: 'Надіслати учню запит на підтвердження сесії через Telegram?',
                 confirmText: 'Надіслати',
                 onConfirm: async () => {
                     await fetch(`/api/v1/sessions/${session.id}/request-trainee-confirmation`, { method: 'POST' });
                 }
             };
         },
        
         async toggleWeekendReminder(trainee) {
             if (!trainee.telegramEnabled || !trainee.telegramConnected) return;
             const res = await fetch(`/api/v1/trainees/${trainee.id}/weekend-reminder`, {
                 method: 'POST',
                 headers: { 'Content-Type': 'application/json' },
                 body: JSON.stringify({ enabled: trainee.weekendReminderEnabled })
             });
             if (!res.ok) {
                 trainee.weekendReminderEnabled = !trainee.weekendReminderEnabled;
             }
         },
         
         async toggleSessionReminder(trainee) {
             if (!trainee.telegramEnabled || !trainee.telegramConnected) return;
             const res = await fetch(`/api/v1/trainees/${trainee.id}/session-reminder`, {
                 method: 'POST',
                 headers: { 'Content-Type': 'application/json' },
                 body: JSON.stringify({ enabled: trainee.sessionReminderEnabled })
             });
             if (!res.ok) {
                 trainee.sessionReminderEnabled = !trainee.sessionReminderEnabled;
             }
         },
        
         handleTraineePhotoSelect(event) {
            const file = event.target.files[0];
            if (!file) return;
            
            const reader = new FileReader();
            reader.onload = (e) => {
                const img = new Image();
                img.onload = () => {
                    const canvas = document.createElement('canvas');
                    const maxSize = 200;
                    let w = img.width;
                    let h = img.height;
                    
                    if (w > h) {
                        if (w > maxSize) {
                            h = Math.round(h * maxSize / w);
                            w = maxSize;
                        }
                    } else {
                        if (h > maxSize) {
                            w = Math.round(w * maxSize / h);
                            h = maxSize;
                        }
                    }
                    
                    canvas.width = w;
                    canvas.height = h;
                    const ctx = canvas.getContext('2d');
                    ctx.drawImage(img, 0, 0, w, h);
                    this.traineeForm.photoBase64 = canvas.toDataURL('image/jpeg', 0.8);
                };
                img.src = e.target.result;
            };
            reader.readAsDataURL(file);
            event.target.value = '';
        },

        async saveLocation() {
            const method = this.editingLocationId ? 'PUT' : 'POST';
            const url = this.editingLocationId ? `/api/v1/locations/${this.editingLocationId}` : '/api/v1/locations';
            await fetch(url, { method: method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...this.locationForm, mentorId: this.mentorId }) });
            this.locationForm = { name: '', description: '', color: '#3b82f6' }; this.editingLocationId = null;
            await this.fetchLocations();
        },

        editLocation(l) { this.editingLocationId = l.id; this.locationForm = { name: l.name, description: l.description || '', color: l.color || '#3b82f6' }; },
        askDeleteLocation(id) { this.confirmData = { show: true, title: this.labels.confirm_delete_title || 'Видалити?', message: this.labels.confirm_delete_location || 'Локація зникне з усіх тренувань.', onConfirm: async () => { await fetch(`/api/v1/locations/${id}`, { method: 'DELETE' }); await this.fetchLocations(); await this.fetchData(); } }; },

        snapToMonday(date) {
            const d = new Date(date);
            const day = d.getDay();
            const diff = d.getDate() - day + (day === 0 ? -6 : 1);
            d.setDate(diff);
            return d;
        },
        generateDays() {
            const start = this.snapToMonday(new Date());
            start.setDate(start.getDate() - 14);
            const days = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Нд'];
            const fullDays = ['Понеділок', 'Вівторок', 'Середа', 'Четвер', 'П\'ятниця', 'Субота', 'Неділя'];
            const shortMonths = ['Січ','Лют','Бер','Кві','Тра','Чер','Лип','Сер','Вер','Жов','Лис','Гру'];
            for (let i = 0; i < 77; i++) {
                const dayIdx = (start.getDay() + 6) % 7;
                const ds = localDateStr(start);
                this.days.push({ dateStr: ds, dayNum: start.getDate(), weekday: days[dayIdx], weekdayFull: fullDays[dayIdx], isMonday: start.getDay() === 1, shortMonth: shortMonths[start.getMonth()], isToday: ds === this.todayStr, isWeekend: start.getDay() === 0 || start.getDay() === 6 });
                start.setDate(start.getDate() + 1);
            }
        },
        formatDisplayDate(str) {
            const d = new Date(str); const months = ['січня', 'лютого', 'березня', 'квітня', 'травня', 'червня', 'липня', 'серпня', 'вересня', 'жовтня', 'листопада', 'грудня'];
            const fullDays = ['Понеділок', 'Вівторок', 'Середа', 'Четвер', 'П\'ятниця', 'Субота', 'Неділя'];
            const dayIdx = (d.getDay() + 6) % 7;
            return `${fullDays[dayIdx]}, ${d.getDate()} ${months[d.getMonth()]}`;
        }
    }
}
