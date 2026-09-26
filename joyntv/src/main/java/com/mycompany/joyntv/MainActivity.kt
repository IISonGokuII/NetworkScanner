package com.mycompany.joyntv

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import org.json.JSONException
import org.json.JSONObject

class MainActivity : Activity() {

    companion object {
        const val HOME_URL = "https://www.joyn.at/"
        private const val PREFS = "joyntv"
        private const val PREF_HINT_SHOWN = "hint_shown_v2"
        private const val SEEK_SECONDS = 10
        private const val ENSURE_FOCUS_DELAY_MS = 1500L
    }

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var cursor: CursorView
    private lateinit var osd: OsdView
    private lateinit var navScript: String

    /** false: D-pad jumps between elements (default); true: D-pad moves a mouse pointer. */
    private var cursorMode = false
    private var okLongPressed = false

    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var lastBackPress = 0L

    private val density by lazy { resources.displayMetrics.density }
    private val ensureFocus = Runnable { nav("ensure()") }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        navScript = assets.open("tvnav.js").bufferedReader().use { it.readText() }

        root = FrameLayout(this)
        webView = WebView(this)
        cursor = CursorView(this)
        osd = OsdView(this)
        root.addView(webView, matchParent())
        root.addView(cursor, matchParent())
        root.addView(osd, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply {
            val margin = (32 * density).toInt()
            setMargins(margin * 2, 0, margin * 2, margin)
        })
        setContentView(root)
        cursor.visibility = View.GONE

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // joyn.at serves its full web player to desktop browsers; the Fire TV
            // WebView's default UA would get a mobile / "download the app" page.
            userAgentString = desktopUserAgent(userAgentString)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = JoynWebViewClient()
        webView.webChromeClient = JoynChromeClient()
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(HOME_URL)
        }
        showHintOnce()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        root.removeCallbacks(ensureFocus)
        root.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------
    // Remote control
    // ---------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Keys typed on the on-screen keyboard belong to the web page.
        if (event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD) return super.dispatchKeyEvent(event)

        val down = event.action == KeyEvent.ACTION_DOWN
        val up = event.action == KeyEvent.ACTION_UP

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (down) {
                    if (cursorMode) moveCursor(event) else navigate(event)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (down && event.repeatCount == 0) {
                    okLongPressed = false
                } else if (down && event.repeatCount >= 1 && !okLongPressed) {
                    // Holding OK switches between focus navigation and mouse pointer.
                    okLongPressed = true
                    setCursorMode(!cursorMode)
                } else if (up && !okLongPressed && !event.isCanceled) {
                    if (cursorMode) tapAt(cursor.cursorX, cursor.cursorY) else nav("enter()")
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (down && event.repeatCount == 0) nav("playPause()")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (down && event.repeatCount % 3 == 0) {
                    nav("skip(${event.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD})")
                }
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (down && event.repeatCount == 0) showMenu()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (up && !event.isCanceled) handleBack()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun direction(keyCode: Int) = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> "up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "left"
        else -> "right"
    }

    private fun navigate(event: KeyEvent) {
        nav("key('${direction(event.keyCode)}', ${event.repeatCount})")
    }

    /** Runs a call on the injected navigation script and acts on its answer. */
    private fun nav(call: String, onResult: ((JSONObject) -> Unit)? = null) {
        webView.evaluateJavascript("$navScript\nwindow.__tvnav.$call") { raw ->
            val result = try {
                JSONObject(raw ?: "null")
            } catch (_: JSONException) {
                JSONObject().put("a", "none")
            }
            handleNavResult(result)
            onResult?.invoke(result)
        }
    }

    private fun handleNavResult(result: JSONObject) {
        val scale = webView.scale
        when (result.optString("a")) {
            "tap" -> tapAt(result.getDouble("x").toFloat() * scale, result.getDouble("y").toFloat() * scale)
            "iframe" -> {
                // Cross-origin frames can't be navigated from script: hand over to the pointer.
                setCursorMode(true)
                cursor.moveTo(result.getDouble("x").toFloat() * scale, result.getDouble("y").toFloat() * scale)
            }
            "video" -> osd.show(result.optDouble("t"), result.optDouble("d"), result.optBoolean("p"))
        }
        if (result.optBoolean("wake")) {
            // Mouse movement makes the web player show its controls.
            hoverAt(root.width / 2f, root.height / 2f)
        }
    }

    private fun setCursorMode(enabled: Boolean) {
        cursorMode = enabled
        cursor.visibility = if (enabled && fullscreenView == null) View.VISIBLE else View.GONE
        if (enabled) nav("clear()") else nav("ensure()")
        Toast.makeText(
            this,
            if (enabled) "Mauszeiger an – OK lange drücken zum Ausschalten"
            else "Mauszeiger aus – Steuerkreuz springt zwischen Elementen",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun moveCursor(event: KeyEvent) {
        // Accelerate while the button is held down.
        val step = (10 + event.repeatCount.coerceAtMost(15) * 3) * density
        val (dx, dy) = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> 0f to -step
            KeyEvent.KEYCODE_DPAD_DOWN -> 0f to step
            KeyEvent.KEYCODE_DPAD_LEFT -> -step to 0f
            else -> step to 0f
        }
        val (overX, overY) = cursor.moveBy(dx, dy)
        if (overX != 0f || overY != 0f) {
            // Pointer hit the screen edge: scroll whatever is under it instead.
            scrollAtCursor(overX * 4, overY * 4)
        }
        hoverAt(cursor.cursorX, cursor.cursorY)
    }

    /** Target for synthetic input: the fullscreen player if shown, else the page. */
    private fun inputTarget(): View = fullscreenView ?: webView

    private fun tapAt(x: Float, y: Float) {
        val target = inputTarget()
        val time = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0)
        val upEvent = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, x, y, 0)
        downEvent.source = InputDevice.SOURCE_TOUCHSCREEN
        upEvent.source = InputDevice.SOURCE_TOUCHSCREEN
        target.dispatchTouchEvent(downEvent)
        target.dispatchTouchEvent(upEvent)
        downEvent.recycle()
        upEvent.recycle()
    }

    private fun hoverAt(x: Float, y: Float) {
        val time = SystemClock.uptimeMillis()
        val hover = MotionEvent.obtain(time, time, MotionEvent.ACTION_HOVER_MOVE, x, y, 0)
        hover.source = InputDevice.SOURCE_MOUSE
        inputTarget().dispatchGenericMotionEvent(hover)
        hover.recycle()
    }

    private fun scrollAtCursor(dxPx: Float, dyPx: Float) {
        val scale = webView.scale
        val x = cursor.cursorX / scale
        val y = cursor.cursorY / scale
        val dx = dxPx / scale
        val dy = dyPx / scale
        // Scroll the innermost scrollable element under the pointer (e.g. the
        // horizontal content rows), falling back to the page itself.
        webView.evaluateJavascript(
            """
            (function(x, y, dx, dy) {
              var e = document.elementFromPoint(x, y);
              while (e && e !== document.body && e !== document.documentElement) {
                var s = getComputedStyle(e);
                if (dx && /(auto|scroll)/.test(s.overflowX) && e.scrollWidth > e.clientWidth) { e.scrollBy(dx, 0); return; }
                if (dy && /(auto|scroll)/.test(s.overflowY) && e.scrollHeight > e.clientHeight) { e.scrollBy(0, dy); return; }
                e = e.parentElement;
              }
              window.scrollBy(dx, dy);
            })($x, $y, $dx, $dy);
            """.trimIndent(), null
        )
    }

    private fun handleBack() {
        if (fullscreenView != null) {
            webView.evaluateJavascript("document.exitFullscreen && document.exitFullscreen();", null)
            exitFullscreen()
            return
        }
        // Let the page leave the player's button row first.
        nav("back()") { result ->
            if (result.optString("a") == "handled") return@nav
            when {
                webView.canGoBack() -> webView.goBack()
                SystemClock.uptimeMillis() - lastBackPress < 2000 -> finish()
                else -> {
                    lastBackPress = SystemClock.uptimeMillis()
                    Toast.makeText(this, "Nochmal Zurück drücken zum Beenden", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showMenu() {
        val items = arrayOf(
            "Startseite",
            "Suche",
            "Live-TV",
            "Serien",
            "Filme",
            if (cursorMode) "Mauszeiger ausschalten" else "Mauszeiger einschalten",
            "Neu laden",
            "Hilfe zur Bedienung",
            "App beenden"
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> webView.loadUrl(HOME_URL)
                    1 -> webView.loadUrl(HOME_URL + "suche")
                    2 -> webView.loadUrl(HOME_URL + "play/live-tv")
                    3 -> webView.loadUrl(HOME_URL + "serien")
                    4 -> webView.loadUrl(HOME_URL + "filme")
                    5 -> setCursorMode(!cursorMode)
                    6 -> webView.reload()
                    7 -> showHelp()
                    8 -> finish()
                }
            }
            .show()
    }

    private fun showHintOnce() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_HINT_SHOWN, false)) {
            prefs.edit().putBoolean(PREF_HINT_SHOWN, true).apply()
            showHelp()
        }
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("Bedienung mit der Fernbedienung")
            .setMessage(
                "Steuerkreuz: Von Kachel zu Kachel / Menüpunkt springen\n" +
                    "OK: Auswählen\n" +
                    "Zurück: Vorherige Seite (2× auf der Startseite = Beenden)\n" +
                    "Menü (≡): Startseite, Suche, Live-TV, Serien, Filme …\n\n" +
                    "Beim Video:\n" +
                    "OK oder Play/Pause: Pause / weiter\n" +
                    "Links/Rechts: 10 s zurück/vor (gedrückt halten = schneller)\n" +
                    "Vor-/Zurückspulen: 30 s\n" +
                    "Hoch/Runter: Player-Tasten (Untertitel, Folgen …) auswählen,\n" +
                    "Zurück: wieder zum Video\n\n" +
                    "OK lange drücken: Mauszeiger ein/aus – für Stellen, die sich\n" +
                    "mit dem Steuerkreuz nicht erreichen lassen.\n\n" +
                    "Suche: Suchfeld auswählen und OK drücken, dann erscheint die Tastatur."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun scheduleEnsureFocus() {
        root.removeCallbacks(ensureFocus)
        if (!cursorMode) root.postDelayed(ensureFocus, ENSURE_FOCUS_DELAY_MS)
    }

    // ---------------------------------------------------------------------
    // Fullscreen video
    // ---------------------------------------------------------------------

    private fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden()
            return
        }
        fullscreenView = view
        fullscreenCallback = callback
        root.addView(view, 1, matchParent())
        webView.visibility = View.GONE
        cursor.visibility = View.GONE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun exitFullscreen() {
        val view = fullscreenView ?: return
        root.removeView(view)
        fullscreenView = null
        webView.visibility = View.VISIBLE
        cursor.visibility = if (cursorMode) View.VISIBLE else View.GONE
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
    }

    // ---------------------------------------------------------------------
    // WebView clients
    // ---------------------------------------------------------------------

    private inner class JoynWebViewClient : WebViewClient() {
        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
            handleUrl(Uri.parse(url))

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            handleUrl(request.url)

        private fun handleUrl(uri: Uri): Boolean {
            val scheme = uri.scheme ?: return false
            if (scheme == "http" || scheme == "https") return false
            // intent://, market:// etc. – try to hand them off, never show an error page.
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
            return true
        }

        override fun onPageFinished(view: WebView, url: String) {
            scheduleEnsureFocus()
        }

        // Also fires for in-page (single page app) navigations.
        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            scheduleEnsureFocus()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                Toast.makeText(
                    this@MainActivity,
                    "Seite konnte nicht geladen werden (${error.description}). Menü → Neu laden",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private inner class JoynChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            // Joyn streams are Widevine-protected: allow EME / protected media only.
            val granted = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
            if (granted.isNotEmpty()) {
                request.grant(granted.toTypedArray())
            } else {
                request.deny()
            }
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            enterFullscreen(view, callback)
        }

        override fun onHideCustomView() {
            exitFullscreen()
        }

        // Hides the grey placeholder WebView draws before a video starts.
        override fun getDefaultVideoPoster(): Bitmap =
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    // ---------------------------------------------------------------------

    private fun desktopUserAgent(defaultUa: String): String {
        val chromeVersion = Regex("Chrome/([\\d.]+)").find(defaultUa)?.groupValues?.get(1) ?: "124.0.0.0"
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36"
    }

    private fun matchParent() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
}
