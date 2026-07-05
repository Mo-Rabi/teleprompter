// index.html: network-first so script updates arrive immediately when online.
// vosk engine + speech model (~46MB total): cache-first — immutable, downloaded once.
// v3: purges the line-ending-corrupted vosk.js cached by v2
const CACHE = "prompter-v3";
const ASSETS = ["./", "./manifest.webmanifest", "./icon-192.png", "./icon-512.png"];
const IMMUTABLE = ["vosk.js", "model-small-en-us.tar.gz"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(ASSETS)));
  self.skipWaiting();
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener("fetch", (e) => {
  if (e.request.method !== "GET") return;
  if (!e.request.url.startsWith("http")) return; // extensions etc. — not cacheable
  const isImmutable = IMMUTABLE.some((f) => e.request.url.endsWith(f));

  if (isImmutable) {
    e.respondWith(
      caches.match(e.request).then(
        (hit) =>
          hit ||
          fetch(e.request).then((res) => {
            if (res.ok) {
              const copy = res.clone();
              caches.open(CACHE).then((c) => c.put(e.request, copy));
            }
            return res;
          })
      )
    );
    return;
  }

  e.respondWith(
    fetch(e.request)
      .then((res) => {
        const copy = res.clone();
        caches.open(CACHE).then((c) => c.put(e.request, copy));
        return res;
      })
      .catch(() => caches.match(e.request, { ignoreSearch: true }))
  );
});
