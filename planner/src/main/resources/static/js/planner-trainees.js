// planner-trainees.js
// Trainee management: CRUD, Telegram integration, availability requests, feedback, conversations.
(function () {
  'use strict';

  // These methods are mixed into the main app object.
  // Inside each method, `this` refers to the Alpine app state.
  window.plannerModules = window.plannerModules || {};

  Object.assign(window.plannerModules, {

    // ── Fetch trainees ────────────────────────────────────────────────────────

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
                    a.sessionReminderEnabled = false;
                    a.timezone = 'Europe/Kyiv';
                    a._editingTz = a.timezone;
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

    // ── CRUD ─────────────────────────────────────────────────────────────────

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

      this.traineeForm = { name: '', description: '', photoBase64: null };
      this.editingTraineeId = null;
      this.showCreateForm = false;
      this.showTraineeFormModal = false;
      await this.fetchTrainees();
    },

    askDeleteTrainee(id) {
      this.confirmData = { show: true, title: this.labels.confirm_delete_title || 'Видалити?', message: this.labels.confirm_delete_trainee || 'Дані зникнуть.', onConfirm: async () => { await fetch(`/api/v1/trainees/${id}`, { method: 'DELETE' }); await this.fetchTrainees(); await this.fetchData(); } };
    },

    openTraineeForm(a) {
      this.editingTraineeId = a.id;
      this.traineeForm = { name: a.name, description: a.description, photoBase64: a.photoBase64 || null };
      this.showTraineeFormModal = true;
    },

    openCreateTraineeForm() {
      this.traineeForm = { name: '', description: '', photoBase64: null };
      this.editingTraineeId = null;
      this.showTraineeFormModal = true;
    },

    // ── Expand / collapse ────────────────────────────────────────────────────

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

    collapseTrainee(id) {
      if (this.traineeCompactView) {
        const i = this.traineeExpandedIds.indexOf(id);
        if (i >= 0) this.traineeExpandedIds.splice(i, 1);
      } else {
        // Detail mode: switch back to compact and collapse all
        this.traineeCompactView = true;
        this.traineeExpandedIds = [];
      }
      this._ref++;
    },

    toggleTraineeFilter(id) {
      if (id === null) this.selectedTraineeFilters = [];
      else {
        const idx = this.selectedTraineeFilters.indexOf(id);
        if (idx > -1) this.selectedTraineeFilters.splice(idx, 1);
        else this.selectedTraineeFilters.push(id);
      }
    },

    toggleConfirmedFilter() {
      this.showConfirmedOnly = !this.showConfirmedOnly;
    },

    // ── Notify modal ─────────────────────────────────────────────────────────

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

    // ── Confirmation helpers ──────────────────────────────────────────────────

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

    async requestTraineeConfirmationForTrainee(traineeId, session) {
      await fetch(`/api/v1/sessions/${session.id}/request-trainee-confirmation/${traineeId}`, { method: 'POST' });
      const name = this.getTraineeName(traineeId);
      this.toast = { show: true, message: `Запит на підтвердження надіслано спортсмену ${name}` };
      setTimeout(() => { this.toast.show = false; }, 3000);
      await this.fetchData();
    },

    // ── Reminders ────────────────────────────────────────────────────────────

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

    // ── Invite links ──────────────────────────────────────────────────────────

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

    getCopyLinkText(id) {
      return this.traineeLinks && this.traineeLinks[id] ? '✓ ' + (this.labels.copied || 'Скопійовано') : '';
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

    toggleBulkAvailTrainee(id) {
      const idx = this.bulkAvailTrainees.indexOf(id);
      if (idx >= 0) this.bulkAvailTrainees.splice(idx, 1);
      else this.bulkAvailTrainees.push(id);
    },

    // ── Conversation modal ────────────────────────────────────────────────────

    async openConversationModal(trainee) {
      this.conversationModal = { show: true, traineeId: trainee.id, traineeName: trainee.name || '' };
      await this.loadConversation(trainee.id);
    },

    closeConversationModal() {
      this.conversationModal = { show: false, traineeId: null, traineeName: '' };
      this.conversation = [];
    },

    async loadConversation(traineeId) {
      if (!this.mentorId || !traineeId) return;
      this.conversationLoading = true;
      try {
        const res = await fetch(`/api/v1/feedback/conversation?mentorId=${this.mentorId}&traineeId=${traineeId}`);
        if (res.ok) this.conversation = (await res.json()).sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
      } catch(e) {}
      finally { this.conversationLoading = false; }
    },

    async loadFeedbackReceivedByMentor() {
      if (!this.mentorId) return;
      try {
        const res = await fetch(`/api/v1/feedback/received-by-mentor?mentorId=${this.mentorId}`);
        if (res.ok) {
          this._feedbackReceivedByMentor = await res.json();
        }
      } catch(e) {}
    },

    // ── Photo upload ──────────────────────────────────────────────────────────

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

    // ── Feedback modal ────────────────────────────────────────────────────────

    openFeedbackModal(traineeId, traineeName, sessionId, sessionTitle) {
      this.feedbackModal = { show: true, traineeId, traineeName: traineeName || '', sessionId: sessionId || null, sessionTitle: sessionTitle || null, text: '', tags: [], rating: 0 };
    },

    closeFeedbackModal() {
      this.feedbackModal = { show: false, traineeId: null, traineeName: '', sessionId: null, sessionTitle: null, text: '', tags: [], rating: 0 };
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
          fromMentorId: this.mentorId,
          toTraineeId: this.feedbackModal.traineeId,
          sessionId: this.feedbackModal.sessionId,
          sessionTitle: this.feedbackModal.sessionTitle,
          text: this.feedbackModal.text.trim() || null,
          tags: this.feedbackModal.tags.length ? this.feedbackModal.tags.join(',') : null,
          rating: this.feedbackModal.rating > 0 ? this.feedbackModal.rating : null
        };
        const res = await fetch('/api/v1/feedback', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        });
        if (res.ok) {
          const saved = await res.json();
          this.closeFeedbackModal();
          this.toast = { show: true, message: 'Відгук надіслано!' };
          setTimeout(() => { this.toast.show = false; }, 3000);
          if (this.conversationModal.show && this.conversationModal.traineeId === saved.toTraineeId) {
            await this.loadConversation(saved.toTraineeId);
          }
        }
      } catch(e) {}
      finally {
        this.feedbackSending = false;
      }
    },

    // ── Session session-level actions ─────────────────────────────────────────

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

    updateSessionTitleFromTrainees() {
      const base = this.labels.session_title_default || 'Тренування';
      const names = this.getTraineeNamesText(this.sessionForm.traineeIds);
      this.sessionForm.title = names ? base + ' — ' + names : base;
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
          if (idx === -1 && this._traineeAvailConflicts(id, this.sessionForm.startTime, this.sessionForm.date)) {
            this.sessionForm._prevStartTime = this.sessionForm.startTime;
            this.sessionForm._prevEndTime = this.sessionForm.endTime;
            this.sessionForm.startTime = null;
            this.sessionForm.endTime = null;
          } else {
            this.onSessionStartChange();
          }
        }
      });
    },

  });
}());
