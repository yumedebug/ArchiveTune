/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.BackButton
import moe.rukamori.archivetune.innertube.utils.hasCompleteYouTubeLoginCookies
import moe.rukamori.archivetune.utils.resetAuthWebViewSession
import moe.rukamori.archivetune.viewmodels.LoginScreenState
import moe.rukamori.archivetune.viewmodels.LoginViewModel

private const val DEFAULT_LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
private const val YOUTUBE_MUSIC_PACKAGE_NAME = "com.google.android.apps.youtube.music"
private const val LOGIN_CONTEXT_RETRY_DELAY_MS = 1_000L
private const val LOGIN_CONTEXT_EXTRACTION_ATTEMPTS = 10

private const val LOGIN_CONTEXT_SCRIPT =
    "(function(){try{var c=window.ytcfg;var y=window.yt&&window.yt.config_;var s=document.querySelectorAll('script');var v=c&&c.get&&c.get('VISITOR_DATA')||y&&y.VISITOR_DATA;var d=c&&c.get&&c.get('DATASYNC_ID')||y&&y.DATASYNC_ID;for(var i=0;i<s.length&&(!v||!d);i++){var x=s[i].textContent;if(!v){var vm=x.match(/[\"']VISITOR_DATA[\"']\\s*:\\s*[\"']([^\"']+)[\"']/);if(vm)v=vm[1]}if(!d){var dm=x.match(/[\"'](?:DATASYNC_ID|dataSyncId)[\"']\\s*:\\s*[\"']([^\"']+)[\"']/);if(dm)d=dm[1]}}if(v)Android.onRetrieveVisitorData(v);if(d)Android.onRetrieveDataSyncId(d)}catch(e){}})();"

private val YOUTUBE_COOKIE_URLS =
    listOf(
        "https://music.youtube.com",
        "https://www.youtube.com",
        "https://youtube.com",
    )

/**
 * YouTube へのログイン画面。
 *
 * ユーザーはアプリを離さず、この中の Google ページでサインインするだけ。
 * サインインが済むと [YouTubeLoginWebViewClient] が Cookie を集めて
 * [viewModel] に渡し、成功したら [LoginScreenState.Success] になる。
 * 画面はトーストだけ出して自動で閉じる。
 *
 * ここで開くのはログインのための1画面だけで、他に WebView は使っていない。
 *
 * @param onBack 戻る操作（矢印・システムの戻る）で呼ぶ。
 * @param onSignedIn ログイン成功時、自動で閉じる前に呼く。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onSignedIn: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val palette = applePalette()
    val type = appleType()

    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val successMessage = stringResource(R.string.login_success)
    val context = LocalContext.current

    // 成功したら手を離す。画面は戻る先（ホーム or 設定）へ閉じる。
    LaunchedEffect(screenState) {
        if (screenState is LoginScreenState.Success) {
            Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            onSignedIn()
            onBack()
        }
    }

    // ページ内の戻る履歴があればまずそれを戻す。尽きたら画面を閉じる。
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }
    val currentWebView = webViewHolder.value
    BackHandler(enabled = currentWebView?.canGoBack() == true) {
        currentWebView?.goBack()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(palette.background),
    ) {
        // 上部の見出し。地色と下端のヘアラインだけで示し、枠や影は足さない。
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                    .background(palette.background),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClick = onBack)
                Text(
                    text = stringResource(R.string.youtube_login),
                    style = type.headline,
                    color = palette.onBackground,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(palette.separator),
            )
        }

        AndroidView(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
            factory = { context ->
                WebView(context).apply {
                    val cookieManager = CookieManager.getInstance()
                    webViewClient =
                        YouTubeLoginWebViewClient(
                            cookieManager = cookieManager,
                            onCookiesCaptured = viewModel::onCookiesCaptured,
                        )
                    settings.apply {
                        javaScriptEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                    val loginWebView = this
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onRetrieveVisitorData(newVisitorData: String?) {
                                loginWebView.post { viewModel.onVisitorDataExtracted(newVisitorData) }
                            }

                            @JavascriptInterface
                            fun onRetrieveDataSyncId(newDataSyncId: String?) {
                                loginWebView.post { viewModel.onDataSyncIdExtracted(newDataSyncId) }
                            }
                        },
                        "Android",
                    )
                    webViewHolder.value = this
                    resetAuthWebViewSession(context, this, clearCookies = true) {
                        loadUrl(DEFAULT_LOGIN_URL)
                    }
                }
            },
            onRelease = { releasedWebView ->
                (releasedWebView.webViewClient as? YouTubeLoginWebViewClient)?.release(releasedWebView)
                releasedWebView.removeJavascriptInterface("Android")
                releasedWebView.stopLoading()
                releasedWebView.destroy()
                if (webViewHolder.value === releasedWebView) {
                    webViewHolder.value = null
                }
            },
        )
    }
}

/**
 * ログイン用の WebView。
 *
 * YouTube のページで Cookie が揃ったら [onCookiesCaptured] に渡す。
 * サインイン直後のページは数秒かけて組み上がるため、同じページで
 * Cookie とユーザー情報（VISITOR_DATA / DATASYNC_ID）を何度か採取し直す。
 */
private class YouTubeLoginWebViewClient(
    private val cookieManager: CookieManager,
    private val onCookiesCaptured: (String) -> Unit,
) : WebViewClient() {
    private var extractionRunnable: Runnable? = null
    private var lastCapturedCookie: String? = null

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val targetUrl = request.url.toString()
        if (!request.isForMainFrame && !targetUrl.isWebViewLoadableUrl()) return true
        return handleUrlLoading(view, targetUrl)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(
        view: WebView,
        url: String?,
    ): Boolean = handleUrlLoading(view, url)

    override fun onPageFinished(
        view: WebView,
        url: String?,
    ) {
        super.onPageFinished(view, url)
        scheduleLoginContextExtraction(view, url)
    }

    override fun doUpdateVisitedHistory(
        view: WebView,
        url: String?,
        isReload: Boolean,
    ) {
        super.doUpdateVisitedHistory(view, url, isReload)
        scheduleLoginContextExtraction(view, url)
    }

    fun release(view: WebView) {
        extractionRunnable?.let(view::removeCallbacks)
        extractionRunnable = null
    }

    private fun scheduleLoginContextExtraction(
        view: WebView,
        url: String?,
    ) {
        extractionRunnable?.let(view::removeCallbacks)
        extractionRunnable = null

        if (!url.isYouTubeUrl()) return

        var remainingAttempts = LOGIN_CONTEXT_EXTRACTION_ATTEMPTS
        val runnable =
            object : Runnable {
                override fun run() {
                    if (!view.url.isYouTubeUrl()) return

                    view.evaluateJavascript(LOGIN_CONTEXT_SCRIPT, null)
                    captureCookies(view.url)

                    remainingAttempts -= 1
                    if (remainingAttempts > 0) {
                        view.postDelayed(this, LOGIN_CONTEXT_RETRY_DELAY_MS)
                    }
                }
            }
        extractionRunnable = runnable
        view.post(runnable)
    }

    private fun captureCookies(currentUrl: String?) {
        val mergedCookie = mergeYouTubeCookies(cookieManager, currentUrl) ?: return
        if (!hasCompleteYouTubeLoginCookies(mergedCookie) || mergedCookie == lastCapturedCookie) return

        lastCapturedCookie = mergedCookie
        onCookiesCaptured(mergedCookie)
    }

    private fun handleUrlLoading(
        view: WebView,
        url: String?,
    ): Boolean {
        val targetUrl = url?.takeIf(String::isNotBlank) ?: return false
        if (targetUrl.isYouTubeMusicStoreListingUrl()) return true
        if (targetUrl.isWebViewLoadableUrl()) return false

        targetUrl.intentWebViewUrl()?.let(view::loadUrl)
        return true
    }
}

private fun String.isWebViewLoadableUrl(): Boolean {
    val scheme = runCatching { Uri.parse(this).scheme?.lowercase() }.getOrNull()
    return scheme == "http" ||
        scheme == "https" ||
        scheme == "javascript" ||
        scheme == "data" ||
        scheme == "blob"
}

private fun String.intentWebViewUrl(): String? {
    val parsedIntent =
        runCatching { Intent.parseUri(this, Intent.URI_INTENT_SCHEME) }.getOrNull()
    return sequenceOf(
        parsedIntent?.dataString,
        intentUriAsHttpsUrl(),
        parsedIntent
            ?.getStringExtra("browser_fallback_url")
            ?.takeUnless(String::isYouTubeMusicStoreListingUrl),
    ).firstOrNull { it?.isHttpUrl() == true }
}

private fun String.intentUriAsHttpsUrl(): String? {
    val intentUri = runCatching { Uri.parse(this) }.getOrNull() ?: return null
    if (!intentUri.scheme.equals("intent", ignoreCase = true)) return null

    val authority = intentUri.encodedAuthority ?: return null
    val path = intentUri.encodedPath.orEmpty()
    val query = intentUri.encodedQuery?.let { "?$it" }.orEmpty()
    return "https://$authority$path$query"
}

private fun String.isHttpUrl(): Boolean {
    val scheme = runCatching { Uri.parse(this).scheme?.lowercase() }.getOrNull()
    return scheme == "http" || scheme == "https"
}

private fun String.isYouTubeMusicStoreListingUrl(): Boolean {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
    return uri.host.equals("play.google.com", ignoreCase = true) &&
        uri.path.equals("/store/apps/details", ignoreCase = true) &&
        uri.getQueryParameter("id") == YOUTUBE_MUSIC_PACKAGE_NAME
}

private fun String?.isYouTubeUrl(): Boolean {
    val host = this?.let(Uri::parse)?.host?.lowercase() ?: return false
    return host == "youtube.com" || host.endsWith(".youtube.com")
}

/**
 * YouTube 系の Cookie を1つに束ねる。
 *
 * Cookie は取得元の URL ごとに分かれているため、ログイン判定に使う
 * キー（SID / HSID / SSID / SAPISID / APISID と __Secure-3P 系）が揃うまで
 * 候補の URL を順に見ていって詰める。
 */
private fun mergeYouTubeCookies(
    cookieManager: CookieManager,
    currentUrl: String? = null,
): String? {
    val cookieParts = linkedMapOf<String, String>()
    val candidateUrls = linkedSetOf<String>()

    currentUrl.toYouTubeCookieOrigin()?.let(candidateUrls::add)
    candidateUrls.addAll(YOUTUBE_COOKIE_URLS)

    cookieManager.flush()

    candidateUrls.forEach { url ->
        cookieManager
            .getCookie(url)
            ?.split(";")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.forEach { part ->
                val separatorIndex = part.indexOf('=')
                if (separatorIndex <= 0) return@forEach

                val key = part.substring(0, separatorIndex).trim()
                val value = part.substring(separatorIndex + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    cookieParts.putIfAbsent(key, value)
                }
            }
    }

    return cookieParts
        .takeIf { it.isNotEmpty() }
        ?.entries
        ?.joinToString(separator = "; ") { (key, value) -> "$key=$value" }
}

private fun String?.toYouTubeCookieOrigin(): String? {
    val parsed = this?.let(Uri::parse) ?: return null
    val host = parsed.host?.lowercase() ?: return null
    if (host != "youtube.com" && !host.endsWith(".youtube.com")) return null

    val scheme =
        parsed.scheme
            ?.takeIf { it.equals("https", ignoreCase = true) || it.equals("http", ignoreCase = true) }
            ?: "https"

    return "$scheme://$host"
}
