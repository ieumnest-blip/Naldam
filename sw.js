/* 날담 서비스 워커 — 앱 설치와 빠른 실행을 위해 기본 파일을 저장해 둬요.
   날씨·로그인·기록 데이터는 저장하지 않고 항상 인터넷에서 새로 받아요. */
const CACHE = "naldam-v1";
const CORE = ["./", "index.html", "manifest.webmanifest", "icon.svg", "icon-192.png", "icon-512.png",
              "icon-maskable-192.png", "icon-maskable-512.png", "apple-touch-icon.png", "favicon-32.png"];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(CORE)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});
self.addEventListener("fetch", e => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;          // 날씨·구글 로그인 등은 그대로 통과
  // 먼저 인터넷에서 최신 파일을 받고, 안 되면 저장해 둔 걸 보여줘요
  e.respondWith(
    fetch(req).then(res => {
      if (res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
      return res;
    }).catch(() => caches.match(req).then(r => r || caches.match("index.html")))
  );
});
