/* KihraMaker: small NW.js / Node stand-in for RPG Maker MV and MZ games whose plugins call require(), process or nw.
   Files are reached through the KihraNW bridge and live under the virtual folder /game (the game's www folder). */
(function () {
  if (window.__kihraNw) return;
  window.__kihraNw = true;
  var K = window.KihraNW, ROOT = '/game';

  function norm(p, keep) {
    p = String(p).replace(/\\/g, '/');
    var abs = p.charAt(0) === '/', out = [];
    p.split('/').forEach(function (s) {
      if (!s || s === '.') return;
      if (s === '..') { if (out.length) out.pop(); } else out.push(s);
    });
    return (abs ? '/' : '') + out.join('/') + (keep && out.length && /\/$/.test(p) ? '/' : '');
  }
  var path = {
    sep: '/', delimiter: ':',
    normalize: function (p) { return norm(p, true); },
    join: function () { return norm(Array.prototype.slice.call(arguments).join('/'), true); },
    resolve: function () {
      var r = '';
      for (var i = arguments.length - 1; i >= 0; i--) {
        r = arguments[i] + (r ? '/' + r : '');
        if (String(arguments[i]).charAt(0) === '/') break;
      }
      r = norm(r);
      return r.charAt(0) === '/' ? r : norm(ROOT + '/' + r);
    },
    isAbsolute: function (p) { return String(p).charAt(0) === '/'; },
    dirname: function (p) { p = norm(p); var i = p.lastIndexOf('/'); return i <= 0 ? (i === 0 ? '/' : '.') : p.slice(0, i); },
    basename: function (p, ext) { var b = norm(p).split('/').pop(); return ext && b.slice(-ext.length) === ext ? b.slice(0, -ext.length) : b; },
    extname: function (p) { var b = path.basename(p), i = b.lastIndexOf('.'); return i > 0 ? b.slice(i) : ''; }
  };
  function abs(p) { return path.resolve(p); }

  // ---- Buffer (just enough) ----
  function b64ToBytes(s) { var bin = atob(s), u = new Uint8Array(bin.length); for (var i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i); return u; }
  function bytesToB64(u) { var s = '', n = 8192; for (var i = 0; i < u.length; i += n) s += String.fromCharCode.apply(null, u.subarray(i, i + n)); return btoa(s); }
  function Buf(u) { u.toString = function (enc) {
      if (enc === 'base64') return bytesToB64(u);
      return new TextDecoder('utf-8').decode(u);
    };
    u.slice = function (a, b) { return Buf(Uint8Array.prototype.slice.call(u, a, b)); };
    return u; }
  var Buffer = {
    from: function (d, enc) {
      if (typeof d === 'string') return Buf(enc === 'base64' ? b64ToBytes(d) : new TextEncoder().encode(d));
      if (d instanceof ArrayBuffer) return Buf(new Uint8Array(d));
      return Buf(new Uint8Array(d));
    },
    alloc: function (n) { return Buf(new Uint8Array(n)); },
    isBuffer: function (b) { return b instanceof Uint8Array; },
    concat: function (list) { var n = 0; list.forEach(function (b) { n += b.length; }); var o = new Uint8Array(n), p = 0; list.forEach(function (b) { o.set(b, p); p += b.length; }); return Buf(o); }
  };

  // ---- fs ----
  function enoent(p) { var e = new Error("ENOENT: no such file or directory, open '" + p + "'"); e.code = 'ENOENT'; return e; }
  var fs = {
    existsSync: function (p) { return !!K.exists(abs(p)); },
    readFileSync: function (p, o) {
      p = abs(p);
      if (!K.exists(p)) throw enoent(p);
      var enc = typeof o === 'string' ? o : (o && o.encoding);
      if (enc) return K.readText(p);
      return Buffer.from(K.readBase64(p), 'base64');
    },
    writeFileSync: function (p, d) {
      p = abs(p);
      if (typeof d === 'string') K.writeText(p, d); else K.writeBase64(p, bytesToB64(d instanceof ArrayBuffer ? new Uint8Array(d) : d));
    },
    appendFileSync: function (p, d) { fs.writeFileSync(p, (fs.existsSync(p) ? fs.readFileSync(p, 'utf8') : '') + d); },
    mkdirSync: function (p) { K.mkdirs(abs(p)); },
    readdirSync: function (p) { return JSON.parse(K.list(abs(p)) || '[]'); },
    unlinkSync: function (p) { K.remove(abs(p)); },
    rmdirSync: function (p) { K.remove(abs(p)); },
    renameSync: function (a, b) { fs.writeFileSync(b, fs.readFileSync(a)); K.remove(abs(a)); },
    statSync: function (p) {
      p = abs(p);
      if (!K.exists(p)) throw enoent(p);
      var dir = !!K.isDir(p), size = Number(K.size(p));
      return { size: size, mtime: new Date(), isDirectory: function () { return dir; }, isFile: function () { return !dir; } };
    }
  };
  fs.lstatSync = fs.statSync;
  function asyncOf(name) {
    return function () {
      var a = Array.prototype.slice.call(arguments), cb = a.pop(), r, err = null;
      if (typeof cb !== 'function') { a.push(cb); cb = function () {}; }
      try { r = fs[name + 'Sync'].apply(fs, a); } catch (e) { err = e; }
      setTimeout(function () { cb(err, r); }, 0);
    };
  }
  ['readFile', 'writeFile', 'appendFile', 'mkdir', 'readdir', 'unlink', 'rmdir', 'rename', 'stat'].forEach(function (n) { fs[n] = asyncOf(n); });
  fs.exists = function (p, cb) { var r = fs.existsSync(p); setTimeout(function () { cb(r); }, 0); };

  // ---- process / nw ----
  var process = {
    platform: 'linux', arch: 'arm64', pid: 1, argv: [], execPath: ROOT + '/Game', env: {}, title: 'Game',
    versions: { node: '12.13.0', nw: '0.40.0', chromium: '80.0' }, version: 'v12.13.0', release: { name: 'node' },
    mainModule: { filename: ROOT + '/index.html' },
    cwd: function () { return ROOT; },
    nextTick: function (f) { var a = Array.prototype.slice.call(arguments, 1); setTimeout(function () { f.apply(null, a); }, 0); },
    on: function () { return process; }, once: function () { return process; }, off: function () {}, emit: function () {},
    exit: function () { K.exit(); },
    hrtime: function () { var t = performance.now(); return [Math.floor(t / 1000), Math.floor((t % 1000) * 1e6)]; },
    memoryUsage: function () { return { rss: 0, heapTotal: 0, heapUsed: 0 }; },
    uptime: function () { return performance.now() / 1000; }
  };
  var win = {
    window: window, x: 0, y: 0, width: window.innerWidth, height: window.innerHeight, zoomLevel: 0, menu: null,
    on: function () { return win; }, once: function () { return win; }, removeListener: function () {}, removeAllListeners: function () {},
    setMaxListeners: function () {}, showDevTools: function () {}, closeDevTools: function () {}, isDevToolsOpen: function () { return false; },
    enterFullscreen: function () {}, leaveFullscreen: function () {}, toggleFullscreen: function () {},
    resizeTo: function () {}, moveTo: function () {}, focus: function () {}, blur: function () {}, show: function () {}, hide: function () {},
    setAlwaysOnTop: function () {}, setResizable: function () {}, setPosition: function () {}, requestAttention: function () {},
    close: function () { K.exit(); }, reload: function () { location.reload(); }
  };
  var clip = { get: function () { return { get: function () { return ''; }, set: function () {}, clear: function () {} }; } };
  var nw = {
    App: { argv: [], fullArgv: [], dataPath: ROOT + '/save', manifest: { name: 'game', version: '1.0.0' },
      quit: function () { K.exit(); }, closeAllWindows: function () { K.exit(); }, clearCache: function () {}, setProxyConfig: function () {} },
    Window: { get: function () { return win; }, open: function () { return win; } },
    Clipboard: clip, Screen: { Init: function () {}, screens: [{ bounds: { x: 0, y: 0, width: screen.width, height: screen.height } }], on: function () {} },
    Shell: { openExternal: function () {}, openItem: function () {}, showItemInFolder: function () {} },
    Shortcut: function () {}, Menu: function () { this.append = function () {}; }, MenuItem: function () {}
  };
  nw.gui = nw;
  var os = { platform: function () { return 'linux'; }, arch: function () { return 'arm64'; }, homedir: function () { return ROOT; },
    tmpdir: function () { return ROOT + '/tmp'; }, EOL: '\n', type: function () { return 'Linux'; }, release: function () { return '5.0'; },
    cpus: function () { return [{}]; }, totalmem: function () { return 4e9; }, freemem: function () { return 2e9; } };
  function Emitter() { this._l = {}; }
  Emitter.prototype.on = function (n, f) { (this._l[n] = this._l[n] || []).push(f); return this; };
  Emitter.prototype.emit = function (n) { var a = Array.prototype.slice.call(arguments, 1); (this._l[n] || []).forEach(function (f) { f.apply(null, a); }); return true; };
  var util = { inherits: function (c, s) { c.prototype = Object.create(s.prototype); c.prototype.constructor = c; },
    format: function (f) { return String(f); }, promisify: function (fn) { return function () { var a = Array.prototype.slice.call(arguments); return new Promise(function (res, rej) { a.push(function (e, r) { e ? rej(e) : res(r); }); fn.apply(null, a); }); }; } };
  var cp = { exec: function (c, o, cb) { if (typeof o === 'function') cb = o; if (cb) setTimeout(function () { cb(null, '', ''); }, 0); }, spawn: function () { return new Emitter(); }, execSync: function () { return Buffer.alloc(0); } };

  var mods = { fs: fs, path: path, os: os, process: process, nw: nw, 'nw.gui': nw, buffer: { Buffer: Buffer }, util: util, events: Emitter,
    child_process: cp, 'fs-extra': fs, crypto: { randomBytes: function (n) { var u = new Uint8Array(n); crypto.getRandomValues(u); return Buf(u); } } };
  function req(name) {
    name = String(name).replace(/^node:/, '');
    if (mods[name]) return mods[name];
    return {};
  }
  window.require = req;
  window.process = process;
  window.nw = nw;
  window.Buffer = Buffer;
  window.global = window;
  window.module = { exports: {} };
  window.__dirname = ROOT;
  window.__filename = ROOT + '/index.html';
  window.gc = function () {};
})();
