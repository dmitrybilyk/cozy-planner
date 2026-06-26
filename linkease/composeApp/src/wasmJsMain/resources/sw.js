const CACHE = 'linkease-v1';

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', e => e.waitUntil(self.clients.claim()));

self.addEventListener('fetch', e => {
    if (e.request.method !== 'GET') return;
    const url = new URL(e.request.url);

    // Never intercept server-controlled paths — auth redirects and API calls
    // must go straight to the network so the service worker can't interfere.
    if (
        url.pathname.startsWith('/api/') ||
        url.pathname.startsWith('/oauth2/') ||
        url.pathname.startsWith('/login/')
    ) return;

    // Cache-first for WASM files (content-addressed by hash, safe forever)
    if (url.pathname.endsWith('.wasm')) {
        e.respondWith(
            caches.open(CACHE).then(cache =>
                cache.match(e.request).then(cached =>
                    cached || fetch(e.request).then(res => {
                        if (res.ok) {
                            const clone = res.clone(); // clone before body is consumed
                            cache.put(e.request, clone);
                        }
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
                if (res.ok && res.type !== 'opaque' && url.origin === self.location.origin) {
                    const clone = res.clone(); // clone synchronously before returning res
                    caches.open(CACHE).then(c => c.put(e.request, clone));
                }
                return res;
            })
            .catch(() => caches.match(e.request))
    );
});
