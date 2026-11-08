package com.circleftp.stream.ui.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.circleftp.stream.data.api.CircleFtpApiClient
import com.circleftp.stream.data.local.CircleDatabase
import com.circleftp.stream.data.local.WatchHistoryEntity
import com.circleftp.stream.data.local.WatchLaterEntity
import com.circleftp.stream.data.local.buildCanonicalMediaId
import com.circleftp.stream.data.model.StreamableEpisode
import com.circleftp.stream.data.provider.ProviderRegistry
import com.circleftp.stream.data.provider.model.MediaDetailModel
import com.circleftp.stream.data.sync.AuthSyncManager
import com.circleftp.stream.player.ActivePlayerSession
import com.circleftp.stream.player.CircleDownloadManager
import com.circleftp.stream.player.UrlNormalizer
import com.circleftp.stream.ui.theme.*
import com.circleftp.stream.utils.AdManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    providerId: String = "circleftp",
    mediaId: String = "",
    postId: Long = 0L,
    apiClient: CircleFtpApiClient? = null,
    onBack: () -> Unit,
    onPlay: (String, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { CircleDatabase.getInstance(context) }
    val watchLaterDao = remember { db.watchLaterDao() }
    val historyDao = remember { db.watchHistoryDao() }
    val authSyncManager = remember { AuthSyncManager.getInstance(context) }

    val effectiveMediaId = if (mediaId.isNotBlank()) mediaId else postId.toString()
    val effectiveProviderId = if (providerId.isNotBlank()) providerId else "circleftp"

    val providerRegistry = remember { ProviderRegistry.getInstance(context) }
    val provider = remember(effectiveProviderId) { providerRegistry.getProvider(effectiveProviderId) }

    var detailModel by remember { mutableStateOf<MediaDetailModel?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isResolvingStream by remember { mutableStateOf(false) }
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }

    // Consistent Details Loading: Uses specified provider's getDetails()
    LaunchedEffect(effectiveProviderId, effectiveMediaId) {
        isLoading = true
        try {
            val detail = provider.getDetails(effectiveMediaId, "movie")
            detailModel = detail
            isLoading = false
        } catch (e: Exception) {
            isLoading = false
        }
    }

    val detail = detailModel
    val canonicalMediaId = remember(detail, effectiveMediaId) {
        if (detail == null) ""
        else {
            val primaryStream = detail.directStreamUrl
                ?: detail.seasons.firstOrNull()?.episodes?.firstOrNull()?.directStreamUrl
                ?: effectiveMediaId
            buildCanonicalMediaId(
                streamUrl = primaryStream,
                isSeries = detail.isSeries,
                parentSeriesId = if (detail.isSeries) effectiveMediaId else null
            )
        }
    }

    val isBookmarked by remember(canonicalMediaId) {
        if (canonicalMediaId.isNotBlank()) {
            watchLaterDao.isWatchLater(canonicalMediaId)
        } else {
            kotlinx.coroutines.flow.flowOf(false)
        }
    }.collectAsState(initial = false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    if (detail != null && canonicalMediaId.isNotBlank()) {
                        // Bookmark Toggle Action
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val primaryStream = detail.directStreamUrl
                                        ?: detail.seasons.firstOrNull()?.episodes?.firstOrNull()?.directStreamUrl
                                        ?: effectiveMediaId

                                    if (isBookmarked) {
                                        watchLaterDao.removeFromWatchLater(canonicalMediaId)
                                        authSyncManager.onWatchLaterRemoved(canonicalMediaId)
                                        Toast.makeText(context, "Removed from Watch Later", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val entity = WatchLaterEntity(
                                            mediaId = canonicalMediaId,
                                            title = detail.title,
                                            posterUrl = detail.backdropUrl ?: detail.posterUrl,
                                            streamUrl = UrlNormalizer.normalize(primaryStream),
                                            isSeries = detail.isSeries
                                        )
                                        watchLaterDao.addToWatchLater(entity)
                                        authSyncManager.onWatchLaterAdded(entity)
                                        Toast.makeText(context, "Saved to Watch Later", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Watch Later",
                                tint = if (isBookmarked) CircleRed else TextPrimary
                            )
                        }

                        // More options menu: "Mark as Watched"
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = TextPrimary
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(CircleCard)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Mark as Watched", color = TextPrimary) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50))
                                    },
                                    onClick = {
                                        showMenu = false
                                        coroutineScope.launch {
                                            val primaryStream = detail.directStreamUrl
                                                ?: detail.seasons.firstOrNull()?.episodes?.firstOrNull()?.directStreamUrl
                                                ?: effectiveMediaId
                                            val normUrl = UrlNormalizer.normalize(primaryStream)

                                            val watchedEntity = WatchHistoryEntity(
                                                mediaId = canonicalMediaId,
                                                sourceId = effectiveProviderId,
                                                title = detail.title,
                                                posterUrl = detail.backdropUrl ?: detail.posterUrl,
                                                streamUrl = normUrl,
                                                positionMs = 7_200_000L,
                                                durationMs = 7_200_000L,
                                                isSeries = detail.isSeries,
                                                parentSeriesId = if (detail.isSeries) effectiveMediaId else null,
                                                lastUpdated = System.currentTimeMillis()
                                            )
                                            historyDao.markAsWatched(watchedEntity)
                                            watchLaterDao.removeFromWatchLater(canonicalMediaId)
                                            authSyncManager.onHistoryProgressUpdated(watchedEntity)
                                            authSyncManager.onWatchLaterRemoved(canonicalMediaId)
                                            Toast.makeText(context, "Marked as Watched", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = CircleDarkBg
    ) { padding ->
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CircleRed)
                }
            }
            detail != null -> {
                val backdrop = detail.backdropUrl ?: detail.posterUrl

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 48.dp)
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        ) {
                            if (!backdrop.isNullOrBlank()) {
                                AsyncImage(
                                    model = backdrop,
                                    contentDescription = detail.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                CircleDarkBg.copy(alpha = 0.3f),
                                                CircleDarkBg.copy(alpha = 0.8f),
                                                CircleDarkBg
                                            )
                                        )
                                    )
                            )
                        }

                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                text = detail.title,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!detail.releaseYear.isNullOrBlank()) {
                                    Text(text = detail.releaseYear, fontSize = 13.sp, color = TextSecondary)
                                }
                                if (!detail.quality.isNullOrBlank()) {
                                    Surface(
                                        color = CircleCard,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = detail.quality,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CircleRed,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Surface(
                                    color = CircleSurface,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = provider.providerName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (!detail.rating.isNullOrBlank()) {
                                    Text(
                                        text = "★ ${detail.rating}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFC107)
                                    )
                                }
                            }

                            if (!detail.description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = detail.description,
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    lineHeight = 18.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Watch Now / Play Button
                            Button(
                                onClick = {
                                    if (isResolvingStream) return@Button
                                    isResolvingStream = true
                                    coroutineScope.launch {
                                        try {
                                            val seasonNum = if (detail.isSeries && detail.seasons.isNotEmpty()) 1 else 0
                                            val episodeNum = if (detail.isSeries && detail.seasons.isNotEmpty()) 1 else 0
                                            val resolved = provider.getStreams(effectiveMediaId, season = seasonNum, episode = episodeNum)

                                            val playUrl = resolved.primaryStreamUrl ?: detail.directStreamUrl
                                            if (playUrl.isNullOrBlank()) {
                                                Toast.makeText(context, "Stream currently unavailable", Toast.LENGTH_SHORT).show()
                                                isResolvingStream = false
                                                return@launch
                                            }

                                            val mappedPlaylist = if (detail.isSeries && detail.seasons.isNotEmpty()) {
                                                detail.seasons.firstOrNull()?.episodes?.map { ep ->
                                                    StreamableEpisode(title = ep.title, streamUrl = ep.directStreamUrl ?: playUrl)
                                                } ?: emptyList()
                                            } else emptyList()

                                            ActivePlayerSession.set(
                                                title = detail.title,
                                                streamUrl = playUrl,
                                                playlist = mappedPlaylist,
                                                index = if (detail.isSeries) 0 else -1,
                                                seriesTitle = detail.title,
                                                postId = effectiveMediaId.toLongOrNull() ?: 0L,
                                                posterUrl = backdrop ?: detail.posterUrl,
                                                isSeries = detail.isSeries,
                                                seasonNumber = if (detail.isSeries) 1 else null,
                                                episodeNumber = if (detail.isSeries) 1 else null,
                                                parentSeriesId = if (detail.isSeries) effectiveMediaId else null,
                                                providerId = effectiveProviderId,
                                                headers = resolved.headers,
                                                subtitles = resolved.subtitles
                                            )
                                            isResolvingStream = false
                                            AdManager.showInterstitialIfEligible(context) {
                                                onPlay(detail.title, playUrl)
                                            }
                                        } catch (e: Exception) {
                                            isResolvingStream = false
                                            android.util.Log.e("DetailScreen", "Failed to resolve stream: ${e.message}", e)
                                            Toast.makeText(context, "Stream currently unavailable", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CircleRed),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isResolvingStream) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Resolving Stream...", color = Color.White, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (detail.isSeries) "Watch Episode 1" else "Play",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Series Season Tabs & Episode List
                    if (detail.isSeries && detail.seasons.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Episodes",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                            )
                            if (detail.seasons.size > 1) {
                                ScrollableTabRow(
                                    selectedTabIndex = selectedSeasonIndex.coerceIn(0, detail.seasons.size - 1),
                                    containerColor = CircleDarkBg,
                                    contentColor = CircleRed,
                                    edgePadding = 16.dp
                                ) {
                                    detail.seasons.forEachIndexed { index, season ->
                                        Tab(
                                            selected = selectedSeasonIndex == index,
                                            onClick = { selectedSeasonIndex = index },
                                            text = {
                                                Text(
                                                    text = season.seasonName,
                                                    fontWeight = if (selectedSeasonIndex == index) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selectedSeasonIndex == index) CircleRed else TextSecondary
                                                )
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        val currentSeason = detail.seasons.getOrNull(selectedSeasonIndex)
                        if (currentSeason != null) {
                            itemsIndexed(currentSeason.episodes) { index, ep ->
                                EpisodeRow(
                                    title = ep.title,
                                    onPlay = {
                                        coroutineScope.launch {
                                            try {
                                                // Ensure the exact numeric post ID is passed without episode suffixes
                                                val targetMediaId = effectiveMediaId.ifBlank { ep.episodeId.substringBefore("_") }
                                                val resolved = if (!ep.directStreamUrl.isNullOrBlank()) {
                                                    com.circleftp.stream.data.provider.model.ResolvedStreamModel(
                                                        streams = listOf(
                                                            com.circleftp.stream.data.provider.model.StreamSource(
                                                                url = ep.directStreamUrl,
                                                                quality = detail.quality ?: "BDIX High-Speed",
                                                                format = if (ep.directStreamUrl.contains(".m3u8", ignoreCase = true)) {
                                                                    com.circleftp.stream.data.provider.model.StreamFormat.HLS
                                                                } else {
                                                                    com.circleftp.stream.data.provider.model.StreamFormat.MP4
                                                                }
                                                            )
                                                        )
                                                    )
                                                } else {
                                                    provider.getStreams(
                                                        mediaId = targetMediaId,
                                                        season = currentSeason.seasonNumber,
                                                        episode = ep.episodeNumber
                                                    )
                                                }

                                                val playUrl = resolved.primaryStreamUrl ?: ep.directStreamUrl
                                                if (playUrl.isNullOrBlank()) {
                                                    Toast.makeText(context, "Episode stream currently unavailable", Toast.LENGTH_SHORT).show()
                                                    return@launch
                                                }

                                                val mappedPlaylist = currentSeason.episodes.map { e ->
                                                    StreamableEpisode(title = e.title, streamUrl = e.directStreamUrl ?: playUrl)
                                                }

                                                ActivePlayerSession.set(
                                                    title = ep.title,
                                                    streamUrl = playUrl,
                                                    playlist = mappedPlaylist,
                                                    index = index,
                                                    seriesTitle = detail.title,
                                                    postId = targetMediaId.toLongOrNull() ?: 0L,
                                                    posterUrl = backdrop ?: detail.posterUrl,
                                                    isSeries = true,
                                                    seasonNumber = currentSeason.seasonNumber,
                                                    episodeNumber = ep.episodeNumber,
                                                    parentSeriesId = targetMediaId,
                                                    providerId = effectiveProviderId,
                                                    headers = resolved.headers,
                                                    subtitles = resolved.subtitles
                                                )
                                                AdManager.showInterstitialIfEligible(context) {
                                                    onPlay(ep.title, playUrl)
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("DetailScreen", "Error resolving episode stream: ${e.message}", e)
                                                Toast.makeText(context, "Episode stream currently unavailable", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onDownload = {
                                        ep.directStreamUrl?.let { url ->
                                            CircleDownloadManager.download(context, ep.title, url)
                                        } ?: Toast.makeText(context, "Direct download link unavailable", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeRow(
    title: String,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = CircleSurface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPlay,
                colors = IconButtonDefaults.iconButtonColors(containerColor = CircleRed.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = CircleRed)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPlay() }
            )
            IconButton(onClick = onDownload) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
