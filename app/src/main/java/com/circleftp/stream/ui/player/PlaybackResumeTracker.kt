package com.circleftp.stream.ui.player

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.circleftp.stream.data.local.CircleDatabase
import com.circleftp.stream.data.local.WatchHistoryEntity
import com.circleftp.stream.data.local.buildCanonicalMediaId
import com.circleftp.stream.data.sync.AuthSyncManager
import com.circleftp.stream.player.ActivePlayerSession
import com.circleftp.stream.player.UrlNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

private val ioPersistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun RememberPlaybackTracker(
    player: ExoPlayer,
    mediaId: String,
    title: String,
    streamUrl: String,
    posterUrl: String? = null,
    sourceId: String = "CIRCLE_FTP",
    isSeries: Boolean = false,
    episodeNumber: Int? = null,
    seasonNumber: Int? = null,
    parentSeriesId: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val db = remember { CircleDatabase.getInstance(context) }
    val historyDao = remember { db.watchHistoryDao() }
    val watchLaterDao = remember { db.watchLaterDao() }
    val authSyncManager = remember { AuthSyncManager.getInstance(context) }

    val normalizedStreamUrl = remember(streamUrl) { UrlNormalizer.normalize(streamUrl) }
    val canonicalId = remember(mediaId, normalizedStreamUrl, isSeries, parentSeriesId) {
        buildCanonicalMediaId(
            streamUrl = normalizedStreamUrl,
            isSeries = isSeries,
            parentSeriesId = parentSeriesId
        )
    }

    var hasRestoredPosition by remember(mediaId, streamUrl) { mutableStateOf(false) }
    var targetSeekPositionMs by remember(mediaId, streamUrl) { mutableLongStateOf(0L) }
    var isDbFetchDone by remember(mediaId, streamUrl) { mutableStateOf(false) }

    val currentTitle by rememberUpdatedState(title)
    val currentPosterUrl by rememberUpdatedState(posterUrl)
    val currentIsSeries by rememberUpdatedState(isSeries)
    val currentEpisodeNumber by rememberUpdatedState(if (isSeries) episodeNumber else null)
    val currentSeasonNumber by rememberUpdatedState(if (isSeries) seasonNumber else null)
    val currentParentSeriesId by rememberUpdatedState(parentSeriesId)

    fun saveCurrentProgress() {
        // Strict Guard: NEVER write to WatchHistoryDao unless restore is complete,
        // currentPosition > 3_000L, and duration > 0L.
        if (!hasRestoredPosition) return
        val pos = player.currentPosition
        val dur = player.duration
        if (pos <= 3_000L || dur <= 0L) return

        val resolvedIsSeries = currentIsSeries || ActivePlayerSession.isSeries
        val resolvedParentSeriesId = if (resolvedIsSeries) {
            currentParentSeriesId ?: ActivePlayerSession.parentSeriesId
                ?: ActivePlayerSession.postId.takeIf { it > 0L }?.toString()
        } else {
            null
        }
        val resolvedCanonicalId = buildCanonicalMediaId(
            streamUrl = normalizedStreamUrl,
            isSeries = resolvedIsSeries,
            parentSeriesId = resolvedParentSeriesId
        )

        val entity = WatchHistoryEntity(
            mediaId = resolvedCanonicalId,
            sourceId = sourceId,
            title = currentTitle.ifBlank { "Untitled" },
            posterUrl = currentPosterUrl ?: ActivePlayerSession.posterUrl,
            streamUrl = normalizedStreamUrl,
            positionMs = pos.coerceAtMost(dur),
            durationMs = dur,
            isSeries = resolvedIsSeries,
            episodeNumber = if (resolvedIsSeries) (currentEpisodeNumber ?: ActivePlayerSession.episodeNumber) else null,
            seasonNumber = if (resolvedIsSeries) (currentSeasonNumber ?: ActivePlayerSession.seasonNumber) else null,
            parentSeriesId = resolvedParentSeriesId,
            lastUpdated = System.currentTimeMillis()
        )

        val progressPercent = pos.toFloat() / dur.toFloat()

        ioPersistScope.launch {
            // Save clean watch history
            historyDao.upsertCleanWatchHistory(entity)
            authSyncManager.onHistoryProgressUpdated(entity)

            // TASK 3: "Boiled Water" Edge Case Fix:
            // When >= 92% complete, the media is considered completed/watched.
            // Automatically remove it from Watch Later so the user doesn't have to manually un-bookmark it!
            if (progressPercent >= 0.92f) {
                watchLaterDao.removeFromWatchLater(resolvedCanonicalId)
                authSyncManager.onWatchLaterRemoved(resolvedCanonicalId)
            }
        }
    }

    // 1. Fetch saved resume position and seek immediately if player is already STATE_READY
    LaunchedEffect(mediaId, streamUrl) {
        if (normalizedStreamUrl.isBlank()) return@LaunchedEffect

        val savedPos = withContext(Dispatchers.IO) {
            historyDao.getResumePosition(canonicalId, normalizedStreamUrl)
        } ?: 0L

        targetSeekPositionMs = savedPos
        isDbFetchDone = true

        if (savedPos <= 5_000L) {
            hasRestoredPosition = true
        } else if (player.playbackState == Player.STATE_READY && player.duration > 0L) {
            player.seekTo(savedPos)
            hasRestoredPosition = true
            Toast.makeText(
                context,
                "Resumed from ${formatResumeTimestamp(savedPos)}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 2. Listen for Player.STATE_READY and lifecycle pause/stop/dispose events
    DisposableEffect(player, mediaId, streamUrl) {
        val playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && !hasRestoredPosition) {
                    if (targetSeekPositionMs > 5_000L) {
                        player.seekTo(targetSeekPositionMs)
                        hasRestoredPosition = true
                        Toast.makeText(
                            context,
                            "Resumed from ${formatResumeTimestamp(targetSeekPositionMs)}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else if (isDbFetchDone) {
                        hasRestoredPosition = true
                    }
                }
            }
        }
        player.addListener(playerListener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                saveCurrentProgress()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            player.removeListener(playerListener)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            saveCurrentProgress()
        }
    }

    // 3. Periodic 5-second background checkpoint while playing
    LaunchedEffect(player, mediaId, streamUrl) {
        while (isActive) {
            delay(5_000L)
            if (player.isPlaying) {
                saveCurrentProgress()
            }
        }
    }
}

private fun formatResumeTimestamp(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
