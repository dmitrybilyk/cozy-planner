const CACHE = 'cozy-v4';
const STATIC_CSS_AND_ASSETS = [
  '/favicon.svg', '/icon.svg', '/manifest.json',
  '/css/shared.css', '/css/mentor-view.css', '/css/trainee-sessions.css',
];

self.addEventListener('install', (e) => {
  self.skipWaiting();
  e.waitUntil(
    caches.open(CACHE).then(c => c.addAll(STATIC_CSS_AND_ASSETS).catch(() => {}))
  );
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    clients.claim().then(() =>
      caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    )
  );
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (STATIC_CSS_AND_ASSETS.includes(url.pathname)) {
    e.respondWith(
      caches.match(e.request).then(r => r || fetch(e.request))
    );
  }
});

self.addEventListener('push', (event) => {
    let title = 'Cozy Planner';
    let body = 'Нове сповіщення';
    let url = '/';
    if (event.data) {
        try {
            const d = event.data.json();
            if (d.title) title = d.title;
            if (d.message) body = d.message;
            if (d.url) url = d.url;
        } catch (e) {}
    }
    const options = {
        body: body + ' — натисніть, щоб відкрити додаток',
        icon: '/favicon.svg',
        badge: '/favicon.svg',
        tag: 'cozy-push',
        data: { url }
    };
    event.waitUntil(
        self.registration.showNotification(title, options)
    );
});

self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    const url = (event.notification.data && event.notification.data.url) || '/';
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
