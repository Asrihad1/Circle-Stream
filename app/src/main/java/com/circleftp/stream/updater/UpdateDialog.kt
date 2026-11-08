package com.circleftp.stream.updater

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.circleftp.stream.ui.theme.CircleCard
import com.circleftp.stream.ui.theme.CircleRed
import com.circleftp.stream.ui.theme.CircleSurface
import com.circleftp.stream.ui.theme.TextPrimary
import com.circleftp.stream.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun AutoUpdateDialogHost(
    updateManager: CircleUpdateManager
) {
    val state by updateManager.updateState.collectAsState()
    val scope = rememberCoroutineScope()

    when (val currentState = state) {
        is UpdateState.Idle,
        is UpdateState.Checking -> Unit

        is UpdateState.UpdateAvailable -> {
            val info = currentState.info
            AlertDialog(
                onDismissRequest = {
                    if (!info.isForceUpdate) updateManager.dismiss()
                },
                properties = DialogProperties(
                    dismissOnBackPress = !info.isForceUpdate,
                    dismissOnClickOutside = !info.isForceUpdate
                ),
                containerColor = CircleSurface,
                icon = {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Update Available",
                        tint = CircleRed
                    )
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "New Update Available",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = CircleRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "v${info.versionName} (Build ${info.versionCode})",
                                color = CircleRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "What's New:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = CircleCard,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = info.changelog,
                                fontSize = 13.sp,
                                color = TextSecondary,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                updateManager.downloadUpdate(info)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CircleRed)
                    ) {
                        Text("Update Now", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    if (!info.isForceUpdate) {
                        TextButton(onClick = { updateManager.dismiss() }) {
                            Text("Later", color = TextSecondary)
                        }
                    }
                }
            )
        }

        is UpdateState.Downloading -> {
            val info = currentState.info
            val progressFraction = (currentState.progressPercent / 100f).coerceIn(0f, 1f)

            AlertDialog(
                onDismissRequest = { },
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                ),
                containerColor = CircleSurface,
                title = {
                    Text(
                        text = "Downloading v${info.versionName}...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            color = CircleRed,
                            trackColor = CircleCard,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Please wait...",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "${currentState.progressPercent}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                },
                confirmButton = { }
            )
        }

        is UpdateState.ReadyToInstall -> {
            val info = currentState.info
            AlertDialog(
                onDismissRequest = {
                    if (!info.isForceUpdate) updateManager.dismiss()
                },
                properties = DialogProperties(
                    dismissOnBackPress = !info.isForceUpdate,
                    dismissOnClickOutside = !info.isForceUpdate
                ),
                containerColor = CircleSurface,
                title = {
                    Text(
                        text = "Update Ready to Install",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                text = {
                    Text(
                        text = "Circle Stream v${info.versionName} has finished downloading. Tap Install to complete the upgrade.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { updateManager.installApk(currentState.apkFile) },
                        colors = ButtonDefaults.buttonColors(containerColor = CircleRed)
                    ) {
                        Text("Install APK", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    if (!info.isForceUpdate) {
                        TextButton(onClick = { updateManager.dismiss() }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }
                }
            )
        }

        is UpdateState.Error -> {
            val info = currentState.info
            val isForce = info?.isForceUpdate == true
            AlertDialog(
                onDismissRequest = {
                    if (!isForce) updateManager.dismiss()
                },
                properties = DialogProperties(
                    dismissOnBackPress = !isForce,
                    dismissOnClickOutside = !isForce
                ),
                containerColor = CircleSurface,
                title = {
                    Text(
                        text = "Update Failed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                text = {
                    Text(
                        text = currentState.message,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    if (info != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    updateManager.downloadUpdate(info)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CircleRed)
                        ) {
                            Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    if (!isForce) {
                        TextButton(onClick = { updateManager.dismiss() }) {
                            Text("Close", color = TextSecondary)
                        }
                    }
                }
            )
        }
    }
}

