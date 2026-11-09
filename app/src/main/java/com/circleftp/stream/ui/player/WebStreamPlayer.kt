package com.circleftp.stream.ui.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import com.circleftp.stream.ui.theme.CircleDarkBg
import com.circleftp.stream.ui.theme.CircleRed
import com.circleftp.stream.ui.theme.TextPrimary
import com.circleftp.stream.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Dedicated Hardware-Accelerated Web Video Player for Global Cloud Streams (NetMirror/Embeds).
 *
 * Provides safe in-app playback for HTML5 multi-audio/embed streaming hosts:
 * - Aggressive ad-popup blocker (blocks spam redirects, malicious popups, and new tab popups).
 * - Seamless HTML5 fullscreen video support via WebChromeClient.
 * - Hardware acceleration with Media playback support.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebStreamPlayer(
    title: String,
    streamUrl: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var showControls by remember { mutableStateOf(true) }

    // Auto-hide top overlay controls after 4 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    BackHandler {
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            customView = null
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

                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        allowFileAccess = false
                        allowContentAccess = false
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = false
                        // Desktop User-Agent prevents ad networks from triggering mobile carrier-billing / SMS gates
                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            customView = view
                            customViewCallback = callback
                        }

                        override fun onHideCustomView() {
                            customView = null
                            customViewCallback = null
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            if (newProgress >= 70) {
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

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val uri = request?.url ?: return false
                            val scheme = uri.scheme?.lowercase().orEmpty()

                            // Block non-HTTP protocols (SMS, TEL, WAP, INTENT, MARKET) to prevent carrier billing
                            if (scheme != "http" && scheme != "https") {
                                return true
                            }

                            val destUrl = uri.toString().lowercase()

                            // Block known malicious ad, carrier-billing, and verification redirect URLs
                            if (destUrl.contains("bet") ||
                                destUrl.contains("casino") ||
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
                                destUrl.contains("clickadu")
                            ) {
                                return true // Block redirect
                            }

                            return false // Allow legitimate media player asset loads
                        }
                    }

                    loadUrl(streamUrl)
                }
            },
            update = { view ->
                // Keep loaded
            }
        )

        // Fullscreen Custom View (when video element requests HTML5 fullscreen)
        if (customView != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        customView?.let { cv ->
                            (cv.parent as? ViewGroup)?.removeView(cv)
                            addView(cv, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                        }
                    }
                }
            )
        }

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
                        text = "Loading Cloud Stream...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
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
                                CircleDarkBg.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
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

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 16.sp,
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
                                    text = "Global Cloud Player",
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
 * Deep anti-spam & anti-carrier-billing DOM scrubber.
 * Permanently removes fake "Age Verification", SMS verification prompts, WAP billing gates,
 * and malicious popup overlays from third-party embed streaming hosts.
 */
private fun injectAntiSpamScrubber(view: WebView?) {
    val jsCode = """
        (function() {
            // 1. Inject aggressive CSS to auto-hide and unblock overlays
            var styleId = 'circle_anti_spam_shield';
            if (!document.getElementById(styleId)) {
                var style = document.createElement('style');
                style.id = styleId;
                style.innerHTML = `
                    div[class*="ad"], iframe[src*="doubleclick"], div[id*="pop"], .banner-ad,
                    div[class*="verify"], div[id*="verify"], div[class*="age-gate"], div[id*="age"],
                    .modal-backdrop, .overlay-popup, [data-ad], [id*="ad_"] {
                        display: none !important;
                        opacity: 0 !important;
                        pointer-events: none !important;
                        visibility: hidden !important;
                    }
                    video {
                        width: 100% !important;
                        height: 100% !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);
            }

            // 2. Active DOM node scrubber: continuously search and delete fake verification modals
            function scrubModals() {
                var targets = document.querySelectorAll('div, section, modal, dialog, .overlay, .popup, .modal');
                targets.forEach(function(el) {
                    var txt = (el.innerText || '').toLowerCase();
                    if (txt.includes('verification code') ||
                        txt.includes('age verification') ||
                        txt.includes('intended for people over 18') ||
                        txt.includes('confirm your age') ||
                        txt.includes('verification code was sent') ||
                        txt.includes('+880') ||
                        txt.includes('resend') ||
                        txt.includes('yes, i am 18')
                    ) {
                        el.style.display = 'none';
                        el.remove();
                    }
                });
            }

            scrubModals();
            setInterval(scrubModals, 350);
        })();
    """.trimIndent()

    view?.evaluateJavascript(jsCode, null)
}

