package com.circleftp.stream.ui.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.circleftp.stream.data.api.CircleFtpApiClient
import com.circleftp.stream.data.model.CategoryItem
import com.circleftp.stream.data.model.PostSummary
import com.circleftp.stream.ui.home.MediaCard
import com.circleftp.stream.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    apiClient: CircleFtpApiClient,
    onBack: () -> Unit,
    onSelectPost: (Long) -> Unit
) {
    var categories by remember { mutableStateOf<List<CategoryItem>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<CategoryItem?>(null) }
    var posts by remember { mutableStateOf<List<PostSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingPosts by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val list = apiClient.getCategories()
            categories = list
            selectedCategory = list.firstOrNull()
            isLoading = false
        } catch (e: Exception) {
            isLoading = false
        }
    }

    LaunchedEffect(selectedCategory) {
        val cat = selectedCategory
        if (cat != null) {
            isLoadingPosts = true
            try {
                posts = apiClient.getCategoryPosts(cat.id)
            } catch (e: Exception) {
                posts = emptyList()
            }
            isLoadingPosts = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedCategory?.name ?: "Categories",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CircleDarkBg)
            )
        },
        containerColor = CircleDarkBg
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Left sidebar: Categories list
            LazyColumn(
                modifier = Modifier
                    .width(130.dp)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory?.id == cat.id
                    Surface(
                        color = if (isSelected) CircleRed else CircleSurface,
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp, horizontal = 4.dp)
                            .clickable { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat.name,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = TextPrimary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            // Right side: Category posts grid
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                when {
                    isLoadingPosts -> {
                        CircularProgressIndicator(
                            color = CircleRed,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    posts.isEmpty() -> {
                        Text(
                            text = "No content found in this category",
                            color = TextSecondary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 100.dp),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(posts) { post ->
                                MediaCard(post = post, onClick = { onSelectPost(post.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

