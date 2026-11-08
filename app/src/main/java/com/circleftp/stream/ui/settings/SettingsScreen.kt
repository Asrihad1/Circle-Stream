package com.circleftp.stream.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.circleftp.stream.data.settings.AppSettings
import com.circleftp.stream.player.AspectRatioMode
import com.circleftp.stream.player.DecoderMode
import com.circleftp.stream.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { AppSettings.getInstance(context) }

    var selectedDecoder by remember { mutableStateOf(settings.defaultDecoder) }
    var selectedAspect by remember { mutableStateOf(settings.defaultAspect) }
    var seekSeconds by remember { mutableIntStateOf(settings.seekIntervalSeconds) }
    var gesturesEnabled by remember { mutableStateOf(settings.gesturesEnabled) }
    var pinchZoomEnabled by remember { mutableStateOf(settings.pinchZoomEnabled) }
    var hwFallback by remember { mutableStateOf(settings.hardwareFallbackEnabled) }

    var autoPlayNext by remember { mutableStateOf(settings.autoPlayNextEpisode) }

    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showDecoderPicker by remember { mutableStateOf(false) }
    var showAspectPicker by remember { mutableStateOf(false) }
    var showSeekPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CircleDarkBg)
            )
        },
        containerColor = CircleDarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Branding Header
            Surface(
                color = CircleSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CIRCLE",
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            color = CircleRed
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "STREAM",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Emulator by ASR",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Version 1.2.0 • High-Speed BDIX Engine",
                        fontSize = 11.sp,
                        color = TextSecondary.copy(alpha = 0.7f)
                    )
                }
            }

            // Section: Playback & Decoders
            SettingsSectionHeader(title = "Player & Decoders")

            SettingsItem(
                icon = Icons.Default.Memory,
                title = "Default Decoder",
                subtitle = selectedDecoder.displayName,
                onClick = { showDecoderPicker = true }
            )

            SettingsItem(
                icon = Icons.Default.AspectRatio,
                title = "Default Aspect Ratio",
                subtitle = selectedAspect.displayName,
                onClick = { showAspectPicker = true }
            )

            SettingsItem(
                icon = Icons.Default.FastForward,
                title = "Double-Tap Seek Interval",
                subtitle = "$seekSeconds seconds",
                onClick = { showSeekPicker = true }
            )

            SettingsSwitchItem(
                icon = Icons.Default.SettingsBackupRestore,
                title = "Decoder Auto-Fallback",
                subtitle = "Automatically switch to SW (FFmpeg) if hardware lacks AC3/DTS",
                checked = hwFallback,
                onCheckedChange = {
                    hwFallback = it
                    settings.hardwareFallbackEnabled = it
                }
            )

            // Section: Series & Episode Autoplay
            SettingsSectionHeader(title = "Series & Episodes")

            SettingsSwitchItem(
                icon = Icons.Default.SkipNext,
                title = "Auto-Play Next Episode",
                subtitle = "Countdown and automatically play next episode when current finishes",
                checked = autoPlayNext,
                onCheckedChange = {
                    autoPlayNext = it
                    settings.autoPlayNextEpisode = it
                }
            )

            // Section: Gestures
            SettingsSectionHeader(title = "Gestures & Controls")

            SettingsSwitchItem(
                icon = Icons.Default.SwipeVertical,
                title = "Swipe Volume & Brightness",
                subtitle = "Vertical swipe on left/right edges to adjust controls",
                checked = gesturesEnabled,
                onCheckedChange = {
                    gesturesEnabled = it
                    settings.gesturesEnabled = it
                }
            )

            SettingsSwitchItem(
                icon = Icons.Default.ZoomIn,
                title = "Pinch-to-Zoom",
                subtitle = "Scale video surface between 0.75x and 3.5x with two fingers",
                checked = pinchZoomEnabled,
                onCheckedChange = {
                    pinchZoomEnabled = it
                    settings.pinchZoomEnabled = it
                }
            )

            // Section: Maintenance
            SettingsSectionHeader(title = "Storage & Cache")

            SettingsItem(
                icon = Icons.Default.CleaningServices,
                title = "Clear Video Stream Cache",
                subtitle = "Frees temporary playback buffer and stream thumbnails",
                onClick = {
                    settings.clearCache()
                    Toast.makeText(context, "Stream cache cleared successfully", Toast.LENGTH_SHORT).show()
                }
            )

            // Section: Legal & Privacy
            SettingsSectionHeader(title = "Privacy & Security")

            SettingsItem(
                icon = Icons.Default.Security,
                title = "Privacy Policy",
                subtitle = "Zero tracking • No account required • 100% Local data",
                onClick = { showPrivacyPolicyDialog = true }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Decoder Picker Dialog
    if (showDecoderPicker) {
        AlertDialog(
            onDismissRequest = { showDecoderPicker = false },
            title = { Text("Select Default Decoder", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    DecoderMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedDecoder = mode
                                    settings.defaultDecoder = mode
                                    showDecoderPicker = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = mode.displayName,
                                color = if (selectedDecoder == mode) CircleRed else TextPrimary,
                                fontWeight = if (selectedDecoder == mode) FontWeight.Bold else FontWeight.Normal
                            )
                            if (selectedDecoder == mode) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = CircleRed)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDecoderPicker = false }) {
                    Text("Cancel", color = CircleRed)
                }
            },
            containerColor = CircleSurface,
            shape = RoundedCornerShape(14.dp)
        )
    }

    // Aspect Ratio Dialog
    if (showAspectPicker) {
        AlertDialog(
            onDismissRequest = { showAspectPicker = false },
            title = { Text("Select Aspect Ratio", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    AspectRatioMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAspect = mode
                                    settings.defaultAspect = mode
                                    showAspectPicker = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = mode.displayName,
                                color = if (selectedAspect == mode) CircleRed else TextPrimary,
                                fontWeight = if (selectedAspect == mode) FontWeight.Bold else FontWeight.Normal
                            )
                            if (selectedAspect == mode) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = CircleRed)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAspectPicker = false }) {
                    Text("Cancel", color = CircleRed)
                }
            },
            containerColor = CircleSurface,
            shape = RoundedCornerShape(14.dp)
        )
    }

    // Seek Interval Dialog
    if (showSeekPicker) {
        val intervals = listOf(5, 10, 15, 30)
        AlertDialog(
            onDismissRequest = { showSeekPicker = false },
            title = { Text("Double Tap Seek Duration", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    intervals.forEach { sec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    seekSeconds = sec
                                    settings.seekIntervalSeconds = sec
                                    showSeekPicker = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$sec seconds",
                                color = if (seekSeconds == sec) CircleRed else TextPrimary,
                                fontWeight = if (seekSeconds == sec) FontWeight.Bold else FontWeight.Normal
                            )
                            if (seekSeconds == sec) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = CircleRed)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSeekPicker = false }) {
                    Text("Cancel", color = CircleRed)
                }
            },
            containerColor = CircleSurface,
            shape = RoundedCornerShape(14.dp)
        )
    }

    // Privacy Policy Dialog
    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = CircleRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Privacy Policy", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Last updated: November 2026\nCircleStream - Emulator by ASR",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    PrivacySection(
                        heading = "1. Information We Do Not Collect",
                        body = "CircleStream does not collect, log, store, or transmit any personal identifiable information (PII). We do not require accounts, logins, emails, phone numbers, or passwords."
                    )

                    PrivacySection(
                        heading = "2. No Tracking & No Third-Party Ads",
                        body = "The application contains zero telemetry trackers, analytics SDKs, advertising frameworks, or behavioral monitoring tools. Your browsing, searches, and playback activity remain strictly private on your device."
                    )

                    PrivacySection(
                        heading = "3. Direct BDIX Streaming",
                        body = "CircleStream functions as a client emulator player connecting directly to Bangladesh Internet Exchange (BDIX) FTP content servers. Network traffic is routed directly between your device and the host server without proxying through any intermediary servers."
                    )

                    PrivacySection(
                        heading = "4. Local Storage & Downloads",
                        body = "All subtitles, settings, and downloaded media files are stored locally in your device's private or standard storage directories. No files or media metadata are ever uploaded to any cloud service."
                    )

                    PrivacySection(
                        heading = "5. Device Permissions",
                        body = "The app requests only essential permissions:\n• Internet & Network State: For high-speed video streaming.\n• Wake Lock: To prevent the screen from dimming while watching videos.\n• Storage (Legacy Android): For storing user-initiated media downloads."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyPolicyDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CircleRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Understood", color = Color.White)
                }
            },
            containerColor = CircleSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun PrivacySection(heading: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = heading, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
        Text(text = body, fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = CircleRed,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        color = CircleSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = CircleRed, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 12.sp, color = TextSecondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = CircleSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = CircleRed, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 12.sp, color = TextSecondary)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = CircleRed,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = CircleDarkBg
                )
            )
        }
    }
}

