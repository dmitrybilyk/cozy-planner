const CACHE = 'cozy-v2';
const STATIC = [
  '/favicon.svg', '/icon.svg', '/manifest.json',
  '/css/shared.css', '/css/mentor-view.css', '/css/trainee-sessions.css',
  '/js/mentor-view.js', '/js/trainee-sessions.js', '/js/coach-availability.js', '/js/shared-availability.js', '/js/trainee-availability.js'
];

self.addEventListener('install', (e) => {
  self.skipWaiting();
  e.waitUntil(
    caches.open(CACHE).then(c => c.addAll(STATIC).catch(() => {}))
  );
});

self.addEventListener('activate', (e) => {
  e.waitUntil(clients.claim());
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (STATIC.includes(url.pathname)) {
    e.respondWith(
      caches.match(e.request).then(r => r || fetch(e.request))
    );
  }
});

self.addEventListener('push', (event) => {
    let title = 'Cozy Planner';
    let body = 'Нове сповіщення';
    let data = { url: '/' };
    if (event.data) {
        try {
            const d = event.data.json();
            if (d.title) title = d.title;
            if (d.message) body = d.message;
            if (d.url) data.url = d.url;
            if (d.sessionId) data.sessionId = d.sessionId;
            if (d.actionType) data.actionType = d.actionType;
        } catch (e) {}
    }
    const options = {
        body: body,
        icon: '/favicon.svg',
        badge: '/favicon.svg',
        tag: 'cozy-push',
        data: data
    };
    if (data.sessionId && data.actionType) {
        options.actions = [
            { action: 'confirm', title: '✅ Підтвердити' },
            { action: 'reject', title: '❌ Відхилити' }
        ];
    }
    event.waitUntil(
        self.registration.showNotification(title, options)
    );
});

self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    const data = event.notification.data || {};
    const sessionId = data.sessionId;
    const actionType = data.actionType;

    if (event.action && sessionId && actionType) {
        event.waitUntil((async () => {
            const isConfirm = event.action === 'confirm';
            let endpoint;
            if (actionType === 'trainee_confirm_session') {
                endpoint = `/api/v1/trainee/sessions/${sessionId}/${isConfirm ? 'confirm' : 'reject'}`;
            } else if (actionType === 'coach_confirm_session') {
                endpoint = `/api/v1/sessions/${sessionId}/${isConfirm ? 'confirm' : 'reject'}`;
            }
            let success = false;
            let statusCode = 0;
            if (endpoint) {
                try {
                    const resp = await fetch(endpoint, { method: 'POST', credentials: 'same-origin' });
                    statusCode = resp.status;
                    success = resp.ok;
                } catch (e) {
                    console.error('[sw] action fetch error:', e.message);
                }
            }
            const clientList = await clients.matchAll({ type: 'window', includeUncontrolled: true });
            const msg = { type: 'session_changed', action: event.action, actionType, sessionId, success, statusCode };
            for (const c of clientList) {
                c.postMessage(msg);
                if (c.url.includes(self.location.host) && 'focus' in c) { await c.focus(); }
            }
            if (clientList.length === 0) {
                await clients.openWindow('/');
            }
        })());
        return;
    }

    const url = data.url || '/';
    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true })
            .then(clientList => {
                for (const c of clientList) {
                    if (c.url.includes(self.location.host) && 'focus' in c) return c.focus();
                }
                return clients.openWindow(url);
            })
    );
});
