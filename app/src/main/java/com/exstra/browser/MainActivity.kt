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
import android.view.View
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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var web: WebView
    private lateinit var home: ScrollView
    private lateinit var progress: android.widget.ProgressBar
    private lateinit var tabCount: TextView
    private val tabs = mutableListOf<String>()
    private val history = mutableListOf<String>()
    private var activeTab = 0
    private var engine = "Google"
    private var dark = true
    private var focusMode = false
    private var gestureX = 0f
    private var gestureY = 0f
    private val accent = Color.rgb(139, 92, 246)
    private val prefs by lazy { getSharedPreferences("exstra", Context.MODE_PRIVATE) }
    private val engines = linkedMapOf("Google" to "https://www.google.com/search?q=", "Bing" to "https://www.bing.com/search?q=", "Yandex" to "https://yandex.com/search/?text=", "DuckDuckGo" to "https://duckduckgo.com/?q=", "Brave" to "https://search.brave.com/search?q=")
    private val defaults = listOf("YouTube|▶|https://youtube.com", "Wikipedia|W|https://wikipedia.org", "GitHub|⌘|https://github.com", "Roblox|◇|https://roblox.com", "Minecraft|▦|https://minecraft.net", "Добавить|＋|")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dark = prefs.getBoolean("dark", true)
        engine = prefs.getString("engine", "Google") ?: "Google"
        setupBars()
        buildUi()
        restore()
    }

    private fun c(key: String): Int = when (key) {
        "bg" -> if (dark) Color.rgb(13, 13, 23) else Color.rgb(246, 247, 251)
        "surface" -> if (dark) Color.rgb(25, 25, 39) else Color.WHITE
        "surface2" -> if (dark) Color.rgb(35, 35, 52) else Color.rgb(234, 236, 244)
        "text" -> if (dark) Color.rgb(246, 245, 255) else Color.rgb(25, 27, 40)
        "muted" -> if (dark) Color.rgb(157, 160, 180) else Color.rgb(106, 111, 132)
        "line" -> if (dark) Color.rgb(49, 49, 66) else Color.rgb(219, 222, 233)
        else -> if (dark) Color.rgb(13, 13, 23) else Color.rgb(246, 247, 251)
    }

    private fun setupBars() {
        window.statusBarColor = c("bg")
        window.navigationBarColor = c("bg")
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = true
            window.isStatusBarContrastEnforced = true
        }
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c("bg"))
            setPadding(0, statusHeight(), 0, 0)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(10)); setBackgroundColor(c("bg")) }
        val brandRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val mark = TextView(this).apply {
            text = "✕"; textSize = 27f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(166, 112, 255)); background = rounded(c("surface2"), 13)
        }
        brandRow.addView(mark, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(10) })
        val brand = TextView(this).apply {
            text = "EXSTRA"; textSize = 20f; letterSpacing = .12f; setTextColor(c("text")); typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        brandRow.addView(brand, LinearLayout.LayoutParams(0, dp(42), 1f))
        tabCount = button(tabs.size.toString(), c("surface2")) { showTabs() }
        brandRow.addView(tabCount, LinearLayout.LayoutParams(dp(42), dp(42)))
        brandRow.addView(button("⚙", c("surface2")) { settings() }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { leftMargin = dp(8) })
        header.addView(brandRow)
        val search = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = rounded(c("surface"), 17, c("line")); setPadding(dp(9), 0, dp(5), 0) }
        search.addView(TextView(this).apply { text = "⌕"; textSize = 25f; gravity = Gravity.CENTER; setTextColor(accent) }, LinearLayout.LayoutParams(dp(32), dp(48)))
        address = EditText(this).apply {
            setSingleLine(); hint = "Поиск или адрес сайта"; setHintTextColor(c("muted")); setTextColor(c("text")); textSize = 14f; background = null; imeOptions = EditorInfo.IME_ACTION_GO; setPadding(dp(4),0,dp(4),0)
        }
        search.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f))
        search.addView(button(engine.take(2).uppercase(), c("surface2")) { chooseEngine() }, LinearLayout.LayoutParams(dp(38), dp(36)))
        search.addView(button("→", accent) { navigate() }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { leftMargin = dp(4) })
        header.addView(search, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })
        if (!focusMode) root.addView(header)
        progress = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progressTintList = android.content.res.ColorStateList.valueOf(accent); progressBackgroundTintList = android.content.res.ColorStateList.valueOf(c("bg")) }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(2)))

        home = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(c("bg")) }
        home.addView(homeContent())
        web = WebView(this).apply {
            setBackgroundColor(c("bg")); settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = prefs.getBoolean("images", true); settings.mediaPlaybackRequiresUserGesture = !prefs.getBoolean("autoplay", false)
            settings.javaScriptCanOpenWindowsAutomatically = false; settings.setSupportMultipleWindows(false)
            if (android.os.Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)
            if (android.os.Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { address.setText(url.orEmpty()); this@MainActivity.progress.setVisibility(android.view.View.VISIBLE) }
                override fun onPageFinished(view: WebView?, url: String?) {
                    this@MainActivity.progress.setVisibility(android.view.View.GONE); address.setText(url.orEmpty())
                    if (url != null && url.startsWith("http")) { history.remove(url); history.add(url); if (history.size > 100) history.removeAt(0); prefs.edit().putString("history", history.joinToString("\n")).apply(); refreshHome() }
                    syncTabs()
                }
            }
            webChromeClient = object : WebChromeClient() { override fun onProgressChanged(view: WebView?, value: Int) { this@MainActivity.progress.setProgress(value); this@MainActivity.progress.setVisibility(if (value >= 100) android.view.View.GONE else android.view.View.VISIBLE) } }
            setOnTouchListener { _, event -> gesture(event) }
            setDownloadListener(DownloadListener { url, ua, disposition, mime, _ ->
                val file = URLUtil.guessFileName(url, disposition, mime)
                val request = DownloadManager.Request(Uri.parse(url)).setMimeType(mime).addRequestHeader("User-Agent", ua).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, file)
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request); toast("Загрузка началась")
            })
        }
        root.addView(home, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f)); web.setVisibility(android.view.View.GONE)
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; background = android.graphics.drawable.ColorDrawable(c("surface")); setPadding(dp(8),dp(5),dp(8),dp(5)) }
        bottom.addView(bottomItem("‹", "Назад") { if (web.getVisibility() == android.view.View.VISIBLE && web.canGoBack()) web.goBack() else showHome() })
        bottom.addView(bottomItem("›", "Вперёд") { if (web.getVisibility() == android.view.View.VISIBLE && web.canGoForward()) web.goForward() })
        bottom.addView(bottomItem("⌂", "Главная") { showHome() })
        bottom.addView(bottomItem("▢", "Вкладки") { showTabs() })
        lateinit var menuButton: TextView
        menuButton = bottomItem("☰", "Меню") { showMenu(menuButton) }
        bottom.addView(menuButton)
        if (!focusMode) root.addView(bottom, LinearLayout.LayoutParams(-1, dp(58)))
        setContentView(root)
        address.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) { navigate(); true } else false }
    }

    private fun homeContent(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18),dp(20),dp(18),dp(24)) }
        page.addView(TextView(this).apply { text = "ТВОЙ ИНТЕРНЕТ. ТВОИ ПРАВИЛА."; textSize = 10f; letterSpacing = .12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(accent) })
        page.addView(TextView(this).apply { text = "Что ищем сегодня?"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; setTextColor(c("text")); setPadding(0,dp(5),0,dp(16)) })
        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(77,48,146),Color.rgb(98,61,191),Color.rgb(43,42,117))).apply { cornerRadius = dp(22).toFloat() }; setPadding(dp(18),dp(18),dp(18),dp(18)) }
        hero.addView(TextView(this).apply { text = "✕   EXSTRA  ·  FAST & SIMPLE"; textSize = 11f; letterSpacing = .08f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(215,203,255)) })
        hero.addView(TextView(this).apply { text = "Интернет без лишнего"; textSize = 21f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); setPadding(0,dp(8),0,dp(5)) })
        hero.addView(TextView(this).apply { text = "Поиск, сайты и твои вкладки — всё под рукой."; textSize = 13f; setTextColor(Color.rgb(226,221,246)) })
        hero.addView(button("Начать поиск  →", Color.argb(50,255,255,255)) { address.requestFocus(); keyboard(address) }, LinearLayout.LayoutParams(-1,dp(44)).apply { topMargin = dp(14) })
        page.addView(hero)
        if (prefs.getBoolean("home_shortcuts",true)) section(page, "Популярные сайты", "＋ Добавить") { addSite() }
        val sites = defaults + (prefs.getStringSet("shortcuts", emptySet())?.toList()?.map { it + "|•|" } ?: emptyList())
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (i in sites.indices step 3) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in i until minOf(i+3, sites.size)) {
                val parts = sites[j].split("|")
                row.addView(site(parts.getOrElse(0) { "Сайт" }, parts.getOrElse(1) { "•" }, parts.getOrElse(2) { "" }), LinearLayout.LayoutParams(0,dp(94),1f).apply { if (j%3 != 0) leftMargin = dp(8) })
            }
            rows.addView(row, LinearLayout.LayoutParams(-1,-2).apply { if (i>0) topMargin = dp(8) })
        }
        if (prefs.getBoolean("home_shortcuts",true)) page.addView(rows)
        if (prefs.getBoolean("home_recent",true)) section(page, "Недавние сайты", "Очистить") { clearHistory() }
        val recent = history.takeLast(4).reversed()
        if (prefs.getBoolean("home_recent",true)) { if (recent.isEmpty()) page.addView(TextView(this).apply { text = "Тут появятся недавно открытые сайты."; textSize = 13f; setTextColor(c("muted")); background = rounded(c("surface"),15,c("line")); setPadding(dp(14),dp(14),dp(14),dp(14)) }) else recent.forEach { page.addView(historyRow(it)) } }
        if (prefs.getBoolean("home_tools",true)) section(page, "Возможности EXSTRA", null) {}
        if (prefs.getBoolean("home_tools",true)) {
            val cards = listOf(
                tool("✦", "EXSTRA AI", "Офлайн-помощник и управление моделями") { aiAssistant() },
                tool("⌘", "EXSTRA COMMAND", "Команды браузера") { command() },
                tool("◷", "EXSTRA REWIND", "Сохранённые сессии") { rewind() },
                tool("⇩", "EXSTRA DROP", "Ссылки на потом") { showDrop() },
                tool("◉", "EXSTRA RADAR", "Состояние страницы и соединения") { radar() },
                tool("⚙", "Настройки", "Внешний вид, жесты и приватность") { settings() },
                tool("✦", "Настроить главную", "Скрыть или показать блоки") { customizeHome() }
            )
            cards.forEach { card ->
                page.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
        }
        return page
    }

    private fun refreshHome() { if (::home.isInitialized) { home.removeAllViews(); home.addView(homeContent()) } }
    private fun section(parent: LinearLayout, title: String, actionText: String?, action: () -> Unit) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0,dp(20),0,dp(10)) }
        row.addView(TextView(this).apply { text = title; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(c("text")) }, LinearLayout.LayoutParams(0,dp(34),1f))
        if (actionText != null) row.addView(TextView(this).apply { text = actionText; textSize = 12f; setTextColor(accent); gravity = Gravity.CENTER; setOnClickListener { action() } })
        parent.addView(row)
    }
    private fun site(title: String, glyph: String, url: String): LinearLayout {
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = rounded(c("surface"),16,c("line")); setOnClickListener { if (url.isBlank()) addSite() else open(url) } }
        card.addView(TextView(this).apply { text = glyph; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(accent); background = rounded(c("surface2"),13) }, LinearLayout.LayoutParams(dp(42),dp(42)))
        card.addView(TextView(this).apply { text = title; textSize = 12f; setTextColor(c("text")); gravity = Gravity.CENTER; setPadding(0,dp(6),0,0) })
        return card
    }
    private fun historyRow(url: String): LinearLayout {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = rounded(c("surface"),14,c("line")); setPadding(dp(12),dp(10),dp(12),dp(10)); setOnClickListener { open(url) } }
        row.addView(TextView(this).apply { text = "↗"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(accent); background = rounded(c("surface2"),12) }, LinearLayout.LayoutParams(dp(36),dp(36)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10),0,0,0) }
        labels.addView(TextView(this).apply { text = Uri.parse(url).host ?: url; textSize = 13f; setTextColor(c("text")); maxLines = 1 })
        labels.addView(TextView(this).apply { text = url; textSize = 10f; setTextColor(c("muted")); maxLines = 1 })
        row.addView(labels,LinearLayout.LayoutParams(0,-2,1f)); return row
    }
    private fun tool(symbol: String, title: String, sub: String, action: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = rounded(c("surface"),15,c("line")); setPadding(dp(12),dp(10),dp(12),dp(10)); setOnClickListener { action() } }
        row.addView(TextView(this).apply { text = symbol; textSize = 18f; gravity = Gravity.CENTER; setTextColor(accent); background = rounded(c("surface2"),12) },LinearLayout.LayoutParams(dp(38),dp(38)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10),0,0,0) }
        labels.addView(TextView(this).apply { text = title; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(c("text")) })
        labels.addView(TextView(this).apply { text = sub; textSize = 11f; setTextColor(c("muted")); setPadding(0,dp(3),0,0) })
        row.addView(labels,LinearLayout.LayoutParams(0,-2,1f)); row.addView(TextView(this).apply { text = "›"; textSize = 22f; setTextColor(c("muted")) })
        return row
    }

    private fun button(text: String, fill: Int, action: (TextView) -> Unit): TextView = TextView(this).apply { this.text = text; textSize = 12f; gravity = Gravity.CENTER; setTextColor(if (fill == accent) Color.WHITE else c("text")); background = rounded(fill,13); setPadding(dp(10),dp(8),dp(10),dp(8)); setOnClickListener { action(this) } }
    private fun bottomItem(symbol: String, label: String, action: () -> Unit) = TextView(this).apply { text = symbol + "\n" + label; textSize = 11f; gravity = Gravity.CENTER; setTextColor(if (label=="Главная") accent else c("muted")); layoutParams = LinearLayout.LayoutParams(0,dp(46),1f); setOnClickListener { action() } }
    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(radius).toFloat(); if (stroke != null) setStroke(dp(1),stroke) }
    private fun dp(v: Int) = (v*resources.displayMetrics.density).toInt()
    private fun statusHeight(): Int { val id = resources.getIdentifier("status_bar_height","dimen","android"); return if (id>0) resources.getDimensionPixelSize(id) else dp(24) }
    private fun keyboard(edit: EditText) { edit.post { (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(edit,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) } }
    private fun toast(message: String) = Toast.makeText(this,message,Toast.LENGTH_SHORT).show()

    private fun navigate() {
        val raw = address.text.toString().trim(); if (raw.isBlank()) return
        val url = if (raw.contains(".") && !raw.contains(" ")) { if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://" + raw } else (engines[engine] ?: engines["Google"]!!) + Uri.encode(raw)
        open(url)
    }
    private fun open(url: String) { if (tabs.isEmpty()) tabs.add(url); tabs[activeTab] = url; home.setVisibility(android.view.View.GONE); web.setVisibility(android.view.View.VISIBLE); web.loadUrl(url); address.setText(url); syncTabs() }
    private fun saveDrop(url: String = web.url ?: address.text.toString()) { if (!url.startsWith("http")) { toast("Сначала открой сайт"); return }; val items=prefs.getStringSet("drop",emptySet())?.toMutableSet() ?: mutableSetOf(); items.add(url); prefs.edit().putStringSet("drop",items).apply(); toast("Сохранено в EXSTRA DROP") }
    private fun showDrop() { val items=prefs.getStringSet("drop",emptySet())?.toList().orEmpty(); if(items.isEmpty()) { AlertDialog.Builder(this).setTitle("EXSTRA DROP").setMessage("Пока пусто. Сохраняй сюда ссылки, которые хочешь открыть позже.").setPositiveButton("Добавить текущую") { _,_ -> saveDrop() }.setNegativeButton("Закрыть",null).show() } else AlertDialog.Builder(this).setTitle("EXSTRA DROP · ${items.size}").setItems(items.map { Uri.parse(it).host ?: it }.toTypedArray()) { _,i -> open(items[i]) }.setNeutralButton("Добавить текущую") { _,_ -> saveDrop() }.setNegativeButton("Очистить") { _,_ -> prefs.edit().remove("drop").apply(); toast("DROP очищен") }.show() }
    private fun quickTools() { val options=arrayOf("Сохранить ссылку в DROP","Добавить в закладки","Поделиться ссылкой","Копировать ссылку","Обновить страницу","Найти на странице","Скриншот (системный)","Перевести страницу (открыть перевод)") ; AlertDialog.Builder(this).setTitle("Quick Tools").setItems(options) { _,i -> when(i){0->saveDrop();1->saveBookmark();2->shareUrl();3->copyUrl();4->web.reload();5->findOnPage();6->toast("Используй системный скриншот");7->web.url?.let { open("https://translate.google.com/translate?u="+Uri.encode(it)) } } }.show() }
    private fun shareUrl() { val u=web.url ?: return; val intent=android.content.Intent(android.content.Intent.ACTION_SEND).apply { type="text/plain"; putExtra(android.content.Intent.EXTRA_TEXT,u) }; startActivity(android.content.Intent.createChooser(intent,"Поделиться ссылкой")) }
    private fun copyUrl() { val u=web.url ?: address.text.toString(); if(u.isBlank())return; (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("EXSTRA URL",u)); toast("Ссылка скопирована") }
    private fun findOnPage() { val e=EditText(this).apply { hint="Найти текст" }; AlertDialog.Builder(this).setTitle("Поиск на странице").setView(e).setPositiveButton("Найти") { _,_ -> web.findAllAsync(e.text.toString()) }.setNegativeButton("Отмена",null).show() }
    private fun radar() { val url=web.url ?: "Домашняя страница"; val net=if(android.net.ConnectivityManager.CONNECTIVITY_ACTION.isNotEmpty()) "Проверяй соединение в системных настройках" else ""; AlertDialog.Builder(this).setTitle("EXSTRA RADAR").setMessage("Страница:\n$url\n\nЗагрузка: ${progress.progress}%\n\nWebView · JavaScript ${if(web.settings.javaScriptEnabled) "включён" else "выключен"}\nCookies ${if(CookieManager.getInstance().acceptCookie()) "разрешены" else "запрещены"}\nБезопасный просмотр: ${if(android.os.Build.VERSION.SDK_INT>=26) "доступен" else "версия Android старше"}").setPositiveButton("Обновить") { _,_ -> web.reload() }.setNeutralButton("Копировать URL") { _,_ -> copyUrl() }.setNegativeButton("ОК",null).show() }
    private fun toggleFocus() { focusMode=!focusMode; buildUi(); if(web.url!=null && tabs.isNotEmpty()) { web.loadUrl(tabs[activeTab]) }; toast(if(focusMode) "EXSTRA FOCUS включён · нажми назад для выхода" else "Обычный режим") }
    private fun customizeHome() { val labels=arrayOf("Быстрый доступ","Недавно посещённые","Инструменты"); val keys=arrayOf("home_shortcuts","home_recent","home_tools"); val checked=BooleanArray(3){prefs.getBoolean(keys[it],true)}; AlertDialog.Builder(this).setTitle("Настроить главную").setMultiChoiceItems(labels,checked){_,i,v->prefs.edit().putBoolean(keys[i],v).apply()}.setPositiveButton("Готово"){_,_->refreshHome()}.show() }
    private fun gesture(e: android.view.MotionEvent): Boolean { when(e.action){android.view.MotionEvent.ACTION_DOWN->{gestureX=e.x;gestureY=e.y}; android.view.MotionEvent.ACTION_UP->{val dx=e.x-gestureX;val dy=e.y-gestureY;if(kotlin.math.abs(dx)>dp(90)&&kotlin.math.abs(dx)>kotlin.math.abs(dy)*1.4){if(dx>0&&web.canGoBack())web.goBack() else if(dx<0&&tabs.size>1){activeTab=(activeTab+1)%tabs.size;open(tabs[activeTab])};return true}} };return false }
    private fun showHome() { web.setVisibility(android.view.View.GONE); home.setVisibility(android.view.View.VISIBLE); address.setText(""); syncTabs() }
    private fun newTab() { if (tabs.size>=20) { toast("Лимит — 20 вкладок"); return }; tabs.add("https://www.google.com"); activeTab=tabs.lastIndex; showHome(); syncTabs() }
    private fun syncTabs() { if (::tabCount.isInitialized) tabCount.text = tabs.size.toString()+" вкладок"; if (web.getVisibility()==android.view.View.VISIBLE && activeTab in tabs.indices) web.url?.let { tabs[activeTab]=it }; save() }

    private fun showTabs() {
        val items = tabs.mapIndexed { i, url -> (if (i==activeTab) "● " else "") + (i+1).toString()+" · "+(Uri.parse(url).host ?: "Новая вкладка") }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Вкладки · "+tabs.size+"/20").setItems(items) { _, i -> activeTab=i; val u=tabs[i]; if (u.startsWith("http")) open(u) else showHome() }
            .setNeutralButton("＋ Новая") { _,_ -> newTab() }.setNegativeButton("Закрыть активную") { _,_ ->
                if (tabs.size>1) { tabs.removeAt(activeTab); activeTab=activeTab.coerceAtMost(tabs.lastIndex); open(tabs[activeTab]) } else { tabs.clear(); tabs.add("https://google.com"); showHome() }
            }.show()
    }
    private fun showMenu(anchor: TextView) {
        val p=PopupMenu(this,anchor); listOf("Закладки","История","Загрузки","EXSTRA DROP","EXSTRA RADAR","Quick Tools","EXSTRA FOCUS","Поисковик","EXSTRA AI","EXSTRA COMMAND","EXSTRA REWIND","Настройки","О браузере").forEach { p.menu.add(it) }
        p.setOnMenuItemClickListener { item -> when(item.title.toString()) { "Закладки"->bookmarks(); "История"->showHistory(); "Загрузки"->downloads(); "Поисковик"->chooseEngine(); "EXSTRA AI"->aiAssistant(); "EXSTRA DROP"->showDrop(); "EXSTRA RADAR"->radar(); "Quick Tools"->quickTools(); "EXSTRA FOCUS"->toggleFocus(); "EXSTRA COMMAND"->command(); "EXSTRA REWIND"->rewind(); "Настройки"->settings(); "О браузере"->about() }; true }; p.show()
    }
    private fun chooseEngine() { val names=engines.keys.toTypedArray(); AlertDialog.Builder(this).setTitle("Поисковик").setSingleChoiceItems(names,names.indexOf(engine).coerceAtLeast(0)) { d,i -> engine=names[i]; prefs.edit().putString("engine",engine).apply(); d.dismiss(); toast("Выбран "+engine) }.setNegativeButton("Отмена",null).show() }
    private fun addSite() {
        val name=EditText(this).apply { hint="Название"; setSingleLine() }; val url=EditText(this).apply { hint="https://site.com"; setSingleLine() }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),0); addView(name); addView(url) }
        AlertDialog.Builder(this).setTitle("Добавить быстрый доступ").setView(box).setPositiveButton("Сохранить") { _,_ -> if (name.text.isNotBlank() && url.text.isNotBlank()) { val s=prefs.getStringSet("shortcuts",emptySet())?.toMutableSet() ?: mutableSetOf(); s.add(name.text.toString()+"|•|"+url.text.toString()); prefs.edit().putStringSet("shortcuts",s).apply(); refreshHome() } }.setNegativeButton("Отмена",null).show()
    }
    private fun bookmarks() { val list=prefs.getStringSet("bookmarks",emptySet())?.toList().orEmpty(); if (list.isEmpty()) toast("Закладок пока нет") else AlertDialog.Builder(this).setTitle("Закладки").setItems(list.toTypedArray()) { _,i -> open(list[i]) }.setNeutralButton("Добавить текущую") { _,_ -> saveBookmark() }.show() }
    private fun showBookmarks() = bookmarks()
    private fun saveBookmark() { val u=web.url ?: tabs.getOrNull(activeTab) ?: return; val b=prefs.getStringSet("bookmarks",emptySet())?.toMutableSet() ?: mutableSetOf(); b.add(u); prefs.edit().putStringSet("bookmarks",b).apply(); toast("Сохранено") }
    private fun showHistory() { if (history.isEmpty()) toast("История пуста") else AlertDialog.Builder(this).setTitle("История").setItems(history.reversed().toTypedArray()) { _,i -> open(history.reversed()[i]) }.setNeutralButton("Очистить") { _,_ -> clearHistory() }.show() }
    private fun clearHistory() { history.clear(); prefs.edit().remove("history").apply(); refreshHome(); toast("История очищена") }
    private fun downloads() {
        val cur=(getSystemService(DOWNLOAD_SERVICE) as DownloadManager).query(DownloadManager.Query()); val rows=mutableListOf<String>()
        if (cur!=null) { while(cur.moveToNext()) { val ti=cur.getColumnIndex(DownloadManager.COLUMN_TITLE); val si=cur.getColumnIndex(DownloadManager.COLUMN_STATUS); val name=if(ti>=0)cur.getString(ti) else "Файл"; val status=if(si>=0)cur.getInt(si) else -1; rows.add(name+" · "+if(status==DownloadManager.STATUS_SUCCESSFUL)"Готово" else "В процессе") }; cur.close() }
        AlertDialog.Builder(this).setTitle("Загрузки").setItems(rows.toTypedArray(),null).setPositiveButton("Закрыть",null).show()
    }
    private fun rewind() {
        val data=prefs.getStringSet("rewind",emptySet())?.toMutableSet() ?: mutableSetOf(); data.add(System.currentTimeMillis().toString()+"|"+activeTab+"|"+tabs.joinToString("~")); while(data.size>15) data.remove(data.minByOrNull { it.substringBefore("|").toLongOrNull() ?: 0L }); prefs.edit().putStringSet("rewind",data).apply()
        val sorted=data.sortedByDescending { it.substringBefore("|").toLongOrNull() ?: 0L }; val labels=sorted.map { val p=it.split("|",limit=3); "Сессия · "+(p.getOrNull(2)?.split("~")?.size ?: 0)+" вкладок · "+java.text.DateFormat.getDateTimeInstance().format(java.util.Date(p[0].toLong())) }.toTypedArray()
        AlertDialog.Builder(this).setTitle("EXSTRA REWIND").setItems(labels) { _,i -> val p=sorted[i].split("|",limit=3); val restored=p.getOrNull(2)?.split("~")?.filter { it.startsWith("http") }.orEmpty(); if(restored.isNotEmpty()){tabs.clear();tabs.addAll(restored.take(20));activeTab=(p.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0,tabs.lastIndex);open(tabs[activeTab])} }.setNegativeButton("Закрыть",null).show()
    }
    private fun command() {
        val input=EditText(this).apply { hint="help / open / search / back / reload / tabs"; setSingleLine(); imeOptions=EditorInfo.IME_ACTION_GO }
        AlertDialog.Builder(this).setTitle("EXSTRA COMMAND").setView(input).setPositiveButton("Выполнить") { _,_ ->
            val cmd=input.text.toString().trim(); val p=cmd.split(Regex("\\s+"),limit=2); val arg=p.getOrNull(1).orEmpty()
            when(p[0].lowercase()){ "open"->if(arg.isNotBlank())open(if(arg.contains(".")&&!arg.contains(" "))if(arg.startsWith("http"))arg else "https://"+arg else engines[engine]+Uri.encode(arg)); "search"->if(arg.isNotBlank())open(engines[engine]+Uri.encode(arg)); "back"->if(web.canGoBack())web.goBack(); "reload"->web.reload(); "home"->showHome(); "tabs"->showTabs(); "bookmark"->saveBookmark(); "drop"->saveDrop(); "focus"->toggleFocus(); "radar"->radar(); "tools"->quickTools(); "copy"->copyUrl(); "share"->shareUrl(); "help"->toast("open, search, back, reload, home, tabs, bookmark, drop, focus, radar, tools, copy, share"); else->toast("Неизвестная команда") }
        }.setNegativeButton("Отмена",null).show()
    }

    private fun aiAssistant() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        panel.addView(TextView(this).apply {
            text = "EXSTRA AI"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(c("text"))
        })
        panel.addView(TextView(this).apply {
            text = "Локальный помощник · без облачного сервера"
            textSize = 13f
            setTextColor(c("muted"))
            setPadding(0, dp(4), 0, dp(14))
        })
        val status = TextView(this).apply {
            text = "Модель пока не установлена"
            textSize = 14f
            setTextColor(c("text"))
            background = rounded(c("surface2"), 14)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        panel.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        panel.addView(button("Управление моделями", c("surface2")) { modelManager(status) },
            LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(12) })
        val chatPreview = TextView(this).apply {
            text = "Чат будет работать прямо на устройстве после подключения локального движка.\n\nПлан: GGUF-модель → llama.cpp → Android JNI. Модель хранится в памяти телефона, сервер не нужен."
            textSize = 13f
            setTextColor(c("muted"))
            background = rounded(c("surface"), 16, c("line"))
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        panel.addView(chatPreview, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val input = EditText(this).apply {
            hint = "Написать сообщение…"
            setSingleLine()
            setTextColor(c("text"))
            setHintTextColor(c("muted"))
            background = rounded(c("surface"), 14, c("line"))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isEnabled = false
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(48)))
        AlertDialog.Builder(this).setView(panel).setPositiveButton("Закрыть", null).show()
    }

    private fun modelManager(status: TextView) {
        val modelDir = getExternalFilesDir("models")
        val existing = modelDir?.listFiles()?.filter { it.extension.equals("gguf", true) }.orEmpty()
        val options = arrayOf("Выбрать GGUF-файл", "Открыть папку моделей", "О модели и совместимости")
        AlertDialog.Builder(this).setTitle("EXSTRA AI · Модели")
            .setItems(options) { _, index ->
                when (index) {
                    0 -> {
                        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                            type = "*/*"
                            addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        }
                        startActivityForResult(intent, 7101)
                    }
                    1 -> {
                        val names = existing.map { it.name + " · " + android.text.format.Formatter.formatFileSize(this, it.length()) }.toTypedArray()
                        if (names.isEmpty()) toast("Папка моделей пока пустая")
                        else AlertDialog.Builder(this).setTitle("Модели на устройстве").setItems(names, null).setPositiveButton("ОК", null).show()
                    }
                    2 -> AlertDialog.Builder(this).setTitle("Поддержка моделей")
                        .setMessage("На первом этапе выбираем компактные GGUF-модели. Сам файл можно хранить на телефоне. Для запуска потребуется встроить llama.cpp Android runtime; сейчас интерфейс подготовлен, но генерация ещё не подключена.")
                        .setPositiveButton("Понятно", null).show()
                }
            }.show()
    }

    @Deprecated("Use Activity Result API in a future refactor")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7101 && resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            val name = uri.lastPathSegment ?: "выбранный файл"
            prefs.edit().putString("selected_model_uri", uri.toString()).apply()
            toast("Файл выбран: $name. Для запуска нужен runtime llama.cpp.")
        }
    }
\n    private fun settings() {
        val labels=arrayOf("Тёмная тема","Загружать картинки","Автовоспроизведение","Сохранять вкладки","Очистить историю","Режим фокуса","Настроить главную","Панель Quick Tools")
        val checked=booleanArrayOf(dark,prefs.getBoolean("images",true),prefs.getBoolean("autoplay",false),prefs.getBoolean("save_session",true),false,focusMode,false,true)
        AlertDialog.Builder(this).setTitle("Настройки EXSTRA").setMultiChoiceItems(labels,checked) { _,i,v ->
            when(i){0->{dark=v;prefs.edit().putBoolean("dark",v).apply();setupBars();buildUi();restore()};1->{prefs.edit().putBoolean("images",v).apply();web.settings.loadsImagesAutomatically=v};2->{prefs.edit().putBoolean("autoplay",v).apply();web.settings.mediaPlaybackRequiresUserGesture=!v};3->prefs.edit().putBoolean("save_session",v).apply();4->if(v)clearHistory();5->if(v)toggleFocus();6->customizeHome();7->quickTools()}
        }.setPositiveButton("Готово",null).show()
    }
    private fun about() { AlertDialog.Builder(this).setTitle("EXSTRA").setMessage("EXSTRA Browser · версия 0.2\\nБыстрый доступ, вкладки, закладки, история и настройки приватности.").setPositiveButton("ОК",null).show() }
    private fun restore() { history.clear(); history.addAll(prefs.getString("history","").orEmpty().split("\n").filter { it.startsWith("http") }.takeLast(100)); tabs.clear(); tabs.addAll(prefs.getString("tabs","").orEmpty().split("\n").filter { it.isNotBlank() }.take(20)); activeTab=prefs.getInt("current",0).coerceIn(0,(tabs.size-1).coerceAtLeast(0)); if(tabs.isEmpty())tabs.add("https://www.google.com"); showHome(); refreshHome(); syncTabs() }
    private fun save() { if(prefs.getBoolean("save_session",true))prefs.edit().putString("tabs",tabs.joinToString("\n")).putInt("current",activeTab).putString("engine",engine).apply() }
    override fun onBackPressed() { if(web.getVisibility()==android.view.View.VISIBLE&&web.canGoBack())web.goBack() else showHome() }
    override fun onPause() { syncTabs(); save(); super.onPause() }
}
