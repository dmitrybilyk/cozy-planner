const CACHE = 'linkease-v1';

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', e => e.waitUntil(self.clients.claim()));

self.addEventListener('fetch', e => {
    if (e.request.method !== 'GET') return;
    const url = new URL(e.request.url);

    // Cache-first for WASM files (content-addressed by hash, safe to cache forever)
    if (url.pathname.endsWith('.wasm')) {
        e.respondWith(
            caches.open(CACHE).then(cache =>
                cache.match(e.request).then(cached =>
                    cached || fetch(e.request).then(res => {
                        if (res.ok) cache.put(e.request, res.clone());
                        return res;
                    })
                )
            )
        );
        return;
    }

    // Network-first for everything else; fall back to cache when offline
    e.respondWith(
        fetch(e.request)
            .then(res => {
                if (res.ok && url.origin === self.location.origin) {
                    caches.open(CACHE).then(c => c.put(e.request, res.clone()));
                }
                return res;
            })
            .catch(() => caches.match(e.request))
    );
});
