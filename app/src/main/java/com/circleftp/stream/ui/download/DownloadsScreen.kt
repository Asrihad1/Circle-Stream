package com.circleftp.stream.ui.download

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.circleftp.stream.data.download.DownloadRepository
import com.circleftp.stream.data.download.DownloadedVideo
import com.circleftp.stream.player.ActiveDownloadInfo
import com.circleftp.stream.player.CircleDownloadManager
import com.circleftp.stream.ui.theme.*
import kotlin.math.roundToInt

enum class DownloadTab {
    DOWNLOADING,
    COMPLETED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlayOffline: (title: String, filePath: String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(DownloadTab.DOWNLOADING) }

    val activeDownloads by CircleDownloadManager.pollActiveDownloads(context)
        .collectAsState(initial = emptyList())

    var completedVideos by remember { mutableStateOf<List<DownloadedVideo>>(emptyList()) }
    var videoToDelete by remember { mutableStateOf<DownloadedVideo?>(null) }
    var activeToCancel by remember { mutableStateOf<ActiveDownloadInfo?>(null) }

    fun refreshCompleted() {
        completedVideos = DownloadRepository.getDownloadedVideos(context)
    }

    LaunchedEffect(Unit) {
        refreshCompleted()
    }

    // Auto-refresh completed when an active download finishes
    LaunchedEffect(activeDownloads.size) {
        refreshCompleted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Downloads",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = if (activeDownloads.isNotEmpty()) "${activeDownloads.size} active download(s)" else "Chrome-style Download Manager",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { refreshCompleted() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CircleDarkBg)
            )
        },
        containerColor = CircleDarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Chrome-style Tabs: "Downloading" and "Downloaded"
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = CircleDarkBg,
                contentColor = CircleRed,
                divider = { HorizontalDivider(color = CircleSurface) }
            ) {
                Tab(
                    selected = selectedTab == DownloadTab.DOWNLOADING,
                    onClick = { selectedTab = DownloadTab.DOWNLOADING },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Downloading",
                                fontWeight = if (selectedTab == DownloadTab.DOWNLOADING) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == DownloadTab.DOWNLOADING) CircleRed else TextSecondary
                            )
                            if (activeDownloads.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = CircleRed,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = activeDownloads.size.toString(),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                )

                Tab(
                    selected = selectedTab == DownloadTab.COMPLETED,
                    onClick = { selectedTab = DownloadTab.COMPLETED },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Downloaded",
                                fontWeight = if (selectedTab == DownloadTab.COMPLETED) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == DownloadTab.COMPLETED) CircleRed else TextSecondary
                            )
                            if (completedVideos.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = CircleSurface,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = completedVideos.size.toString(),
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }

            // Tab Content
            when (selectedTab) {
                DownloadTab.DOWNLOADING -> {
                    if (activeDownloads.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = TextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No Active Downloads",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Videos you download from CircleStream will show live progress, speed, and remaining time here.",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(activeDownloads, key = { it.id }) { item ->
                                ActiveDownloadCard(
                                    item = item,
                                    onCancel = { activeToCancel = item }
                                )
                            }
                        }
                    }
                }

                DownloadTab.COMPLETED -> {
                    if (completedVideos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = TextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No Offline Videos Yet",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Completed downloads will be available here to watch offline without an internet connection.",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(completedVideos, key = { it.path }) { video ->
                                CompletedVideoCard(
                                    video = video,
                                    onPlay = { onPlayOffline(video.title, video.path) },
                                    onDelete = { videoToDelete = video }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Cancel Active Download Confirmation
        activeToCancel?.let { activeItem ->
            AlertDialog(
                onDismissRequest = { activeToCancel = null },
                title = { Text("Cancel Download?", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Text(
                        "Are you sure you want to cancel downloading \"${activeItem.title}\"?",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            CircleDownloadManager.cancelDownload(context, activeItem.id)
                            activeToCancel = null
                        }
                    ) {
                        Text("Cancel Download", color = CircleRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeToCancel = null }) {
                        Text("Keep Downloading", color = TextSecondary)
                    }
                },
                containerColor = CircleSurface,
                shape = RoundedCornerShape(14.dp)
            )
        }

        // Delete Completed Video Confirmation
        videoToDelete?.let { video ->
            AlertDialog(
                onDismissRequest = { videoToDelete = null },
                title = { Text("Delete Download?", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Text(
                        "Delete \"${video.title}\" from your offline storage?",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            DownloadRepository.deleteVideo(video)
                            videoToDelete = null
                            refreshCompleted()
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Delete", color = CircleRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { videoToDelete = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = CircleSurface,
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}

/**
 * Chrome-Style Active Download Card with live progress, speed, and remaining time.
 */
@Composable
fun ActiveDownloadCard(
    item: ActiveDownloadInfo,
    onCancel: () -> Unit
) {
    val animatedProgress by animateFloatAsState(targetValue = item.progress, label = "dlProgress")

    Surface(
        color = CircleSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CircleRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = CircleRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = item.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.statusText,
                                fontSize = 11.sp,
                                color = CircleRed,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "•",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.speedText,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Real-time Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = CircleRed,
                trackColor = CircleDarkBg
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Chrome-style Download Stats line: "540 MB / 1.2 GB (45%) • 45s left"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val downloadedStr = CircleDownloadManager.formatBytes(item.bytesDownloaded)
                val totalStr = if (item.totalBytes > 0) CircleDownloadManager.formatBytes(item.totalBytes) else "Unknown"
                val percentStr = (item.progress * 100).roundToInt()

                Text(
                    text = "$downloadedStr / $totalStr ($percentStr%)",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                // Remaining Time Chip
                Surface(
                    color = CircleDarkBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = CircleRed,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.remainingTimeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Completed Offline Video Card
 */
@Composable
fun CompletedVideoCard(
    video: DownloadedVideo,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = CircleSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CircleRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.VideoFile,
                    contentDescription = null,
                    tint = CircleRed,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = video.formattedSize,
                        fontSize = 12.sp,
                        color = CircleRed,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = video.formattedDate,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Default.PlayCircleFilled,
                    contentDescription = "Play",
                    tint = CircleRed,
                    modifier = Modifier.size(34.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
