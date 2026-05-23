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
function calendarApp() {
    const _today = localDateStr();
    return {
        activeTab: 'feed', showModal: false,
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
        pastDaysToShow: 0,
        loading: false,
        loadingMore: false,
        agendaReady: false,
        loadedDates: {},
        sessionCounts: {},
        availabilityMap: {},
        dayOffs: [],
        touchDrag: null,
        touchJustDragged: false,
        _touchDragCleanup: null,
        deferredInstallPrompt: null,
        isStandalone: window.matchMedia('(display-mode: standalone)').matches,
        workStart: '09:00',
        workEnd: '21:00',
        timezone: 'Europe/Kyiv',
        editingTimezone: 'Europe/Kyiv',
        photoUrl: null,
        notifications: [],
        unreadCount: 0,
        shareToken: null,
        shareUrl: '',
        copied: false,
        copiedToday: false,
        imgCopiedWeek: false,
        imgCopiedDay: false,
        pastSessions: [],
        _pastSessionPage: 0,
        hasMorePastSessions: true,
        loadingMorePast: false,
        coachAvailDate: _today,
        coachAvailDays: [],
        coachAvailGrid: [],
        coachCells: {},
        coachSess: {},
        coachCurLoc: null,
        coachBusySession: null,
        coachSaving: false,
        coachDirtyDates: new Set(),
        coachDirtyDayOffs: [],
        coachCopied: false,
        coachImgCopiedWeek: false,
        coachLoaded: false,
        sessionValidationErrors: null,

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
        async init() {
            console.log('init() starting...');
            this.initAudioContext();
            if (_deferredInstall) this.deferredInstallPrompt = _deferredInstall;
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

                await this.fetchDayOffs();
                console.log('fetchDayOffs done');

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
                    case 'coach_availability_changed': this.fetchDayOffs(); this.buildCoachGrid(); break;
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
                date: w.date,
                startTime: st,
                endTime: et,
                traineeIds: [...(w.traineeIds || [])],
                locationId: w.locationId || null
            };
            this.showModal = true;
        },

        copyFromHistory(session) {
            this.editingSessionId = null;
            this.originalSessionData = null;
            let st = session.time, et = session.endTime || this.nextSlot(session.time);
            let date = this.selectedDate;
            if (date < this.todayStr) date = this.todayStr;
            if (date === this.todayStr && new Date(`${date}T${st}`) <= new Date()) {
                st = this.getNearestSlot();
                const dur = this.slotToMin(et) - this.slotToMin(st);
                const em = this.slotToMin(st) + Math.max(dur, 30);
                const eh = Math.floor(em / 60) % 24;
                et = `${eh.toString().padStart(2, '0')}:${em % 60 === 0 ? '00' : '30'}`;
            }
            this.sessionForm = {
                title: session.title,
                description: session.description || '',
                date,
                startTime: st,
                endTime: et,
                traineeIds: [...(session.traineeIds || [])],
                locationId: session.locationId || null
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
                    this.timezone = data.mentor?.timezone || 'Europe/Kyiv';
                    this.editingTimezone = this.timezone;
                    this.photoUrl = data.mentor?.photoUrl || null;
                    this.shareToken = data.mentor?.shareToken || null;
                    this.shareUrl = this.shareToken ? window.location.origin + '/shared/' + this.shareToken : '';
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

        async selectDay(dateStr) {
            this.selectedDate = dateStr;
            this.activeTab = 'feed';
            this.$nextTick(() => this.scrollToActive());
            if (!this.loadedDates[dateStr]) {
                await this.loadDaySessions(dateStr);
            }
        },

        get visibleDays() {
            const todayIdx = this.days.findIndex(d => d.dateStr === this.todayStr);
            if (todayIdx <= 0) return this.days;
            return this.days.slice(Math.max(0, todayIdx - this.pastDaysToShow));
        },

        get hasMorePast() {
            const todayIdx = this.days.findIndex(d => d.dateStr === this.todayStr);
            return todayIdx > 0 && this.pastDaysToShow < todayIdx;
        },

        showMorePast() {
            this.pastDaysToShow += 14;
            this.$nextTick(() => {
                if (this.selectedDate) this.scrollToActive();
            });
        },

        sessionCountOn(dateStr) {
            return this.sessionCounts[dateStr] || 0;
        },

        sessionCountClass(dateStr) {
            const count = this.sessionCounts[dateStr] || 0;
            if (count > 2) return 'bg-blue-500';
            if (count > 0) return 'bg-blue-400';
            return '';
        },

        isDayOff(dateStr) {
            return this.dayOffs.includes(dateStr);
        },

        goToDay(date) { this.selectedDate = date; this.activeTab = 'feed'; setTimeout(() => this.scrollToActive(), 150); },
        toggleTraineeFilter(id) { if (id === null) this.selectedTraineeFilters = []; else { const idx = this.selectedTraineeFilters.indexOf(id); if (idx > -1) this.selectedTraineeFilters.splice(idx, 1); else this.selectedTraineeFilters.push(id); } },

        get agendaGroups() {
            const loadedList = Object.keys(this.loadedDates).sort().filter(d => d >= this.todayStr);
            let filtered = this.sessions.filter(w => loadedList.includes(w.date));
            if (this.selectedTraineeFilters.length > 0) filtered = filtered.filter(w => w.traineeIds.some(id => this.selectedTraineeFilters.includes(id)));
            const groups = filtered.reduce((acc, w) => { if (!acc[w.date]) acc[w.date] = []; acc[w.date].push(w); return acc; }, {});
            return loadedList.map(date => ({ date, items: groups[date] || [] }));
        },

        get hasMoreAgenda() {
            return this.hasMoreDays;
        },

        async loadFutureSessions() {
            this.agendaReady = false;
            const today = this.todayStr;
            this.loadedDates = {};
            for (let i = 0; i < 3; i++) {
                this.loadedDates[addDays(today, i)] = true;
            }
            const end = addDays(today, 2);
            try {
                const res = await fetch(`/api/v1/sessions?mentorId=${this.mentorId}&startDate=${today}&endDate=${end}`);
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
                }
            } catch (e) {
                console.error('[agenda] fetch error:', e);
            } finally {
                this.agendaReady = true;
            }
        },

        async loadMoreAgenda() {
            if (!this.hasMoreDays) return;
            await this.loadNextDays(7);
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

        get pastSessionsGroups() {
            const filtered = this.pastSessions.filter(s => s.date < this.todayStr);
            const groups = filtered.reduce((acc, w) => { if (!acc[w.date]) acc[w.date] = []; acc[w.date].push(w); return acc; }, {});
            return Object.keys(groups).sort().reverse().map(date => ({ date, items: groups[date] }));
        },

        async loadPastSessions() {
            this._pastSessionPage = 0;
            this.pastSessions = [];
            this.hasMorePastSessions = true;
            await this.loadMorePastSessions();
        },

        async loadMorePastSessions() {
            if (!this.hasMorePastSessions) return;
            this.loadingMorePast = true;
            const daysBack = 7;
            const startDate = addDays(this.todayStr, -(this._pastSessionPage + 1) * daysBack + 1);
            const endDate = addDays(this.todayStr, -this._pastSessionPage * daysBack);
            try {
                const res = await fetch(`/api/v1/sessions?mentorId=${this.mentorId}&startDate=${startDate}&endDate=${endDate}`);
                if (res.ok) {
                    const data = await res.json();
                    const sorted = data.sort((a, b) => b.date.localeCompare(a.date) || b.time.localeCompare(a.time));
                    for (const w of sorted) {
                        w.color = this.getLocationColor(w.locationId);
                        if (!this.pastSessions.find(s => s.id === w.id)) this.pastSessions.push(w);
                    }
                    this.hasMorePastSessions = sorted.length >= daysBack * 2;
                } else {
                    this.hasMorePastSessions = false;
                }
            } catch (e) {
                console.error('Failed to load past sessions:', e);
                this.hasMorePastSessions = false;
            } finally {
                this.loadingMorePast = false;
                this._pastSessionPage++;
            }
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
            const today = this.todayStr;
            if (!this.mentorId) { console.warn('[agenda] no mentorId'); this.agendaReady = true; return; }
            this.loadedDates = {};
            for (let i = 0; i < 3; i++) {
                this.loadedDates[addDays(today, i)] = true;
            }
            const end = addDays(today, 2);
            try {
                const res = await fetch(`/api/v1/sessions?mentorId=${this.mentorId}&startDate=${today}&endDate=${end}`);
                if (res.ok) {
                    this.sessions = (await res.json()).sort((a, b) => a.time.localeCompare(b.time));
                    for (const w of this.sessions) {
                        w.color = this.getLocationColor(w.locationId);
                    }
                }
            } catch (e) {
                console.error('[agenda] fetch error:', e);
            }
            if (this.selectedDate && !this.loadedDates[this.selectedDate]) {
                await this.loadDaySessions(this.selectedDate);
            }
            this.agendaReady = true;
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
                                        a.timezone = tg.timezone || 'Europe/Kyiv';
                                        a._editingTz = a.timezone;
                                    } else {
                                        a.telegramEnabled = true;
                                        a.telegramConnected = false;
                                        a.photoBase64 = null;
                                        a.weekendReminderEnabled = false;
                                        a.timezone = 'Europe/Kyiv';
                                        a._editingTz = a.timezone;
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

        async saveTimezone() {
            await fetch('/api/v1/mentor/profile', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({timezone: this.editingTimezone})
            });
            this.timezone = this.editingTimezone;
        },

        cancelTimezone() {
            this.editingTimezone = this.timezone;
        },

        async mkShareToken() {
            if (this.shareToken) return;
            const r = await fetch('/api/v1/coach/share-token', { method:'POST' });
            if (r.ok) { const d = await r.json(); this.shareToken = d.shareToken; this.shareUrl = window.location.origin + '/shared/' + this.shareToken; }
        },
        async copyShareLink() {
            if (!this.shareToken) await this.mkShareToken();
            if (!this.shareUrl) return;
            try { await navigator.clipboard.writeText(this.shareUrl); this.copied = true; setTimeout(() => this.copied = false, 2000); }
            catch(e) { prompt('Скопіюйте:', this.shareUrl); }
        },
        async copyTodayLink() {
            if (!this.shareToken) await this.mkShareToken();
            if (!this.shareUrl) return;
            const url = this.shareUrl + '?date=' + this.todayStr;
            try { await navigator.clipboard.writeText(url); this.copiedToday = true; setTimeout(() => this.copiedToday = false, 2000); }
            catch(e) { prompt('Скопіюйте:', url); }
        },
        async copyImageWeek() {
            if (!this.shareToken) await this.mkShareToken();
            if (!this.shareToken) return;
            const imgUrl = window.location.origin + '/api/v1/shared/' + this.shareToken + '/image';
            try { await navigator.clipboard.writeText(imgUrl); this.imgCopiedWeek = true; setTimeout(() => this.imgCopiedWeek = false, 3000); }
            catch(e) { prompt('Скопіюйте посилання на картинку:', imgUrl); }
        },
        async copyImageDay() {
            if (!this.shareToken) await this.mkShareToken();
            if (!this.shareToken) return;
            const imgUrl = window.location.origin + '/api/v1/shared/' + this.shareToken + '/image?date=' + this.todayStr;
            try { await navigator.clipboard.writeText(imgUrl); this.imgCopiedDay = true; setTimeout(() => this.imgCopiedDay = false, 3000); }
            catch(e) { prompt('Скопіюйте посилання на картинку:', imgUrl); }
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

        async fetchDayOffs() {
            if (!this.mentorId) return;
            const start = this.days[0].dateStr;
            const end = this.days[this.days.length - 1].dateStr;
            const res = await fetch(`/api/v1/coach/day-off?startDate=${start}&endDate=${end}`);
            if (res.ok) this.dayOffs = await res.json();
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
        getLocationName(id) { return this.locations.find(l => l.id === id)?.name || ''; },
        getLocationColor(id) { return this.locations.find(l => l.id === id)?.color || '#3b82f6'; },
        getTraineeConfirmStatus(traineeId, session) {
            const confirmed = (session.confirmedTraineeIds || '').split(',').map(s => s.trim()).filter(Boolean).map(Number);
            const rejected = (session.rejectedTraineeIds || '').split(',').map(s => s.trim()).filter(Boolean).map(Number);
            if (confirmed.includes(traineeId)) return 'confirmed';
            if (rejected.includes(traineeId)) return 'rejected';
            return 'none';
        },
        getSessionIcons(session, index) {
            const count = (session.traineeIds || []).length;
            return Array(count).fill('😊');
        },
        get nowMinutes() {
            this.nowTick; // trigger reactivity
            const now = new Date();
            return now.getHours() * 60 + now.getMinutes();
        },
        sessionStartMin(session) {
            const [h, m] = (session.time || '0:00').split(':').map(Number);
            return h * 60 + (m || 0);
        },
        sessionEndMin(session) {
            const [h, m] = (session.endTime || session.time || '0:00').split(':').map(Number);
            return h * 60 + (m || 0);
        },
        isSessionNow(session) {
            if (session.date !== this.todayStr) return false;
            const now = this.nowMinutes;
            const start = this.sessionStartMin(session);
            const end = this.sessionEndMin(session);
            return now >= start && now < end;
        },
        showNowLineAfter(session, allSessions) {
            const now = this.nowMinutes;
            const end = this.sessionEndMin(session);
            const sorted = allSessions
                .filter(s => s.id !== session.id && s.date === session.date)
                .slice()
                .sort((a, b) => this.sessionStartMin(a) - this.sessionStartMin(b));
            const nextSession = sorted.find(s => this.sessionStartMin(s) > this.sessionStartMin(session));
            if (!nextSession) return false;
            return end <= now && now < this.sessionStartMin(nextSession);
        },
        hasSessionOn(date) {
            if (date < addDays(this.todayStr, -14)) return false;
            return (this.sessionCounts[date] || 0) > 0;
        },
        handleMouseScroll(e) { document.getElementById('calendar-container').scrollLeft += e.deltaY; },
        dragStart(event, index) { this.draggedIndex = index; },
        dragEnd() { this.draggedIndex = null; },

        getNearestSlot() {
            const now = new Date();
            const totalMin = Math.max(now.getHours() * 60 + now.getMinutes(), 7 * 60);
            const nextSlot = Math.ceil(totalMin / 30) * 30;
            const nh = Math.floor(nextSlot / 60) % 24;
            const nm = nextSlot % 60;
            return `${nh.toString().padStart(2, '0')}:${nm === 0 ? '00' : '30'}`;
        },

        slotToMin(t) {
            const [h, m] = t.split(':').map(Number);
            return h * 60 + m;
        },

        pickTime(t) {
            if (typeof t === 'number') {
                const h = String(Math.floor(t / 60)).padStart(2, '0');
                const m = String(t % 60).padStart(2, '0');
                t = `${h}:${m}`;
            }
            if (this.isSlotPast(t)) return;
            if (this.isSlotOnCoachSession(t)) return;
            const tm = this.slotToMin(t);
            const sel = this.selectedCoachSlots;
            if (sel.length === 0) {
                this.selectedCoachSlots = [tm];
            } else {
                const sorted = [...sel].sort((a, b) => a - b);
                const min = sorted[0], max = sorted[sorted.length - 1];
                if (tm === max + 30) {
                    this.selectedCoachSlots = [...sorted, tm];
                } else if (tm === min - 30) {
                    this.selectedCoachSlots = [tm, ...sorted];
                } else if (tm >= min && tm <= max) {
                    this.selectedCoachSlots = [tm];
                } else {
                    this.selectedCoachSlots = [tm];
                }
            }
            const sorted = [...this.selectedCoachSlots].sort((a, b) => a - b);
            if (sorted.length) {
                const sh = String(Math.floor(sorted[0] / 60)).padStart(2, '0');
                const sm = String(sorted[0] % 60).padStart(2, '0');
                const last = sorted[sorted.length - 1] + 30;
                const eh = String(Math.floor(last / 60)).padStart(2, '0');
                const em = String(last % 60).padStart(2, '0');
                this.sessionForm.startTime = `${sh}:${sm}`;
                this.sessionForm.endTime = `${eh}:${em}`;
                const locId = this.getCoachAvailLocId(this.sessionForm.date, sorted[0]);
                if (locId != null) {
                    this.sessionForm.locationId = locId;
                }
            } else {
                this.sessionForm.startTime = null;
                this.sessionForm.endTime = null;
            }
        },

        isSlotOnCoachSession(t) {
            if (!this.sessionForm.date) return false;
            const tm = this.slotToMin(t);
            return this.sessions.some(s => {
                if (s.date !== this.sessionForm.date) return false;
                if (this.editingSessionId && s.id === this.editingSessionId) return false;
                const [sh, sm] = s.time.split(':').map(Number);
                const [eh, em] = (s.endTime || s.time).split(':').map(Number);
                return tm >= sh * 60 + sm && tm < eh * 60 + em;
            });
        },

        nextSlot(t) {
            const m = this.slotToMin(t) + 30;
            const h = Math.floor(m / 60) % 24;
            return `${h.toString().padStart(2, '0')}:${m % 60 === 0 ? '00' : '30'}`;
        },

        getTimeClass(t) {
            if (this.isSlotPast(t)) return 'bg-[#1a1a1a] text-gray-700 line-through cursor-not-allowed opacity-30';
            if (this.isSlotOnCoachSession(t)) return 'bg-[#1a1a1a] text-gray-700 line-through cursor-not-allowed opacity-30';
            const tm = this.slotToMin(t);
            const sel = this.selectedCoachSlots;
            if (sel.includes(tm)) return 'bg-blue-600 text-white shadow-lg';
            const sorted = [...sel].sort((a, b) => a - b);
            if (sorted.length) {
                const min = sorted[0], max = sorted[sorted.length - 1];
                if (tm > min && tm < max) return 'bg-blue-500/20 text-blue-300';
            }
            if (this.sessionForm.traineeIds.length > 0 && this.hasSlotConflict(t)) return 'bg-red-900/20 text-red-400/60 line-through';
            return 'bg-[#262626] text-gray-400 hover:bg-[#333]';
        },

        coachCellStyle(mm) {
            const t = `${String(Math.floor(mm/60)).padStart(2,'0')}:${String(mm%60).padStart(2,'0')}`;
            if (this.isSlotPast(t)) return 'background:#1a1a1a; pointer-events:none';
            if (this.selectedCoachSlots.includes(mm)) return 'background:#3b82f5';
            if (this.selectedCoachSlots.length > 1) {
                const sorted = [...this.selectedCoachSlots].sort((a,b) => a-b);
                if (mm > sorted[0] && mm < sorted[sorted.length-1]) return 'background:rgba(59,130,245,0.25)';
            }
            if (this.isSlotOnCoachSession(t)) return 'background:#1a1a1a; pointer-events:none';
            if (this.sessionForm.traineeIds.length > 0 && this.hasSlotConflict(t)) return 'background:#1a1a1a; pointer-events:none';
            return 'background:#2a2a2a';
        },

        hasSlotConflict(t) {
            if (this.sessionForm.traineeIds.length === 0) return false;
            const tm = this.slotToMin(t);
            for (const aId of this.sessionForm.traineeIds) {
                const key = aId + '|' + this.sessionForm.date;
                const slots = this.availabilityMap[key];
                if (!slots) continue;
                const ok = slots.some(s => {
                    const ss = this.slotToMin(s.startTime);
                    const se = this.slotToMin(s.endTime);
                    return tm >= ss && tm < se;
                });
                if (!ok) return true;
            }
            return false;
        },

        get isSessionTimeValid() {
            const { startTime, endTime, date, traineeIds } = this.sessionForm;
            if (!startTime || !endTime || traineeIds.length === 0) return true;
            const sm = this.slotToMin(startTime);
            const em = this.slotToMin(endTime);
            for (const aId of traineeIds) {
                const slots = this.availabilityMap[aId + '|' + date];
                if (!slots || slots.length === 0) continue;
                const ok = slots.some(s => {
                    const ss = this.slotToMin(s.startTime);
                    const se = this.slotToMin(s.endTime);
                    return sm >= ss && em <= se;
                });
                if (!ok) return false;
            }
            return true;
        },

        getSessionValidationErrors() {
            const errors = [];
            const { startTime, endTime, date, traineeIds } = this.sessionForm;
            if (!startTime || !endTime || traineeIds.length === 0) return errors;
            if (this.editingSessionId && this.originalSessionData) {
                const sameDate = date === this.originalSessionData.date;
                const sameTime = startTime === this.originalSessionData.time;
                const sameEndTime = endTime === this.originalSessionData.endTime;
                const sameTrainees = this.originalSessionData.traineeIds &&
                    traineeIds.length === this.originalSessionData.traineeIds.length &&
                    traineeIds.every(id => this.originalSessionData.traineeIds.includes(id));
                if (sameDate && sameTime && sameEndTime && sameTrainees) return errors;
            }
            const sm = this.slotToMin(startTime);
            const em = this.slotToMin(endTime);
            if (this.dayOffs.includes(date)) {
                errors.push(this.labels.err_coach_day_off || 'Цей день є вихідним для тренера');
            }
            if (this.workStart && this.workEnd) {
                const [wsh, wsm] = this.workStart.split(':').map(Number);
                const [weh, wem] = this.workEnd.split(':').map(Number);
                const ws = wsh * 60 + wsm;
                const we = weh * 60 + wem;
                if (sm < ws || em > we) {
                    errors.push((this.labels.err_coach_work_hours || 'Час сесії має бути в межах {ws} — {we}').replace('{ws}', this.workStart).replace('{we}', this.workEnd));
                }
            }
            const coachCells = this.coachAvailByDate[date];
            if (coachCells && Object.keys(coachCells).length > 0) {
                let within = true;
                for (let m = sm; m < em; m += 30) {
                    if (!(m in coachCells)) { within = false; break; }
                }
                if (!within) {
                    errors.push(this.labels.err_coach_avail || 'Час сесії не входить в години доступності тренера');
                }
            }
            const coachSessions = this.coachSess[date] || [];
            for (const s of coachSessions) {
                if (this.editingSessionId && s.id === this.editingSessionId) continue;
                const st = s.startTime || s.time;
                const et = s.endTime || st;
                if (!st) continue;
                const [ssh, ssm] = st.split(':').map(Number);
                const [seh, sem] = et.split(':').map(Number);
                const sStart = ssh * 60 + ssm;
                const sEnd = seh * 60 + sem;
                if (sm < sEnd && em > sStart) {
                    errors.push(this.labels.err_coach_conflict || 'Тренер уже має сесію на цей час');
                    break;
                }
            }
            for (const aId of traineeIds) {
                const slots = this.availabilityMap[aId + '|' + date];
                if (slots && slots.length > 0) {
                    const ok = slots.some(s => {
                        const ss = this.slotToMin(s.startTime);
                        const se = this.slotToMin(s.endTime);
                        return sm >= ss && em <= se;
                    });
                    if (!ok) {
                        const trainee = this.trainees.find(t => t.id === aId);
                        errors.push((this.labels.err_trainee_avail || 'Час сесії не входить в години доступності учня {name}').replace('{name}', trainee?.name || ''));
                    }
                }
            }
            if (traineeIds.length > 0) {
                const checked = new Set();
                for (const aId of traineeIds) {
                    if (checked.has(aId)) continue;
                    for (const s of this.sessions) {
                        if (this.editingSessionId && s.id === this.editingSessionId) continue;
                        if (s.date !== date) continue;
                        if (!s.traineeIds || !s.traineeIds.includes(aId)) continue;
                        const st = s.time || s.startTime;
                        const et = s.endTime || st;
                        if (!st) continue;
                        const [ssh, ssm] = st.split(':').map(Number);
                        const [seh, sem] = et.split(':').map(Number);
                        const sStart = ssh * 60 + ssm;
                        const sEnd = seh * 60 + sem;
                        if (sm < sEnd && em > sStart) {
                            const trainee = this.trainees.find(t => t.id === aId);
                            errors.push((this.labels.err_trainee_conflict || 'Учень {name} уже має сесію на цей час').replace('{name}', trainee?.name || ''));
                            checked.add(aId);
                            break;
                        }
                    }
                }
            }
            return errors;
        },

        get saveBtnClass() {
            const base = 'flex-[2] text-white py-4 rounded-2xl font-bold text-sm shadow-lg ';
            if (this.sessionForm.traineeIds.length === 0 || this.isTimePassed() || !this.sessionForm.endTime) {
                return base + 'bg-gray-600 opacity-30';
            }
            if (this.sessionForm.startTime && this.sessionForm.endTime && this.getSessionValidationErrors().length > 0) {
                return base + 'bg-red-700';
            }
            return base + 'bg-blue-600';
        },

        getDurationLabel() {
            if (!this.sessionForm.startTime || !this.sessionForm.endTime) return '';
            const diff = this.slotToMin(this.sessionForm.endTime) - this.slotToMin(this.sessionForm.startTime);
            const h = Math.floor(diff / 60);
            const m = diff % 60;
            if (h > 0 && m > 0) return `${h} год ${m} хв`;
            if (h > 0) return `${h} год`;
            return `${m} хв`;
        },

        sessionDuration(session) {
            if (!session || !session.time || !session.endTime) return '';
            const diff = this.slotToMin(session.endTime) - this.slotToMin(session.time);
            const h = Math.floor(diff / 60);
            const m = diff % 60;
            if (h > 0 && m > 0) return `${h} год ${m} хв`;
            if (h > 0) return `${h} год`;
            return `${m} хв`;
        },

        async drop(event, targetIndex) {
            if (this.draggedIndex === null || this.draggedIndex === targetIndex) return;
            const list = this.filteredSessions; const dragged = list[this.draggedIndex]; const target = list[targetIndex];
            if (dragged.date < this.todayStr) return;
            const ts = dragged.time; const te = dragged.endTime;
            dragged.time = target.time; dragged.endTime = target.endTime;
            target.time = ts; target.endTime = te;
            await this.saveRaw(dragged); await this.saveRaw(target);
            this.draggedIndex = null; await this.fetchData();
        },

        async saveRaw(w) { await fetch('/api/v1/sessions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...w, mentorId: this.mentorId }) }); },

        scrollDateIntoView() {
            this.$nextTick(() => {
                const btn = this.$refs.dateScroller?.querySelector('[data-date="' + this.sessionForm.date + '"]');
                if (btn) btn.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
            });
        },

        openSessionModal() {
            this.traineeSearch = '';
            this.editingSessionId = null;
            this.originalSessionData = null;
            this.sessionValidationErrors = null;
            const workEnd = this.workEnd || '22:00';
            const [weh, wem] = workEnd.split(':').map(Number);
            const now = new Date();
            const currentMinutes = now.getHours() * 60 + now.getMinutes();
            const workEndMinutes = weh * 60 + wem;
            let defaultDate = this.selectedDate;
            if (currentMinutes >= workEndMinutes) {
                const tomorrow = new Date(now);
                tomorrow.setDate(tomorrow.getDate() + 1);
                defaultDate = tomorrow.toISOString().slice(0, 10);
            }
            this.sessionForm = { title: this.labels.session_title_default || 'Тренування', description: '', date: defaultDate, startTime: null, endTime: null, traineeIds: [], locationId: null };
            this.selectedCoachSlots = [];
            this.buildCoachGrid(defaultDate);
            this.showModal = true;
            this.scrollDateIntoView();
        },

        editSession(w) {
            if (w.date < this.todayStr) return;
            this.traineeSearch = '';
            this.editingSessionId = w.id;
            this.originalSessionData = { date: w.date, time: w.time, endTime: w.endTime, traineeIds: [...(w.traineeIds || [])], title: w.title, description: w.description || '', locationId: w.locationId || null };
            this.sessionValidationErrors = null;
            this.sessionForm = { title: w.title, description: w.description || '', date: w.date, startTime: w.time, endTime: w.endTime || null, traineeIds: [...(w.traineeIds || [])], locationId: w.locationId || null };
            if (w.time && w.endTime) {
                const slots = [];
                let t = this.slotToMin(w.time);
                const end = this.slotToMin(w.endTime);
                while (t < end) { slots.push(t); t += 30; }
                this.selectedCoachSlots = slots;
            } else {
                this.selectedCoachSlots = [];
            }
            this.buildCoachGrid(w.date);
            this.showModal = true;
            this.scrollDateIntoView();
        },

        createFromAvailability(traineeId, date, startTime, endTime) {
            this.activeTab = 'feed';
            this.traineeSearch = '';
            this.editingSessionId = null;
            this.sessionValidationErrors = null;
            this.$nextTick(() => {
                const trainee = this.trainees.find(t => t.id === traineeId);
                const traineeName = trainee ? trainee.name : '';
                this.sessionForm = { title: (this.labels.session_title_default || 'Тренування') + (traineeName ? ' — ' + traineeName : ''), description: '', date: date, startTime: startTime.slice(0,5), endTime: endTime.slice(0,5), traineeIds: [traineeId], locationId: null };
                const slots = [];
                let t = this.slotToMin(startTime);
                const end = this.slotToMin(endTime);
                while (t < end) { slots.push(t); t += 30; }
                this.selectedCoachSlots = slots;
                this.showModal = true;
                this.scrollDateIntoView();
            });
        },

        async saveSession() {
            const { startTime, endTime, date, title, description, traineeIds, locationId } = this.sessionForm;
            if (!startTime || !endTime) return;
            if (date < this.todayStr) return;
            const errors = this.getSessionValidationErrors();
            if (errors.length > 0) { this.sessionValidationErrors = errors; return; }
            this.sessionValidationErrors = null;
            if (this.editingSessionId && this.originalSessionData) {
                const sameDate = date === this.originalSessionData.date;
                const sameTime = startTime === this.originalSessionData.time;
                const sameEndTime = endTime === this.originalSessionData.endTime;
                const sameTrainees = this.originalSessionData.traineeIds &&
                    traineeIds.length === this.originalSessionData.traineeIds.length &&
                    traineeIds.every(id => this.originalSessionData.traineeIds.includes(id));
                if (sameDate && sameTime && sameEndTime && sameTrainees && title === this.originalSessionData.title && description === this.originalSessionData.description && locationId === this.originalSessionData.locationId) {
                    this.showModal = false; return;
                }
            }
            const newTime = startTime;
            if (this.editingSessionId && this.originalSessionData) {
                if (date !== this.originalSessionData.date || newTime !== this.originalSessionData.time) {
                    await fetch(`/api/v1/sessions/${this.editingSessionId}`, { method: 'DELETE' });
                }
            }
            const payload = {
                id: (this.editingSessionId && date === this.originalSessionData?.date && newTime === this.originalSessionData?.time) ? this.editingSessionId : null,
                title, description, date, mentorId: this.mentorId,
                time: newTime, endTime,
                traineeIds, locationId
            };
            await fetch('/api/v1/sessions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
            this.showModal = false; await this.fetchData();
        },

        askDeleteSession() {
            this.confirmData = { show: true, title: this.labels.confirm_delete_title || 'Видалити?', message: this.labels.confirm_delete_session || 'Дію неможливо скасувати.', onConfirm: async () => { await fetch(`/api/v1/sessions/${this.editingSessionId}`, { method: 'DELETE' }); this.showModal = false; await this.fetchData(); } };
        },

        async saveTrainee() {
            const method = this.editingTraineeId ? 'PUT' : 'POST';
            const url = this.editingTraineeId ? `/api/v1/trainees/${this.editingTraineeId}` : '/api/v1/trainees';
            const res = await fetch(url, { method: method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...this.traineeForm, mentorId: this.mentorId }) });
            
            let traineeId = this.editingTraineeId;
            if (!traineeId && res.ok) {
                try {
                    const data = await res.json();
                    traineeId = data.id;
                } catch (e) {}
            }
            
            if (traineeId && this.traineeForm.photoBase64 !== undefined) {
                await fetch(`/api/v1/trainees/${traineeId}/photo`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ photoBase64: this.traineeForm.photoBase64 })
                });
            }
            
            this.traineeForm = { name: '', description: '', photoBase64: null }; this.editingTraineeId = null; this.showCreateForm = false; await this.fetchTrainees();
        },

        openCreateTraineeForm() {
            if (this.showCreateForm && !this.editingTraineeId) {
                this.showCreateForm = false;
                return;
            }
            this.traineeForm = { name: '', description: '', photoBase64: null };
            this.editingTraineeId = null;
            this.showCreateForm = true;
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
            this.activeTab = 'feed';
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
             if (session.confirmationStatus === 'PENDING') {
                 const confirmed = (session.confirmedTraineeIds || '').split(',').filter(Boolean).length;
                 const total = (session.traineeIds || []).length;
                 if (confirmed > 0) return 'bg-yellow-900/15 border-yellow-600/40';
                 return 'bg-yellow-900/10 border-yellow-500/20';
             }
             return base;
         },

         getAgendaCardClass(session) {
             const base = this.isSessionNow(session) ? 'bg-[#1c1c1c] ring-2 ring-red-500/50 shadow-lg shadow-red-500/10' : 'bg-[#1c1c1c] border border-[#333]';
             if (session.confirmationStatus === 'CONFIRMED') return 'bg-green-900/20 border-green-500';
             if (session.confirmationStatus === 'REJECTED') return 'bg-red-900/10 border-red-500/30 opacity-70';
             if (session.confirmationStatus === 'PENDING' && session.createdBy === 'TRAINEE') return 'bg-yellow-900/10 border-yellow-500/30';
             if (session.confirmationStatus === 'PENDING') {
                 const confirmed = (session.confirmedTraineeIds || '').split(',').filter(Boolean).length;
                 const total = (session.traineeIds || []).length;
                 if (confirmed > 0) return 'bg-yellow-900/15 border-yellow-600/40';
                 return 'bg-yellow-900/10 border-yellow-500/20';
             }
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

          async saveTraineeTimezone(trainee) {
              const res = await fetch(`/api/v1/trainees/${trainee.id}/timezone`, {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({ timezone: trainee._editingTz })
              });
              if (res.ok) {
                  trainee.timezone = trainee._editingTz;
              } else {
                  trainee._editingTz = trainee.timezone;
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
        get coachAvailTitle() {
            const titles = { sport:'Доступність тренера', studying:'Доступність вчителя', psychology:'Доступність психолога', other:'Доступність ментора' };
            return titles[this.mentorProfile] || titles.sport;
        },
        get coachIsDayOff() { return this.dayOffs.includes(this.coachAvailDate); },
        get coachAvailHasUnsaved() { return this.coachDirtyDates.size > 0 || this.coachDirtyDayOffs.length > 0; },

        coachAvailHasSlots(ds) { return (this.coachCells[ds] || []).length > 0; },

        coachGetCurCells() { return this.coachCells[this.coachAvailDate] || []; },

        coachCellAt(mm) { return this.coachGetCurCells().find(x => x.mm === mm); },

        coachInSess(mm) {
            return (this.coachSess[this.coachAvailDate] || []).some(s => {
                const [sh,sm] = s.startTime.split(':').map(Number);
                const [eh,em] = (s.endTime || s.startTime).split(':').map(Number);
                return mm >= sh*60+sm && mm < eh*60+em;
            });
        },

        coachAvailSessionAt(mm) {
            return (this.coachSess[this.coachAvailDate] || []).find(s => {
                const [sh,sm] = s.startTime.split(':').map(Number);
                const [eh,em] = (s.endTime || s.startTime).split(':').map(Number);
                return mm >= sh*60+sm && mm < eh*60+em;
            }) || null;
        },

        coachAvailTitleAt(mm) {
            return `${String(Math.floor(mm/60)).padStart(2,'0')}:${String(mm%60).padStart(2,'0')}`;
        },

        coachAvailSel(ds) { this.coachAvailDate = ds; },

        coachBuildGrid() {
            const [sh, sm] = this.workStart.split(':').map(Number);
            const [eh, em] = this.workEnd.split(':').map(Number);
            const startMin = sh * 60 + sm;
            const endMin = eh * 60 + em;
            const arr = [];
            for (let m = startMin; m < endMin; m += 60) {
                const cells = [{ mm: m }, { mm: m + 30 }];
                arr.push({ lbl: `${String(Math.floor(m / 60)).padStart(2, '0')}:00`, cells });
            }
            this.coachAvailGrid = arr;
        },

        async coachAvailTap(mm) {
            const busy = this.coachAvailSessionAt(mm);
            if (busy) { this.coachBusySession = busy; return; }
            const arr = this.coachGetCurCells();
            const idx = arr.findIndex(x => x.mm === mm);
            if (idx >= 0) {
                arr.splice(idx, 1);
            } else {
                arr.push({ mm, locId: this.coachCurLoc ? Number(this.coachCurLoc) : null });
            }
            this.coachCells[this.coachAvailDate] = [...arr];
            this.coachDirtyDates.add(this.coachAvailDate);
        },

        coachAvailCellStyle(mm) {
            if (this.coachIsDayOff) return 'background:#2a1a1a';
            if (this.coachInSess(mm)) return 'background:#dc2626';
            const c = this.coachCellAt(mm);
            if (!c) return 'background:#2a2a2a';
            if (c.locId != null && this.locations.length) {
                const l = this.locations.find(x => Number(x.id) === Number(c.locId));
                if (l && l.color) return `background:${l.color}`;
            }
            return 'background:#3b82f6';
        },

        coachToggleDayOff() {
            const wasDayOff = this.coachIsDayOff;
            if (wasDayOff) {
                this.dayOffs = this.dayOffs.filter(d => d !== this.coachAvailDate);
                this.coachDirtyDayOffs.push({ date: this.coachAvailDate, action: 'remove' });
            } else {
                this.dayOffs.push(this.coachAvailDate);
                this.coachCells[this.coachAvailDate] = [];
                this.coachDirtyDayOffs.push({ date: this.coachAvailDate, action: 'add' });
            }
            this.coachDirtyDates.delete(this.coachAvailDate);
        },

        coachFillAllDay() {
            const [sh, sm] = this.workStart.split(':').map(Number);
            const [eh, em] = this.workEnd.split(':').map(Number);
            const startMin = sh * 60 + sm;
            const endMin = eh * 60 + em;
            const cur = this.coachGetCurCells();
            const freeSlots = [];
            for (let m = startMin; m < endMin; m += 30) {
                if (!this.coachInSess(m)) freeSlots.push(m);
            }
            const allFilled = freeSlots.every(mm => cur.some(c => c.mm === mm));
            if (allFilled) {
                this.coachCells[this.coachAvailDate] = cur.filter(c => this.coachInSess(c.mm));
                this.coachDirtyDates.add(this.coachAvailDate);
            } else {
                const filled = cur.filter(c => this.coachInSess(c.mm));
                for (const mm of freeSlots) {
                    if (!filled.some(c => c.mm === mm)) {
                        filled.push({ mm, locId: this.coachCurLoc ? Number(this.coachCurLoc) : null });
                    }
                }
                this.coachCells[this.coachAvailDate] = filled;
                this.coachDirtyDates.add(this.coachAvailDate);
            }
        },

        async coachSaveAll() {
            if (this.coachSaving) return;
            this.coachSaving = true;
            try {
                for (const { date } of this.coachDirtyDayOffs) {
                    await fetch('/api/v1/coach/day-off', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ date })
                    });
                }
                for (const date of this.coachDirtyDates) {
                    const arr = this.coachCells[date] || [];
                    if (!arr.length) {
                        await fetch(`/api/v1/coach/availability?dates=${date}`, { method: 'DELETE' });
                    } else {
                        const sorted = [...arr].sort((a,b) => a.mm - b.mm);
                        const merged = [];
                        let cs = sorted[0].mm, ce = sorted[0].mm + 30, cl = sorted[0].locId;
                        for (let i = 1; i < sorted.length; i++) {
                            if (sorted[i].mm === ce && sorted[i].locId === cl) {
                                ce = sorted[i].mm + 30;
                            } else {
                                const sh = String(Math.floor(cs / 60)).padStart(2, '0');
                                const sm = String(cs % 60).padStart(2, '0');
                                const eh = String(Math.floor(ce / 60)).padStart(2, '0');
                                const em = String(ce % 60).padStart(2, '0');
                                merged.push({ date, startTime: sh + ':' + sm, endTime: eh + ':' + em, locationId: cl || null });
                                cs = sorted[i].mm; ce = sorted[i].mm + 30; cl = sorted[i].locId;
                            }
                        }
                        const sh = String(Math.floor(cs / 60)).padStart(2, '0');
                        const sm = String(cs % 60).padStart(2, '0');
                        const eh = String(Math.floor(ce / 60)).padStart(2, '0');
                        const em = String(ce % 60).padStart(2, '0');
                        merged.push({ date, startTime: sh + ':' + sm, endTime: eh + ':' + em, locationId: cl || null });
                        await fetch('/api/v1/coach/availability', {
                            method:'POST',
                            headers:{'Content-Type':'application/json'},
                            body:JSON.stringify(merged)
                        });
                    }
                }
                this.coachDirtyDates.clear();
                this.coachDirtyDayOffs = [];
                if (!this.shareToken) await this.mkShareToken();
            } catch(e) { console.error('[coach] saveAll error', e); }
            finally { this.coachSaving = false; }
        },

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
            this.coachBuildGrid();
            const sd = this.coachAvailDays[0].ds;
            const ed = this.coachAvailDays[this.coachAvailDays.length-1].ds;
            try {
                const [ar, sr, dr] = await Promise.all([
                    fetch(`/api/v1/coach/availability?startDate=${sd}&endDate=${ed}`),
                    fetch(`/api/v1/coach/sessions?startDate=${sd}&endDate=${ed}`),
                    fetch(`/api/v1/coach/day-off?startDate=${sd}&endDate=${ed}`)
                ]);
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
                    this.coachCells = g;
                }
                if (sr.ok) {
                    const g = {};
                    for (const i of await sr.json()) {
                        if (!g[i.date]) g[i.date] = [];
                        g[i.date].push(i);
                    }
                    this.coachSess = g;
                }
                if (dr.ok) this.dayOffs = await dr.json();
            } catch(e) {
                console.error('[coach] load error', e);
            }
            this.coachDirtyDates.clear();
            this.coachDirtyDayOffs = [];
            this.coachLoaded = true;
        },

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
            const imgUrl = window.location.origin + '/api/v1/shared/' + this.shareToken + '/image';
            navigator.clipboard.writeText(imgUrl).then(() => {
                this.coachImgCopiedWeek = true;
                setTimeout(() => this.coachImgCopiedWeek = false, 3000);
            }).catch(e => prompt('Скопіюйте посилання на картинку:', imgUrl));
        },

        formatDisplayDate(str) {
            const d = new Date(str); const months = ['січня', 'лютого', 'березня', 'квітня', 'травня', 'червня', 'липня', 'серпня', 'вересня', 'жовтня', 'листопада', 'грудня'];
            const fullDays = ['Понеділок', 'Вівторок', 'Середа', 'Четвер', 'П\'ятниця', 'Субота', 'Неділя'];
            const dayIdx = (d.getDay() + 6) % 7;
            return `${fullDays[dayIdx]}, ${d.getDate()} ${months[d.getMonth()]}`;
        },

        async installPwa() {
            const prompt = this.deferredInstallPrompt || _deferredInstall;
            if (prompt) {
                prompt.prompt();
                const result = await prompt.userChoice;
                this.deferredInstallPrompt = null;
                _deferredInstall = null;
            }
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
