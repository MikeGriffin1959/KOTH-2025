/*
 * KOTH service worker (PWA).
 * Strategy: NETWORK-FIRST for everything, cache as fallback. This guarantees
 * players always see the latest deploy (scores, picks, commentary) the moment
 * they open the app; the cache only serves when offline. Bump CACHE on
 * strategy changes — content freshness never depends on it.
 */
const CACHE = 'koth-v1';

self.addEventListener('install', function (e) {
  self.skipWaiting();
});

self.addEventListener('activate', function (e) {
  e.waitUntil(
    caches.keys()
      .then(function (keys) {
        return Promise.all(keys.filter(function (k) { return k !== CACHE; })
          .map(function (k) { return caches.delete(k); }));
      })
      .then(function () { return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function (e) {
  // Only GETs, and only our own origin (leave CDN css/js/font requests alone)
  if (e.request.method !== 'GET') return;
  var url = new URL(e.request.url);
  if (url.origin !== self.location.origin) return;

  e.respondWith(
    fetch(e.request)
      .then(function (resp) {
        if (resp && resp.ok && resp.type === 'basic') {
          var copy = resp.clone();
          caches.open(CACHE).then(function (c) { c.put(e.request, copy); });
        }
        return resp;
      })
      .catch(function () {
        return caches.match(e.request);
      })
  );
});
