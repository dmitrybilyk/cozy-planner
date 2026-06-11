// mentor-view.js
// Alpine.js entry point: global helpers, calendarApp() state + computed getters + init().
// All methods are injected via window.plannerModules (loaded by the module files above).

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
    return Object.assign({
        // ── State ──────────────────────────────────────────────────────────────
        activeTab: 'feed', showModal: false, sessionSaving: false, _prevTab: null, profileTab: 'main',
        editingSessionId: null, editingTraineeId: null, editingLocationId: null, draggedIndex: null, showCreateForm: false, showTraineeFormModal: false, showLocationFormModal: false, traineeCompactView: true, traineeExpandedIds: [], _ref: 0,
        selectedDate: _today, todayStr: _today,
        days: [], sessions: [], trainees: [], locations: [], mentorId: null, mentor: { name: '' }, user: {}, labels: {}, mentorProfile: 'sport', theme: 'default',
        sessionReminderEnabled: false,
        shareAvailability: false,
        multiLocation: false,
        sessionConfirmations: false,
        telegramIntegration: false,
        traineeComm: false,
        bulkAvailTrainees: [], bulkAvailSending: false, bulkAvailResult: null,
        hours: Array.from({length: 24}, (_, i) => i.toString().padStart(2, '0')),
        halfHours: Array.from({length: 48}, (_, i) => `${Math.floor(i/2).toString().padStart(2,'0')}:${i%2===0?'00':'30'}`),
        daySlots: Array.from({length: 29}, (_, i) => `${(7+Math.floor(i/2)).toString().padStart(2,'0')}:${i%2===0?'00':'30'}`),
        selectedTraineeFilters: [], traineeSearch: '',
        sessionForm: { title: '', description: '', date: '', startTime: null, endTime: null, traineeIds: [], locationId: null, recurring: false, recurringCount: 8 },
        freeSlotContext: null,
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
        loadingStuck: false,
        _lastHiddenAt: null,
        loadingMore: false,
        _noMoreFutureSessions: false,
        ptrRefreshing: false,
        agendaReady: false,
        loadedDates: {},
        showFreeTime: false,
        agendaAvailByDate: {},
        sessionCounts: {},
        availabilityMap: {},
        dayOffs: [],
        touchDrag: null,
        touchJustDragged: false,
        _touchDragCleanup: null,
        deferredInstallPrompt: null,
        showIosInstall: false,
        isStandalone: window.matchMedia('(display-mode: standalone)').matches,
        workStart: '08:00',
        workEnd: '21:00',
        availStep: 30,
        timezone: 'Europe/Kyiv',
        editingTimezone: 'Europe/Kyiv',
        photoUrl: null,
        notifications: [],
        unreadCount: 0,
        wisdomPopup: false,
        currentWisdom: '',
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
        planModal: { show: false, traineeId: null, traineeName: '', slots: [], saving: false, error: '' },
        feedbackModal: { show: false, traineeId: null, traineeName: '', sessionId: null, sessionTitle: null, text: '', tags: [], rating: 0 },
        feedbackSending: false,
        contactModal: { show: false, message: '', sending: false, sent: false, error: '' },
        _feedbackReceivedByMentor: [],
        conversationModal: { show: false, traineeId: null, traineeName: '' },
        conversation: [],
        conversationLoading: false,
        COACH_FEEDBACK_TAGS: ['Відмінна робота!', 'Добрий прогрес', 'Потрібно більше зусиль', 'Гарна техніка', 'Тримай темп!'],
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

        // ── Computed Getters ───────────────────────────────────────────────────
        // These use JavaScript getter syntax and CANNOT be split into other files.

        get timeSlots15() {
            const ws = this.workStart || '08:00';
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
            return this.computeValidSessionSlots(this.sessionForm.date, { ignoreAvail: !this.shareAvailability });
        },
        get validEndSlots() {
            if (!this.sessionForm.startTime) return [];
            return this.computeValidSessionEndSlots(this.sessionForm.date, this.sessionForm.startTime, { ignoreAvail: !this.shareAvailability });
        },
        get introSlides() {
            const l = this.labels || {};
            const trainees_acc = (l.trainees_acc || 'клієнтів').toLowerCase();
            const trainees     = (l.trainees     || 'Клієнти');
            const sessions_gen = (l.sessions_gen || 'занять').toLowerCase();
            const session_gen  = (l.session_gen  || 'заняття').toLowerCase();
            return [
                {
                    icon: '📅',
                    title: 'Керуй розкладом без накладок',
                    body: `Додавай ${trainees_acc}, налаштовуй локації, створюй ${sessions_gen} без накладок. Завжди бачиш свій вільний час — натисни на слот і ${session_gen} готове миттєво. Скопіюй вільний час для конкретного дня і відправ учню прямо звідси.`
                },
                {
                    icon: '🔗',
                    title: 'Ділися доступністю — і тебе бронюватимуть самі',
                    body: `У налаштуваннях увімкни «Ділитися доступністю» — отримаєш вічне посилання для соцмереж і можливість ділитися картинкою з вільним часом на конкретний день. ${trainees} самі записуватимуться, а ти просто підтверджуєш.`
                },
                {
                    icon: '📲',
                    title: 'Telegram: підтвердження, нагадування, відгуки',
                    body: `У налаштуваннях підключи Telegram — це також необов'язково, але дає більше. ${trainees} підтверджуватимуть ${sessions_gen} прямо з повідомлень у боті. Нагадування за годину до заняття і відгуки після — все автоматично.`,
                    cta: 'tg'
                }
            ];
        },
        get tourSteps() {
            const l = this.labels || {};
            const trainee     = (l.trainee     || 'клієнт').toLowerCase();
            const trainees    = (l.trainees    || 'Клієнти');
            const trainees_acc = (l.trainees_acc || 'клієнтів').toLowerCase();
            const trainee_gen = (l.trainee_gen || trainee);
            const session     = (l.session     || 'заняття').toLowerCase();
            const sessions    = (l.sessions    || 'заняття').toLowerCase();
            const sessions_gen = (l.sessions_gen || 'занять').toLowerCase();
            return [
                {
                    target: '[data-tour="days-row"]',
                    tab: 'feed',
                    title: 'Ряд дат — розклад одним поглядом',
                    body: `Кожна дата показує кількість ${sessions_gen} під нею. Натисни на дату — побачиш деталі дня. Сьогодні виділено рамкою, а дати із заняттями підсвічені.`,
                    position: 'bottom'
                },
                {
                    target: '[data-tour="feed-view"]',
                    tab: 'feed',
                    title: 'День і План',
                    body: `<b>День</b> — деталі дати: ${sessions} і вільний час слотами. Натисни на вільний слот — ${session} створюється миттєво. <b>+</b> — для повного контролю. <b>План</b> — всі майбутні ${sessions} одним списком.<br><br><b>Доступність</b> (налаштування) — регулярний графік на майбутнє: коли ти взагалі доступний. <b>Вільний час</b> — фактичні незайняті слоти конкретного дня після врахування всіх ${sessions_gen}. Скопіюй одним кліком і відправ учню.`,
                    position: 'top'
                },
                {
                    target: '[data-tour="trainees"]',
                    tab: 'trainees',
                    detailTrainees: true,
                    title: l.manage_trainees || 'Команда',
                    body: `Додавай ${trainees_acc}, завантажуй фото, давай посилання на особисту сторінку. Звідси можна швидко створити ${session} або серію ${sessions_gen} для конкретного ${trainee_gen} — з повторенням за потрібні дні.`,
                    position: 'bottom'
                },
                {
                    target: '[data-tour="no-target"]',
                    tab: null,
                    title: '✅ Ти готовий до роботи!',
                    body: `<b>Базово:</b> розклад, вільний час, ${trainees_acc} — керуй без накладок.<br><b>+Доступність:</b> увімкни в налаштуваннях — ділися посиланням, ${trainees} бронюють самі.<br><b>+Telegram:</b> увімкни в налаштуваннях — підтвердження, нагадування та відгуки в боті.`,
                    position: 'bottom'
                }
            ];
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
        get filteredSessions() {
            let result = this.sessions.filter(w => w.date === this.selectedDate);
            if (this.locationFilter) result = result.filter(w => w.locationId == this.locationFilter);
            if (this.sessionFilter === 'all' || (!this.sessionFilter)) { return result; }
            if (this.sessionFilter === 'confirmed') result = result.filter(w => w.confirmationStatus === 'CONFIRMED' || (w.traineeIds && w.traineeIds.some(id => this.getTraineeConfirmStatus(id, w) === 'confirmed')));
            if (this.sessionFilter === 'pending') result = result.filter(w => w.confirmationStatus !== 'REJECTED' && w.traineeIds && w.traineeIds.every(id => this.getTraineeConfirmStatus(id, w) === 'none'));
            return result;
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
            if (this._noMoreFutureSessions) return false;
            const loadedFuture = Object.keys(this.loadedDates).filter(d => d >= this.todayStr).sort();
            if (loadedFuture.length === 0) return false;
            const lastLoaded = loadedFuture[loadedFuture.length - 1];
            const counts = this.sessionCounts;
            if (counts && Object.keys(counts).length > 0) {
                return Object.keys(counts).some(d => d > lastLoaded && counts[d] > 0);
            }
            return this.days.filter(d => d.dateStr > lastLoaded).length > 0;
        },
        get hasMoreDays() {
            const allDays = this.days;
            const loaded = Object.keys(this.loadedDates);
            return loaded.length < allDays.length;
        },
        get futureDays() {
            return this.days.filter(d => d.dateStr >= this.todayStr);
        },
        get pastSessionsGroups() {
            const filtered = this.pastSessions.filter(s => s.date < this.todayStr);
            const groups = filtered.reduce((acc, w) => { if (!acc[w.date]) acc[w.date] = []; acc[w.date].push(w); return acc; }, {});
            return Object.keys(groups).sort().reverse().map(date => ({ date, items: groups[date].sort((a, b) => a.time.localeCompare(b.time)) }));
        },
        get filteredTrainees() {
            const q = this.traineeSearch.toLowerCase();
            return this.trainees
                .filter(a => !q || a.name.toLowerCase().includes(q))
                .sort((a, b) => a.name.localeCompare(b.name));
        },
        get freeSlotsByDate() {
            this.nowTick;
            const ws = this.workStart || '08:00';
            const we = this.workEnd || '21:00';
            const wsMin = this.slotToMin(ws);
            const weMin = this.slotToMin(we);
            const result = {};
            for (const date of Object.keys(this.loadedDates).sort()) {
                const daySessions = this.sessions.filter(s => s.date === date && s.time && s.endTime);
                const avail = this.agendaAvailByDate[date] || [];
                let freeIntervals;
                if (avail.length > 0) {
                    freeIntervals = avail
                        .map(a => ({ start: this.slotToMin(a.startTime), end: this.slotToMin(a.endTime), locationId: a.locationId }))
                        .sort((a, b) => a.start - b.start);
                } else {
                    freeIntervals = [{ start: wsMin, end: weMin, locationId: null }];
                }
                for (const s of daySessions) {
                    const sStart = this.slotToMin(s.time);
                    const sEnd = this.slotToMin(s.endTime);
                    const next = [];
                    for (const iv of freeIntervals) {
                        if (sEnd <= iv.start || sStart >= iv.end) {
                            next.push(iv);
                        } else {
                            if (sStart > iv.start) next.push({ start: iv.start, end: sStart, locationId: iv.locationId });
                            if (sEnd < iv.end) next.push({ start: sEnd, end: iv.end, locationId: iv.locationId });
                        }
                    }
                    freeIntervals = next;
                }
                const now = new Date();
                const nowMin = date === this.todayStr ? now.getHours() * 60 + now.getMinutes() : 0;
                const step = this.availStep || 30;
                result[date] = freeIntervals
                    .filter(iv => iv.end > iv.start && iv.end > nowMin)
                    .map(iv => {
                        const rawStart = date === this.todayStr ? Math.max(iv.start, nowMin) : iv.start;
                        const start = Math.ceil(rawStart / step) * step;
                        return { startStr: this.minToTime(start), endStr: this.minToTime(iv.end), locationId: iv.locationId, durationMin: iv.end - start };
                    })
                    .filter(iv => iv.durationMin >= step);
            }
            return result;
        },
        get nowMinutes() {
            this.nowTick; // trigger reactivity
            const now = new Date();
            return now.getHours() * 60 + now.getMinutes();
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
        get saveBtnClass() {
            const base = 'flex-[2] text-white py-4 rounded-2xl font-bold text-sm shadow-lg disabled:cursor-not-allowed ';
            if (this.sessionSaving) return base + 'bg-blue-600 opacity-60';
            if (this.sessionForm.traineeIds.length === 0 || this.isTimePassed() || !this.sessionForm.endTime || (this.locations.length > 0 && !this.sessionForm.locationId)) {
                return base + 'bg-gray-600 opacity-30';
            }
            if (this.sessionForm.startTime && this.sessionForm.endTime && this.getSessionValidationErrors().length > 0) {
                return base + 'bg-red-700';
            }
            return base + 'bg-blue-600';
        },
        get coachAvailTitle() {
            const titles = { sport:'Моя доступність', studying:'Моя доступність', psychology:'Моя доступність', other:'Моя доступність' };
            return titles[this.mentorProfile] || titles.sport;
        },
        get coachAvailHasUnsaved() { return this.coachDirtyDates.size > 0; },
        get coachAllCovered() {
            const we = this.workEnd || '21:00';
            const ws = this.workStart || '08:00';
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

        // ── init ───────────────────────────────────────────────────────────────

        async init() {
            console.log('init() starting...');
            this.initAudioContext();
            if (_deferredInstall) this.deferredInstallPrompt = _deferredInstall;

            // Watchdog: if still loading after 15s, surface a "stuck" UI so the user can tap reload
            const _loadWatchdog = setTimeout(() => {
                if (this.loading) {
                    console.warn('[init] loading watchdog fired — showing stuck UI');
                    this.loadingStuck = true;
                }
            }, 15000);

            // Custom pull-to-refresh on main scroll container
            this.$nextTick(() => {
                const mainEl = document.getElementById('main-scroll');
                if (!mainEl) return;
                const PTR_THRESHOLD = 72;
                let ptrStartY = 0;
                let ptrDelta = 0;
                let ptrArmed = false;
                mainEl.addEventListener('touchstart', (e) => {
                    if (mainEl.scrollTop === 0) {
                        ptrStartY = e.touches[0].clientY;
                        ptrArmed = true;
                    } else {
                        ptrArmed = false;
                    }
                    ptrDelta = 0;
                }, { passive: true });
                mainEl.addEventListener('touchmove', (e) => {
                    if (!ptrArmed) return;
                    ptrDelta = e.touches[0].clientY - ptrStartY;
                    if (ptrDelta < 0) ptrArmed = false;
                }, { passive: true });
                mainEl.addEventListener('touchend', async () => {
                    if (ptrArmed && ptrDelta >= PTR_THRESHOLD && !this.ptrRefreshing) {
                        this.ptrRefreshing = true;
                        try { await this.fetchData(); } finally { this.ptrRefreshing = false; }
                    }
                    ptrStartY = 0; ptrDelta = 0; ptrArmed = false;
                }, { passive: true });
            });

            // Visibility: reload if page becomes visible while still stuck; reconnect WS after long absence
            document.addEventListener('visibilitychange', () => {
                const state = document.visibilityState;
                console.log(`[lifecycle] visibilitychange → ${state}, lastHiddenAt=${this._lastHiddenAt}, loading=${this.loading}`);
                if (state === 'hidden') {
                    this._lastHiddenAt = Date.now();
                    console.log(`[lifecycle] page hidden at ${this._lastHiddenAt}`);
                } else {
                    this._handleVisibilityResume();
                }
            });

            // iOS bfcache: pageshow with persisted=true fires instead of visibilitychange on restore
            window.addEventListener('pageshow', (event) => {
                console.log(`[lifecycle] pageshow persisted=${event.persisted}, visState=${document.visibilityState}, lastHiddenAt=${this._lastHiddenAt}`);
                if (event.persisted) {
                    // Page was restored from bfcache — visibilitychange may not have fired
                    this._handleVisibilityResume();
                }
            });

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
                this.loadingStuck = true;
            } finally {
                clearTimeout(_loadWatchdog);
                this.loading = false;
            }
        },

    }, window.plannerModules || {});
}
