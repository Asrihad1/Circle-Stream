package com.circleftp.stream

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.circleftp.stream.data.api.CircleFtpApiClient
import com.circleftp.stream.data.config.ConfigManager
import com.circleftp.stream.data.iptv.IptvStreamResolver
import com.circleftp.stream.data.provider.ProviderRegistry
import com.circleftp.stream.player.ActivePlayerSession
import com.circleftp.stream.ui.catalog.CatalogScreen
import com.circleftp.stream.ui.category.CategoryScreen
import com.circleftp.stream.ui.detail.DetailScreen
import com.circleftp.stream.ui.download.DownloadsScreen
import com.circleftp.stream.ui.home.HomeScreen
import com.circleftp.stream.ui.library.LibraryScreen
import com.circleftp.stream.ui.livetv.LiveTvScreen
import com.circleftp.stream.ui.player.PlayerScreen
import com.circleftp.stream.ui.search.SearchScreen
import com.circleftp.stream.ui.settings.SettingsScreen
import com.circleftp.stream.ui.theme.CircleCard
import com.circleftp.stream.ui.theme.CircleRed
import com.circleftp.stream.ui.theme.CircleStreamTheme
import com.circleftp.stream.ui.theme.TextPrimary
import com.circleftp.stream.ui.theme.TextSecondary
import com.circleftp.stream.updater.AppUpdater
import com.circleftp.stream.updater.AutoUpdateDialogHost
import com.circleftp.stream.updater.CircleUpdateManager
import com.circleftp.stream.utils.AdManager
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Runtime Security & Anti-Crack Protection (Anti-tamper, Anti-Frida, Signature verification)
        if (com.circleftp.stream.security.AntiReverseGuard.isDebuggerConnected() ||
            com.circleftp.stream.security.AntiReverseGuard.isFridaActive() ||
            com.circleftp.stream.security.AntiReverseGuard.isFridaPortOpen() ||
            !com.circleftp.stream.security.AntiReverseGuard.isSignatureValid(this)
        ) {
            android.widget.Toast.makeText(this, "Security Alert: App integrity compromised", android.widget.Toast.LENGTH_LONG).show()
            finish()
            return
        }

        enableEdgeToEdge()

        // Initialize Start.io Monetization SDK
        StartAppSDK.init(this, "210959821", false)
        StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG)
        StartAppAd.disableSplash()

        setContent {
            CircleStreamTheme {
                CircleStreamApp()
            }
        }
    }
}

@Composable
fun CircleStreamApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val apiClient = remember { CircleFtpApiClient() }
    val updateManager = remember { CircleUpdateManager(context) }
    val configManager = remember { ConfigManager.getInstance(context) }
    val providerRegistry = remember { ProviderRegistry.getInstance(context, configManager) }

    // Firebase Remote Config Auto-Updater State
    var updateDownloadUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Asynchronously fetch remote provider domains
        configManager.fetchRemoteConfig()
        updateManager.checkForUpdates(silentIfUpToDate = true)

        // Firebase Remote Config Auto-Update check
        AppUpdater.checkForUpdates(currentVersionCode = BuildConfig.VERSION_CODE.toLong()) { apkUrl ->
            updateDownloadUrl = apkUrl
        }
    }

    AutoUpdateDialogHost(updateManager = updateManager)

    // Persistent Mandatory Update Dialog via Firebase Remote Config
    updateDownloadUrl?.let { apkUrl ->
        AlertDialog(
            onDismissRequest = {
                // Persistent dialog: Dismissal disabled for mandatory updates
            },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = {
                Text(
                    text = "Update Available",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "A new version of Circle Stream is available!",
                    fontSize = 15.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedUrl = apkUrl.trim()
                        val safeUrl = if (trimmedUrl.startsWith("http://", ignoreCase = true) || 
                            trimmedUrl.startsWith("https://", ignoreCase = true)
                        ) {
                            trimmedUrl
                        } else {
                            "https://$trimmedUrl"
                        }

                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }.onFailure {
                            Toast.makeText(context, "Invalid download link.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CircleRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Update Now",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            containerColor = CircleCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                apiClient = apiClient,
                onSelectProvider = { providerId ->
                    if (providerId == "iptvidn") {
                        navController.navigate("livetv")
                    } else {
                        navController.navigate("catalog/$providerId")
                    }
                },
                onSelectMedia = { providerId, mediaId ->
                    if (providerId == "iptvidn") {
                        navController.navigate("livetv")
                    } else {
                        navController.navigate("detail/$providerId/$mediaId")
                    }
                },
                onSelectPost = { postId ->
                    navController.navigate("detail/circleftp/$postId")
                },
                onResumePlayback = { historyItem ->
                    AdManager.showInterstitialIfEligible(context) {
                        val resolvedPostId = historyItem.parentSeriesId?.toLongOrNull()
                            ?: historyItem.mediaId.removePrefix("series_").removePrefix("movie_").toLongOrNull()
                            ?: 0L
                        ActivePlayerSession.set(
                            mediaId = historyItem.mediaId,
                            title = historyItem.title,
                            streamUrl = historyItem.streamUrl,
                            postId = resolvedPostId,
                            posterUrl = historyItem.posterUrl,
                            isSeries = historyItem.isSeries,
                            seasonNumber = if (historyItem.isSeries) historyItem.seasonNumber else null,
                            episodeNumber = if (historyItem.isSeries) historyItem.episodeNumber else null,
                            parentSeriesId = if (historyItem.isSeries) historyItem.parentSeriesId else null,
                            index = if (historyItem.isSeries && historyItem.episodeNumber != null) {
                                (historyItem.episodeNumber - 1).coerceAtLeast(0)
                            } else {
                                -1
                            }
                        )
                        navController.navigate("player")
                    }
                },
                onOpenSearch = {
                    navController.navigate("search")
                },
                onOpenCategories = {
                    navController.navigate("categories")
                },
                onOpenLibrary = {
                    navController.navigate("library")
                },
                onOpenDownloads = {
                    navController.navigate("downloads")
                },
                onOpenSettings = {
                    navController.navigate("settings")
                },
                onOpenLiveTv = {
                    navController.navigate("livetv")
                }
            )
        }

        composable("livetv") {
            LiveTvScreen(
                onBack = { navController.popBackStack() },
                onSelectChannel = { channel ->
                    scope.launch {
                        val resolved = IptvStreamResolver.resolveStream(channel.id)
                        val streamUrl = resolved?.primaryStreamUrl
                        if (!streamUrl.isNullOrBlank()) {
                            ActivePlayerSession.set(
                                mediaId = "iptv_${channel.id}",
                                title = channel.name,
                                streamUrl = streamUrl,
                                isSeries = false,
                                providerId = "iptvidn",
                                headers = resolved.headers
                            )
                            navController.navigate("player")
                        } else {
                            Toast.makeText(context, "Channel stream currently unavailable at source", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        composable(
            route = "catalog/{providerId}",
            arguments = listOf(navArgument("providerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val providerId = backStackEntry.arguments?.getString("providerId") ?: "circleftp"
            CatalogScreen(
                providerId = providerId,
                onBack = { navController.popBackStack() },
                onSelectMedia = { provId, mediaId ->
                    navController.navigate("detail/$provId/$mediaId")
                }
            )
        }

        composable(
            route = "detail/{providerId}/{mediaId}",
            arguments = listOf(
                navArgument("providerId") { type = NavType.StringType },
                navArgument("mediaId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val providerId = backStackEntry.arguments?.getString("providerId") ?: "circleftp"
            val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
            DetailScreen(
                providerId = providerId,
                mediaId = mediaId,
                onBack = { navController.popBackStack() },
                onPlay = { _, _ ->
                    navController.navigate("player")
                }
            )
        }

        composable(
            route = "detail/{postId}",
            arguments = listOf(navArgument("postId") { type = NavType.LongType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getLong("postId") ?: 0L
            DetailScreen(
                providerId = "circleftp",
                mediaId = postId.toString(),
                postId = postId,
                onBack = { navController.popBackStack() },
                onPlay = { _, _ ->
                    ActivePlayerSession.postId = postId
                    navController.navigate("player")
                }
            )
        }

        composable(route = "player") {
            PlayerScreen(
                onClose = { navController.popBackStack() }
            )
        }

        composable("search") {
            SearchScreen(
                apiClient = apiClient,
                onBack = { navController.popBackStack() },
                onSelectMedia = { providerId, mediaId ->
                    navController.navigate("detail/$providerId/$mediaId")
                },
                onSelectPost = { postId ->
                    navController.navigate("detail/circleftp/$postId")
                }
            )
        }

        composable("categories") {
            CategoryScreen(
                apiClient = apiClient,
                onBack = { navController.popBackStack() },
                onSelectPost = { postId ->
                    navController.navigate("detail/circleftp/$postId")
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("library") {
            LibraryScreen(
                onBack = { navController.popBackStack() },
                onPlayMedia = { historyItem ->
                    val resolvedPostId = historyItem.parentSeriesId?.toLongOrNull()
                        ?: historyItem.mediaId.removePrefix("series_").removePrefix("movie_").toLongOrNull()
                        ?: 0L
                    ActivePlayerSession.set(
                        mediaId = historyItem.mediaId,
                        title = historyItem.title,
                        streamUrl = historyItem.streamUrl,
                        postId = resolvedPostId,
                        posterUrl = historyItem.posterUrl,
                        isSeries = historyItem.isSeries,
                        seasonNumber = if (historyItem.isSeries) historyItem.seasonNumber else null,
                        episodeNumber = if (historyItem.isSeries) historyItem.episodeNumber else null,
                        parentSeriesId = if (historyItem.isSeries) historyItem.parentSeriesId else null,
                        index = if (historyItem.isSeries && historyItem.episodeNumber != null) {
                            (historyItem.episodeNumber - 1).coerceAtLeast(0)
                        } else {
                            -1
                        }
                    )
                    navController.navigate("player")
                },
                onPlayWatchLater = { item ->
                    val resolvedPostId = item.mediaId.removePrefix("series_").removePrefix("movie_").toLongOrNull() ?: 0L
                    ActivePlayerSession.set(
                        mediaId = item.mediaId,
                        title = item.title,
                        streamUrl = item.streamUrl,
                        postId = resolvedPostId,
                        posterUrl = item.posterUrl,
                        isSeries = item.isSeries
                    )
                    navController.navigate("player")
                },
                onSelectPostId = { postId ->
                    navController.navigate("detail/$postId")
                }
            )
        }

        composable("downloads") {
            DownloadsScreen(
                onBack = { navController.popBackStack() },
                onPlayOffline = { title, filePath ->
                    ActivePlayerSession.set(title, filePath)
                    navController.navigate("player")
                }
            )
        }
    }
}
