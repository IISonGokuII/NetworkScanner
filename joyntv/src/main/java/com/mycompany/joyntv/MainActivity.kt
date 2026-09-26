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
import android.view.InputDevice
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

class MainActivity : Activity() {

    companion object {
        const val HOME_URL = "https://www.joyn.at/"
        private const val PREFS = "joyntv"
        private const val PREF_HINT_SHOWN = "hint_shown"
        private const val SEEK_SECONDS = 10
    }

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var cursor: CursorView

    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var lastBackPress = 0L

    private val density by lazy { resources.displayMetrics.density }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        root = FrameLayout(this)
        webView = WebView(this)
        cursor = CursorView(this)
        root.addView(webView, matchParent())
        root.addView(cursor, matchParent())
        setContentView(root)

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
        root.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------
    // Remote control
    // ---------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val inFullscreen = fullscreenView != null

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (inFullscreen) return true
                if (down) moveCursor(event)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (inFullscreen) {
                    if (down && event.repeatCount % 3 == 0) {
                        seek(if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -SEEK_SECONDS else SEEK_SECONDS)
                    }
                    return true
                }
                if (down) moveCursor(event)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (down && event.repeatCount == 0) {
                    if (inFullscreen) togglePlayPause() else clickAtCursor()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (down && event.repeatCount == 0) togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (down) {
                    val forward = event.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                    seekOrScroll(forward)
                }
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (down && event.repeatCount == 0) showMenu()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (!down && !event.isCanceled) handleBack()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
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
        sendHover()
    }

    private fun clickAtCursor() {
        val x = cursor.cursorX
        val y = cursor.cursorY
        val time = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0)
        val upEvent = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, x, y, 0)
        downEvent.source = InputDevice.SOURCE_TOUCHSCREEN
        upEvent.source = InputDevice.SOURCE_TOUCHSCREEN
        webView.dispatchTouchEvent(downEvent)
        webView.dispatchTouchEvent(upEvent)
        downEvent.recycle()
        upEvent.recycle()
    }

    private fun sendHover() {
        val time = SystemClock.uptimeMillis()
        val hover = MotionEvent.obtain(time, time, MotionEvent.ACTION_HOVER_MOVE, cursor.cursorX, cursor.cursorY, 0)
        hover.source = InputDevice.SOURCE_MOUSE
        webView.dispatchGenericMotionEvent(hover)
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

    private fun togglePlayPause() {
        webView.evaluateJavascript(
            """
            (function() {
              var v = document.querySelector('video');
              if (!v) return;
              if (v.paused) v.play(); else v.pause();
            })();
            """.trimIndent(), null
        )
    }

    private fun seek(seconds: Int) {
        webView.evaluateJavascript(
            """
            (function() {
              var v = document.querySelector('video');
              if (v && isFinite(v.duration)) v.currentTime = Math.max(0, Math.min(v.duration, v.currentTime + ($seconds)));
            })();
            """.trimIndent(), null
        )
    }

    private fun seekOrScroll(forward: Boolean) {
        val pageStep = webView.height * 0.8f / webView.scale
        webView.evaluateJavascript(
            """
            (function(fwd, page) {
              var v = document.querySelector('video');
              if (v && isFinite(v.duration) && v.duration > 0) {
                v.currentTime = Math.max(0, Math.min(v.duration, v.currentTime + (fwd ? 30 : -30)));
              } else {
                window.scrollBy(0, fwd ? page : -page);
              }
            })($forward, $pageStep);
            """.trimIndent(), null
        )
    }

    private fun handleBack() {
        when {
            fullscreenView != null -> {
                webView.evaluateJavascript("document.exitFullscreen && document.exitFullscreen();", null)
                exitFullscreen()
            }
            webView.canGoBack() -> webView.goBack()
            SystemClock.uptimeMillis() - lastBackPress < 2000 -> finish()
            else -> {
                lastBackPress = SystemClock.uptimeMillis()
                Toast.makeText(this, "Nochmal Zurück drücken zum Beenden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showMenu() {
        val items = arrayOf("Startseite", "Neu laden", "Hilfe zur Bedienung", "App beenden")
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> webView.loadUrl(HOME_URL)
                    1 -> webView.reload()
                    2 -> showHelp()
                    3 -> finish()
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
            .setTitle("Bedienung")
            .setMessage(
                "Steuerkreuz: Mauszeiger bewegen (am Bildrand wird gescrollt)\n" +
                    "OK: Klicken\n" +
                    "Zurück: Vorherige Seite / Vollbild verlassen\n" +
                    "Menü (≡): Startseite, Neu laden, Beenden\n" +
                    "Play/Pause: Video starten/pausieren\n" +
                    "Vor-/Zurückspulen: ±30 s im Video, sonst Seite scrollen\n" +
                    "Im Vollbild: Links/Rechts = ±10 s, OK = Pause\n\n" +
                    "Tipp: Beim ersten Start einloggen, damit Joyn dich erkennt."
            )
            .setPositiveButton("OK", null)
            .show()
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
        root.addView(view, matchParent())
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
        cursor.visibility = View.VISIBLE
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
