package com.exstra.browser

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var web: WebView
    private lateinit var tabLabel: TextView
    private val tabs = mutableListOf<String>()
    private var current = 0
    private var engine = "Google"
    private val prefs by lazy { getSharedPreferences("exstra", Context.MODE_PRIVATE) }
    private val engines = linkedMapOf(
        "Google" to "https://www.google.com/search?q=",
        "Bing" to "https://www.bing.com/search?q=",
        "Yandex" to "https://yandex.com/search/?text=",
        "DuckDuckGo" to "https://duckduckgo.com/?q="
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        restoreSession()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6, 6, 6, 6)
        }
        address = EditText(this).apply {
            setSingleLine(true)
            hint = "Search or enter address"
            imeOptions = EditorInfo.IME_ACTION_GO
            textSize = 15f
        }
        bar.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f))
        bar.addView(button("GO") { navigateInput() })
        bar.addView(button("⌕") { showEngines(it) })
        root.addView(bar)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(button("‹") { if (web.canGoBack()) web.goBack() })
        controls.addView(button("›") { if (web.canGoForward()) web.goForward() })
        controls.addView(button("↻") { web.reload() })
        tabLabel = TextView(this).apply {
            text = "Tabs: 1"
            gravity = Gravity.CENTER
            setPadding(10, 8, 10, 8)
        }
        controls.addView(tabLabel)
        controls.addView(button("＋") { newTab("https://www.google.com") })
        controls.addView(button("Tabs") { showTabs() })
        controls.addView(button("★") { saveBookmark() })
        root.addView(controls)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            CookieManager.getInstance().setAcceptCookie(true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    address.setText(url.orEmpty())
                    updateTab()
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    address.setText(url.orEmpty())
                    updateTab()
                }
            }
            webChromeClient = WebChromeClient()
            setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(Uri.parse(url))
                    .setMimeType(mimeType)
                    .addRequestHeader("User-Agent", userAgent)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(this@MainActivity, "Download started", Toast.LENGTH_SHORT).show()
            })
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        address.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_GO) {
                navigateInput()
                true
            } else false
        }
    }

    private fun button(label: String, action: (Button) -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12f
            minWidth = dp(40)
            setPadding(4, 0, 4, 0)
            setOnClickListener { action(this) }
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun navigateInput() {
        val raw = address.text.toString().trim()
        if (raw.isEmpty()) return
        val url = if (raw.contains(".") && !raw.contains(" ")) {
            if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        } else {
            (engines[engine] ?: engines.getValue("Google")) + Uri.encode(raw)
        }
        web.loadUrl(url)
        address.clearFocus()
    }

    private fun showEngines(anchor: Button) {
        val popup = PopupMenu(this, anchor)
        engines.keys.forEach { popup.menu.add(it) }
        popup.setOnMenuItemClickListener {
            engine = it.title.toString()
            prefs.edit().putString("engine", engine).apply()
            Toast.makeText(this, "Search: $engine", Toast.LENGTH_SHORT).show()
            true
        }
        popup.show()
    }

    private fun newTab(url: String) {
        if (tabs.size >= 12) {
            Toast.makeText(this, "Tab limit reached (12)", Toast.LENGTH_SHORT).show()
            return
        }
        tabs.add(url)
        current = tabs.lastIndex
        web.loadUrl(url)
        updateTab()
        saveSession()
    }

    private fun updateTab() {
        if (tabs.isNotEmpty() && current in tabs.indices) {
            web.url?.let { tabs[current] = it }
        }
        if (::tabLabel.isInitialized) tabLabel.text = "Tabs: ${tabs.size}"
        if (tabs.isNotEmpty()) saveSession()
    }

    private fun showTabs() {
        val names = tabs.mapIndexed { index, url ->
            "${index + 1}. ${Uri.parse(url).host ?: url}"
        }.toTypedArray()
        if (names.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Open tabs")
            .setItems(names) { _, which ->
                current = which
                web.loadUrl(tabs[which])
                updateTab()
            }
            .setNegativeButton("Close current") { _, _ ->
                if (tabs.size > 1) {
                    tabs.removeAt(current)
                    current = current.coerceAtMost(tabs.lastIndex)
                    web.loadUrl(tabs[current])
                    updateTab()
                }
            }
            .setNeutralButton("New tab") { _, _ -> newTab("https://www.google.com") }
            .show()
    }

    private fun saveBookmark() {
        val url = web.url ?: return
        val bookmarks = prefs.getStringSet("bookmarks", emptySet())?.toMutableSet() ?: mutableSetOf()
        bookmarks.add(url)
        prefs.edit().putStringSet("bookmarks", bookmarks).apply()
        Toast.makeText(this, "Bookmark saved", Toast.LENGTH_SHORT).show()
    }

    private fun restoreSession() {
        engine = prefs.getString("engine", "Google") ?: "Google"
        val saved = prefs.getString("tabs", null)
        if (saved != null) tabs.addAll(saved.split("\n").filter { it.isNotBlank() })
        current = prefs.getInt("current", 0).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        if (tabs.isEmpty()) tabs.add("https://www.google.com")
        web.loadUrl(tabs[current])
        updateTab()
    }

    private fun saveSession() {
        prefs.edit()
            .putString("tabs", tabs.joinToString("\n"))
            .putInt("current", current)
            .putString("engine", engine)
            .apply()
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onPause() {
        updateTab()
        saveSession()
        super.onPause()
    }
}
