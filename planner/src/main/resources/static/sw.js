self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => e.waitUntil(clients.claim()));

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
    event.waitUntil(
        self.registration.showNotification(title, {
            body: body,
            icon: '/favicon.svg',
            badge: '/favicon.svg',
            tag: 'cozy-push',
            data: { url: url }
        })
    );
});

self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    const url = event.notification.data?.url || '/';
    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true })
            .then(clientList => {
                for (const c of clientList) {
                    if (c.url.includes(location.host) && 'focus' in c) return c.focus();
                }
                return clients.openWindow(url);
            })
    );
});
