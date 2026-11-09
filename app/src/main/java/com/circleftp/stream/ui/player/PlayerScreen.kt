package com.circleftp.stream.ui.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import android.view.LayoutInflater
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.circleftp.stream.R
import com.circleftp.stream.data.api.CircleFtpApiClient
import com.circleftp.stream.data.iptv.IptvCatalog
import com.circleftp.stream.data.iptv.IptvChannel
import com.circleftp.stream.data.iptv.IptvStreamResolver
import com.circleftp.stream.data.model.PostSummary
import kotlinx.coroutines.launch
import com.circleftp.stream.player.*
import com.circleftp.stream.security.AntiReverseGuard
import androidx.compose.ui.draw.clip
import com.circleftp.stream.ui.theme.CircleDarkBg
import com.circleftp.stream.ui.theme.CircleCard
import com.circleftp.stream.ui.theme.CircleRed
import com.circleftp.stream.ui.theme.CircleSurface
import com.circleftp.stream.ui.theme.TextPrimary
import com.circleftp.stream.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current

    var activeTitle by remember { mutableStateOf(ActivePlayerSession.title) }
    var activeStreamUrl by remember { mutableStateOf(ActivePlayerSession.streamUrl) }

    val scope = rememberCoroutineScope()
    val apiClient = remember { CircleFtpApiClient() }
    var showCastDialog by remember { mutableStateOf(false) }
    var showExploreDrawer by remember { mutableStateOf(false) }
    var explorePosts by remember { mutableStateOf<List<PostSummary>>(emptyList()) }
    var isExploreLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val home = apiClient.getHomePage()
            explorePosts = home.latestPost + home.mostVisitedPosts
        } catch (_: Exception) {}
    }

    // App Security Protection Verification
    LaunchedEffect(Unit) {
        if (AntiReverseGuard.isDebuggerConnected()) {
            Toast.makeText(context, "Security Alert: Debugging detected", Toast.LENGTH_SHORT).show()
        }
    }

    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // Dialog & Control States
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showDecoderDialog by remember { mutableStateOf(false) }
    var showAspectDialog by remember { mutableStateOf(false) }
    var showEpisodeDrawer by remember { mutableStateOf(false) }
    var showChannelDrawer by remember { mutableStateOf(false) }
    var channelSearchQuery by remember { mutableStateOf("") }

    val isLiveIptv = remember(activeStreamUrl) {
        ActivePlayerSession.providerId == "iptvidn" || activeStreamUrl.contains(".m3u8")
    }
    val netSpeed by rememberNetworkSpeed()

    val activeChannels = remember { IptvCatalog.ACTIVE_CHANNELS }
    val currentChannelIndex = remember(activeTitle) {
        activeChannels.indexOfFirst {
            it.name.equals(activeTitle, ignoreCase = true) || it.id.equals(activeTitle, ignoreCase = true)
        }
    }

    fun switchToChannel(channel: IptvChannel) {
        scope.launch {
            isBuffering = true
            val resolved = IptvStreamResolver.resolveStream(channel.id)
            val url = resolved?.primaryStreamUrl
            if (!url.isNullOrBlank()) {
                ActivePlayerSession.set(
                    mediaId = "iptv_${channel.id}",
                    title = channel.name,
                    streamUrl = url,
                    isSeries = false,
                    providerId = "iptvidn",
                    headers = resolved.headers
                )
                activeTitle = channel.name
                activeStreamUrl = url
                showChannelDrawer = false
                retryTrigger++
                android.widget.Toast.makeText(context, "Playing: ${channel.name}", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Stream temporarily unavailable", android.widget.Toast.LENGTH_SHORT).show()
                isBuffering = false
            }
        }
    }

    fun switchNextChannel() {
        if (activeChannels.isNotEmpty()) {
            val nextIdx = if (currentChannelIndex >= 0) {
                (currentChannelIndex + 1) % activeChannels.size
            } else 0
            switchToChannel(activeChannels[nextIdx])
        }
    }

    fun switchPrevChannel() {
        if (activeChannels.isNotEmpty()) {
            val prevIdx = if (currentChannelIndex > 0) {
                currentChannelIndex - 1
            } else activeChannels.size - 1
            switchToChannel(activeChannels[prevIdx])
        }
    }

    // Controls Auto-Hide & Synchronization State
    var areControlsVisible by remember { mutableStateOf(true) }
    var playerViewRef by remember { mutableStateOf<androidx.media3.ui.PlayerView?>(null) }

    // Position & Episode Progression States
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var showNextEpisodePrompt by remember { mutableStateOf(false) }
    var nextEpisodeCountdown by remember { mutableIntStateOf(5) }

    var audioTracks by remember { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<SubtitleTrackOption>>(emptyList()) }

    val settings = remember { com.circleftp.stream.data.settings.AppSettings.getInstance(context) }
    var currentDecoderMode by remember { mutableStateOf(settings.defaultDecoder) }
    var currentAspectMode by remember { mutableStateOf(settings.defaultAspect) }
    var autoPlayNextEnabled by remember { mutableStateOf(settings.autoPlayNextEpisode) }
    var videoWidth by remember { mutableIntStateOf(1920) }
    var videoHeight by remember { mutableIntStateOf(1080) }

    // Controls 3.5-second auto-hide timer
    LaunchedEffect(areControlsVisible) {
        if (areControlsVisible) {
            delay(3500)
            areControlsVisible = false
            playerViewRef?.hideController()
        }
    }

    // Auto-load series playlist in background if not already populated
    LaunchedEffect(Unit) {
        if (ActivePlayerSession.postId > 0L) {
            try {
                val detail = apiClient.getPostDetail(ActivePlayerSession.postId) ?: return@LaunchedEffect
                if (ActivePlayerSession.posterUrl.isNullOrBlank()) {
                    ActivePlayerSession.posterUrl = detail.backdropUrl.ifBlank { detail.posterUrl }
                }
                ActivePlayerSession.isSeries = detail.isSeries
                if (detail.isSeries) {
                    ActivePlayerSession.parentSeriesId = detail.id.toString()
                    val allEpisodes = if (detail.seasons.isNotEmpty()) {
                        detail.seasons.flatMap { it.episodes }
                    } else {
                        detail.multiFiles
                    }
                    if (allEpisodes.isNotEmpty()) {
                        ActivePlayerSession.playlist = allEpisodes
                        val normalizedActive = UrlNormalizer.normalize(activeStreamUrl)
                        val found = allEpisodes.indexOfFirst {
                            UrlNormalizer.normalize(it.streamUrl) == normalizedActive || it.title == activeTitle
                        }
                        ActivePlayerSession.currentIndex = if (found >= 0) found else 0
                        ActivePlayerSession.episodeNumber = ActivePlayerSession.currentIndex + 1
                        ActivePlayerSession.seriesTitle = detail.title
                    }
                    ActivePlayerSession.mediaId = com.circleftp.stream.data.local.buildCanonicalMediaId(
                        streamUrl = activeStreamUrl,
                        isSeries = true,
                        parentSeriesId = ActivePlayerSession.parentSeriesId
                    )
                } else {
                    ActivePlayerSession.playlist = emptyList()
                    ActivePlayerSession.currentIndex = -1
                    ActivePlayerSession.episodeNumber = null
                    ActivePlayerSession.seasonNumber = null
                    ActivePlayerSession.parentSeriesId = null
                    ActivePlayerSession.mediaId = com.circleftp.stream.data.local.buildCanonicalMediaId(
                        streamUrl = activeStreamUrl,
                        isSeries = false,
                        parentSeriesId = null
                    )
                }
            } catch (_: Exception) {}
        }
    }

    // Reset episode-specific flags when activeStreamUrl changes
    LaunchedEffect(activeStreamUrl) {
        currentPositionMs = 0L
        showNextEpisodePrompt = false
        nextEpisodeCountdown = 5
    }

    // Auto-countdown for next episode
    LaunchedEffect(showNextEpisodePrompt) {
        if (showNextEpisodePrompt) {
            nextEpisodeCountdown = 5
            while (nextEpisodeCountdown > 0) {
                delay(1000)
                nextEpisodeCountdown--
            }
            if (showNextEpisodePrompt) {
                val nextEp = ActivePlayerSession.getNextEpisode()
                if (nextEp != null) {
                    ActivePlayerSession.switchToIndex(ActivePlayerSession.currentIndex + 1)
                    activeTitle = nextEp.title
                    activeStreamUrl = nextEp.streamUrl
                    showNextEpisodePrompt = false
                    currentPositionMs = 0L
                    retryTrigger++
                    Toast.makeText(context, "Playing next: ${nextEp.title}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Swipe Volume & Brightness HUD
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVolumePercent by remember {
        mutableFloatStateOf(
            (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()) * 100f
        )
    }
    var currentBrightnessPercent by remember {
        val window = activity?.window
        val lp = window?.attributes?.screenBrightness ?: 0.5f
        mutableFloatStateOf((if (lp < 0f) 0.5f else lp) * 100f)
    }
    var showGestureIndicator by remember { mutableStateOf<String?>(null) }
    var gestureIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector>(Icons.Default.VolumeUp) }

    // Double Tap Indicator
    var doubleTapFeedback by remember { mutableStateOf<String?>(null) }

    // Pinch-to-zoom scaling
    var zoomScale by remember { mutableFloatStateOf(1f) }
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        zoomScale = (zoomScale * zoomChange).coerceIn(0.75f, 3.5f)
    }

    // Auto-hide gesture HUD
    LaunchedEffect(showGestureIndicator) {
        if (showGestureIndicator != null) {
            delay(1200)
            showGestureIndicator = null
        }
    }
    LaunchedEffect(doubleTapFeedback) {
        if (doubleTapFeedback != null) {
            delay(900)
            doubleTapFeedback = null
        }
    }

    // Subtitle Local Import
    val subtitleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(context, "Loaded subtitle file", Toast.LENGTH_SHORT).show()
        }
    }

    // 1. Immersive Full-screen (Hides Navigation Bar & Status Bar with Transient Swipe)
    DisposableEffect(Unit) {
        val window = activity?.window
        val originalOrientation = activity?.requestedOrientation

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            window.decorView.keepScreenOn = true
        }

        onDispose {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                window.decorView.keepScreenOn = false
            }
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val isInitialWebStream = remember(activeStreamUrl) {
        ActivePlayerSession.providerId == "netmirror" ||
        activeStreamUrl.contains("/embed", ignoreCase = true) ||
        activeStreamUrl.contains("autoembed", ignoreCase = true) ||
        activeStreamUrl.contains("vidsrc", ignoreCase = true) ||
        activeStreamUrl.contains("multiembed", ignoreCase = true)
    }
    var isWebStreamMode by remember(activeStreamUrl) {
        mutableStateOf(isInitialWebStream)
    }

    // Player instance built with PotPlayer OpenCodec & HW/HW+/SW selection
    val player = remember(retryTrigger, currentDecoderMode, activeStreamUrl) {
        CircleVideoPlayerFactory.create(context, currentDecoderMode).apply {
            val mediaItem = CircleVideoPlayerFactory.buildMediaItem(activeTitle, activeStreamUrl, ActivePlayerSession.subtitles)
            setMediaItem(mediaItem)
            if (!isWebStreamMode) {
                prepare()
                playWhenReady = true
            }

            // Volume boost for SW mode
            if (currentDecoderMode == DecoderMode.SW) {
                volume = 1.35f
            }

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY) {
                        playbackError = null
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        if (settings.autoPlayNextEpisode && ActivePlayerSession.hasNextEpisode()) {
                            showNextEpisodePrompt = true
                        }
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    audioTracks = TrackSelectorHelper.getAudioTracks(this@apply)
                    subtitleTracks = TrackSelectorHelper.getSubtitleTracks(this@apply)
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoWidth = videoSize.width
                        videoHeight = videoSize.height
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    isBuffering = false
                    val errorMsg = error.cause?.message ?: error.message ?: "Playback error"

                    // Intelligent Hardware -> Software Decoder Fallback (Video & Audio):
                    // If hardware decoder fails (e.g. c2.unisoc.hevc.decoder crash or AC3 audio failure),
                    // automatically fall back to universal Software (Google SW / FFmpeg) mode
                    if (errorMsg.contains("Decoder failed", ignoreCase = true) ||
                        errorMsg.contains("Decoder init failed", ignoreCase = true) ||
                        errorMsg.contains("unisoc", ignoreCase = true) ||
                        errorMsg.contains("c2.", ignoreCase = true) ||
                        errorMsg.contains("hevc", ignoreCase = true) ||
                        errorMsg.contains("audio", ignoreCase = true) ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED) {

                        if (currentDecoderMode == DecoderMode.HW) {
                            Toast.makeText(
                                context,
                                "Hardware decoder failed. Auto-switching to SW decoder...",
                                Toast.LENGTH_SHORT
                            ).show()
                            currentDecoderMode = DecoderMode.SW
                            playbackError = null
                            return
                        }
                    }

                    // Intelligent Extractor / Web Stream Fallback:
                    // If ExoPlayer cannot extract binary video because the URL returned an HTML embed/web player,
                    // seamlessly fall back to the hardware-accelerated WebStreamPlayer
                    if (errorMsg.contains("None of the available extractors", ignoreCase = true) ||
                        errorMsg.contains("UnrecognizedInputFormatException", ignoreCase = true) ||
                        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                    ) {
                        android.util.Log.i("PlayerScreen", "Container extraction failed; transitioning to WebStreamPlayer for $activeStreamUrl")
                        isWebStreamMode = true
                        playbackError = null
                        return
                    }

                    playbackError = "Stream Error: $errorMsg"
                }
            })
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    val canonicalMediaId = remember(activeStreamUrl, ActivePlayerSession.isSeries, ActivePlayerSession.parentSeriesId) {
        ActivePlayerSession.mediaId.ifBlank {
            com.circleftp.stream.data.local.buildCanonicalMediaId(
                streamUrl = activeStreamUrl,
                isSeries = ActivePlayerSession.isSeries,
                parentSeriesId = if (ActivePlayerSession.isSeries) {
                    ActivePlayerSession.parentSeriesId ?: ActivePlayerSession.postId.takeIf { it > 0L }?.toString()
                } else {
                    null
                }
            )
        }
    }

    if (!isLiveIptv) {
        RememberPlaybackTracker(
            player = player,
            mediaId = canonicalMediaId,
            title = activeTitle,
            streamUrl = activeStreamUrl,
            posterUrl = ActivePlayerSession.posterUrl,
            sourceId = "CIRCLE_FTP",
            isSeries = ActivePlayerSession.isSeries,
            episodeNumber = if (ActivePlayerSession.isSeries) ActivePlayerSession.episodeNumber else null,
            seasonNumber = if (ActivePlayerSession.isSeries) ActivePlayerSession.seasonNumber else null,
            parentSeriesId = if (ActivePlayerSession.isSeries) {
                ActivePlayerSession.parentSeriesId ?: ActivePlayerSession.postId.takeIf { it > 0L }?.toString()
            } else {
                null
            }
        )
    }

    // Periodic time & position tracker (every 500ms)
    LaunchedEffect(player, activeStreamUrl) {
        while (true) {
            val pos = player.currentPosition
            val dur = player.duration
            currentPositionMs = pos
            if (dur > 0) totalDurationMs = dur

            // Auto-Play Next Episode prompt in last 10s of video
            val hasNext = ActivePlayerSession.hasNextEpisode()
            if (autoPlayNextEnabled && hasNext && dur > 30_000L) {
                val remainingMs = dur - pos
                if (remainingMs in 1L..10_000L) {
                    if (!showNextEpisodePrompt) {
                        showNextEpisodePrompt = true
                    }
                }
            }

            delay(500)
        }
    }

    BackHandler {
        onClose()
    }

    if (isWebStreamMode) {
        DisposableEffect(Unit) {
            player.pause()
            onDispose { }
        }
        WebStreamPlayer(
            title = activeTitle,
            streamUrl = activeStreamUrl,
            availableStreams = ActivePlayerSession.availableStreams,
            onClose = onClose
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .transformable(state = transformState, enabled = settings.pinchZoomEnabled)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        areControlsVisible = !areControlsVisible
                        if (areControlsVisible) {
                            playerViewRef?.showController()
                        } else {
                            playerViewRef?.hideController()
                        }
                    },
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        val stepMs = settings.seekIntervalSeconds * 1000L
                        if (offset.x < screenWidth * 0.4f) {
                            val newPos = (player.currentPosition - stepMs).coerceAtLeast(0L)
                            player.seekTo(newPos)
                            doubleTapFeedback = "⏪ ${settings.seekIntervalSeconds} sec"
                        } else if (offset.x > screenWidth * 0.6f) {
                            val duration = player.duration
                            val newPos = player.currentPosition + stepMs
                            player.seekTo(if (duration > 0) newPos.coerceAtMost(duration) else newPos)
                            doubleTapFeedback = "${settings.seekIntervalSeconds} sec ⏩"
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                if (!settings.gesturesEnabled) return@pointerInput
                detectDragGestures { change, dragAmount ->
                    val screenWidth = size.width
                    val screenHeight = size.height
                    val x = change.position.x
                    val y = change.position.y
                    val deltaY = -dragAmount.y // Drag up is positive, drag down is negative

                    // YouTube-style: Pull up from bottom to explore videos while playing
                    if (y > screenHeight * 0.65f && deltaY > 35f) {
                        showExploreDrawer = true
                        return@detectDragGestures
                    }

                    if (x < screenWidth * 0.5f) {
                        // Left side swipe: Brightness control
                        val window = activity?.window
                        if (window != null) {
                            val current = currentBrightnessPercent
                            val updated = (current + (deltaY / 3f)).coerceIn(1f, 100f)
                            currentBrightnessPercent = updated

                            val lp = window.attributes
                            lp.screenBrightness = updated / 100f
                            window.attributes = lp

                            gestureIcon = Icons.Default.BrightnessMedium
                            showGestureIndicator = "Brightness: ${updated.roundToInt()}%"
                        }
                    } else {
                        // Right side swipe: Volume control
                        val current = currentVolumePercent
                        val updated = (current + (deltaY / 3f)).coerceIn(0f, 100f)
                        currentVolumePercent = updated

                        val targetVol = ((updated / 100f) * maxVolume).roundToInt()
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)

                        gestureIcon = if (targetVol == 0) Icons.Default.VolumeMute else Icons.Default.VolumeUp
                        showGestureIndicator = "Volume: ${updated.roundToInt()}%"
                    }
                }
            }
    ) {
        val screenWidthDp = configuration.screenWidthDp.toFloat()
        val screenHeightDp = configuration.screenHeightDp.toFloat()
        val screenAspect = if (screenHeightDp > 0f) screenWidthDp / screenHeightDp else (16f / 9f)
        val videoAspect = if (videoHeight > 0) videoWidth.toFloat() / videoHeight.toFloat() else (16f / 9f)

        val (aspectScaleX, aspectScaleY) = when (currentAspectMode) {
            AspectRatioMode.FIT -> Pair(1f, 1f)
            AspectRatioMode.FILL -> {
                if (screenAspect > videoAspect) {
                    val scale = screenAspect / videoAspect
                    Pair(scale, scale)
                } else {
                    val scale = videoAspect / screenAspect
                    Pair(scale, scale)
                }
            }
            AspectRatioMode.STRETCH -> {
                if (screenAspect > videoAspect) {
                    Pair(screenAspect / videoAspect, 1f)
                } else {
                    Pair(1f, videoAspect / screenAspect)
                }
            }
            AspectRatioMode.ORIGINAL -> Pair(1f, 1f)
        }

        // Core Video Surface with Zoom and Aspect Ratio
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = aspectScaleX * zoomScale
                    scaleY = aspectScaleY * zoomScale
                },
            factory = { ctx ->
                val view = LayoutInflater.from(ctx).inflate(R.layout.player_view_custom, null, false) as PlayerView
                playerViewRef = view
                view.apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 3500
                    controllerAutoShow = true
                    keepScreenOn = true

                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        areControlsVisible = (visibility == android.view.View.VISIBLE)
                    })

                    resizeMode = when (currentAspectMode) {
                        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }

                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = when (currentAspectMode) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                view.requestLayout()
            }
        )

        // Overlay: Custom Status Overlay (Network Speed & HW Decoder Status)
        // Moved completely out of center alignment and anchored to TopStart with 16.dp padding
        // to guarantee it does not share alignment scope or overlap central playback controls (Play/Pause/Rewind).
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            PlayerStatusOverlay(
                netSpeed = netSpeed,
                currentDecoderMode = currentDecoderMode,
                onDecoderClick = { showDecoderDialog = true }
            )
        }

        // Overlay: Professional PotPlayer-Style Top Header Bar (Auto-hides with controls)
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 148.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(
                        onClick = onClose,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.65f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = activeTitle.ifBlank { "CircleStream" },
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            maxLines = 1
                        )
                    }

                    if (isLiveIptv) {
                        Surface(
                            color = Color(0xFFB71C1C),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(Color.White)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LIVE",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                // Controls: Aspect Ratio, Audio, Subtitle, Cast, Download
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLiveIptv) {
                        IconButton(
                            onClick = { showChannelDrawer = !showChannelDrawer },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (showChannelDrawer) Color(0xFFFF5252) else Color.Black.copy(alpha = 0.65f)
                            ),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Icon(Icons.Default.LiveTv, contentDescription = "Channels", tint = Color.White)
                        }
                    }

                    // Aspect Ratio Selector
                    IconButton(
                        onClick = { showAspectDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.65f))
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    // Audio Language Track Selector
                    IconButton(
                        onClick = {
                            audioTracks = TrackSelectorHelper.getAudioTracks(player)
                            showAudioDialog = true
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.65f))
                    ) {
                        Icon(Icons.Default.Audiotrack, contentDescription = "Audio Track", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    // Subtitle Selector
                    IconButton(
                        onClick = {
                            subtitleTracks = TrackSelectorHelper.getSubtitleTracks(player)
                            showSubtitleDialog = true
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.65f))
                    ) {
                        Icon(Icons.Default.Subtitles, contentDescription = "Subtitles", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    // Play in External Player (VLC / MX)
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(activeStreamUrl), "video/*")
                                putExtra("title", activeTitle)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, "Play in VLC / MX Player..."))
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.videolan.vlc")))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Install VLC from Play Store for external playback", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.65f))
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Play in VLC / MX", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    // Cast to TV
                    IconButton(
                        onClick = { showCastDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.65f))
                    ) {
                        Icon(Icons.Default.Cast, contentDescription = "Cast to TV", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    // Download to Local Storage
                    IconButton(
                        onClick = {
                            val id = CircleDownloadManager.download(context, activeTitle, activeStreamUrl)
                            if (id != -1L) {
                                showGestureIndicator = "Downloading: Track progress in Downloads tab"
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.65f))
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download Movie", tint = TextPrimary)
                    }

                    // Episodes Drawer Button (For series or multi-file movies)
                    if (ActivePlayerSession.playlist.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { showEpisodeDrawer = !showEpisodeDrawer },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (showEpisodeDrawer) CircleRed else Color.Black.copy(alpha = 0.65f)
                            )
                        ) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = "Episodes", tint = TextPrimary)
                        }
                    }
                }
            }
        }

        // Double Tap ±10s Center Feedback
        AnimatedVisibility(
            visible = doubleTapFeedback != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = doubleTapFeedback ?: "",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }

        // Swipe Volume / Brightness HUD
        AnimatedVisibility(
            visible = showGestureIndicator != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(gestureIcon, contentDescription = null, tint = CircleRed)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = showGestureIndicator ?: "",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Buffering Indicator
        if (isBuffering && playbackError == null) {
            CircularProgressIndicator(
                color = CircleRed,
                modifier = Modifier
                    .size(54.dp)
                    .align(Alignment.Center)
            )
        }

        // Playback Error Dialog & Multi-Engine Fallback Action
        if (playbackError != null) {
            val isCodecFailure = playbackError?.contains("hevc", ignoreCase = true) == true ||
                    playbackError?.contains("c2.", ignoreCase = true) == true ||
                    playbackError?.contains("decoder", ignoreCase = true) == true ||
                    playbackError?.contains("[-49999]", ignoreCase = true) == true

            Surface(
                color = Color(0xFF1E1E24).copy(alpha = 0.96f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = CircleRed,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isCodecFailure) "Hardware Codec Incompatibility" else "Stream Playback Error",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CircleRed
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isCodecFailure) {
                            "This video is encoded in high-profile 10-bit HEVC or dual-audio AC3. Your device processor's hardware video decoder crashed on this stream.\n\nPlay in VLC or MX Player for 100% smooth playback, or download for offline viewing."
                        } else {
                            playbackError ?: "Unable to stream media"
                        },
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Prominent primary button: Play in VLC / MX Player
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(activeStreamUrl), "video/*")
                                    putExtra("title", activeTitle)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(intent, "Play with..."))
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.videolan.vlc")))
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Install VLC from Play Store for 100% codec playback", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CircleRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Icon(Icons.Default.PlayCircleFilled, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play in VLC / MX Player", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val id = CircleDownloadManager.download(context, activeTitle, activeStreamUrl)
                                    if (id != -1L) {
                                        Toast.makeText(context, "Downloading movie to local storage...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = TextPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download", color = TextPrimary)
                            }

                            OutlinedButton(
                                onClick = { showCastDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Cast, contentDescription = null, tint = TextPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cast TV", color = TextPrimary)
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    playbackError = null
                                    currentDecoderMode = if (currentDecoderMode == DecoderMode.SW) DecoderMode.HW else DecoderMode.SW
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (currentDecoderMode == DecoderMode.SW) "Try HW" else "Try SW",
                                    color = TextPrimary
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    playbackError = null
                                    isBuffering = true
                                    retryTrigger++
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = TextPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }

        // Decoder Mode Selection Dialog (HW / HW+ / SW)
        if (showDecoderDialog) {
            AlertDialog(
                onDismissRequest = { showDecoderDialog = false },
                title = { Text("Decoder Engine", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Column {
                        DecoderMode.values().forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentDecoderMode = mode
                                        showDecoderDialog = false
                                        Toast.makeText(context, "Switched to ${mode.displayName}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = mode.displayName,
                                    color = if (currentDecoderMode == mode) CircleRed else TextPrimary,
                                    fontWeight = if (currentDecoderMode == mode) FontWeight.Bold else FontWeight.Normal
                                )
                                if (currentDecoderMode == mode) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = CircleRed)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDecoderDialog = false }) {
                        Text("Close", color = CircleRed)
                    }
                },
                containerColor = CircleSurface,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Aspect Ratio Dialog
        if (showAspectDialog) {
            AlertDialog(
                onDismissRequest = { showAspectDialog = false },
                title = { Text("Aspect Ratio", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Column {
                        AspectRatioMode.values().forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentAspectMode = mode
                                        showAspectDialog = false
                                        showGestureIndicator = "Aspect Ratio: ${mode.displayName}"
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = mode.displayName,
                                    color = if (currentAspectMode == mode) CircleRed else TextPrimary,
                                    fontWeight = if (currentAspectMode == mode) FontWeight.Bold else FontWeight.Normal
                                )
                                if (currentAspectMode == mode) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = CircleRed)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAspectDialog = false }) {
                        Text("Close", color = CircleRed)
                    }
                },
                containerColor = CircleSurface,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Audio Track Selection Dialog
        if (showAudioDialog) {
            AlertDialog(
                onDismissRequest = { showAudioDialog = false },
                title = { Text("Audio Track (Dual Audio)", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    if (audioTracks.isEmpty()) {
                        Text("No alternate audio streams detected.", color = TextSecondary)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(audioTracks) { track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            TrackSelectorHelper.selectAudioTrack(player, track)
                                            audioTracks = TrackSelectorHelper.getAudioTracks(player)
                                            showAudioDialog = false
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = track.displayName,
                                        color = if (track.isSelected) CircleRed else TextPrimary,
                                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (track.isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = CircleRed)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudioDialog = false }) {
                        Text("Close", color = CircleRed)
                    }
                },
                containerColor = CircleSurface,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Subtitle Track Selection Dialog + Local File Picker
        if (showSubtitleDialog) {
            AlertDialog(
                onDismissRequest = { showSubtitleDialog = false },
                title = { Text("Subtitles", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                subtitleFilePicker.launch(arrayOf("text/*", "application/x-subrip", "*/*"))
                                showSubtitleDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, tint = CircleRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import Local Subtitle (.srt)")
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Embedded Tracks in Stream:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(subtitleTracks) { track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            TrackSelectorHelper.selectSubtitleTrack(player, track)
                                            subtitleTracks = TrackSelectorHelper.getSubtitleTracks(player)
                                            showSubtitleDialog = false
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = track.displayName,
                                        color = if (track.isSelected) CircleRed else TextPrimary,
                                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (track.isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = CircleRed)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleDialog = false }) {
                        Text("Close", color = CircleRed)
                    }
                },
                containerColor = CircleSurface,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // YouTube-style "Pull up to Explore More Videos" Handle (Auto-hides with controls)
        AnimatedVisibility(
            visible = areControlsVisible && !showExploreDrawer && playbackError == null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                modifier = Modifier
                    .clickable { showExploreDrawer = true }
                    .padding(bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = CircleRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Explore Videos",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // YouTube-style Explore More Videos Drawer
        if (showExploreDrawer) {
            Surface(
                color = Color(0xFF141418).copy(alpha = 0.96f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Explore, contentDescription = null, tint = CircleRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Explore More Videos • Up Next", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                        }
                        IconButton(onClick = { showExploreDrawer = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isExploreLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CircleRed)
                        }
                    } else if (explorePosts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Loading videos from Circle FTP...", color = TextSecondary, fontSize = 12.sp)
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(explorePosts) { post ->
                                Surface(
                                    color = CircleSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .width(120.dp)
                                        .fillMaxHeight()
                                        .clickable {
                                            scope.launch {
                                                isExploreLoading = true
                                                try {
                                                    val detail = apiClient.getPostDetail(post.id) ?: run {
                                                        isExploreLoading = false
                                                        Toast.makeText(context, "Stream currently unavailable", Toast.LENGTH_SHORT).show()
                                                        return@launch
                                                    }
                                                    val urlToPlay = detail.singleStreamUrl
                                                        ?: detail.multiFiles.firstOrNull()?.streamUrl
                                                        ?: detail.seasons.firstOrNull()?.episodes?.firstOrNull()?.streamUrl

                                                    val titleToPlay = detail.multiFiles.firstOrNull()?.title
                                                        ?: detail.seasons.firstOrNull()?.episodes?.firstOrNull()?.title
                                                        ?: detail.title

                                                    if (!urlToPlay.isNullOrBlank()) {
                                                        ActivePlayerSession.set(
                                                            title = titleToPlay,
                                                            streamUrl = urlToPlay,
                                                            postId = detail.id,
                                                            posterUrl = detail.backdropUrl.ifBlank { detail.posterUrl },
                                                            isSeries = detail.isSeries,
                                                            seasonNumber = if (detail.isSeries) 1 else null,
                                                            episodeNumber = if (detail.isSeries) 1 else null
                                                        )
                                                        activeTitle = titleToPlay
                                                        activeStreamUrl = urlToPlay
                                                        showExploreDrawer = false
                                                        Toast.makeText(context, "Playing: $titleToPlay", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "No streamable video file found", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Failed to load: ${e.message}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isExploreLoading = false
                                                }
                                            }
                                        }
                                ) {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(115.dp)
                                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                                .background(Color.DarkGray)
                                        ) {
                                            AsyncImage(
                                                model = post.getPosterUrl(),
                                                contentDescription = post.displayTitle,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Column(modifier = Modifier.padding(4.dp)) {
                                            Text(
                                                text = post.displayTitle,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp,
                                                color = TextPrimary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!post.year.isNullOrBlank()) {
                                                Text(text = post.year, fontSize = 9.sp, color = TextSecondary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Netflix-Style Auto-Play Next Episode Countdown Card (Appears only at end of episode)
        AnimatedVisibility(
            visible = showNextEpisodePrompt && ActivePlayerSession.hasNextEpisode(),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 48.dp)
        ) {
            val nextEp = ActivePlayerSession.getNextEpisode()
            Surface(
                color = CircleDarkBg.copy(alpha = 0.96f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, CircleRed),
                modifier = Modifier.widthIn(max = 340.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Next Episode in ${nextEpisodeCountdown}s",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CircleRed
                        )
                        IconButton(
                            onClick = { showNextEpisodePrompt = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = nextEp?.title ?: "Next Episode",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (nextEpisodeCountdown / 5f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = CircleRed,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (nextEp != null) {
                                    ActivePlayerSession.switchToIndex(ActivePlayerSession.currentIndex + 1)
                                    activeTitle = nextEp.title
                                    activeStreamUrl = nextEp.streamUrl
                                    showNextEpisodePrompt = false
                                    retryTrigger++
                                    Toast.makeText(context, "Playing: ${nextEp.title}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CircleRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play Now", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showNextEpisodePrompt = false },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = TextPrimary)
                        }
                    }
                }
            }
        }

        // 4. Right-Side Series Episode Drawer Panel
        AnimatedVisibility(
            visible = showEpisodeDrawer && ActivePlayerSession.playlist.isNotEmpty(),
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                color = CircleDarkBg.copy(alpha = 0.96f),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Drawer Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Episodes",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${ActivePlayerSession.playlist.size} episodes • ${ActivePlayerSession.seriesTitle}",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { showEpisodeDrawer = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // Episode List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(ActivePlayerSession.playlist) { idx, ep ->
                            val isCurrent = idx == ActivePlayerSession.currentIndex
                            Card(
                                onClick = {
                                    if (!isCurrent) {
                                        ActivePlayerSession.switchToIndex(idx)
                                        activeTitle = ep.title
                                        activeStreamUrl = ep.streamUrl
                                        showEpisodeDrawer = false
                                        retryTrigger++
                                        Toast.makeText(context, "Playing: ${ep.title}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrent) CircleCard else Color.Black.copy(alpha = 0.4f)
                                ),
                                border = if (isCurrent) BorderStroke(1.5.dp, CircleRed) else null,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (isCurrent) CircleRed else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (isCurrent) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = "${idx + 1}",
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        if (isCurrent) {
                                            Text(
                                                text = "▶ NOW PLAYING",
                                                color = CircleRed,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                        Text(
                                            text = ep.title,
                                            color = if (isCurrent) TextPrimary else TextSecondary,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            CircleDownloadManager.download(context, ep.title, ep.streamUrl)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = "Download",
                                            tint = if (isCurrent) CircleRed else TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4b. Right-Side Live TV Channel Switcher Drawer Panel
        AnimatedVisibility(
            visible = showChannelDrawer,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                color = CircleDarkBg.copy(alpha = 0.96f),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Drawer Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Live Channels",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${activeChannels.size} Active BDIX Feeds",
                                fontSize = 11.sp,
                                color = Color(0xFF69F0AE)
                            )
                        }
                        IconButton(onClick = { showChannelDrawer = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                        }
                    }

                    // Search within Drawer
                    OutlinedTextField(
                        value = channelSearchQuery,
                        onValueChange = { channelSearchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        placeholder = { Text("Filter channels...", fontSize = 12.sp, color = TextSecondary) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            if (channelSearchQuery.isNotBlank()) {
                                IconButton(onClick = { channelSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CircleCard,
                            unfocusedContainerColor = CircleCard,
                            focusedBorderColor = CircleRed,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(top = 8.dp))

                    val filteredDrawerChannels = remember(channelSearchQuery) {
                        if (channelSearchQuery.isBlank()) activeChannels
                        else activeChannels.filter {
                            it.name.contains(channelSearchQuery, ignoreCase = true) || it.category.contains(channelSearchQuery, ignoreCase = true)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredDrawerChannels, key = { it.id }) { ch ->
                            val isCurrent = ch.name.equals(activeTitle, ignoreCase = true) || ch.id.equals(activeTitle, ignoreCase = true)
                            Card(
                                onClick = {
                                    if (!isCurrent) {
                                        switchToChannel(ch)
                                    }
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrent) CircleCard else Color.Black.copy(alpha = 0.4f)
                                ),
                                border = if (isCurrent) BorderStroke(1.5.dp, Color(0xFF00E676)) else null,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(if (isCurrent) Color(0xFF00E676) else CircleRed)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = ch.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCurrent) Color(0xFF69F0AE) else TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${ch.category} • ${ch.resolution}",
                                                fontSize = 10.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                    if (isCurrent) {
                                        Text(
                                            text = "PLAYING",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00E676)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Cast to TV Dialog
        if (showCastDialog) {
            AlertDialog(
                onDismissRequest = { showCastDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CastConnected, contentDescription = null, tint = CircleRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cast to Smart TV", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Stream \"$activeTitle\" directly to your Smart TV, Chromecast, or DLNA receiver.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        Surface(
                            color = CircleDarkBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCastDialog = false
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(activeStreamUrl), "video/*")
                                        putExtra("title", activeTitle)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(intent, "Cast / Play on TV with..."))
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "No casting app installed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tv, contentDescription = null, tint = CircleRed)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Cast via Streaming App / DLNA", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                    Text("Web Video Caster, Google Home, VLC, BubbleUPnP", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }

                        Surface(
                            color = CircleDarkBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCastDialog = false
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Screen cast settings not found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ScreenShare, contentDescription = null, tint = CircleRed)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Wireless Screen Cast / Mirroring", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                    Text("Mirror phone screen directly to Smart TV", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }

                        Surface(
                            color = CircleDarkBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipMgr.setPrimaryClip(ClipData.newPlainText("Stream URL", activeStreamUrl))
                                    Toast.makeText(context, "Stream link copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    showCastDialog = false
                                }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CircleRed)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Copy Stream Link", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                    Text("Paste into Android TV browser or Kodi", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCastDialog = false }) {
                        Text("Close", color = CircleRed)
                    }
                },
                containerColor = CircleSurface,
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}

/**
 * Custom Status Overlay displaying network speed and "HW" hardware decoding status.
 * Explicitly anchored to TopStart of the parent Box with 16.dp padding.
 * Ensured not to share the same alignment scope as the central playback controls (Play/Pause/Rewind)
 * so it never overlaps user playback interactions.
 */
@Composable
fun BoxScope.PlayerStatusOverlay(
    netSpeed: String,
    currentDecoderMode: DecoderMode,
    onDecoderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.70f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .align(Alignment.TopStart)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = "Network Speed",
                tint = Color(0xFF69F0AE),
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = netSpeed,
                color = Color(0xFF69F0AE),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(2.dp))

            Surface(
                color = when (currentDecoderMode) {
                    DecoderMode.HW -> CircleRed
                    DecoderMode.HW_PLUS -> Color(0xFF2E7D32)
                    DecoderMode.SW -> Color(0xFF1565C0)
                },
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.clickable { onDecoderClick() }
            ) {
                Text(
                    text = currentDecoderMode.name,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
