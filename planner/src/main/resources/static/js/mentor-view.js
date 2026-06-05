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
        activeTab: 'feed', showModal: false, _prevTab: null,
        editingSessionId: null, editingTraineeId: null, editingLocationId: null, draggedIndex: null, showCreateForm: false, showTraineeFormModal: false, showLocationFormModal: false, traineeCompactView: true, traineeExpandedIds: [], _ref: 0,
        selectedDate: _today, todayStr: _today,
        days: [], sessions: [], trainees: [], locations: [], mentorId: null, mentor: { name: '' }, user: {}, labels: {}, mentorProfile: 'sport', theme: 'default',
        sessionReminderEnabled: true,
        bulkAvailTrainees: [], bulkAvailSending: false, bulkAvailResult: null,
        hours: Array.from({length: 24}, (_, i) => i.toString().padStart(2, '0')),
        halfHours: Array.from({length: 48}, (_, i) => `${Math.floor(i/2).toString().padStart(2,'0')}:${i%2===0?'00':'30'}`),
        daySlots: Array.from({length: 29}, (_, i) => `${(7+Math.floor(i/2)).toString().padStart(2,'0')}:${i%2===0?'00':'30'}`),
        get timeSlots15() {
            const ws = this.workStart || '09:00';
            const we = this.workEnd || '21:00';
            const step = this.availStep || 30;
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
        get validStartSlots() {
            return this.computeValidSessionSlots(this.sessionForm.date);
        },
        get validEndSlots() {
            if (!this.sessionForm.startTime) return [];
            return this.computeValidSessionEndSlots(this.sessionForm.date, this.sessionForm.startTime);
        },
        selectedTraineeFilters: [], traineeSearch: '',
        sessionForm: { title: '', description: '', date: '', startTime: null, endTime: null, traineeIds: [], locationId: null },
        traineeForm: { name: '', description: '', photoBase64: null },
        locationForm: { name: '', description: '', color: '#3b82f6', googleMapsUrl: '' },
        confirmData: { show: false, title: '', message: '', confirmText: 'Видалити', onConfirm: () => {} },
        notifyModal: { show: false, traineeId: null, traineeName: '', customMessage: '', dayType: 'tomorrow', targetDate: '' },
        toast: { show: false, message: '' },
        mentorTg: { enabled: false, connected: false, username: '', connectLink: '' },
        inviteUrls: {},
        traineeLinks: {},
        notifyingTrainees: {},
        notifyErrors: {},
        notifySuccess: {},
        _nowTimer: null,
        nowTick: 0,
        pastDaysToShow: 0,
        loading: true,
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
        showIosInstall: false,
        isStandalone: window.matchMedia('(display-mode: standalone)').matches,
        workStart: '09:00',
        workEnd: '21:00',
        availStep: 30,
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
        coachRanges: [],
        coachRangesByDate: {},
        coachSess: {},
        coachSaving: false,
        coachLoaded: false,
        coachDirtyDates: new Set(),
        coachCopied: false,
        coachImgCopiedWeek: false,
        coachSaved: false,
        sessionValidationErrors: null,
        showAvailPopup: false,
        compactView: true,
        expandedIds: [],
        collapsedIds: [],
        sessionFilter: 'all',
        locationFilter: null,
        showRefreshModal: false,
        showIntro: false,
        introSlide: 0,
        showTour: false,
        tourStep: 0,
        _tourRect: null,
        get introSlides() {
            const l = this.labels || {};
            const trainee        = (l.trainee        || 'клієнт').toLowerCase();
            const trainees       = (l.trainees       || 'клієнти').toLowerCase();
            const trainees_instr = (l.trainees_instr || trainees);
            const trainees_acc   = (l.trainees_acc   || trainees);
            const session        = (l.session        || 'зустріч').toLowerCase();
            const session_gen    = (l.session_gen    || session);
            const sessions       = (l.sessions       || 'зустрічі').toLowerCase();
            const sessions_gen   = (l.sessions_gen   || sessions);
            const location       = (l.location       || 'локація').toLowerCase();
            const locations      = (l.locations      || 'локації').toLowerCase();
            return [
                {icon: '📅', title: 'Ласкаво просимо до Cozy Planner',        body: `Цей додаток допоможе тобі організувати ${sessions} з ${trainees_instr}. Розклад, підтвердження і сповіщення — все в одному місці.`},
                {icon: '👥', title: `${l.trainees || 'Клієнти'} та їх часові зони`, body: `Додавай ${trainees_acc} і призначай їм ${sessions}. Кожному можна вказати свій часовий пояс — зручно, якщо ${trainee} живе в іншій країні: він бачить час у своєму форматі.`},
                {icon: '📍', title: `${l.locations || 'Локації'} з кольорами`, body: `Створюй ${locations} для ${sessions_gen}. Кожна ${location} має свій колір, який видно прямо в розкладі для швидкого орієнтування.`},
                {icon: '🕐', title: 'Доступність та постійне посилання',       body: `Відмічай дні та час, коли ти вільний. Поділись постійним посиланням на свою доступність — ${trainees} побачать твій вільний час без реєстрації.`},
                {icon: '📲', title: `${l.trainee || 'Клієнт'} може забронювати сам`, body: `Якщо ти встановив доступність, ${trainee} може самостійно зарезервувати зручний час зі своєї сторінки. Ти отримаєш сповіщення і підтвердиш або відхилиш.`},
                {icon: '✅', title: 'Підтвердження та Telegram',                body: `Підтверджуй ${sessions} сам або нехай ${trainee} зробить це зі свого боку. Підключи Telegram — і ти, і ${trainees} отримаєте нагадування.`},
                {icon: '⏱️', title: 'Тільки реально вільний час',              body: `При створенні або редагуванні ${session_gen} показуються лише вільні слоти — ті, що не перетинаються з вже запланованими і вписуються у твою доступність.`},
                {icon: '🗺️', title: 'Показати тур по інтерфейсу?',            body: 'Хочеш, щоб ми провели тебе по основних елементах додатку? Займе менше хвилини — ти побачиш, де що знаходиться і як цим користуватись.'}
            ];
        },
        get tourSteps() {
            const l = this.labels || {};
            const trainee        = (l.trainee        || 'клієнт').toLowerCase();
            const trainees       = (l.trainees       || 'клієнти').toLowerCase();
            const trainees_instr = (l.trainees_instr || trainees);
            const trainee_gen    = (l.trainee_gen    || trainee);
            const trainee_dat    = (l.trainee_dat    || trainee);
            const manage         = l.manage_trainees || 'Клієнти';
            const session        = (l.session        || 'зустріч').toLowerCase();
            const session_gen    = (l.session_gen    || session);
            const sessions       = (l.sessions       || 'зустрічі').toLowerCase();
            const sessions_gen   = (l.sessions_gen   || sessions);
            const location       = (l.location       || 'локація').toLowerCase();
            const locations      = (l.locations      || 'локації').toLowerCase();
            const new_session    = l.new_session     || 'Нова зустріч';
            return [
                { target: '[data-tour="profile"]',          tab: null,                 title: 'Твій профіль',                  body: `Налаштування профілю: фото, тема, крок часу (15/30/60 хв) для вікон доступності, а також твій особистий Telegram для сповіщень.`,                                                                                                                                                              position: 'bottom' },
                { target: '[data-tour="view-toggle"]',      tab: 'feed',               title: 'Вигляд розкладу',               body: `"День" — детальний список ${sessions} на обрану дату. "План" — хронологічний планер усіх майбутніх ${sessions}.`,                                                                                                                                                                          position: 'bottom' },
                { target: '#calendar-container',            tab: 'feed',               title: 'Календар',                      body: `Вибирай дату. Числа під днем — кількість ${sessions_gen}. Поточна дата завжди виділена рамкою.`,                                                                                                                                                                                            position: 'bottom' },
                { target: '[data-tour="add-session"]',      tab: 'feed',               title: new_session,                     body: `При створенні ${session_gen} слоти, що збігаються із запланованими або виходять за межі доступності, не відображаються — тільки реально вільний час.`,                                                                                                                                      position: 'bottom' },
                { target: '[data-tour="add-session"]',      tab: 'feed',               title: `🔁 Повторюваний ${session_gen}`, body: `У формі створення є прапорець <b>«Повторювати щотижня»</b> — він автоматично створює 8 ${sessions_gen} через однаковий часовий слот кожного тижня. Зручно для постійного розкладу з регулярними ${trainees_instr}.`,                                                                     position: 'bottom' },
                { target: '[data-tour="day-filter"]',       tab: 'feed',               title: 'Фільтри та режим відображення', body: `Фільтруй по ${location}, статусу (всі / підтверджені / очікують). Перемикай між компактним і детальним режимами карток.`,                                                                                                                                                                   position: 'bottom' },
                { target: '[data-session-id]',              tab: 'feed',               title: 'Статус підтвердження',          body: `Ім'я ${trainee_gen} у картці одразу показує статус: зелений — підтверджено, червоний — відхилено, сірий — очікує. Видно без відкривання картки.`,                                                                                                 fullCards: true,   position: 'bottom' },
                { target: '[data-tour="gcal"]',             tab: 'feed',               title: 'Експорт в Google Calendar',     body: `Підтверджений ${session_gen} можна одразу додати до Google Calendar — кнопка з'являється автоматично після підтвердження.`,                                                                                                                        fullCards: true,   position: 'bottom' },
                { target: '[data-tour="copy-session"]',     tab: 'feed',               title: `Копіювати ${session_gen}`,      body: `Копіює ${session_gen} із тими самими параметрами. Залишиться лише змінити дату і час — зручно для регулярних ${sessions_gen}.`,                                                                                                                   fullCards: true,   position: 'bottom' },
                { target: '[data-tour="history"]',          tab: 'feed',               title: 'Історія',                       body: `Кнопка 🕐 відкриває історію минулих ${sessions_gen}. Можна скопіювати будь-який минулий ${session_gen} і перепризначити на нову дату.`,                                                                                                                                      position: 'bottom' },
                { target: '[data-tour="trainees"]',         tab: 'feed',               title: manage,                          body: `${manage}: тут додаєш нових, редагуєш профілі, бачиш їх доступність і можеш вручну надіслати ${trainee_dat} запит на доступність через Telegram.`,                                                                                                                                         position: 'bottom' },
                { target: '[data-tour="trainee-site-btn"]', tab: 'trainees', detailTrainees: true,           title: `Сайт ${trainee_gen}`,           body: `Натисни — посилання скопіюється. Надішли ${trainee_dat}: він відкриє свою сторінку, де може бачити розклад, відмічати свою доступність і самостійно резервувати ${sessions}.`,                                                                                                             position: 'bottom' },
                { target: '[data-tour="trainee-actions"]',  tab: 'trainees', detailTrainees: true,           title: 'Telegram',                      body: `${l.trainee || 'Клієнт'} підключає Telegram зі своєї сторінки або ти копіюєш посилання тут і надсилаєш напряму. Якщо ${trainee} підключений — можеш попросити його відмітити свою доступність прямо з цього меню.`,                                                                       position: 'bottom' },
                { target: '[data-tour="locations-btn"]',    tab: 'feed',               title: l.locations || 'Локації',        body: `${l.locations || 'Місця'} для ${sessions_gen} з власними кольорами — одразу видно у розкладі. Додавай Google Maps посилання і ділись ним з новими ${trainees_instr}.`,                                                                                                                     position: 'bottom' },
                { target: '[data-tour="locations-list"]',   tab: 'locations',          title: `Список ${locations}`,           body: `Кожна ${location} має колір і може мати Google Maps посилання. Скопіюй посилання і поділись напряму з ${trainees_instr}.`,                                                                                                                                                                  position: 'top'    },
                { target: '[data-tour="availability"]',     tab: 'feed',               title: 'Моя доступність',               body: `Відмічай конкретні часові інтервали по ${locations} — де і коли ти вільний. ${l.trainees || 'Клієнти'} бачать це і можуть самостійно бронювати вільний час.`,                                                                                                                              position: 'bottom' },
                { target: '[data-tour="avail-intervals"]',  tab: 'coach-availability', title: `Інтервали по ${locations}`,     body: `Додавай вікна доступності з прив'язкою до ${location}: "15:00–18:00 у Залі А". Кілька інтервалів на один день — без проблем.`,                                                                                                                                                             position: 'top'    },
                { target: '[data-tour="avail-share"]',      tab: 'coach-availability', title: 'Постійне посилання',            body: `Ділись з ${trainees_instr} або у соцмережах. Будь-які зміни — нова ${session_gen}, нова доступність — відображаються там одразу. Посилання завжди актуальне.`,                                                                                                                              position: 'top'    },
                { target: '[data-tour="no-target"]',        tab: null,                 title: '🎉 Усе готово!',                body: `Ти познайомився з основними можливостями. Бажаємо приємної роботи — нехай кожен ${session_gen} буде запланований вчасно і без зайвого клопоту!`,                                                                                                                                           position: 'bottom' },
            ];
        },
        _tourAbove(r, step) {
            const pad = 8, gap = 14, tipH = 190;
            const spaceBelow = window.innerHeight - (r.top + r.height + pad + gap);
            return (step.position === 'top' && r.top - pad - gap >= tipH) || spaceBelow < tipH;
        },
        get tourHighlightStyle() {
            if (!this._tourRect || this._tourRect.width === 0) return 'display:none';
            const r = this._tourRect;
            const pad = 8;
            return `top:${r.top - pad}px; left:${r.left - pad}px; width:${r.width + pad * 2}px; height:${r.height + pad * 2}px;`;
        },
        get tourTooltipStyle() {
            if (!this._tourRect) return 'display:none';
            const r = this._tourRect;
            const tipW = 288, tipH = 190;
            if (r.width === 0) {
                return `top:${Math.max(12, (window.innerHeight - tipH) / 2)}px; left:${Math.max(12, (window.innerWidth - tipW) / 2)}px; width:${tipW}px;`;
            }
            const step = this.tourSteps[this.tourStep] || {};
            const pad = 8, gap = 14;
            const cx = r.left + r.width / 2;
            const left = Math.max(12, Math.min(cx - tipW / 2, window.innerWidth - tipW - 12));
            const spaceBelow = window.innerHeight - (r.top + r.height + pad + gap);
            const isAbove = this._tourAbove(r, step);
            const top = isAbove ? Math.max(12, r.top - pad - gap - tipH) : r.top + r.height + pad + gap;
            return `top:${top}px; left:${left}px; width:${tipW}px;`;
        },
        get tourArrowStyle() {
            if (!this._tourRect || this._tourRect.width === 0) return 'display:none';
            const r = this._tourRect;
            const step = this.tourSteps[this.tourStep] || {};
            const pad = 8, gap = 14, tipW = 288, tipH = 190;
            const cx = r.left + r.width / 2;
            const tipLeft = Math.max(12, Math.min(cx - tipW / 2, window.innerWidth - tipW - 12));
            const arrowX = Math.max(20, Math.min(cx - tipLeft - 6, tipW - 32));
            const isAbove = this._tourAbove(r, step);
            if (isAbove) {
                return `left:${arrowX}px; bottom:-7px; width:12px; height:12px; background:#1c1c1c; transform:rotate(45deg); border-bottom:1px solid #444; border-right:1px solid #444; border-top:none; border-left:none;`;
            } else {
                return `left:${arrowX}px; top:-7px; width:12px; height:12px; background:#1c1c1c; transform:rotate(45deg); border-top:1px solid #444; border-left:1px solid #444; border-bottom:none; border-right:none;`;
            }
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
        async dismissIntro() {
            this.showIntro = false;
            await this._markIntroSeen();
        },
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
                this.activeTab = step.tab;
                setTimeout(doMeasure, 380);
            } else {
                doMeasure();
            }
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
        async init() {
            console.log('init() starting...');
            this.initAudioContext();
            if (_deferredInstall) this.deferredInstallPrompt = _deferredInstall;
            try {
                const me = await this.fetchMe();
                console.log('fetchMe result:', me, 'mentorId:', this.mentorId);
                if (!me) return;
                const _introSeen = me.introSeen;
                const _isDemo = me.isDemo;
                
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

                await this.loadCoachAvailabilityData();
                console.log('loadCoachAvailabilityData done');

                await this.fetchMentorTelegramStatus();
                console.log('fetchMentorTelegramStatus done');

                await this.loadNotifications();
                this.requestNotificationPermission();
                this.connectWebSocket();
                this.listenForSWMessages();
                this.registerPush();
                setInterval(() => this.loadNotifications(), 30000);
                this.initTouchDrag();
                setTimeout(() => this.scrollToToday(), 200);
                this._nowTimer = setInterval(() => { this.nowTick++; }, 1000);
                console.log('init() complete!');
                if (!_introSeen && !_isDemo) this.showIntro = true;

                this.$watch('showModal', (val) => {
                    if (val) history.pushState({modal: true}, '');
                });
                window.addEventListener('popstate', () => {
                    if (this.showModal) this.closeModal();
                });
            } catch (e) {
                console.error('init() error:', e);
            } finally {
                this.loading = false;
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
                console.log(`[WS] received: ${event.data}`);
                switch(event.data) {
                    case 'session_changed': console.log('[WS] session_changed -> fetchSessionCounts + fetchData'); this.fetchSessionCounts(); this.fetchData(); break;
                    case 'trainee_changed': console.log('[WS] trainee_changed'); this.fetchTrainees(); this.fetchSessionCounts(); this.fetchData(); break;
                    case 'location_changed': console.log('[WS] location_changed'); this.fetchLocations(); this.fetchSessionCounts(); this.fetchData(); break;
                    case 'availability_changed': this.fetchAvailability(); break;
                    case 'coach_availability_changed': this.fetchDayOffs(); this.loadCoachAvailabilityData(); break;
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

        toggleCompactView() {
            this.compactView = !this.compactView;
            if (this.compactView) {
                this.expandedIds = [];
            } else {
                this.collapsedIds = [];
            }
        },

        toggleConfirmedFilter() {
            this.showConfirmedOnly = !this.showConfirmedOnly;
        },

        toggleExpand(id) {
            const idx = this.expandedIds.indexOf(id);
            if (idx >= 0) {
                this.expandedIds.splice(idx, 1);
            } else {
                this.expandedIds.push(id);
            }
        },

        toggleCollapse(id) {
            const idx = this.collapsedIds.indexOf(id);
            if (idx >= 0) {
                this.collapsedIds.splice(idx, 1);
            } else {
                this.collapsedIds.push(id);
            }
        },

        isExpanded(id) {
            return this.expandedIds.indexOf(id) >= 0;
        },

        isCollapsed(id) {
            return this.collapsedIds.indexOf(id) >= 0;
        },

        toggleTraineeExpand(id) {
            const i = this.traineeExpandedIds.indexOf(id);
            if (i >= 0) {
                this.traineeExpandedIds.splice(i, 1);
            } else {
                this.traineeExpandedIds.push(id);
            }
            this._ref++;
        },

        toggleTraineeView() {
            this.traineeCompactView = !this.traineeCompactView;
            this.traineeExpandedIds = [];
            this._ref++;
        },

        toggleConfirmedFilter() {
            this.showConfirmedOnly = !this.showConfirmedOnly;
        },

        toggleExpand(id) {
            const idx = this.expandedIds.indexOf(id);
            if (idx >= 0) {
                this.expandedIds.splice(idx, 1);
            } else {
                this.expandedIds.push(id);
            }
        },

        isExpanded(id) {
            return this.expandedIds.includes(id);
        },

        copySession(w) {
            this.editingSessionId = null;
            this.originalSessionData = null;
            let date = w.date;
            if (date < this.todayStr) date = this.todayStr;
            let st = w.time, et = w.endTime || this.nextSlot(w.time);
            if (date === this.todayStr && new Date(`${date}T${st}`) <= new Date()) {
                const dur = this.slotToMin(et) - this.slotToMin(st);
                st = this.getNearestSlot();
                const em = this.slotToMin(st) + Math.max(dur, 30);
                const eh = Math.floor(em / 60) % 24;
                et = `${eh.toString().padStart(2, '0')}:${em % 60 === 0 ? '00' : '30'}`;
            }
            this.sessionForm = {
                title: w.title,
                description: w.description || '',
                date,
                startTime: st,
                endTime: et,
                traineeIds: [...(w.traineeIds || [])],
                locationId: w.locationId || null,
                recurring: false
            };
            this.showModal = true;
        },

        copySessionById(id) {
            const w = this.pastSessions.find(s => s.id === id) || this.sessions.find(s => s.id === id);
            if (!w) return;
            this.copySession(w);
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
                locationId: session.locationId || null,
                recurring: false
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
                if (!drag) return;
                const t = e.touches[0];
                const dx = t.clientX - drag.startX;
                if (dx >= -20 && dx <= 20) return;
                e.preventDefault();
                if (!drag.ghost) {
                    const g = drag.card.cloneNode(true);
                    g.style.position = 'fixed';
                    g.style.width = drag.card.offsetWidth + 'px';
                    g.style.pointerEvents = 'none';
                    g.style.zIndex = '9999';
                    g.style.transform = 'scale(0.95) rotate(1deg)';
                    g.style.transition = 'none';
                    const overlay = document.createElement('div');
                    overlay.style.cssText = 'position:absolute;inset:0;display:flex;align-items:center;border-radius:24px;opacity:0;pointer-events:none;z-index:20;font-size:15px;font-weight:800;color:#fff;transition:none';
                    if (dx < 0) {
                        overlay.style.background = 'linear-gradient(90deg,rgba(220,38,38,0.93),rgba(220,38,38,0.5) 55%,transparent)';
                        overlay.style.justifyContent = 'flex-start';
                        overlay.style.paddingLeft = '20px';
                        overlay.innerHTML = '<svg style="width:22px;height:22px;margin-right:8px;flex-shrink:0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg>Видалити';
                    } else {
                        overlay.style.background = 'linear-gradient(270deg,rgba(34,197,94,0.93),rgba(34,197,94,0.5) 55%,transparent)';
                        overlay.style.justifyContent = 'flex-end';
                        overlay.style.paddingRight = '20px';
                        overlay.innerHTML = 'Копіювати<svg style="width:22px;height:22px;margin-left:8px;flex-shrink:0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"/></svg>';
                    }
                    g.appendChild(overlay);
                    document.body.appendChild(g);
                    drag.ghost = g;
                    drag._overlay = overlay;
                }
                drag.ghost.style.left = (t.clientX - drag.ghost.offsetWidth / 2) + 'px';
                drag.ghost.style.top = (t.clientY - 30) + 'px';
                const intensity = Math.min(Math.abs(dx) / 120, 1);
                drag._overlay.style.opacity = intensity;
            };

            const onEnd = async (e) => {
                clearTimeout(timer);
                if (!drag) return;
                const dx = e.changedTouches[0].clientX - drag.startX;
                if (dx < -80) {
                    if (drag.ghost) drag.ghost.remove();
                    drag.ghost = null;
                    this.touchJustDragged = true;
                    setTimeout(() => { this.touchJustDragged = false; }, 400);
                    this.editingSessionId = drag.session.id;
                    this.confirmData = {
                        show: true,
                        title: this.labels.confirm_delete_title || 'Видалити?',
                        message: this.labels.confirm_delete_session || 'Дію неможливо скасувати.',
                        onConfirm: async () => {
                            await fetch(`/api/v1/sessions/${this.editingSessionId}`, { method: 'DELETE' });
                            this.editingSessionId = null;
                            await this.fetchData();
                        }
                    };
                } else if (dx > 80) {
                    if (drag.ghost) drag.ghost.remove();
                    drag.ghost = null;
                    this.touchJustDragged = true;
                    setTimeout(() => { this.touchJustDragged = false; }, 400);
                    this.copySession(drag.session);
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
                    this.availStep = data.mentor?.availStep || 30;
                    this.timezone = data.mentor?.timezone || 'Europe/Kyiv';
                    this.editingTimezone = this.timezone;
                    this.photoUrl = data.mentor?.photoUrl || null;
                    this.shareToken = data.mentor?.shareToken || null;
                    this.shareUrl = this.shareToken ? window.location.origin + '/shared/' + this.shareToken : '';
                    this.labels = data.labels || {};
                    this.theme = data.mentor?.theme || 'default';
                    this._applyTheme(this.theme);
                    this.sessionReminderEnabled = data.mentor?.sessionReminderEnabled !== false;
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

        get filteredSessions() {
            let result = this.sessions.filter(w => w.date === this.selectedDate);
            if (this.locationFilter) result = result.filter(w => w.locationId == this.locationFilter);
            if (this.sessionFilter === 'all' || (!this.sessionFilter)) { return result; }
            if (this.sessionFilter === 'confirmed') result = result.filter(w => w.confirmationStatus === 'CONFIRMED' || (w.traineeIds && w.traineeIds.some(id => this.getTraineeConfirmStatus(id, w) === 'confirmed')));
            if (this.sessionFilter === 'pending') result = result.filter(w => w.confirmationStatus !== 'REJECTED' && w.traineeIds && w.traineeIds.every(id => this.getTraineeConfirmStatus(id, w) === 'none'));
            return result;
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
            const loadedList = Object.keys(this.loadedDates).sort();
            let filtered = this.sessions.filter(w => loadedList.includes(w.date));
            if (this.locationFilter) filtered = filtered.filter(w => w.locationId == this.locationFilter);
            if (this.sessionFilter !== 'all' && this.sessionFilter) {
                if (this.sessionFilter === 'confirmed') filtered = filtered.filter(w => w.confirmationStatus === 'CONFIRMED' || (w.traineeIds && w.traineeIds.some(id => this.getTraineeConfirmStatus(id, w) === 'confirmed')));
                if (this.sessionFilter === 'pending') filtered = filtered.filter(w => w.confirmationStatus !== 'REJECTED' && w.traineeIds && w.traineeIds.every(id => this.getTraineeConfirmStatus(id, w) === 'none'));
            }
            if (this.selectedTraineeFilters.length > 0) filtered = filtered.filter(w => w.traineeIds.some(id => this.selectedTraineeFilters.includes(id)));
            const groups = filtered.reduce((acc, w) => { if (!acc[w.date]) acc[w.date] = []; acc[w.date].push(w); return acc; }, {});
            const result = loadedList.map(date => ({ date, items: groups[date] || [] })).filter(g => g.items.length > 0);
            console.log(`[agendaGroups] loadedDates=${loadedList.length} days, grouped dates=${Object.keys(groups).length}, result dates with items=${result.length}`);
            return result;
        },

        get hasMoreAgenda() {
            return this.hasMoreDays;
        },

        async loadFutureSessions() {
            this.agendaReady = false;
            const today = this.todayStr;
            this.loadedDates = {};
            for (let i = 0; i < 6; i++) {
                this.loadedDates[addDays(today, i)] = true;
            }
            const end = addDays(today, 5);
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
            this.loadingMore = true;
            await Promise.all([
                this.loadNextDays(7),
                this.loadPrevDays(7)
            ]);
            this.loadingMore = false;
        },

        get hasMoreDays() {
            const allDays = this.days;
            const loaded = Object.keys(this.loadedDates);
            return loaded.length < allDays.length;
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
            return Object.keys(groups).sort().reverse().map(date => ({ date, items: groups[date].sort((a, b) => a.time.localeCompare(b.time)) }));
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
            const daysBack = this._pastSessionPage === 0 ? 3 : 7;
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

        async fetchSessionCounts() {
            if (!this.mentorId) return;
            console.log(`[fetchSessionCounts] calling... mentorId=${this.mentorId}`);
            const startDate = this.days[0].dateStr;
            const endDate = this.days[this.days.length - 1].dateStr;
            const res = await fetch(`/api/v1/sessions/counts?mentorId=${this.mentorId}&startDate=${startDate}&endDate=${endDate}`);
            if (res.ok) {
                this.sessionCounts = await res.json();
                const count = Object.keys(this.sessionCounts).length;
                console.log(`[fetchSessionCounts] received ${count} dates, sample:`, JSON.stringify(Object.fromEntries(Object.entries(this.sessionCounts).slice(0, 5))));
            } else {
                console.warn(`[fetchSessionCounts] HTTP ${res.status} for mentorId=${this.mentorId}`);
            }
        },

        async fetchData() {
            if (this._fetchPromise) {
                console.log('[fetchData] concurrent call detected, waiting for in-progress fetch');
                await this._fetchPromise;
            }
            this._fetchPromise = this._doFetch();
            try {
                await this._fetchPromise;
            } finally {
                this._fetchPromise = null;
            }
        },

        async _doFetch() {
            const today = this.todayStr;
            if (!this.mentorId) { console.warn('[agenda] no mentorId'); this.agendaReady = true; return; }
            console.log(`[fetchData] calling... selectedDate=${this.selectedDate}, sessions before=${this.sessions.length}`);
            this.loadedDates = {};
            const pastStart = addDays(today, -3);
            for (let i = 0; i < 6; i++) {
                this.loadedDates[addDays(pastStart, i)] = true;
            }
            const end = addDays(today, 2);
            try {
                const res = await fetch(`/api/v1/sessions?mentorId=${this.mentorId}&startDate=${pastStart}&endDate=${end}`);
                if (res.ok) {
                    this.sessions = (await res.json()).sort((a, b) => a.time.localeCompare(b.time));
                    console.log(`[fetchData] received ${this.sessions.length} sessions for ${pastStart}..${end}`);
                    for (const w of this.sessions) {
                        w.color = this.getLocationColor(w.locationId);
                    }
                } else {
                    console.warn(`[fetchData] HTTP ${res.status} for mentorId=${this.mentorId}`);
                }
            } catch (e) {
                console.error('[agenda] fetch error:', e);
            }
            if (this.selectedDate && !this.loadedDates[this.selectedDate]) {
                console.log(`[fetchData] selectedDate ${this.selectedDate} not in loadedDates, calling loadDaySessions`);
                await this.loadDaySessions(this.selectedDate);
            } else {
                console.log(`[fetchData] selectedDate ${this.selectedDate} in loadedDates: ${!!this.loadedDates[this.selectedDate]}, sessions for date: ${this.sessions.filter(s => s.date === this.selectedDate).length}`);
            }
            await this.loadCoachAvailabilityData();
            this.agendaReady = true;
        },

        async loadDaySessions(dateStr) {
            if (!this.mentorId || this.loadedDates[dateStr]) { console.log(`[loadDaySessions] skipped for ${dateStr}: mentorId=${!!this.mentorId}, alreadyLoaded=${!!this.loadedDates[dateStr]}`); return; }
            console.log(`[loadDaySessions] loading ${dateStr}, sessions before=${this.sessions.length}`);
            this.loading = true;
            try {
                const res = await fetch(`/api/v1/sessions?mentorId=${this.mentorId}&startDate=${dateStr}&endDate=${dateStr}`);
                if (res.ok) {
                    const daySessions = (await res.json()).sort((a, b) => a.time.localeCompare(b.time));
                    console.log(`[loadDaySessions] received ${daySessions.length} sessions for ${dateStr}`);
                    for (const w of daySessions) {
                        w.color = this.getLocationColor(w.locationId);
                    }
                    const removed = this.sessions.filter(s => s.date !== dateStr).length;
                    this.sessions = [...this.sessions.filter(s => s.date !== dateStr), ...daySessions];
                    this.loadedDates[dateStr] = true;
                    console.log(`[loadDaySessions] sessions after=${this.sessions.length}, removed ${removed} for other dates, kept ${this.sessions.length - removed}, date ${dateStr} now has ${daySessions.length}`);
                } else {
                    console.warn(`[loadDaySessions] HTTP ${res.status} for ${dateStr}`);
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
            } catch (e) {
                console.error('[agenda] loadNextDays error:', e);
            }
        },

        async loadPrevDays(n) {
            const loaded = Object.keys(this.loadedDates).sort();
            const firstLoaded = loaded[0];
            if (!firstLoaded) return;
            const startDate = addDays(firstLoaded, -n);
            const endDate = addDays(firstLoaded, -1);
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
            } catch (e) {
                console.error('[agenda] loadPrevDays error:', e);
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
            if (dayType === 'today') {
                body.targetDate = localDateStr();
            } else if (dayType === 'specific_day' && targetDate) {
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

        toggleBulkAvailTrainee(id) {
            const idx = this.bulkAvailTrainees.indexOf(id);
            if (idx >= 0) this.bulkAvailTrainees.splice(idx, 1);
            else this.bulkAvailTrainees.push(id);
        },

        async bulkRequestAvailability() {
            if (!this.bulkAvailTrainees.length) return;
            this.bulkAvailSending = true;
            this.bulkAvailResult = null;
            try {
                const res = await fetch('/api/v1/trainees/bulk-notify-availability', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({ traineeIds: this.bulkAvailTrainees, dayType: 'tomorrow' })
                });
                const data = await res.json();
                this.bulkAvailResult = data;
                this.bulkAvailTrainees = [];
            } catch(e) {
                this.bulkAvailResult = { success: false };
            } finally {
                this.bulkAvailSending = false;
            }
        },

        async saveTimezone() {
            await fetch('/api/v1/mentor/profile', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({timezone: this.editingTimezone})
            });
            this.timezone = this.editingTimezone;
        },

        _applyTheme(t) {
            document.documentElement.classList.remove('theme-default', 'theme-male', 'theme-female');
            document.documentElement.classList.add('theme-' + t);
            try { localStorage.setItem('cpTheme', t); } catch {}
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
            if (slots.some(s => s.startTime === 'all_day')) return [{startTime: 'all_day', endTime: 'all_day'}];
            if (dateStr !== this.todayStr) return slots;
            const now = new Date();
            const currentMin = now.getHours() * 60 + now.getMinutes();
            return slots.filter(s => this.slotToMin(s.endTime) > currentMin);
        },

        getAvailLabel(slots) {
            if (!slots || slots.length === 0) return '';
            return slots.map(s => s.startTime === 'all_day' ? 'Весь день' : s.startTime.slice(0,5) + '-' + s.endTime.slice(0,5)).join(', ');
        },

        toggleTraineeSelection(id) {
            const idx = this.sessionForm.traineeIds.indexOf(id);
            if (idx > -1) {
                this.sessionForm.traineeIds = this.sessionForm.traineeIds.filter(i => i !== id);
            } else {
                this.sessionForm.traineeIds = [...this.sessionForm.traineeIds, id];
            }
            this.updateSessionTitleFromTrainees();
            this.$nextTick(() => {
                const validStarts = this.validStartSlots;
                if (this.sessionForm.startTime && !validStarts.includes(this.sessionForm.startTime)) {
                    this.sessionForm.startTime = null;
                    this.sessionForm.endTime = null;
                } else if (this.sessionForm.startTime) {
                    this.onSessionStartChange();
                }
            });
        },
        getTraineeName(id) { return (this.trainees.find(at => at.id == id)?.name) || 'Unknown'; },
        isWeekend(dateStr) { const d = new Date(dateStr + 'T12:00:00'); const day = d.getDay(); return day === 0 || day === 6; },
        getLocationName(id) { return this.locations.find(l => l.id == id)?.name || ''; },
        getLocationColor(id) { return this.locations.find(l => l.id == id)?.color || '#3b82f6'; },
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
            const dz = this.timezone;
            const dayStart = this.toUtcMin('00:00', session.date, dz);
            if (nowUtc < dayStart || nowUtc >= dayStart + 1440) return false;
            const start = this.toUtcMin(session.time, session.date, dz);
            const end = session.endTime ? this.toUtcMin(session.endTime, session.date, dz) : start + 60;
            return nowUtc >= start && nowUtc < end;
        },
        _agendaSessionsForDate(date) {
            const result = this.sessions.filter(s => s.date === date);
            console.log(`[agenda] sessionsForDate(${date}) → ${result.length} sessions (total ${this.sessions.length})`);
            return result;
        },
        _formatCountdown(targetUtc) {
            const diffMs = targetUtc * 60000 - Date.now();
            if (diffMs <= 0) return '';
            const h = Math.floor(diffMs / 3600000);
            const m = Math.floor((diffMs % 3600000) / 60000);
            const s = Math.floor((diffMs % 60000) / 1000);
            return `● ${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
        },
        showNowLineBefore(session) {
            try {
                if (!session.time || !session.date) return false;
                this.nowTick;
                const nowUtc = Date.now() / 60000;
                const dz = this.timezone;
                const dayStart = this.toUtcMin('00:00', session.date, dz);
                if (nowUtc < dayStart || nowUtc >= dayStart + 1440) return false;
                const sessionStartUtc = this.toUtcMin(session.time, session.date, dz);
                if (nowUtc >= sessionStartUtc) return false;
                const allSessions = this._agendaSessionsForDate(session.date);
                const hasPrev = allSessions
                    .filter(s => s.id !== session.id)
                    .some(s => this.toUtcMin(s.time, s.date, dz) < sessionStartUtc);
                return !hasPrev;
            } catch (e) {
                console.error(`[nowline-before] ERROR session ${session.id}:`, e);
                return false;
            }
        },
        showNowLineAfter(session, dateOrArray) {
            try {
                if (!session.time || !session.date) return false;
                this.nowTick;
                const nowUtc = Date.now() / 60000;
                const dz = this.timezone;
                const dayStart = this.toUtcMin('00:00', session.date, dz);
                if (nowUtc < dayStart || nowUtc >= dayStart + 1440) return false;
                const sessionStartUtc = this.toUtcMin(session.time, session.date, dz);
                const end = session.endTime ? this.toUtcMin(session.endTime, session.date, dz) : sessionStartUtc + 60;
                if (nowUtc < end) return false;
                const allSessions = this._agendaSessionsForDate(session.date);
                const sorted = allSessions
                    .filter(s => s.id !== session.id)
                    .slice()
                    .sort((a, b) => this.toUtcMin(a.time, a.date, dz) - this.toUtcMin(b.time, b.date, dz));
                const nextSession = sorted.find(s => this.toUtcMin(s.time, s.date, dz) > sessionStartUtc);
                if (!nextSession) return false;
                const nextStart = this.toUtcMin(nextSession.time, nextSession.date, dz);
                return end <= nowUtc && nowUtc < nextStart;
            } catch (e) {
                console.error(`[nowline] ERROR session ${session.id}:`, e);
                return false;
            }
        },
        nowCountdown(session, dateOrArray) {
            this.nowTick;
            if (!this.showNowLineAfter(session, dateOrArray)) return '';
            const dz = this.timezone;
            const allSessions = this._agendaSessionsForDate(session.date);
            const sessionStartUtc = this.toUtcMin(session.time, session.date, dz);
            const sorted = allSessions
                .filter(s => s.id !== session.id)
                .slice()
                .sort((a, b) => this.toUtcMin(a.time, a.date, dz) - this.toUtcMin(b.time, b.date, dz));
            const nextSession = sorted.find(s => this.toUtcMin(s.time, s.date, dz) > sessionStartUtc);
            if (!nextSession) return '';
            return this._formatCountdown(this.toUtcMin(nextSession.time, nextSession.date, dz));
        },
        nowCountdownBefore(session) {
            this.nowTick;
            if (!this.showNowLineBefore(session)) return '';
            const dz = this.timezone;
            const sessionStartUtc = this.toUtcMin(session.time, session.date, dz);
            return this._formatCountdown(sessionStartUtc);
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
            const step = this.availStep || 30;
            const nextSlot = Math.ceil(totalMin / step) * step;
            const nh = Math.floor(nextSlot / 60) % 24;
            const nm = nextSlot % 60;
            const mm = nm === 0 ? '00' : String(nm).padStart(2, '0');
            return `${nh.toString().padStart(2, '0')}:${mm}`;
        },

        slotToMin(t) {
            const [h, m] = t.split(':').map(Number);
            return h * 60 + m;
        },

        get isSessionTimeValid() {
            const { startTime, endTime, date, traineeIds } = this.sessionForm;
            if (!startTime || !endTime || traineeIds.length === 0) return true;
            const sm = this.slotToMin(startTime);
            const em = this.slotToMin(endTime);
            for (const aId of traineeIds) {
                const slots = this.availabilityMap[aId + '|' + date];
                if (!slots || slots.length === 0) continue;
                if (slots.some(s => s.startTime === 'all_day')) continue;
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
                errors.push(this.labels.err_coach_day_off || 'Цей день є вихідним для майстра');
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
            const ranges = this.coachRangesByDate?.[date];
            if (ranges && ranges.length > 0) {
                const locId = this.sessionForm.locationId;
                const filteredRanges = locId ? ranges.filter(r => r.locationId == locId || r.locationId == null) : ranges;
                if (filteredRanges.length > 0) {
                    let within = false;
                    for (const r of filteredRanges) {
                        const rs = this.slotToMin(r.startTime);
                        const re = this.slotToMin(r.endTime);
                        if (sm >= rs && em <= re) { within = true; break; }
                    }
                    if (!within) {
                        errors.push(this.labels.err_coach_avail || 'Час сесії не входить в години доступності майстра');
                    }
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
                    if (slots.some(s => s.startTime === 'all_day')) continue;
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
            if (this.sessionForm.traineeIds.length === 0 || this.isTimePassed() || !this.sessionForm.endTime || (this.locations.length > 0 && !this.sessionForm.locationId)) {
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
            this.draggedIndex = null;
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
            if (defaultDate === this.todayStr && currentMinutes >= workEndMinutes) {
                const tomorrow = new Date(now);
                tomorrow.setDate(tomorrow.getDate() + 1);
                defaultDate = tomorrow.toISOString().slice(0, 10);
            }
            this.sessionForm = { title: this.labels.session_title_default || 'Тренування', description: '', date: defaultDate, startTime: null, endTime: null, traineeIds: [], locationId: this.defaultLocationId(defaultDate), recurring: false };
            this.showModal = true;
            this.scrollDateIntoView();
            this.$nextTick(() => this.onSessionStartChange());
        },

        editSession(w) {
            if (w.date < this.todayStr) return;
            this.traineeSearch = '';
            this.editingSessionId = w.id;
            this.originalSessionData = { date: w.date, time: w.time, endTime: w.endTime, traineeIds: [...(w.traineeIds || [])], title: w.title, description: w.description || '', locationId: w.locationId || null };
            this.sessionValidationErrors = null;
            this.sessionForm = { title: w.title, description: w.description || '', date: w.date, startTime: w.time, endTime: w.endTime || null, traineeIds: [...(w.traineeIds || [])], locationId: w.locationId || null, recurring: false };
            this.showModal = true;
            this.scrollDateIntoView();
        },

        closeModal() {
            if (this._prevTab) {
                this.activeTab = this._prevTab;
                this._prevTab = null;
            }
            this.showModal = false;
            this.sessionValidationErrors = null;
        },

        createFromAvailability(traineeId, date, startTime, endTime) {
            this._prevTab = this.activeTab;
            this.activeTab = 'feed';
            this.traineeSearch = '';
            this.editingSessionId = null;
            this.sessionValidationErrors = null;
            if (startTime === 'all_day') {
                const trainee = this.trainees.find(t => t.id === traineeId);
                const traineeName = trainee ? trainee.name : '';
                this.sessionForm = { title: (this.labels.session_title_default || 'Тренування') + (traineeName ? ' — ' + traineeName : ''), description: '', date, startTime: null, endTime: null, traineeIds: [traineeId], locationId: this.defaultLocationId(date), recurring: false };
                this.showModal = true;
                this.scrollDateIntoView();
                this.$nextTick(() => {
                    const slots = this.validStartSlots;
                    if (slots.length > 0) {
                        const st = slots[0];
                        const min = this.slotToMin(st) + (this.availStep || 60);
                        const et = String(Math.floor(min / 60)).padStart(2, '0') + ':' + String(min % 60).padStart(2, '0');
                        this.sessionForm.startTime = st;
                        this.sessionForm.endTime = et;
                    } else {
                        const first = this.timeSlots15?.[0] || '08:00';
                        this.sessionForm.startTime = first;
                        const min = this.slotToMin(first) + (this.availStep || 60);
                        this.sessionForm.endTime = String(Math.floor(min / 60)).padStart(2, '0') + ':' + String(min % 60).padStart(2, '0');
                    }
                });
                return;
            }
            this.$nextTick(() => {
                const trainee = this.trainees.find(t => t.id === traineeId);
                const traineeName = trainee ? trainee.name : '';
                const st = startTime;
                const et = endTime;
                this.sessionForm = { title: (this.labels.session_title_default || 'Тренування') + (traineeName ? ' — ' + traineeName : ''), description: '', date: date, startTime: st.slice(0,5), endTime: et.slice(0,5), traineeIds: [traineeId], locationId: this.defaultLocationId(date), recurring: false };
                this.showModal = true;
                this.scrollDateIntoView();
            });
        },

        async saveSession() {
            const { startTime, endTime, date, title, description, traineeIds, locationId, recurring } = this.sessionForm;
            if (!startTime || !endTime) return;
            if (date < this.todayStr) return;
            if (this.locations.length > 0 && !locationId) return;
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
                    this._prevTab = null; this.showModal = false; return;
                }
            }
            const newTime = startTime;
            if (this.editingSessionId && this.originalSessionData) {
                if (date !== this.originalSessionData.date || newTime !== this.originalSessionData.time) {
                    await fetch(`/api/v1/sessions/${this.editingSessionId}`, { method: 'DELETE' });
                }
            }
            const isNew = !this.editingSessionId || (date !== this.originalSessionData?.date || newTime !== this.originalSessionData?.time);
            const payload = {
                id: (this.editingSessionId && date === this.originalSessionData?.date && newTime === this.originalSessionData?.time) ? this.editingSessionId : null,
                title, description, date, mentorId: this.mentorId,
                time: newTime, endTime,
                traineeIds, locationId,
                recurring: isNew ? !!recurring : false
            };
            console.log(`[saveSession] creating session: date=${payload.date}, time=${payload.time}, endTime=${payload.endTime}, editingId=${payload.id}`);
            const res = await fetch('/api/v1/sessions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
            console.log(`[saveSession] save done, status=${res.status}`);
            this._prevTab = null; this.showModal = false; await this.fetchData();
        },

        askDeleteSession() {
            this.confirmData = { show: true, title: this.labels.confirm_delete_title || 'Видалити?', message: this.labels.confirm_delete_session || 'Дію неможливо скасувати.', onConfirm: async () => { await fetch(`/api/v1/sessions/${this.editingSessionId}`, { method: 'DELETE' }); this._prevTab = null; this.showModal = false; await this.fetchData(); } };
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
            
            this.traineeForm = { name: '', description: '', photoBase64: null }; this.editingTraineeId = null; this.showCreateForm = false; this.showTraineeFormModal = false; await this.fetchTrainees();
        },

        openCreateTraineeForm() {
            this.traineeForm = { name: '', description: '', photoBase64: null };
            this.editingTraineeId = null;
            this.showTraineeFormModal = true;
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
            this._prevTab = this.activeTab;
            this.activeTab = 'feed';
            this.traineeSearch = '';
            this.editingSessionId = null;
            this.originalSessionData = null;
            this.sessionForm = { title: (this.labels.session_title_default || 'Тренування') + ' — ' + (trainee.name || ''), description: '', date: this.selectedDate, startTime: null, endTime: null, traineeIds: [trainee.id], locationId: this.defaultLocationId(this.selectedDate), recurring: false };
            this.showModal = true;
        },
        _isSlotInAllTraineeAvail(fromMin, toMin, date) {
            const traineeIds = this.sessionForm.traineeIds || [];
            for (const tId of traineeIds) {
                const slots = this.availabilityMap[tId + '|' + date];
                if (slots && slots.length > 0) {
                    if (slots.some(s => s.startTime === 'all_day')) continue;
                    const ok = slots.some(s => {
                        const ss = this.slotToMin(s.startTime);
                        const se = this.slotToMin(s.endTime);
                        return fromMin >= ss && toMin <= se;
                    });
                    if (!ok) return false;
                }
            }
            return true;
        },
        computeValidSessionSlots(date) {
            if (!date) return [];
            const allSlots = this.timeSlots15;
            const we = this.workEnd || '21:00';
            const busyMinRanges = this.getBusyMinuteRanges(date);
            const dateRanges = this.coachRangesByDate?.[date] || [];
            const hasAvail = dateRanges.length > 0;
            if (hasAvail && this.locations.length > 0 && !this.sessionForm.locationId) return [];
            const now = new Date();
            const isToday = date === now.toISOString().slice(0, 10);
            const todayMin = now.getHours() * 60 + now.getMinutes();
            const step = this.availStep || 30;

            return allSlots.filter(t => {
                if (t >= we) return false;
                const tm = this.slotToMin(t);
                if (isToday && tm <= todayMin) return false;
                if (hasAvail) {
                    const locId = this.sessionForm.locationId;
                    const filteredRanges = locId ? dateRanges.filter(r => r.locationId == locId || r.locationId == null) : dateRanges;
                    if (filteredRanges.length === 0) return false;
                    const inRange = filteredRanges.some(r =>
                        tm >= this.slotToMin(r.startTime) && tm + step <= this.slotToMin(r.endTime)
                    );
                    if (!inRange) return false;
                }
                for (const [bs, be] of busyMinRanges) {
                    if (tm < be && tm + step > bs) return false;
                }
                if (!this._isSlotInAllTraineeAvail(tm, tm + step, date)) return false;
                return true;
            });
        },
        computeValidSessionEndSlots(date, startTime) {
            if (!date || !startTime) return [];
            const allSlots = this.timeSlots15;
            const busyMinRanges = this.getBusyMinuteRanges(date);
            const dateRanges = this.coachRangesByDate?.[date] || [];
            const hasAvail = dateRanges.length > 0;
            if (hasAvail && this.locations.length > 0 && !this.sessionForm.locationId) return [];
            const sm = this.slotToMin(startTime);
            const step = this.availStep || 30;

            return allSlots.filter(t => {
                const tm = this.slotToMin(t);
                if (tm <= sm) return false;
                if (hasAvail) {
                    const locId = this.sessionForm.locationId;
                    const filteredRanges = locId ? dateRanges.filter(r => r.locationId == locId || r.locationId == null) : dateRanges;
                    if (filteredRanges.length === 0) return false;
                    const inRange = filteredRanges.some(r =>
                        tm <= this.slotToMin(r.endTime) && sm >= this.slotToMin(r.startTime)
                    );
                    if (!inRange) return false;
                }
                for (const [bs, be] of busyMinRanges) {
                    if (tm > bs && sm < be) return false;
                }
                if (!this._isSlotInAllTraineeAvail(sm, tm, date)) return false;
                return true;
            });
        },
        getBusyMinuteRanges(date) {
            const ranges = [];
            const allSessions = this.sessions || [];
            for (const s of allSessions) {
                if (s.date && s.date !== date) continue;
                if (this.editingSessionId && s.id === this.editingSessionId) continue;
                const st = s.startTime || s.time;
                const et = s.endTime || st;
                if (!st) continue;
                ranges.push([this.slotToMin(st), this.slotToMin(et)]);
            }
            return ranges;
        },
        onSessionDateChange(date) {
            this.sessionForm.date = date;
            this.sessionForm.startTime = null;
            this.sessionForm.endTime = null;
            this.sessionForm.locationId = this.defaultLocationId(date);
            this.$nextTick(() => this.onSessionStartChange());
        },
        onSessionStartChange() {
            const slots = this.validEndSlots;
            if (slots.length > 0) {
                this.sessionForm.endTime = slots[0];
            } else {
                this.sessionForm.endTime = null;
            }
        },
        onSessionLocationChange() {
            this.sessionForm.startTime = null;
            this.sessionForm.endTime = null;
        },
        defaultLocationId(date) {
            if (this.locations.length === 0) return null;
            const ranges = this.coachRangesByDate?.[date] || [];
            if (ranges.length === 0) return this.locations[0].id;
            const availLocIds = new Set(ranges.map(r => r.locationId));
            if (availLocIds.has(null)) return this.locations[0].id;
            const firstAvail = this.locations.find(l => availLocIds.has(l.id));
            return firstAvail ? firstAvail.id : this.locations[0].id;
        },
        get availableLocations() {
            const date = this.sessionForm.date;
            if (!date) return this.locations;
            const ranges = this.coachRangesByDate?.[date] || [];
            if (ranges.length === 0) return this.locations;
            const availLocIds = new Set(ranges.map(r => r.locationId));
            if (availLocIds.has(null)) return this.locations;
            const currentLocId = this.sessionForm.locationId;
            return this.locations.filter(l => availLocIds.has(l.id) || l.id === currentLocId);
        },
        getAvailPopupRanges() {
            const date = this.sessionForm.date;
            const ranges = this.coachRangesByDate?.[date] || [];
            const groups = {};
            for (const r of ranges) {
                if (!r.startTime && !r.endTime) continue;
                const key = r.locationId ?? '__none__';
                if (!groups[key]) groups[key] = { locationId: r.locationId, ranges: [] };
                groups[key].ranges.push(r);
            }
            return Object.values(groups).map(g => ({
                ...g,
                locationName: g.locationId ? this.getLocationName(g.locationId) : null,
                locationColor: g.locationId ? this.getLocationColor(g.locationId) : null
            }));
        },
        openTraineeForm(a) { this.editingTraineeId = a.id; this.traineeForm = { name: a.name, description: a.description, photoBase64: a.photoBase64 || null }; this.showTraineeFormModal = true; },
         askDeleteTrainee(id) { this.confirmData = { show: true, title: this.labels.confirm_delete_title || 'Видалити?', message: this.labels.confirm_delete_trainee || 'Дані зникнуть.', onConfirm: async () => { await fetch(`/api/v1/trainees/${id}`, { method: 'DELETE' }); await this.fetchTrainees(); await this.fetchData(); } }; },

          getCopyLinkText(id) { return this.traineeLinks && this.traineeLinks[id] ? '✓ ' + (this.labels.copied || 'Скопійовано') : ''; },

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
             if (this.isSessionNow(session)) return 'bg-[#1c1c1c] ring-4 ring-red-500/30 border-red-500/20 shadow-lg shadow-red-500/10';
             if (session.confirmationStatus === 'CONFIRMED') return 'bg-green-900/20 border-green-500';
             if (session.confirmationStatus === 'REJECTED') return 'bg-red-900/10 border-red-500/30 opacity-70';
             if (session.confirmationStatus === 'PENDING' && session.createdBy === 'TRAINEE') return 'bg-yellow-900/10 border-yellow-500/30';
             if (session.confirmationStatus === 'PENDING') {
                 const confirmed = (session.confirmedTraineeIds || '').split(',').filter(Boolean).length;
                 const total = (session.traineeIds || []).length;
                 if (confirmed > 0) return 'bg-yellow-900/15 border-yellow-600/40';
                 return 'bg-yellow-900/10 border-yellow-500/20';
             }
             return 'bg-[#1c1c1c] border border-[#333]';
         },

         getAgendaCardClass(session) {
             if (this.isSessionNow(session)) return 'bg-[#1c1c1c] ring-4 ring-red-500/30 border-red-500/20 shadow-lg shadow-red-500/10';
             if (session.confirmationStatus === 'CONFIRMED') return 'bg-green-900/20 border-green-500';
             if (session.confirmationStatus === 'REJECTED') return 'bg-red-900/10 border-red-500/30 opacity-70';
             if (session.confirmationStatus === 'PENDING' && session.createdBy === 'TRAINEE') return 'bg-yellow-900/10 border-yellow-500/30';
             if (session.confirmationStatus === 'PENDING') {
                 const confirmed = (session.confirmedTraineeIds || '').split(',').filter(Boolean).length;
                 const total = (session.traineeIds || []).length;
                 if (confirmed > 0) return 'bg-yellow-900/15 border-yellow-600/40';
                 return 'bg-yellow-900/10 border-yellow-500/20';
             }
             return 'bg-[#1c1c1c] border border-[#333]';
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

          async toggleTraineeConfirm(traineeId, session) {
               const status = this.getTraineeConfirmStatus(traineeId, session);
               if (status === 'confirmed') {
                   await this.rejectTrainee(traineeId, session);
               } else if (status === 'rejected') {
                   await this.unrejectTrainee(traineeId, session);
               } else {
                   await this.confirmTrainee(traineeId, session);
               }
           },

          async confirmTrainee(traineeId, session) {
               console.log(`[confirmTrainee] sessionId=${session.id}, traineeId=${traineeId}, date=${session.date}, time=${session.time}`);
               const r = await fetch(`/api/v1/sessions/${session.id}/confirm-trainee/${traineeId}`, { method: 'POST' });
               console.log(`[confirmTrainee] POST done, status=${r.status}`);
               await this.fetchData();
               console.log(`[confirmTrainee] fetchData done`);
           },

          async unconfirmTrainee(traineeId, session) {
               console.log(`[unconfirmTrainee] sessionId=${session.id}, traineeId=${traineeId}, date=${session.date}, time=${session.time}`);
               const r = await fetch(`/api/v1/sessions/${session.id}/unconfirm-trainee/${traineeId}`, { method: 'POST' });
               console.log(`[unconfirmTrainee] POST done, status=${r.status}`);
               await this.fetchData();
           },

           async rejectTrainee(traineeId, session) {
               console.log(`[rejectTrainee] sessionId=${session.id}, traineeId=${traineeId}, date=${session.date}, time=${session.time}`);
               const r = await fetch(`/api/v1/sessions/${session.id}/reject-trainee/${traineeId}`, { method: 'POST' });
               console.log(`[rejectTrainee] POST done, status=${r.status}`);
               await this.fetchData();
           },

           async unrejectTrainee(traineeId, session) {
               console.log(`[unrejectTrainee] sessionId=${session.id}, traineeId=${traineeId}, date=${session.date}, time=${session.time}`);
               const r = await fetch(`/api/v1/sessions/${session.id}/unreject-trainee/${traineeId}`, { method: 'POST' });
               console.log(`[unrejectTrainee] POST done, status=${r.status}`);
               await this.fetchData();
           },

          allTraineesConfirmed(session) {
               const ids = session.traineeIds || [];
               if (ids.length === 0) return false;
               return ids.every(id => this.getTraineeConfirmStatus(id, session) === 'confirmed');
           },

          someTraineesConfirmed(session) {
               const ids = session.traineeIds || [];
               if (ids.length === 0) return false;
               return ids.some(id => this.getTraineeConfirmStatus(id, session) === 'confirmed') && !this.allTraineesConfirmed(session);
           },

          isTraineeTelegramConnected(traineeId) {
               const t = this.trainees.find(a => a.id == traineeId);
               return t && t.telegramConnected;
          },

           async requestTraineeConfirmationForTrainee(traineeId, session) {
                await fetch(`/api/v1/sessions/${session.id}/request-trainee-confirmation/${traineeId}`, { method: 'POST' });
                const name = this.getTraineeName(traineeId);
                this.toast = { show: true, message: `Запит на підтвердження надіслано спортсмену ${name}` };
                setTimeout(() => { this.toast.show = false; }, 3000);
                await this.fetchData();
            },

           async confirmSession(sessionId) {
               console.log(`[confirmSession] sessionId=${sessionId}`);
               const r = await fetch(`/api/v1/sessions/${sessionId}/confirm`, { method: 'POST' });
               console.log(`[confirmSession] POST done, status=${r.status}`);
               await this.fetchData();
           },

           async rejectSession(sessionId) {
               console.log(`[rejectSession] sessionId=${sessionId}`);
               const r = await fetch(`/api/v1/sessions/${sessionId}/reject`, { method: 'POST' });
               console.log(`[rejectSession] POST done, status=${r.status}`);
               await this.fetchData();
           },

          deleteSessionConfirm(session) {
              this.editingSessionId = session.id;
              this.confirmData = {
                  show: true,
                  title: this.labels.confirm_delete_title || 'Видалити?',
                  message: this.labels.confirm_delete_session || 'Дію неможливо скасувати.',
                  onConfirm: async () => {
                      console.log(`[deleteSession] sessionId=${this.editingSessionId}, date=${session.date}, time=${session.time}`);
                      const r = await fetch(`/api/v1/sessions/${this.editingSessionId}`, { method: 'DELETE' });
                      console.log(`[deleteSession] DELETE done, status=${r.status}`);
                      this.editingSessionId = null;
                      await this.fetchData();
                  }
              };
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
            this.locationForm = { name: '', description: '', color: '#3b82f6', googleMapsUrl: '' }; this.editingLocationId = null; this.showLocationFormModal = false;
            await this.fetchLocations();
        },

        openCreateLocationForm() { this.editingLocationId = null; this.locationForm = { name: '', description: '', color: '#3b82f6', googleMapsUrl: '' }; this.showLocationFormModal = true; },
        editLocation(l) { this.editingLocationId = l.id; this.locationForm = { name: l.name, description: l.description || '', color: l.color || '#3b82f6', googleMapsUrl: l.googleMapsUrl || '' }; this.showLocationFormModal = true; },
        copyGoogleMapsUrl(url) {
            if (!url) return;
            navigator.clipboard.writeText(url).catch(() => {});
        },
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
            const titles = { sport:'Моя доступність', studying:'Моя доступність', psychology:'Моя доступність', other:'Моя доступність' };
            return titles[this.mentorProfile] || titles.sport;
        },
        get coachAvailHasUnsaved() { return this.coachDirtyDates.size > 0; },

        get coachAllCovered() {
            const we = this.workEnd || '21:00';
            const ws = this.workStart || '09:00';
            const valid = this.timeSlots15.filter(t => t >= ws && t < we);
            const covered = valid.filter(t => {
                const tm = this.slotToMin(t);
                return this.coachRanges.some(r => {
                    if (!r.startTime || !r.endTime) return false;
                    const rs = this.slotToMin(r.startTime);
                    const re = this.slotToMin(r.endTime);
                    return tm >= rs && tm < re;
                });
            });
            return valid.length > 0 && covered.length === valid.length;
        },

        coachHasRanges(date) { return (this.coachRangesByDate[date] || []).length > 0; },
        coachNormTime(t) { return t ? t.replace(/:00$/, '') : t; },

        coachAvailSel(ds) {
            this.coachAvailDate = ds;
            this.coachRanges = this.coachRangesByDate[ds] || [];
            if (!this.coachRangesByDate[ds]) {
                this.coachRangesByDate[ds] = this.coachRanges;
            } else {
                for (const r of this.coachRanges) r._new = false;
            }
            this.coachSortRanges();
        },

        coachSortRanges() {
            this.coachRanges.sort((a, b) => (a.startTime || '').localeCompare(b.startTime || ''));
        },

        coachAddRange() {
            this.coachRanges.forEach(r => r._new = false);
            const defaultLocId = this.locations.length > 0 ? this.locations[0].id : null;
            this.coachRanges.unshift({ startTime: '', endTime: '', locationId: defaultLocId, _new: true });
            this.coachDirtyDates.add(this.coachAvailDate);
        },

        coachRemoveRange(i) {
            this.coachRanges.splice(i, 1);
            this.coachDirtyDates.add(this.coachAvailDate);
        },

        onCoachAvailStartChange(i) {
            this.coachSortRanges();
            this.coachDirtyDates.add(this.coachAvailDate);
        },

        coachMarkDirty() { this.coachDirtyDates.add(this.coachAvailDate); },

        timeOptionsAfter(startTime) {
            if (!startTime) return this.timeSlots15;
            return this.timeSlots15.filter(t => t > startTime);
        },

        coachStartSlots(excludeIndex) {
            const we = this.workEnd || '21:00';
            const result = this.timeSlots15.filter(t => {
                if (t >= we) return false;
                const tm = this.slotToMin(t);
                for (let i = 0; i < this.coachRanges.length; i++) {
                    if (i === excludeIndex) continue;
                    const r = this.coachRanges[i];
                    if (!r.startTime || !r.endTime) continue;
                    const rs = this.slotToMin(r.startTime);
                    const re = this.slotToMin(r.endTime);
                    if (tm >= rs && tm < re) return false;
                }
                return true;
            });
            return result;
        },

        coachEndSlots(startTime, excludeIndex) {
            if (!startTime) return this.timeOptionsAfter(startTime);
            const sm = this.slotToMin(startTime);
            const afterOpts = this.timeOptionsAfter(startTime);
            const result = afterOpts.filter(t => {
                const tm = this.slotToMin(t);
                for (let i = 0; i < this.coachRanges.length; i++) {
                    if (i === excludeIndex) continue;
                    const r = this.coachRanges[i];
                    if (!r.startTime || !r.endTime) continue;
                    const rs = this.slotToMin(r.startTime);
                    const re = this.slotToMin(r.endTime);
                    if (sm < re && tm > rs) return false;
                }
                return true;
            });
            return result;
        },

        getLocName(id) { return this.getLocationName(id); },
        getLocColor(id) { return id ? this.getLocationColor(id) : null; },

        async coachSaveAll() {
            if (this.coachSaving) return;
            this.coachSaving = true;
            try {
                for (const date of this.coachDirtyDates) {
                    const ranges = this.coachRangesByDate[date] || [];
                    const body = [];
                    for (const r of ranges) {
                        if (r.startTime && r.endTime && (!this.locations.length || r.locationId != null)) {
                            body.push({ date, startTime: r.startTime, endTime: r.endTime, locationId: r.locationId || null });
                        }
                    }
                    if (body.length === 0) {
                        await fetch(`/api/v1/coach/availability?dates=${date}`, { method: 'DELETE' });
                    } else {
                        await fetch('/api/v1/coach/availability', {
                            method:'POST',
                            headers:{'Content-Type':'application/json'},
                            body:JSON.stringify(body)
                        });
                    }
                }
                this.coachDirtyDates.clear();
                for (const date of Object.keys(this.coachRangesByDate)) {
                    for (const r of this.coachRangesByDate[date]) r._new = false;
                }
                if (!this.shareToken) await this.mkShareToken();
            } catch(e) { console.error('[coach] saveAll error', e); }
            finally { this.coachSaving = false; }
        },

        async loadCoachAvailabilityData() {
            const sd = this.days[0]?.dateStr;
            const ed = this.days[this.days.length - 1]?.dateStr;
            if (!sd || !ed) return;
            try {
                const [ar, sr] = await Promise.all([
                    fetch(`/api/v1/coach/availability?startDate=${sd}&endDate=${ed}`),
                    fetch(`/api/v1/coach/sessions?startDate=${sd}&endDate=${ed}`),
                ]);
                if (ar.ok) {
                    const apiData = await ar.json();
                    const g = {};
                    for (const i of apiData) {
                        if (!g[i.date]) g[i.date] = [];
                        g[i.date].push({ startTime: this.coachNormTime(i.startTime), endTime: this.coachNormTime(i.endTime), locationId: i.locationId });
                    }
                    this.coachRangesByDate = g;
                }
                if (sr.ok) {
                    const apiData = await sr.json();
                    const g = {};
                    for (const i of apiData) {
                        if (!g[i.date]) g[i.date] = [];
                        g[i.date].push(i);
                    }
                    this.coachSess = g;
                }
            } catch(e) {
                console.error('[coach] load error', e);
            }
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
            await this.loadCoachAvailabilityData();
            this.coachDirtyDates.clear();
            this.coachLoaded = true;
            this.coachAvailSel(this.coachAvailDate);
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
            const imgUrl = window.location.origin + '/api/v1/shared/' + this.shareToken + '/image?date=' + this.coachAvailDate;
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
