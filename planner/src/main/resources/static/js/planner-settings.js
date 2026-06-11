// planner-settings.js
// Profile settings, PWA install, push notifications, WebSocket, service-worker messages, tour, intro, wisdom.
(function () {
    'use strict';
    window.plannerModules = window.plannerModules || {};

    // ── Profile / Settings Saves ───────────────────────────────────────────────

    Object.assign(window.plannerModules, {

        async saveWorkHours() {
            await fetch('/api/v1/mentor/profile', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({workStart: this.workStart, workEnd: this.workEnd})
            });
        },

        async saveAvailStep() {
            await fetch('/api/v1/mentor/profile', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({availStep: String(this.availStep)})
            });
            this.showRefreshModal = true;
        },

        async saveSessionReminderEnabled() {
            await fetch('/api/v1/mentor/profile', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({sessionReminderEnabled: String(this.sessionReminderEnabled)})
            });
        },

        async saveShareAvailability() {
            await fetch('/api/v1/mentor/profile', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({shareAvailability: String(this.shareAvailability)})
            });
        },

        async saveMultiLocation() {
            await fetch('/api/v1/mentor/profile', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({multiLocation: String(this.multiLocation)})
            });
        },

        async saveSessionConfirmations() {
            if (!this.sessionConfirmations) this.sessionFilter = 'all';
            await fetch('/api/v1/mentor/profile', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({sessionConfirmations: String(this.sessionConfirmations)})
            });
        },

        async saveTelegramIntegration() {
            if (!this.telegramIntegration) {
                this.sessionConfirmations = false;
                this.traineeComm = false;
                this.sessionReminderEnabled = false;
                this.sessionFilter = 'all';
            }
            await fetch('/api/v1/mentor/profile', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    telegramIntegration: String(this.telegramIntegration),
                    sessionConfirmations: String(this.sessionConfirmations),
                    traineeComm: String(this.traineeComm),
                    sessionReminderEnabled: String(this.sessionReminderEnabled)
                })
            });
        },

        async saveTraineeComm() {
            await fetch('/api/v1/mentor/profile', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({traineeComm: String(this.traineeComm)})
            });
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

        async saveTheme(t) {
            if (this.theme === t) return;
            this.theme = t;
            this._applyTheme(t);
            await fetch('/api/v1/mentor/profile', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({theme: t})
            });
            this.toast = { show: true, message: 'Тему збережено' };
            setTimeout(() => { this.toast.show = false; }, 2500);
        },

        _applyTheme(t) {
            document.documentElement.classList.remove('theme-default', 'theme-male', 'theme-female');
            document.documentElement.classList.add('theme-' + t);
            try { localStorage.setItem('cpTheme', t); } catch {}
        },

        // ── Mentor Data ────────────────────────────────────────────────────────

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

        async openMentorTelegramLink() {
            if (this.mentorTg.connectLink && this.mentorTg.connectLink.length > 0) {
                window.open(this.mentorTg.connectLink, '_blank');
                return;
            }
            try {
                const res = await fetch('/api/v1/mentor/telegram/generate-token', { method: 'POST' });
                if (res.ok) {
                    const data = await res.json();
                    if (data.success) {
                        this.mentorTg.connectLink = data.connectLink;
                        if (data.connectLink) {
                            window.open(data.connectLink, '_blank');
                        }
                    }
                }
            } catch (e) {
                console.error('Failed to generate mentor telegram token:', e);
            }
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

        // ── PWA Install ────────────────────────────────────────────────────────

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

        // ── Notifications ──────────────────────────────────────────────────────

        async loadNotifications() {
            try {
                const res = await fetch('/api/v1/notifications');
                if (res.ok) {
                    this.notifications = await res.json();
                    this.unreadCount = this.notifications.filter(n => !n.isRead).length;
                }
            } catch (e) {
                // endpoint may not exist
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
                const url = msg.url || '/';
                const options = {
                    body: body,
                    tag: 'cozy-notification',
                    icon: '/favicon.svg',
                    data: { url }
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

        // ── WebSocket + Service Worker ─────────────────────────────────────────

        connectWebSocket() {
            const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${wsProtocol}//${window.location.host}/api/v1/ws`;
            console.log(`[ws] connecting to ${wsUrl}`);
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
                console.log(`[WS] received: ${event.data}`);
                switch(event.data) {
                    case 'session_changed': console.log('[WS] session_changed -> fetchSessionCounts + fetchData'); this.fetchSessionCounts(); this.fetchData(); break;
                    case 'trainee_changed': console.log('[WS] trainee_changed'); this.fetchTrainees(); this.fetchSessionCounts(); this.fetchData(); break;
                    case 'location_changed': console.log('[WS] location_changed'); this.fetchLocations(); this.fetchSessionCounts(); this.fetchData(); break;
                    case 'availability_changed': this.fetchAvailability(); break;
                    case 'coach_availability_changed': this.fetchDayOffs(); this.loadCoachAvailabilityData(); this.reloadAgendaAvailability(); break;
                    case 'mentor_changed': this.fetchMentorTelegramStatus(); this.fetchTrainees(); break;
                }
            };

            ws.onopen = () => {
                console.log('[ws] connected');
                this.loadNotifications();
            };
            ws.onclose = (ev) => {
                console.log(`[ws] closed code=${ev.code} clean=${ev.wasClean} — reconnecting in 3s`);
                setTimeout(() => this.connectWebSocket(), 3000);
            };
            ws.onerror = (ev) => {
                console.error('[ws] error', ev);
                ws.close();
            };
            this.ws = ws;
        },

        async _handleVisibilityResume() {
            const now = Date.now();
            const hiddenMs = this._lastHiddenAt ? now - this._lastHiddenAt : null;
            const hiddenSec = hiddenMs !== null ? Math.round(hiddenMs / 1000) : '?';
            const wsState = this.ws ? ['CONNECTING','OPEN','CLOSING','CLOSED'][this.ws.readyState] : 'none';
            console.log(`[resume] hiddenMs=${hiddenSec}s, loading=${this.loading}, mentorId=${this.mentorId}, ws=${wsState}, url=${window.location.href}`);

            if (this.loading) {
                console.warn('[resume] still loading on resume — reloading page');
                window.location.reload();
                return;
            }

            // Treat unknown absence (hiddenMs=null, e.g. bfcache pageshow on iOS) as long absence
            const isLongAbsence = hiddenMs === null || hiddenMs > 10 * 60 * 1000;
            if (isLongAbsence) {
                console.log(`[resume] long/unknown absence — reconnecting WS and refreshing data`);
                if (this.ws) {
                    console.log(`[resume] closing ws (readyState=${wsState})`);
                    this.ws.onclose = null;
                    this.ws.close();
                }
                this.connectWebSocket();
                try {
                    console.log('[resume] calling fetchData...');
                    await this.fetchData();
                    console.log('[resume] fetchData done');
                    console.log('[resume] calling loadNotifications...');
                    await this.loadNotifications();
                    console.log('[resume] loadNotifications done');
                } catch (e) {
                    console.error('[resume] fetchData/loadNotifications error:', e);
                }
            } else {
                console.log(`[resume] short absence (${hiddenSec}s) — no refresh needed`);
            }
        },

        async fetchMe() {
            try {
                const res = await this._fetchWithTimeout('/api/v1/me');
                if (res.ok) {
                    const data = await res.json();
                    this.user = data.user || {};
                    this.mentorId = data.mentor?.id;
                    this.mentor.name = data.mentor?.name || 'Cozy Planner';
                    this.mentorProfile = data.mentor?.profile || 'sport';
                    this.workStart = data.mentor?.workStart || '08:00';
                    this.workEnd = data.mentor?.workEnd || '21:00';
                    this.availStep = data.mentor?.availStep || 30;
                    this.timezone = data.mentor?.timezone || 'Europe/Kyiv';
                    this.editingTimezone = this.timezone;
                    this.photoUrl = data.mentor?.photoUrl || null;
                    this.shareToken = data.mentor?.shareToken || null;
                    this.shareUrl = this.shareToken ? window.location.origin + '/shared/' + this.shareToken : '';
                    this.labels = data.labels || {};
                    this.theme = data.mentor?.theme || 'default';
                    this._applyTheme(this.theme);
                    this.sessionReminderEnabled = data.mentor?.sessionReminderEnabled === true;
                    this.shareAvailability = data.mentor?.shareAvailability === true;
                    this.multiLocation = data.mentor?.multiLocation === true;
                    this.sessionConfirmations = data.mentor?.sessionConfirmations === true;
                    this.telegramIntegration = data.mentor?.telegramIntegration === true;
                    this.traineeComm = data.mentor?.traineeComm === true;
                    return { introSeen: data.mentor?.introSeen === true, isDemo: data.mentor?.isDemo === true };
                } else {
                    window.location.href = '/setup';
                    return false;
                }
            } catch {
                window.location.href = '/setup';
                return false;
            }
        },

        listenForSWMessages() {
            if ('serviceWorker' in navigator) {
                navigator.serviceWorker.addEventListener('message', (event) => {
                    if (event.data?.type === 'session_changed') {
                        console.log(`[SW] session_changed action=${event.data.action}, actionType=${event.data.actionType}, sessionId=${event.data.sessionId}, success=${event.data.success}`);
                        this.fetchSessionCounts();
                        this.fetchData();
                        this.loadNotifications();
                    }
                });
            }
        },

        // ── Contact Modal ──────────────────────────────────────────────────────

        openContactModal() {
            this.contactModal = { show: true, message: '', sending: false, sent: false, error: '' };
        },

        async sendContactMessage() {
            if (!this.contactModal.message.trim() || this.contactModal.sending) return;
            this.contactModal.sending = true;
            this.contactModal.error = '';
            try {
                const res = await fetch('/api/v1/contact-developer', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ message: this.contactModal.message.trim() })
                });
                if (res.ok) {
                    this.contactModal.sent = true;
                    this.contactModal.message = '';
                    setTimeout(() => { this.contactModal.show = false; }, 2500);
                } else {
                    this.contactModal.error = 'Помилка надсилання. Спробуйте ще раз.';
                }
            } catch(e) {
                this.contactModal.error = 'Помилка з\'єднання.';
            } finally {
                this.contactModal.sending = false;
            }
        },

        // ── Tour ───────────────────────────────────────────────────────────────

        async startTour() {
            this.showIntro = false;
            this._markIntroSeen();
            this.tourStep = 0;
            this.showTour = true;
            this.selectedDate = this.todayStr;
            this.activeTab = 'feed';
            setTimeout(() => this._updateTourRect(), 100);
            try {
                await fetch('/api/v1/tour/demo', { method: 'POST' });
                this.coachLoaded = false;
                await Promise.all([this.fetchData(), this.fetchTrainees(), this.fetchLocations()]);
            } catch (e) {}
        },

        nextTourStep() {
            if (this.tourStep < this.tourSteps.length - 1) {
                this.tourStep++;
                setTimeout(() => this._updateTourRect(), 50);
            } else {
                this.endTour();
            }
        },

        async endTour() {
            this.showTour = false;
            this._tourRect = null;
            try {
                await fetch('/api/v1/tour/demo', { method: 'DELETE' });
                this.coachLoaded = false;
                await Promise.all([this.fetchData(), this.fetchTrainees(), this.fetchLocations()]);
            } catch (e) {}
        },

        async dismissIntro() {
            this.showIntro = false;
            await this._markIntroSeen();
        },

        async _markIntroSeen() {
            if (this.mentorId && this.mentorId > 0) {
                await fetch('/api/v1/mentor/profile', {
                    method: 'PUT',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({introSeen: 'true'})
                });
            }
        },

        _tourAbove(r, step) {
            const pad = 8, gap = 14, tipH = 190;
            const spaceBelow = window.innerHeight - (r.top + r.height + pad + gap);
            return (step.position === 'top' && r.top - pad - gap >= tipH) || spaceBelow < tipH;
        },

        _updateTourRect() {
            const step = this.tourSteps[this.tourStep];
            if (!step) { this._tourRect = null; return; }
            const doMeasure = () => {
                const el = document.querySelector(step.target);
                if (!el) {
                    this._tourRect = { top: window.innerHeight / 2, left: window.innerWidth / 2, width: 0, height: 0 };
                    return;
                }
                el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                setTimeout(() => {
                    const r = el.getBoundingClientRect();
                    this._tourRect = { top: r.top, left: r.left, width: r.width, height: r.height };
                }, 300);
            };
            if (step.fullCards && this.compactView) {
                this.compactView = false;
                this.collapsedIds = [];
            }
            if (step.detailTrainees && this.traineeCompactView) {
                this.traineeCompactView = false;
                this.traineeExpandedIds = [];
                this._ref++;
            }
            if (step.tab && this.activeTab !== step.tab) {
                if (step.tab === 'coach-availability') { this.coachLoaded = false; this.loadCoachAvailability(); }
                if (step.tab === 'agenda') { this.loadFutureSessions(); }
                this.activeTab = step.tab;
                setTimeout(doMeasure, 380);
            } else {
                doMeasure();
            }
        },

        // ── Wisdom ─────────────────────────────────────────────────────────────

        showWisdom() {
            const pool = typeof APHORISMS_UK !== 'undefined' ? APHORISMS_UK : ['Мудрість починається з подиву.'];
            let pick;
            do { pick = pool[Math.floor(Math.random() * pool.length)]; } while (pick === this.currentWisdom && pool.length > 1);
            this.currentWisdom = pick;
            this.wisdomPopup = true;
        },

        // ── Mentor Form (profile tab) ──────────────────────────────────────────
        // (openMentorForm is intentionally not defined here — it lives inline in the profile tab HTML)

    });
}());
