// Local preview server: serves the page and proxies the IPTV server so the browser
// is not blocked by CORS or by http-only streams.
const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');

const PORT = 8765;
const UA = 'VLC/3.0.20 LibVLC/3.0.20';

function fetchUp(url, headers, cb, hops = 0) {
  let u;
  try { u = new URL(url); } catch (e) { return cb(e); }
  const lib = u.protocol === 'https:' ? https : http;
  const req = lib.request(u, { method: 'GET', headers: { 'User-Agent': UA, ...headers } }, (res) => {
    if ([301, 302, 303, 307, 308].includes(res.statusCode) && res.headers.location && hops < 5) {
      res.resume();
      return fetchUp(new URL(res.headers.location, u).href, headers, cb, hops + 1);
    }
    cb(null, res, u.href);
  });
  req.on('error', (e) => cb(e));
  req.end();
  return req;
}

const proxied = (abs) => '/p?url=' + encodeURIComponent(abs);

http.createServer((req, res) => {
  const q = new URL(req.url, 'http://localhost');
  if (q.pathname === '/' || q.pathname === '/index.html') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    return fs.createReadStream(path.join(__dirname, 'index.html')).pipe(res);
  }
  if (q.pathname !== '/p') { res.writeHead(404); return res.end(); }

  const target = q.searchParams.get('url');
  const raw = q.searchParams.has('raw');
  const headers = req.headers.range ? { Range: req.headers.range } : {};
  const upReq = fetchUp(target, headers, (err, up, finalUrl) => {
    if (err) { res.writeHead(502, { 'Access-Control-Allow-Origin': '*' }); return res.end(String(err.message)); }
    const ct = up.headers['content-type'] || '';
    const isHls = !raw && (/mpegurl/i.test(ct) || /\.m3u8(\?|$)/i.test(finalUrl));
    if (isHls) {
      // Rewrite segment and key URLs so they also go through this proxy.
      let body = '';
      up.setEncoding('utf8');
      up.on('data', (c) => (body += c));
      up.on('end', () => {
        const out = body.split(/\r?\n/).map((line) => {
          const t = line.trim();
          if (!t) return line;
          if (t.startsWith('#')) return line.replace(/URI="([^"]+)"/g, (_, u) => `URI="${proxied(new URL(u, finalUrl).href)}"`);
          return proxied(new URL(t, finalUrl).href);
        }).join('\n');
        res.writeHead(up.statusCode, { 'Content-Type': 'application/vnd.apple.mpegurl', 'Access-Control-Allow-Origin': '*' });
        res.end(out);
      });
      return;
    }
    const h = { 'Access-Control-Allow-Origin': '*' };
    for (const k of ['content-type', 'content-length', 'content-range', 'accept-ranges']) if (up.headers[k]) h[k] = up.headers[k];
    res.writeHead(up.statusCode, h);
    up.pipe(res);
    res.on('close', () => up.destroy());
  });
  res.on('close', () => upReq && upReq.destroy && upReq.destroy());
}).listen(PORT, '127.0.0.1', () => console.log(`Preview running at http://localhost:${PORT}`));
