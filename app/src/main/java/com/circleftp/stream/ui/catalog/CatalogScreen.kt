package com.circleftp.stream.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.circleftp.stream.data.api.CircleFtpApiClient
import com.circleftp.stream.data.config.ConfigManager
import com.circleftp.stream.data.model.HomePageResponse
import com.circleftp.stream.data.model.PostSummary
import com.circleftp.stream.data.provider.ProviderRegistry
import com.circleftp.stream.data.provider.model.MediaItemModel
import com.circleftp.stream.ui.home.UnifiedMediaCard
import com.circleftp.stream.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    providerId: String,
    onBack: () -> Unit,
    onSelectMedia: (providerId: String, mediaId: String) -> Unit
) {
    val context = LocalContext.current
    val configManager = remember { ConfigManager.getInstance(context) }
    val providerRegistry = remember { ProviderRegistry.getInstance(context, configManager) }
    val provider = remember(providerId) { providerRegistry.getProvider(providerId) }

    val isCircleFtp = provider.providerId.equals("circleftp", ignoreCase = true)

    // Dedicated Circle FTP Client with remote domain awareness
    val circleApiClient = remember(providerId) {
        if (isCircleFtp) {
            val domain = configManager.getDomain("circleftp", "http://new.circleftp.net:5000")
            val base = if (domain.endsWith("/api")) domain else "$domain/api"
            CircleFtpApiClient(base)
        } else null
    }

    var searchQuery by remember { mutableStateOf("") }
    var circleHomeData by remember { mutableStateOf<HomePageResponse?>(null) }
    var circleSearchResults by remember { mutableStateOf<List<PostSummary>>(emptyList()) }

    var genericTrendingItems by remember { mutableStateOf<List<MediaItemModel>>(emptyList()) }
    var genericSearchResults by remember { mutableStateOf<List<MediaItemModel>>(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Initial Catalog Data Loading
    LaunchedEffect(provider.providerId) {
        isLoading = true
        errorMsg = null
        try {
            if (isCircleFtp && circleApiClient != null) {
                circleHomeData = circleApiClient.getHomePage()
            } else {
                genericTrendingItems = provider.getTrending()
            }
            isLoading = false
        } catch (e: Exception) {
            errorMsg = e.message ?: "Failed to load catalog from ${provider.providerName}"
            isLoading = false
        }
    }

    // Debounced Search
    LaunchedEffect(searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.length >= 2) {
            isSearching = true
            delay(350)
            try {
                if (isCircleFtp && circleApiClient != null) {
                    circleSearchResults = circleApiClient.searchPosts(trimmed)
                } else {
                    genericSearchResults = provider.search(trimmed)
                }
            } catch (e: Exception) {
                if (isCircleFtp) circleSearchResults = emptyList() else genericSearchResults = emptyList()
            }
            isSearching = false
        } else {
            circleSearchResults = emptyList()
            genericSearchResults = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = provider.providerName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (provider.isBdixOnly) "BDIX High-Speed Network" else "Global Internet Streaming",
                            fontSize = 11.sp,
                            color = if (provider.isBdixOnly) Color(0xFF4CAF50) else CircleRed,
                            fontWeight = FontWeight.Medium
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar for this specific provider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(44.dp)
                    .background(CircleSurface, RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(CircleRed),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search in ${provider.providerName}...",
                                    color = TextSecondary.copy(alpha = 0.6f),
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
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

            when {
                // Active Search Results
                searchQuery.trim().length >= 2 -> {
                    when {
                        isSearching -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = CircleRed)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Searching ${provider.providerName}...",
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                        (isCircleFtp && circleSearchResults.isEmpty()) || (!isCircleFtp && genericSearchResults.isEmpty()) -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No results found for \"$searchQuery\"",
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        isCircleFtp -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 120.dp),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(circleSearchResults) { post ->
                                    CircleMediaCard(
                                        post = post,
                                        onClick = { onSelectMedia("circleftp", post.id.toString()) }
                                    )
                                }
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
                                items(genericSearchResults) { item ->
                                    UnifiedMediaCard(
                                        item = item,
                                        onClick = { onSelectMedia(provider.providerId, item.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Initial Loading State
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = CircleRed)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Loading ${provider.providerName}...",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Error State
                errorMsg != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text(text = "Error: $errorMsg", color = TextSecondary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    isLoading = true
                                    errorMsg = null
                                    // Trigger reload
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CircleRed)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                // Legendary Circle FTP Layout (Hero + Carousels for all categories)
                isCircleFtp && circleHomeData != null -> {
                    val homeData = circleHomeData!!
                    val hero = homeData.mostVisitedPosts.firstOrNull() ?: homeData.latestPost.firstOrNull()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 36.dp)
                    ) {
                        // Hero Feature Banner at top
                        if (hero != null) {
                            item {
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    CircleHeroBanner(
                                        hero = hero,
                                        onSelect = { onSelectMedia("circleftp", hero.id.toString()) }
                                    )
                                }
                            }
                        }

                        // Trending & Popular Horizontal Carousel
                        if (homeData.mostVisitedPosts.isNotEmpty()) {
                            item {
                                CatalogSectionHeader(title = "Trending & Popular")
                                CircleMediaRow(
                                    posts = homeData.mostVisitedPosts,
                                    onSelectPost = { onSelectMedia("circleftp", it.toString()) }
                                )
                            }
                        }

                        // Latest Releases Horizontal Carousel
                        if (homeData.latestPost.isNotEmpty()) {
                            item {
                                CatalogSectionHeader(title = "Latest Releases")
                                CircleMediaRow(
                                    posts = homeData.latestPost,
                                    onSelectPost = { onSelectMedia("circleftp", it.toString()) }
                                )
                            }
                        }

                        // All Dynamic Category Carousels
                        items(homeData.categoryPosts) { categoryRow ->
                            if (categoryRow.posts.isNotEmpty()) {
                                CatalogSectionHeader(title = categoryRow.name ?: "Category")
                                CircleMediaRow(
                                    posts = categoryRow.posts,
                                    onSelectPost = { onSelectMedia("circleftp", it.toString()) }
                                )
                            }
                        }
                    }
                }

                // Fallback / Generic Provider Layout
                genericTrendingItems.isNotEmpty() -> {
                    val hero = genericTrendingItems.firstOrNull()
                    val gridItems = genericTrendingItems.drop(1)

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (hero != null) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                CatalogHeroBanner(
                                    hero = hero,
                                    onSelect = { onSelectMedia(provider.providerId, hero.id) }
                                )
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            CatalogSectionHeader(title = "Trending & Popular")
                        }

                        items(gridItems) { item ->
                            UnifiedMediaCard(
                                item = item,
                                onClick = { onSelectMedia(provider.providerId, item.id) }
                            )
                        }
                    }
                }

                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No content found in this catalog",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CatalogSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 10.dp)
    )
}

@Composable
fun CircleMediaRow(
    posts: List<PostSummary>,
    onSelectPost: (Long) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(posts) { post ->
            CircleMediaCard(post = post, onClick = { onSelectPost(post.id) })
        }
    }
}

@Composable
fun CircleMediaCard(
    post: PostSummary,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(192.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CircleCard)
        ) {
            AsyncImage(
                model = post.getPosterUrl(),
                contentDescription = post.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Red BDIX Badge at top-left
            Surface(
                shape = RoundedCornerShape(bottomEnd = 4.dp),
                color = CircleRed,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = "BDIX",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }

            // Quality tag at bottom-right
            if (!post.quality.isNullOrBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(topStart = 4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = post.quality,
                        fontSize = 9.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = post.displayTitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!post.year.isNullOrBlank()) {
            Text(
                text = post.year,
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun CircleHeroBanner(
    hero: PostSummary,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelect() }
    ) {
        AsyncImage(
            model = hero.getBackdropUrl(),
            contentDescription = hero.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            CircleDarkBg.copy(alpha = 0.65f),
                            CircleDarkBg
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            if (!hero.quality.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = CircleRed
                ) {
                    Text(
                        text = hero.quality,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hero.displayTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSelect,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Watch Now",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun CatalogHeroBanner(
    hero: MediaItemModel,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelect() }
    ) {
        AsyncImage(
            model = hero.bannerUrl ?: hero.posterUrl,
            contentDescription = hero.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            CircleDarkBg.copy(alpha = 0.6f),
                            CircleDarkBg
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            if (!hero.quality.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = CircleRed
                ) {
                    Text(
                        text = hero.quality,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hero.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onSelect,
                colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Watch Now", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
