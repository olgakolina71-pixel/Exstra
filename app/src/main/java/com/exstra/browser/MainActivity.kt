package com.exstra.browser

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
    private lateinit var loadingBar: android.widget.ProgressBar
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

    private val ink = Color.rgb(17, 24, 39)
    private val muted = Color.rgb(148, 163, 184)
    private val accent = Color.rgb(99, 102, 241)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(11, 18, 32)
        window.navigationBarColor = Color.rgb(11, 18, 32)
        window.decorView.systemUiVisibility = 0
        buildUi()
        restoreSession()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(0, statusBarHeight(), 0, 0)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ink)
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        val brandRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val brand = TextView(this).apply {
            text = "EXSTRA"
            textSize = 18f
            letterSpacing = 0.12f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        brandRow.addView(brand, LinearLayout.LayoutParams(0, dp(32), 1f))
        tabLabel = TextView(this).apply {
            text = "1 TAB"
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(Color.rgb(199, 210, 254))
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(Color.rgb(39, 48, 75), 30)
        }
        brandRow.addView(tabLabel)
        header.addView(brandRow)

        val addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(31, 41, 61), 16)
            setPadding(dp(12), 0, dp(6), 0)
        }
        val lock = TextView(this).apply {
            text = "⌁"
            textSize = 20f
            setTextColor(Color.rgb(165, 180, 252))
            gravity = Gravity.CENTER
        }
        addressRow.addView(lock, LinearLayout.LayoutParams(dp(28), dp(46)))
        address = EditText(this).apply {
            setSingleLine(true)
            hint = "Search or enter address"
            setHintTextColor(Color.rgb(148, 163, 184))
            setTextColor(Color.WHITE)
            textSize = 14f
            background = null
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dp(4), 0, dp(4), 0)
        }
        addressRow.addView(address, LinearLayout.LayoutParams(0, dp(46), 1f))
        val engineButton = smallAction("⌄") { showEngines(it) }
        addressRow.addView(engineButton, LinearLayout.LayoutParams(dp(38), dp(38)))
        header.addView(addressRow, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        root.addView(header)

        loadingBar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            this.progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }
        root.addView(loadingBar, LinearLayout.LayoutParams(-1, dp(2)))

        web = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = prefs.getBoolean("load_images", true)
            settings.mediaPlaybackRequiresUserGesture = !prefs.getBoolean("autoplay", false)
            if (android.os.Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
            if (android.os.Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
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
                    loadingBar.progress = 100
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loadingBar.progress = newProgress
                    loadingBar.visibility = if (newProgress >= 100) android.view.View.GONE else android.view.View.VISIBLE
                }
            }
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

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(ink)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        bottom.addView(bottomAction("‹", "Back") { if (web.canGoBack()) web.goBack() })
        bottom.addView(bottomAction("›", "Forward") { if (web.canGoForward()) web.goForward() })
        bottom.addView(bottomAction("⌂", "Home") { web.loadUrl("https://www.google.com") })
        bottom.addView(bottomAction("＋", "New tab") { newTab("https://www.google.com") })
        bottom.addView(bottomAction("▦", "Tabs") { showTabs() })
        bottom.addView(bottomAction("⋯", "More") { showMore(it) })
        root.addView(bottom, LinearLayout.LayoutParams(-1, dp(58)))
        setContentView(root)

        address.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) {
                navigateInput()
                true
            } else false
        }
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun smallAction(label: String, action: (TextView) -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(51, 65, 95), 12)
            setOnClickListener { action(this) }
        }

    private fun bottomAction(symbol: String, label: String, action: (TextView) -> Unit): TextView =
        TextView(this).apply {
            text = "$symbol\n$label"
            textSize = 11f
            setTextColor(if (label == "Home") Color.rgb(165, 180, 252) else Color.rgb(226, 232, 240))
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(2), dp(4), dp(2))
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
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

    private fun showEngines(anchor: TextView) {
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

    private fun showMore(anchor: TextView) {
        val popup = PopupMenu(this, anchor)
        listOf("Reload", "Bookmarks", "Search engine", "EXSTRA COMMAND", "Save bookmark", "EXSTRA REWIND", "Skins", "Data Control", "Modules", "Tab groups").forEach { popup.menu.add(it) }
        popup.setOnMenuItemClickListener {
            when (it.title.toString()) {
                "Reload" -> web.reload()
                "Bookmarks" -> showBookmarks()
                "Search engine" -> showEngines(anchor)
                "EXSTRA COMMAND" -> showCommandConsole()
                "Save bookmark" -> saveBookmark()
                "EXSTRA REWIND" -> showRewind()
                "Skins" -> showSkins()
                "Data Control" -> showDataControl()
                "Modules" -> showModules()
                "Tab groups" -> showTabGroups()
            }
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
        if (tabs.isNotEmpty() && current in tabs.indices) web.url?.let { tabs[current] = it }
        if (::tabLabel.isInitialized) tabLabel.text = "${tabs.size} TAB${if (tabs.size == 1) "" else "S"}"
        if (tabs.isNotEmpty()) saveSession()
    }

    private fun showTabs() {
        val names = tabs.mapIndexed { index, url -> "${index + 1}. ${Uri.parse(url).host ?: url}" }.toTypedArray()
        if (names.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Your tabs")
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

    private fun showBookmarks() {
        val saved = prefs.getStringSet("bookmarks", emptySet())?.toList().orEmpty()
        if (saved.isEmpty()) {
            Toast.makeText(this, "No bookmarks yet", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Bookmarks").setItems(saved.toTypedArray()) { _, which ->
            web.loadUrl(saved[which])
        }.setNegativeButton("Close", null).show()
    }

    private fun showCommandConsole() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        val output = TextView(this).apply {
            text = "EXSTRA COMMAND v1\nType help to see available commands.\n"
            textSize = 13f
            setTextColor(Color.rgb(74, 222, 128))
            setBackgroundColor(Color.rgb(15, 23, 42))
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val input = EditText(this).apply {
            setSingleLine(true)
            hint = "Enter command..."
            textSize = 14f
        }
        panel.addView(output, LinearLayout.LayoutParams(-1, dp(170)))
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(52)))
        val dialog = AlertDialog.Builder(this).setTitle("EXSTRA COMMAND").setView(panel)
            .setNegativeButton("Close", null).create()
        fun runCommand() {
            val raw = input.text.toString().trim()
            if (raw.isEmpty()) return
            val parts = raw.split(Regex("\\s+"), limit = 2)
            val command = parts[0].lowercase()
            val argument = if (parts.size > 1) parts[1].trim() else ""
            val result = when (command) {
                "help" -> "Commands: help, url, open <address>, search <text>, back, forward, reload, newtab, tabs, bookmark, engine, clear"
                "url" -> "Current URL: ${web.url ?: "none"}"
                "open", "search" -> {
                    if (argument.isBlank()) "Usage: $command <text>" else {
                        address.setText(argument); navigateInput(); "Opening: $argument"
                    }
                }
                "back" -> if (web.canGoBack()) { web.goBack(); "Going back" } else "No previous page"
                "forward" -> if (web.canGoForward()) { web.goForward(); "Going forward" } else "No next page"
                "reload" -> { web.reload(); "Reloading page" }
                "newtab" -> { newTab("https://www.google.com"); "New tab opened" }
                "tabs" -> "Open tabs: ${tabs.size} / 12"
                "bookmark" -> { saveBookmark(); "Bookmark saved" }
                "engine" -> "Search engine: $engine"
                "rewind" -> { showRewind(); "Opening session history" }
                "skin" -> { showSkins(); "Opening skins" }
                "data" -> { showDataControl(); "Opening data controls" }
                "modules" -> { showModules(); "Opening modules" }
                "group" -> { showTabGroups(); "Opening tab groups" }
                "clear" -> { output.text = "EXSTRA COMMAND v1\nConsole cleared.\n"; input.text.clear(); return }
                else -> "Unknown command: $command. Type help."
            }
            output.append("\n> $raw\n$result\n")
            input.text.clear()
        }
        val run = TextView(this).apply {
            text = "RUN"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(accent, 12)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { runCommand() }
        }
        panel.addView(run, LinearLayout.LayoutParams(-1, dp(44)))
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) { runCommand(); true } else false
        }
        dialog.show()
    }

    private fun showRewind() {
        val items = prefs.getStringSet("rewind", emptySet())?.toMutableSet() ?: mutableSetOf()
        val snapshot = System.currentTimeMillis().toString() + "|" + current + "|" + tabs.joinToString("~")
        items.add(snapshot)
        while (items.size > 20) items.remove(items.minByOrNull { it.substringBefore("|").toLongOrNull() ?: 0L })
        prefs.edit().putStringSet("rewind", items).apply()
        val ordered = items.sortedByDescending { it.substringBefore("|").toLongOrNull() ?: 0L }
        if (ordered.isEmpty()) return
        val labels = ordered.map { item ->
            val p = item.split("|", limit = 3)
            val count = p.getOrNull(2)?.split("~")?.size ?: 0
            "Session · " + count + " tabs · " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(p[0].toLong()))
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("EXSTRA REWIND · Sessions").setItems(labels) { _, which ->
            val p = ordered[which].split("|", limit = 3)
            val restored = p.getOrNull(2)?.split("~")?.filter { it.startsWith("http") }.orEmpty()
            if (restored.isNotEmpty()) {
                tabs.clear(); tabs.addAll(restored.take(12))
                current = (p.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, tabs.lastIndex)
                web.loadUrl(tabs[current]); updateTab(); saveSession()
            }
        }.setNegativeButton("Close", null).show()
    }

    private fun showSkins() {
        val names = arrayOf("Midnight Indigo", "Graphite", "Light")
        AlertDialog.Builder(this).setTitle("EXSTRA SKINS").setSingleChoiceItems(names, prefs.getInt("skin", 0)) { dialog, which ->
            prefs.edit().putInt("skin", which).apply()
            val colors = when (which) { 1 -> intArrayOf(Color.rgb(25,29,36), Color.rgb(48,55,67)); 2 -> intArrayOf(Color.WHITE, Color.rgb(238,242,247)); else -> intArrayOf(Color.rgb(17,24,39), Color.rgb(31,41,61)) }
            root.setBackgroundColor(colors[0]); dialog.dismiss()
            Toast.makeText(this, names[which] + " applied to browser controls", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun showDataControl() {
        val labels = arrayOf("Data saver mode", "Load images", "Allow autoplay")
        val checked = booleanArrayOf(prefs.getBoolean("data_saver", false), prefs.getBoolean("load_images", true), prefs.getBoolean("autoplay", false))
        AlertDialog.Builder(this).setTitle("EXSTRA DATA CONTROL").setMultiChoiceItems(labels, checked) { _, which, value ->
            val keys = arrayOf("data_saver", "load_images", "autoplay")
            prefs.edit().putBoolean(keys[which], value).apply()
        }.setPositiveButton("Apply") { _, _ ->
            web.settings.loadsImagesAutomatically = prefs.getBoolean("load_images", true)
            web.settings.mediaPlaybackRequiresUserGesture = !prefs.getBoolean("autoplay", false)
            Toast.makeText(this, "Data settings applied", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun showModules() {
        val names = arrayOf("Bookmarks", "Downloads", "Command console", "Rewind", "Data Control", "Skins")
        val checked = BooleanArray(names.size) { prefs.getBoolean("module_" + names[it], true) }
        AlertDialog.Builder(this).setTitle("EXSTRA MODULES").setMultiChoiceItems(names, checked) { _, which, value ->
            prefs.edit().putBoolean("module_" + names[which], value).apply()
        }.setPositiveButton("Save", null).show()
    }

    private fun showTabGroups() {
        val names = arrayOf("Work", "Study", "Personal", "Entertainment")
        val groups = prefs.getStringSet("tab_groups", emptySet())?.toMutableSet() ?: mutableSetOf()
        AlertDialog.Builder(this).setTitle("Assign current tab to group").setItems(names) { _, which ->
            groups.removeAll { it.endsWith("|" + current) }
            groups.add(names[which] + "|" + current)
            prefs.edit().putStringSet("tab_groups", groups).apply()
            Toast.makeText(this, "Tab added to " + names[which], Toast.LENGTH_SHORT).show()
        }.setNeutralButton("Show groups") { _, _ ->
            val rows = groups.map { it.replace("|", " · tab ") }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Tab groups").setItems(rows, null).setPositiveButton("OK", null).show()
        }.setNegativeButton("Close", null).show()
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
        if (saved != null) tabs.addAll(saved.split("\n").filter { it.isNotBlank() }.take(12))
        current = prefs.getInt("current", 0).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        if (tabs.isEmpty()) tabs.add("https://www.google.com")
        web.loadUrl(tabs[current])
        updateTab()
    }

    private fun saveSession() {
        prefs.edit().putString("tabs", tabs.joinToString("\n")).putInt("current", current)
            .putString("engine", engine).apply()
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
