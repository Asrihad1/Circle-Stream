package com.circleftp.stream.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.circleftp.stream.data.provider.model.StreamSource
import com.circleftp.stream.ui.theme.CircleCard
import com.circleftp.stream.ui.theme.CircleDarkBg
import com.circleftp.stream.ui.theme.CircleRed
import com.circleftp.stream.ui.theme.TextPrimary
import com.circleftp.stream.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream

/**
 * Dedicated Hardware-Accelerated Web Video Player for Global Cloud Streams (NetMirror/Embeds).
 *
 * Provides safe in-app playback for HTML5 multi-audio/embed streaming hosts:
 * - Aggressive ad-popup blocker (blocks spam redirects, malicious popups, and new tab popups).
 * - Multi-server switching (VidLink, Embed.su, VidSrc, AutoEmbed) with quick 1-tap switching.
 * - Deep anti-spam & anti-carrier-billing DOM scrubber (+880 SMS, Age Gate, Fake Captchas).
 * - Seamless HTML5 fullscreen video support via WebChromeClient.
 * - Hardware acceleration with Media playback support.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebStreamPlayer(
    title: String,
    streamUrl: String,
    availableStreams: List<StreamSource> = emptyList(),
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentStreamUrl by remember(streamUrl) { mutableStateOf(streamUrl) }
    var activeServerIndex by remember(streamUrl, availableStreams) {
        val found = availableStreams.indexOfFirst { it.url == streamUrl }
        mutableIntStateOf(if (found >= 0) found else 0)
    }

    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showServerMenu by remember { mutableStateOf(false) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var chromeClientRef by remember { mutableStateOf<WebChromeClient?>(null) }
    var showControls by remember { mutableStateOf(true) }

    fun loadUrlWithValidationHeaders(targetUrl: String, customHeaders: Map<String, String> = emptyMap()) {
        val extraHeaders = mutableMapOf<String, String>()
        val host = android.net.Uri.parse(targetUrl).host.orEmpty()
        val referer = customHeaders["Referer"]
            ?: if (host.isNotBlank()) "https://$host/" else "https://netmirror.app/"
        extraHeaders["Referer"] = referer
        customHeaders.forEach { (k, v) -> extraHeaders[k] = v }
        webViewRef?.loadUrl(targetUrl, extraHeaders)
    }

    fun selectServer(index: Int) {
        if (index in availableStreams.indices) {
            val target = availableStreams[index]
            activeServerIndex = index
            currentStreamUrl = target.url
            hasError = false
            errorMessage = null
            isLoading = true
            loadUrlWithValidationHeaders(target.url, target.headers)
        }
    }

    // Auto-hide top overlay controls after 5 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(5000)
            showControls = false
        }
    }

    // Cleanly exit fullscreen on lifecycle disposal
    DisposableEffect(Unit) {
        onDispose {
            if (customView != null) {
                chromeClientRef?.onHideCustomView()
            }
        }
    }

    BackHandler {
        if (customView != null) {
            chromeClientRef?.onHideCustomView()
        } else if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onClose()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Main Web Video Player View
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        allowFileAccess = false
                        allowContentAccess = false
                        // Enable multiple windows routing so WebChromeClient.onCreateWindow catches and destroys popups!
                        setSupportMultipleWindows(true)
                        javaScriptCanOpenWindowsAutomatically = false
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        // Spoof Desktop Browser to bypass CDN mobile blocks & carrier-billing
                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    }

                    webChromeClient = object : WebChromeClient() {
                        init {
                            chromeClientRef = this
                        }

                        // Prevents broken media / default gray film icon from showing during HTML5 video buffer
                        override fun getDefaultVideoPoster(): Bitmap? {
                            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                        }

                        // Deep popup suppression: Discard all popup/popunder windows created by ad scripts
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            return false // Immediately destroy the popup request
                        }

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            if (view == null) return

                            // If another custom view is showing, dismiss it first
                            if (customView != null) {
                                onHideCustomView()
                                return
                            }

                            customView = view
                            customViewCallback = callback

                            val act = context.findActivity()
                            val rootLayout = act?.findViewById<ViewGroup>(android.R.id.content)

                            (view.parent as? ViewGroup)?.removeView(view)
                            view.layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            view.setBackgroundColor(android.graphics.Color.BLACK)
                            rootLayout?.addView(view)

                            act?.let { a ->
                                a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                a.window?.let { win ->
                                    val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                                }
                            }
                        }

                        override fun onHideCustomView() {
                            val act = context.findActivity()
                            val rootLayout = act?.findViewById<ViewGroup>(android.R.id.content)

                            customView?.let { cv ->
                                rootLayout?.removeView(cv)
                                (cv.parent as? ViewGroup)?.removeView(cv)
                            }
                            customViewCallback?.onCustomViewHidden()

                            customView = null
                            customViewCallback = null

                            act?.let { a ->
                                a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                a.window?.let { win ->
                                    val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                                }
                            }
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            if (newProgress >= 75) {
                                isLoading = false
                            }
                        }

                        // Auto-dismiss intrusive JS alerts, confirms, and prompts
                        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                            result?.cancel()
                            return true
                        }

                        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                            result?.confirm()
                            return true
                        }

                        override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                            result?.cancel()
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            injectAntiSpamScrubber(view)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            injectAntiSpamScrubber(view)
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            if (request?.isForMainFrame == true) {
                                isLoading = false
                                hasError = true
                                val desc = error?.description?.toString() ?: "DNS / Connection issue"
                                errorMessage = "Mirror connection failed: $desc"
                            }
                        }

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val reqUrl = request?.url?.toString()?.lowercase() ?: return null

                            // Fast subresource ad-blocking: kill tracking, popups, and scam scripts before execution
                            if (reqUrl.contains("doubleclick") ||
                                reqUrl.contains("monetag") ||
                                reqUrl.contains("clickadu") ||
                                reqUrl.contains("alwingulla") ||
                                reqUrl.contains("poringasitia") ||
                                reqUrl.contains("propellerads") ||
                                reqUrl.contains("popads") ||
                                reqUrl.contains("adsterra") ||
                                reqUrl.contains("borojeet") ||
                                reqUrl.contains("p-app-link") ||
                                reqUrl.contains("syndication") ||
                                reqUrl.contains("adservice")
                            ) {
                                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val uri = request?.url ?: return false
                            val scheme = uri.scheme?.lowercase().orEmpty()

                            // 1. Block non-HTTP protocols (SMS, TEL, WAP, INTENT, MARKET) to prevent carrier billing
                            if (scheme != "http" && scheme != "https") {
                                return true
                            }

                            val destUrl = uri.toString().lowercase()
                            val destHost = uri.host?.lowercase().orEmpty()

                            // 2. Block search engines (prevent redirect to google.com when mirror is missing title)
                            if (destHost.contains("google.com") ||
                                destHost.contains("google.co") ||
                                destHost.contains("bing.com") ||
                                destHost.contains("yahoo.com")
                            ) {
                                return true
                            }

                            // 3. Block known malicious ad, carrier-billing, and verification redirect URLs
                            if (destUrl.contains("bet") ||
                                destUrl.contains("casino") ||
                                destUrl.contains("borojeet") ||
                                destUrl.contains("baji") ||
                                destUrl.contains("1xbet") ||
                                destUrl.contains("melbet") ||
                                destUrl.contains("track") ||
                                destUrl.contains("popunder") ||
                                destUrl.contains("adservice") ||
                                destUrl.contains("directlink") ||
                                destUrl.contains("apk") ||
                                destUrl.contains("download") ||
                                destUrl.contains("verify") ||
                                destUrl.contains("otp") ||
                                destUrl.contains("sms") ||
                                destUrl.contains("carrier") ||
                                destUrl.contains("billing") ||
                                destUrl.contains("subscribe") ||
                                destUrl.contains("vas") ||
                                destUrl.contains("age") ||
                                destUrl.contains("adult") ||
                                destUrl.contains("poringasitia") ||
                                destUrl.contains("monetag") ||
                                destUrl.contains("alwingulla") ||
                                destUrl.contains("clickadu") ||
                                destUrl.contains("propeller") ||
                                destUrl.contains("popads") ||
                                destUrl.contains("adsterra") ||
                                destUrl.contains("p-app-link") ||
                                destUrl.contains("moviesapi.vip")
                            ) {
                                return true
                            }

                            // 4. Strict Main Frame Navigation Confinement:
                            // Prevent embed iframes from hijacking the entire WebView window to unapproved third-party domains
                            if (request.isForMainFrame) {
                                val currentHost = android.net.Uri.parse(currentStreamUrl).host?.lowercase().orEmpty()
                                val isAuthorizedHost = destHost == currentHost ||
                                    destHost.contains("vidlink.pro") ||
                                    destHost.contains("embed.su") ||
                                    destHost.contains("vidsrc") ||
                                    destHost.contains("autoembed.co") ||
                                    destHost.contains("stream") ||
                                    destHost.contains("cdn") ||
                                    destHost.contains("m3u8")

                                if (!isAuthorizedHost) {
                                    return true // Block external hijack
                                }
                            }

                            return false // Allow legitimate media player asset loads
                        }
                    }

                    val initialHeaders = availableStreams.getOrNull(activeServerIndex)?.headers ?: emptyMap()
                    val extraHeaders = mutableMapOf<String, String>()
                    val host = android.net.Uri.parse(currentStreamUrl).host.orEmpty()
                    val referer = initialHeaders["Referer"]
                        ?: if (host.isNotBlank()) "https://$host/" else "https://netmirror.app/"
                    extraHeaders["Referer"] = referer
                    initialHeaders.forEach { (k, v) -> extraHeaders[k] = v }
                    loadUrl(currentStreamUrl, extraHeaders)
                }
            },
            update = { view ->
                // Maintain active instance
            }
        )

        // Loading Spinner Overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CircleRed)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Connecting to ${availableStreams.getOrNull(activeServerIndex)?.serverName?.substringBefore(" -") ?: "Cloud Stream"}...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Stream Error Fallback Banner
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CircleCard,
                    border = BorderStroke(1.dp, CircleRed),
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(max = 420.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = CircleRed,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Stream Unavailable on This Server",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage ?: "The current mirror could not provide this stream.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (availableStreams.size > 1) {
                            val nextIndex = (activeServerIndex + 1) % availableStreams.size
                            val nextServerName = availableStreams[nextIndex].serverName ?: "Server ${nextIndex + 1}"
                            Button(
                                onClick = { selectServer(nextIndex) },
                                colors = ButtonDefaults.buttonColors(containerColor = CircleRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Switch to $nextServerName")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Go Back", color = TextPrimary)
                        }
                    }
                }
            }
        }

        // Top Cinema Bar (Controls overlay)
        AnimatedVisibility(
            visible = showControls || isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                CircleDarkBg.copy(alpha = 0.95f),
                                CircleDarkBg.copy(alpha = 0.65f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onClose,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CircleRed
                            ) {
                                Text(
                                    text = "Global Cloud",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Auto Dual-Audio / Hindi",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // In-Player Multi-Server Switcher Dropdown
                    if (availableStreams.size > 1) {
                        Box {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = CircleCard.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier.clickable { showServerMenu = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Server",
                                        tint = CircleRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Server ${activeServerIndex + 1}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showServerMenu,
                                onDismissRequest = { showServerMenu = false },
                                modifier = Modifier.background(CircleDarkBg)
                            ) {
                                availableStreams.forEachIndexed { idx, src ->
                                    val isSelected = idx == activeServerIndex
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = src.serverName ?: "Server ${idx + 1}",
                                                    color = if (isSelected) CircleRed else Color.White,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.sp
                                                )
                                                if (!src.quality.isNullOrBlank()) {
                                                    Text(
                                                        text = src.quality,
                                                        color = TextSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            showServerMenu = false
                                            selectServer(idx)
                                        },
                                        trailingIcon = {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Active",
                                                    tint = CircleRed,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Fast Next Server Button
                        IconButton(
                            onClick = {
                                val nextIdx = (activeServerIndex + 1) % availableStreams.size
                                selectServer(nextIdx)
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Next Mirror",
                                tint = Color.White
                            )
                        }
                    }

                    IconButton(
                        onClick = { showControls = !showControls },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Deep anti-spam, anti-carrier-billing & anti-fake-captcha DOM scrubber.
 * Permanently removes fake "Age Verification", SMS verification prompts, WAP billing gates,
 * fake Cloudflare/Robot checks, and malicious popup overlays from third-party embed streaming hosts.
 */
private fun injectAntiSpamScrubber(view: WebView?) {
    val jsCode = """
        (function() {
            // 1. Neutralize aggressive JS window manipulation and permission spam
            try {
                window.open = function() { return null; };
                window.alert = function() { return true; };
                window.confirm = function() { return true; };
                window.prompt = function() { return null; };
                if (window.Notification) {
                    window.Notification.requestPermission = function() { return Promise.resolve('denied'); };
                }
                // Suppress frame-busting top-level redirects
                window.onbeforeunload = null;
            } catch(e) {}

            // 2. Inject aggressive CSS to auto-hide and unblock overlays
            var styleId = 'circle_anti_spam_shield';
            if (!document.getElementById(styleId)) {
                var style = document.createElement('style');
                style.id = styleId;
                style.innerHTML = `
                    div[class*="ad"], iframe[src*="doubleclick"], div[id*="pop"], .banner-ad,
                    div[class*="verify"], div[id*="verify"], div[class*="age-gate"], div[id*="age"],
                    div[class*="carrier"], div[id*="carrier"], div[class*="turnstile"], div[id*="turnstile"],
                    div[class*="recaptcha"], div[id*="recaptcha"],
                    .modal-backdrop, .overlay-popup, [data-ad], [id*="ad_"] {
                        display: none !important;
                        opacity: 0 !important;
                        pointer-events: none !important;
                        visibility: hidden !important;
                        z-index: -9999 !important;
                    }
                    body, html {
                        overflow: auto !important;
                    }
                    video {
                        width: 100% !important;
                        height: 100% !important;
                        object-fit: contain !important;
                    }
                    /* Ensure native & custom player UI control bars are always visible on top with pointer interaction */
                    video::-webkit-media-controls,
                    .jw-controls, .plyr__controls, .vjs-control-bar, [class*="control-bar"], [class*="player-controls"], [class*="player-ui"] {
                        display: flex !important;
                        opacity: 1 !important;
                        visibility: visible !important;
                        z-index: 2147483647 !important;
                        pointer-events: auto !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);
            }

            // 3. Ensure audio output is unmuted, volume is 100%, and media controls + fullscreen are active
            function ensureAudioAndControls() {
                var vids = document.querySelectorAll('video');
                vids.forEach(function(v) {
                    if (v.muted) {
                        v.muted = false;
                    }
                    v.volume = 1.0;
                    v.controls = true;
                });

                var iframes = document.querySelectorAll('iframe');
                iframes.forEach(function(f) {
                    if (!f.hasAttribute('allowfullscreen')) {
                        f.setAttribute('allowfullscreen', 'true');
                        f.setAttribute('webkitallowfullscreen', 'true');
                        f.setAttribute('mozallowfullscreen', 'true');
                        f.setAttribute('allow', 'fullscreen; autoplay; encrypted-media');
                    }
                });
            }

            // 4. Active DOM node scrubber: continuously search and delete fake verification modals & fake captchas
            function scrubModals() {
                ensureAudioAndControls();
                var targets = document.querySelectorAll('div, section, modal, dialog, .overlay, .popup, .modal, p, span, form, button');
                targets.forEach(function(el) {
                    // Do not remove video players or player containers
                    if (el.querySelector('video') || el.tagName.toLowerCase() === 'video') {
                        return;
                    }

                    var txt = (el.innerText || el.textContent || '').toLowerCase();
                    if (txt.includes('verification code') ||
                        txt.includes('age verification') ||
                        txt.includes('intended for people over 18') ||
                        txt.includes('confirm your age') ||
                        txt.includes('verification code was sent') ||
                        txt.includes('+880') ||
                        txt.includes('resend') ||
                        txt.includes('yes, i am 18') ||
                        txt.includes('not a robot') ||
                        txt.includes('check the box if you are not a robot') ||
                        txt.includes('verify you are human') ||
                        txt.includes('recaptcha') ||
                        txt.includes('turnstile') ||
                        txt.includes('borojeet') ||
                        txt.includes('carrier billing')
                    ) {
                        el.style.display = 'none';
                        try { el.remove(); } catch(err) {}
                    }
                });
            }

            scrubModals();
            setInterval(scrubModals, 300);
        })();
    """.trimIndent()

    view?.evaluateJavascript(jsCode, null)
}

/**
 * Safely resolves the nearest Activity from a Compose Context.
 */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
