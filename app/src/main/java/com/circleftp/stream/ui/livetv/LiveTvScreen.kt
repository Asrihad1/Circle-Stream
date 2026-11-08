package com.circleftp.stream.ui.livetv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TvOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.circleftp.stream.data.iptv.IptvCatalog
import com.circleftp.stream.data.iptv.IptvChannel
import com.circleftp.stream.player.rememberNetworkSpeed
import com.circleftp.stream.ui.theme.*

/**
 * Modern Live TV Dashboard & Catalog.
 * High-speed BDIX channel streaming with real-time network speed monitor,
 * instant channel search, category filters, and active vs offline channel isolation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(
    onBack: () -> Unit,
    onSelectChannel: (IptvChannel) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showInactiveChannels by remember { mutableStateOf(false) }

    val netSpeed by rememberNetworkSpeed()

    val activeChannels = remember(searchQuery, selectedCategory) {
        IptvCatalog.searchChannels(
            query = searchQuery,
            category = selectedCategory,
            includeInactive = false
        )
    }

    val inactiveChannels = remember(searchQuery, selectedCategory) {
        if (!showInactiveChannels) emptyList()
        else IptvCatalog.searchChannels(
            query = searchQuery,
            category = selectedCategory,
            includeInactive = true
        ).filter { !it.isActive }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Live TV",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = TextPrimary
                        )

                        // Real-time Network Speed Meter Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1B5E20),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Net Speed",
                                    tint = Color(0xFF69F0AE),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = netSpeed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF69F0AE)
                                )
                            }
                        }
                    }
                },
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
                    // Segmented Filter for Inactive / Offline Feeds
                    FilterChip(
                        selected = showInactiveChannels,
                        onClick = { showInactiveChannels = !showInactiveChannels },
                        label = {
                            Text(
                                text = if (showInactiveChannels) "All Feeds" else "Active Only",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CircleRed.copy(alpha = 0.2f),
                            selectedLabelColor = CircleRed,
                            containerColor = CircleCard,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = showInactiveChannels,
                            borderColor = if (showInactiveChannels) CircleRed else Color.Transparent
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CircleDarkBg
                )
            )
        },
        containerColor = CircleDarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = {
                    Text(
                        text = "Search channels (e.g. Cricket, T-Sports, Ten 1, Sky, PTV)...",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CircleRed,
                    unfocusedBorderColor = CircleCard,
                    focusedContainerColor = CircleCard,
                    unfocusedContainerColor = CircleCard,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            // Category Filter Pills
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(IptvCatalog.CATEGORIES) { category ->
                    val isSelected = selectedCategory == category
                    val count = if (category == "All") {
                        IptvCatalog.ACTIVE_CHANNELS.size
                    } else {
                        IptvCatalog.ACTIVE_CHANNELS.count { it.category.equals(category, ignoreCase = true) }
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = "$category ($count)",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CircleRed,
                            selectedLabelColor = Color.White,
                            containerColor = CircleCard,
                            labelColor = TextSecondary
                        ),
                        border = null,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            // Channels Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 165.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header item showing active channel count & BDIX indicator
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Verified Active Streams (${activeChannels.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "BDIX Ultra-Speed",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF00E676)
                            )
                        }
                    }
                }

                // Active Channels
                items(activeChannels, key = { it.id }) { channel ->
                    IptvChannelCard(
                        channel = channel,
                        onClick = { onSelectChannel(channel) }
                    )
                }

                // Inactive / Offline section if enabled
                if (showInactiveChannels && inactiveChannels.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                            HorizontalDivider(color = CircleCard, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Offline Feeds (${inactiveChannels.size})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CircleRed
                                )
                                Text(
                                    text = "Encoder Satellite Down",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = "These channels are currently offline at the ISP source. Do not expect smooth playback until upstream restores ingest.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    items(inactiveChannels, key = { "inactive_${it.id}" }) { channel ->
                        IptvChannelCard(
                            channel = channel,
                            onClick = { onSelectChannel(channel) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern Card for an IPTV Live TV channel.
 */
@Composable
fun IptvChannelCard(
    channel: IptvChannel,
    onClick: () -> Unit
) {
    val borderColor = if (channel.isActive) {
        Color(0xFF00E676).copy(alpha = 0.35f)
    } else {
        Color(0xFFE53935).copy(alpha = 0.35f)
    }

    val glowColor = if (channel.isActive) {
        Color(0xFF00E676).copy(alpha = 0.08f)
    } else {
        Color(0xFFE53935).copy(alpha = 0.08f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = CircleCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            glowColor,
                            Color.Transparent
                        )
                    )
                )
                .padding(12.dp)
        ) {
            Column {
                // Top row with Category and Live Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category pill
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = CircleDarkBg
                    ) {
                        Text(
                            text = channel.category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Status Indicator
                    if (channel.isActive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LIVE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(CircleRed)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "OFFLINE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CircleRed
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Channel Visual Icon Block
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (channel.isActive) Color(0xFF1B5E20) else Color(0xFF3E2723)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (channel.isActive) Icons.Default.LiveTv else Icons.Default.TvOff,
                            contentDescription = channel.name,
                            tint = if (channel.isActive) Color(0xFF69F0AE) else Color(0xFFEF9A9A),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = channel.resolution,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (channel.isActive) Color(0xFF69F0AE) else TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (channel.isActive) "Watch Now" else "Feed Down",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (channel.isActive) Color(0xFF69F0AE) else CircleRed
                    )
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = if (channel.isActive) Color(0xFF69F0AE) else CircleRed,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

