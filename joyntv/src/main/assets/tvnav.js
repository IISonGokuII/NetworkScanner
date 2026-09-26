// TV remote navigation for web pages, injected by MainActivity.
// The D-pad moves a highlight between clickable elements ("spatial
// navigation"); on a large playing video it controls the player instead.
// Every entry point returns a small object that MainActivity acts on:
//   {a:'tap', x, y}     tap at CSS pixel position (real user gesture)
//   {a:'iframe', x, y}  element is a cross-origin frame -> use mouse pointer
//   {a:'video', t, d, p} current time, duration, paused -> show OSD
//   {a:'focus', wake}   focus moved (wake: show player controls)
//   {a:'handled'} / {a:'none'}
(function () {
  if (window.__tvnav) return;

  var SEL = 'a[href],button,input:not([type=hidden]),select,textarea,summary,iframe,' +
    '[role=button],[role=link],[role=tab],[role=menuitem],[role=option],[role=checkbox],' +
    '[role=radio],[role=switch],[tabindex]:not([tabindex="-1"]),[onclick]';
  var CONTROLS_TIMEOUT = 8000;

  var cur = null;            // element that currently has the TV focus
  var curHref = location.href;
  var lastRect = null;       // where cur was, to recover when React re-renders it
  var box = null;            // highlight overlay
  var controlsMode = false;  // navigating the player's buttons instead of seeking
  var lastKey = 0;
  var rootsCache = [document], extraCache = [], rootsTime = 0;

  function vw() { return window.innerWidth; }
  function vh() { return window.innerHeight; }

  // --- DOM helpers ---------------------------------------------------------

  // React attaches click handlers without any trace in the markup, so
  // clickable <div>s are found through React's props object instead.
  function reactClickable(el) {
    var keys = Object.keys(el); // own properties only – cheap
    for (var i = 0; i < keys.length; i++) {
      if (keys[i].lastIndexOf('__reactProps', 0) === 0) {
        var props = el[keys[i]];
        return !!(props && (props.onClick || props.onPointerUp || props.onMouseDown));
      }
    }
    return false;
  }

  // Scans the DOM (at most every 2 s) for open shadow roots – cookie banners
  // often live in one – and for React click targets not matched by SEL.
  function refresh() {
    var now = Date.now();
    if (now - rootsTime < 2000) return;
    var list = [document], extra = [];
    for (var k = 0; k < list.length; k++) {
      var all = list[k].querySelectorAll('*');
      for (var i = 0; i < all.length; i++) {
        var el = all[i];
        if (el.shadowRoot) list.push(el.shadowRoot);
        if (reactClickable(el) && !el.matches(SEL)) extra.push(el);
      }
    }
    rootsCache = list;
    extraCache = extra;
    rootsTime = now;
  }

  function candidates() {
    refresh();
    var out = extraCache.slice(), rs = rootsCache;
    for (var k = 0; k < rs.length; k++) {
      var list = rs[k].querySelectorAll(SEL);
      for (var i = 0; i < list.length; i++) out.push(list[i]);
    }
    return out;
  }

  function parentOf(n) { return n.parentNode || n.host || null; }

  function contains(a, b) {
    for (var n = b; n; n = parentOf(n)) if (n === a) return true;
    return false;
  }

  function related(a, b) { return contains(a, b) || contains(b, a); }

  function deepFromPoint(x, y) {
    var e = document.elementFromPoint(x, y);
    while (e && e.shadowRoot) {
      var inner = e.shadowRoot.elementFromPoint(x, y);
      if (!inner || inner === e) break;
      e = inner;
    }
    return e;
  }

  function hScrollAncestor(el) {
    for (var n = el.parentElement; n && n !== document.body; n = n.parentElement) {
      if (n.scrollWidth > n.clientWidth + 2) {
        var o = getComputedStyle(n).overflowX;
        if (o === 'auto' || o === 'scroll') return n;
      }
    }
    return null;
  }

  function isFixed(el) {
    for (var n = el; n && n.nodeType === 1; n = n.parentElement) {
      var p = getComputedStyle(n).position;
      if (p === 'fixed' || p === 'sticky') return true;
    }
    return false;
  }

  function usable(el, r) {
    // Tiny elements are typically visually hidden "skip to content" links.
    if (!el.isConnected || r.width < 8 || r.height < 8) return false;
    if (el.disabled || el.getAttribute('aria-disabled') === 'true' || el.getAttribute('aria-hidden') === 'true') return false;
    if (el.tagName === 'IFRAME' && (r.width < 120 || r.height < 60)) return false;
    var s = getComputedStyle(el);
    if (s.visibility === 'hidden' || s.display === 'none' || s.pointerEvents === 'none') return false;
    // "Visually hidden" pattern used for screen-reader-only links.
    if ((s.clip && s.clip !== 'auto') || /inset\((50|100)%/.test(s.clipPath)) return false;
    var depth = 0;
    for (var n = el; n && n.nodeType === 1 && depth < 10; n = n.parentElement, depth++) {
      if (parseFloat(getComputedStyle(n).opacity) < 0.1) return false;
    }
    var cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    var inX = cx >= 0 && cx < vw(), inY = cy >= 0 && cy < vh();
    // Off to the side is only fine inside a horizontally scrollable row.
    if (!inX && !hScrollAncestor(el)) return false;
    if (inX && inY) {
      var hit = deepFromPoint(cx, cy);
      // Covered by something else (e.g. a dialog). Near the top this is
      // usually a fixed header that scrolling will clear.
      if (hit && !related(el, hit) && cy > vh() * 0.25) return false;
    }
    return true;
  }

  function gap(a1, a2, b1, b2) { return Math.max(0, Math.max(a1, b1) - Math.min(a2, b2)); }

  // --- Focus movement ------------------------------------------------------

  function best(dir, r) {
    var list = candidates(), scored = [];
    var rcx = (r.left + r.right) / 2, rcy = (r.top + r.bottom) / 2;
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      if (el === cur || (cur && related(el, cur))) continue;
      var c = el.getBoundingClientRect();
      if (c.width < 2 || c.height < 2) continue;
      var cx = (c.left + c.right) / 2, cy = (c.top + c.bottom) / 2, primary, ortho, align;
      if (dir === 'right' || dir === 'left') {
        if (dir === 'right' ? (cx <= rcx + 2 || c.left < r.left + 2) : (cx >= rcx - 2 || c.right > r.right - 2)) continue;
        ortho = gap(c.top, c.bottom, r.top, r.bottom);
        if (ortho > 0) continue; // left/right stays in the same row
        primary = dir === 'right' ? Math.max(0, c.left - r.right) : Math.max(0, r.left - c.right);
        align = Math.abs(cy - rcy);
      } else {
        if (dir === 'down' ? (cy <= rcy + 2 || c.top < r.top + 2) : (cy >= rcy - 2 || c.bottom > r.bottom - 2)) continue;
        primary = dir === 'down' ? Math.max(0, c.top - r.bottom) : Math.max(0, r.top - c.bottom);
        ortho = gap(c.left, c.right, r.left, r.right);
        align = Math.abs(cx - rcx);
      }
      scored.push({ el: el, r: c, s: primary + ortho * 4 + align * 0.05 });
    }
    scored.sort(function (a, b) { return a.s - b.s; });
    for (var j = 0; j < scored.length && j < 80; j++) {
      if (usable(scored[j].el, scored[j].r)) return scored[j].el;
    }
    return null;
  }

  // Element to start with: the one clicked last on this page, else the one
  // nearest to a point in the upper-left content area (or bottom centre for
  // player controls).
  function initial(px, py, ignoreSaved) {
    var saved = null;
    if (!ignoreSaved) {
      try { saved = sessionStorage.getItem('tvnav:' + location.pathname); } catch (e) {}
    }
    var list = candidates(), scored = [];
    for (var i = 0; i < list.length; i++) {
      var el = list[i], c = el.getBoundingClientRect();
      if (saved && el.getAttribute('href') === saved && usable(el, c)) return el;
      if (c.width < 2 || c.height < 2 || c.bottom < 0 || c.top > vh() || c.right < 0 || c.left > vw()) continue;
      var dx = (c.left + c.right) / 2 - px, dy = (c.top + c.bottom) / 2 - py;
      scored.push({ el: el, r: c, s: dx * dx + dy * dy });
    }
    scored.sort(function (a, b) { return a.s - b.s; });
    for (var j = 0; j < scored.length && j < 80; j++) {
      if (usable(scored[j].el, scored[j].r)) return scored[j].el;
    }
    return null;
  }

  function reveal(el) {
    var r = el.getBoundingClientRect();
    var needV = (r.top < vh() * 0.12 || r.bottom > vh() * 0.9) && !isFixed(el);
    var needH = r.left < 0 || r.right > vw();
    if (needV || needH) {
      el.scrollIntoView({ block: needV ? 'center' : 'nearest', inline: needH ? 'center' : 'nearest' });
    }
  }

  function setCur(el) {
    cur = el;
    curHref = location.href;
    try {
      var h = el.getAttribute('href');
      if (h) sessionStorage.setItem('tvnav:' + location.pathname, h);
    } catch (e) {}
    reveal(el);
    lastRect = el.getBoundingClientRect();
    draw();
  }

  // Same link / label as the element that was re-rendered away.
  function twin(old) {
    var href = old.getAttribute('href'), label = old.getAttribute('aria-label');
    if (!href && !label) return null;
    var list = candidates();
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      if (el.tagName !== old.tagName) continue;
      if (href ? el.getAttribute('href') === href : el.getAttribute('aria-label') === label) {
        if (usable(el, el.getBoundingClientRect())) return el;
      }
    }
    return null;
  }

  function focusResult(el) {
    if (el.tagName === 'IFRAME') {
      var r = el.getBoundingClientRect();
      return { a: 'iframe', x: r.left + r.width / 2, y: r.top + r.height / 2 };
    }
    return { a: 'focus', wake: !!video() };
  }

  function curValid() {
    if (location.href !== curHref) { cur = null; lastRect = null; } // SPA navigation: start fresh
    if (cur && lastRect && (!cur.isConnected || cur.getBoundingClientRect().width < 1)) {
      // The page re-rendered the element: continue with whatever is there now.
      var again = twin(cur) || initial(lastRect.left + lastRect.width / 2, lastRect.top + lastRect.height / 2, true);
      cur = again;
      if (again) lastRect = again.getBoundingClientRect();
    }
    return !!cur && cur.isConnected;
  }

  // --- Highlight -----------------------------------------------------------

  function draw() {
    if (!box) {
      box = document.createElement('div');
      box.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483647;box-sizing:border-box;' +
        'border:4px solid #fff;border-radius:10px;display:none;' +
        'box-shadow:0 0 0 3px rgba(0,0,0,.6),0 0 22px 6px rgba(255,255,255,.5);' +
        'transition:left .1s,top .1s,width .1s,height .1s';
      requestAnimationFrame(tick);
    }
    var fs = document.fullscreenElement;
    var host = fs && fs.tagName !== 'VIDEO' ? fs : document.documentElement;
    if (box.parentNode !== host) host.appendChild(box);
    var hide = !cur || !cur.isConnected || (video() && !controlsMode);
    if (hide) { box.style.display = 'none'; return; }
    var r = cur.getBoundingClientRect(), pad = 4;
    if (r.width < 1 || r.height < 1) { box.style.display = 'none'; return; }
    lastRect = r;
    box.style.left = (r.left - pad) + 'px';
    box.style.top = (r.top - pad) + 'px';
    box.style.width = (r.width + pad * 2) + 'px';
    box.style.height = (r.height + pad * 2) + 'px';
    box.style.display = 'block';
  }

  // Keeps the highlight glued to its element while the page scrolls/animates.
  function tick() {
    if (controlsMode && Date.now() - lastKey > CONTROLS_TIMEOUT) controlsMode = false;
    draw();
    requestAnimationFrame(tick);
  }

  // --- Video ---------------------------------------------------------------

  // The main video, if one fills a good part of the screen.
  function video() {
    var vs = document.querySelectorAll('video'), found = null, bestArea = 0;
    for (var i = 0; i < vs.length; i++) {
      var r = vs[i].getBoundingClientRect();
      var w = Math.max(0, Math.min(r.right, vw()) - Math.max(r.left, 0));
      var h = Math.max(0, Math.min(r.bottom, vh()) - Math.max(r.top, 0));
      if (w * h > bestArea) { bestArea = w * h; found = vs[i]; }
    }
    return found && bestArea > vw() * vh() * 0.4 ? found : null;
  }

  function anyVideo() { return video() || document.querySelector('video'); }

  function status(v) {
    return { a: 'video', t: v.currentTime || 0, d: isFinite(v.duration) ? v.duration : 0, p: v.paused };
  }

  function seekBy(v, sec) {
    if (isFinite(v.duration) && v.duration > 0) {
      v.currentTime = Math.max(0, Math.min(v.duration - 1, v.currentTime + sec));
    }
    return status(v);
  }

  function toggle(v) {
    if (v.paused) { var p = v.play(); if (p && p.catch) p.catch(function () {}); } else v.pause();
    return status(v);
  }

  // --- Entry points --------------------------------------------------------

  function key(dir, repeat) {
    var idle = Date.now() - lastKey > CONTROLS_TIMEOUT;
    lastKey = Date.now();
    if (idle) controlsMode = false;
    var v = video();
    if (v && !controlsMode) {
      if (dir === 'left' || dir === 'right') {
        var step = 10 * Math.min(6, 1 + Math.floor(repeat / 8));
        return seekBy(v, dir === 'left' ? -step : step);
      }
      // Up/Down on the player: move through its buttons instead.
      controlsMode = true;
      var start = initial(vw() / 2, vh() * 0.9);
      if (start) setCur(start); else draw();
      return { a: 'focus', wake: true };
    }
    if (!curValid()) {
      var first = initial(vw() * 0.3, vh() * 0.4);
      if (first) { setCur(first); return focusResult(first); }
      return pageScroll(dir);
    }
    var next = best(dir, cur.getBoundingClientRect());
    if (next) { setCur(next); return focusResult(next); }
    return pageScroll(dir);
  }

  // Nothing further in that direction: scroll so lazy-loaded rows appear.
  function pageScroll(dir) {
    if (dir !== 'up' && dir !== 'down') return { a: 'none' };
    window.scrollBy({ top: (dir === 'down' ? 1 : -1) * vh() * 0.5, behavior: 'instant' });
    return { a: 'none' };
  }

  function enter() {
    var idle = Date.now() - lastKey > CONTROLS_TIMEOUT;
    lastKey = Date.now();
    var v = video();
    if (v && (!controlsMode || idle)) { controlsMode = false; return toggle(v); }
    if (!curValid()) {
      var first = initial(vw() * 0.3, vh() * 0.4);
      if (first) { setCur(first); return focusResult(first); }
      return { a: 'none' };
    }
    reveal(cur);
    var r = cur.getBoundingClientRect();
    var cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    if (cur.tagName === 'IFRAME') return { a: 'iframe', x: cx, y: cy };
    if (cx >= 0 && cy >= 0 && cx < vw() && cy < vh()) {
      var hit = deepFromPoint(cx, cy);
      if (hit && related(cur, hit)) return { a: 'tap', x: cx, y: cy };
    }
    cur.click();
    return { a: 'handled' };
  }

  function back() {
    if (controlsMode && video()) {
      controlsMode = false;
      draw();
      return { a: 'handled' };
    }
    return { a: 'none' };
  }

  function playPause() {
    var v = anyVideo();
    return v ? toggle(v) : { a: 'none' };
  }

  function skip(forward) {
    var v = anyVideo();
    if (v && isFinite(v.duration) && v.duration > 0) return seekBy(v, forward ? 30 : -30);
    // No video: page down/up and put the focus on what is now visible.
    window.scrollBy({ top: (forward ? 1 : -1) * vh() * 0.8, behavior: 'instant' });
    var el = initial(vw() * 0.3, vh() * 0.4);
    if (el) setCur(el);
    return { a: 'handled' };
  }

  // Called after page loads / SPA navigations so a highlight is visible
  // without having to press a key first.
  function ensure() {
    if (curValid() || video()) { draw(); return { a: 'none' }; }
    var el = initial(vw() * 0.3, vh() * 0.4);
    if (el) setCur(el);
    return { a: 'none' };
  }

  function clear() {
    cur = null;
    draw();
    return { a: 'none' };
  }

  window.__tvnav = {
    key: key, enter: enter, back: back, playPause: playPause, skip: skip,
    ensure: ensure, clear: clear,
    _debug: { best: best, usable: usable, candidates: candidates, current: function () { return cur; } }
  };
})();
