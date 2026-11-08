package com.circleftp.stream.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.circleftp.stream.BuildConfig
import com.circleftp.stream.data.config.ConfigManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@Serializable
data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String = "",
    val apkUrl: String = "",
    val updateUrl: String = "",
    val changelog: String = "",
    val isForceUpdate: Boolean = false,
    val domains: Map<String, String> = emptyMap(),
    val providers: Map<String, String> = emptyMap()
) {
    val downloadUrl: String
        get() = apkUrl.ifBlank { updateUrl }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateState
    data class Downloading(val info: AppUpdateInfo, val progressPercent: Int) : UpdateState
    data class ReadyToInstall(val info: AppUpdateInfo, val apkFile: File) : UpdateState
    data class Error(val message: String, val info: AppUpdateInfo? = null) : UpdateState
}

class CircleUpdateManager(
    private val context: Context,
    private val updateJsonUrl: String = DEFAULT_UPDATE_JSON_URL
) {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(20, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                followRedirects(true)
                followSslRedirects(true)
            }
        }
        install(ContentNegotiation) {
            json(jsonParser)
        }
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    suspend fun checkForUpdates(silentIfUpToDate: Boolean = true) {
        if (_updateState.value is UpdateState.Downloading) return

        if (!silentIfUpToDate) {
            _updateState.value = UpdateState.Checking
        }

        withContext(Dispatchers.IO) {
            try {
                val rawBody = httpClient.get(updateJsonUrl).bodyAsText()
                val remoteInfo = jsonParser.decodeFromString<AppUpdateInfo>(rawBody)

                // Sync remote provider domains to ConfigManager
                val remoteDomains = remoteInfo.domains + remoteInfo.providers
                if (remoteDomains.isNotEmpty()) {
                    ConfigManager.getInstance(context).saveDomains(remoteDomains)
                }

                if (remoteInfo.versionCode > BuildConfig.VERSION_CODE) {
                    _updateState.value = UpdateState.UpdateAvailable(remoteInfo)
                } else {
                    _updateState.value = UpdateState.Idle
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (silentIfUpToDate) {
                    _updateState.value = UpdateState.Idle
                } else {
                    _updateState.value = UpdateState.Error(
                        message = e.localizedMessage ?: "Unable to check for updates"
                    )
                }
            }
        }
    }

    suspend fun downloadUpdate(info: AppUpdateInfo) {
        _updateState.value = UpdateState.Downloading(info, 0)

        withContext(Dispatchers.IO) {
            try {
                val targetDir = context.externalCacheDir ?: context.cacheDir
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                val safeVersion = info.versionName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val apkFile = File(targetDir, "CircleStream_v${safeVersion}.apk")
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                val targetDownloadUrl = info.downloadUrl.ifBlank { info.apkUrl }
                httpClient.prepareGet(targetDownloadUrl).execute { response ->
                    val totalBytes = response.contentLength() ?: -1L
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = 0L
                    var lastEmittedPercent = 0

                    FileOutputStream(apkFile).use { output ->
                        while (!channel.isClosedForRead) {
                            val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                            if (bytesRead <= 0) break
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            if (totalBytes > 0L) {
                                val percent = ((downloadedBytes * 100L) / totalBytes)
                                    .toInt()
                                    .coerceIn(0, 100)
                                if (percent != lastEmittedPercent) {
                                    lastEmittedPercent = percent
                                    _updateState.value = UpdateState.Downloading(info, percent)
                                }
                            }
                        }
                        output.flush()
                    }
                }

                if (!apkFile.exists() || apkFile.length() == 0L) {
                    _updateState.value = UpdateState.Error("Downloaded APK file is empty or corrupted.", info)
                    return@withContext
                }

                _updateState.value = UpdateState.ReadyToInstall(info, apkFile)
                installApk(apkFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(
                    message = e.localizedMessage ?: "Failed to download update package.",
                    info = info
                )
            }
        }
    }

    fun installApk(apkFile: File) {
        if (!apkFile.exists()) {
            _updateState.value = UpdateState.Error("APK file no longer exists in cache.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(installIntent)
    }

    fun dismiss() {
        val current = _updateState.value
        val isForce = when (current) {
            is UpdateState.UpdateAvailable -> current.info.isForceUpdate
            is UpdateState.Downloading -> current.info.isForceUpdate
            is UpdateState.ReadyToInstall -> current.info.isForceUpdate
            is UpdateState.Error -> current.info?.isForceUpdate == true
            else -> false
        }
        if (!isForce) {
            _updateState.value = UpdateState.Idle
        }
    }

    companion object {
        const val DEFAULT_UPDATE_JSON_URL =
            "https://raw.githubusercontent.com/circleftp/circlestream-releases/main/update.json"
    }
}

