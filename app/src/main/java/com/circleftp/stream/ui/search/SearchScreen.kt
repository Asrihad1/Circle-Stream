package com.circleftp.stream.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.circleftp.stream.data.api.CircleFtpApiClient
import com.circleftp.stream.data.provider.ProviderRegistry
import com.circleftp.stream.data.provider.model.MediaItemModel
import com.circleftp.stream.ui.home.UnifiedMediaCard
import com.circleftp.stream.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    apiClient: CircleFtpApiClient? = null,
    onBack: () -> Unit,
    onSelectMedia: (providerId: String, mediaId: String) -> Unit,
    onSelectPost: (Long) -> Unit = { onSelectMedia("circleftp", it.toString()) }
) {
    val context = LocalContext.current
    val providerRegistry = remember { ProviderRegistry.getInstance(context) }
    val activeProvider by providerRegistry.activeProviderFlow.collectAsState()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItemModel>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Real-time debounced search scoped to the currently active provider
    LaunchedEffect(query, activeProvider.providerId) {
        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            isSearching = true
            delay(400)
            try {
                results = activeProvider.search(trimmed)
            } catch (e: Exception) {
                results = emptyList()
            }
            isSearching = false
        } else {
            results = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(CircleSurface, RoundedCornerShape(22.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                ),
                                cursorBrush = SolidColor(CircleRed),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (query.isEmpty()) {
                                        Text(
                                            text = "Search ${activeProvider.providerName}...",
                                            color = TextSecondary.copy(alpha = 0.6f),
                                            fontSize = 14.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = { query = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CircleDarkBg)
            )
        },
        containerColor = CircleDarkBg
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isSearching -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = CircleRed)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Searching ${activeProvider.providerName}...",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
                query.trim().length >= 2 && results.isEmpty() -> {
                    Text(
                        text = "No results found for \"$query\" on ${activeProvider.providerName}",
                        color = TextSecondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                results.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Type at least 2 characters to search ${activeProvider.providerName}",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(results) { item ->
                            UnifiedMediaCard(
                                item = item,
                                onClick = { onSelectMedia(activeProvider.providerId, item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
