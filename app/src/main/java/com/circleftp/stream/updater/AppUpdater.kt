package com.circleftp.stream.updater

import android.util.Log
import com.circleftp.stream.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Singleton class responsible for checking sideloaded APK updates
 * via Firebase Remote Config.
 */
object AppUpdater {

    private const val TAG = "AppUpdater"

    // Remote Config parameter keys
    const val KEY_LATEST_VERSION_CODE = "latest_version_code"
    const val KEY_APK_DOWNLOAD_URL = "apk_download_url"

    /**
     * Initializes Firebase Remote Config, fetches and activates values,
     * and evaluates if a newer version is available.
     *
     * @param currentVersionCode The current application version code (e.g. from BuildConfig.VERSION_CODE)
     * @param onUpdateAvailable Callback invoked with the APK download URL if latest_version_code > currentVersionCode
     */
    fun checkForUpdates(
        currentVersionCode: Long,
        onUpdateAvailable: (String) -> Unit
    ) {
        val remoteConfig = FirebaseRemoteConfig.getInstance()

        // 0 seconds fetch interval during debugging, 3600 seconds (1 hour) for production
        val minimumFetchInterval = if (BuildConfig.DEBUG) 0L else 3600L

        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(minimumFetchInterval)
            .build()

        remoteConfig.setConfigSettingsAsync(configSettings)

        // Set safe default values
        val defaultValues = mapOf<String, Any>(
            KEY_LATEST_VERSION_CODE to currentVersionCode,
            KEY_APK_DOWNLOAD_URL to ""
        )
        remoteConfig.setDefaultsAsync(defaultValues)

        Log.d(TAG, "Fetching Firebase Remote Config with interval ${minimumFetchInterval}s (currentVersionCode: $currentVersionCode)...")

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val latestVersionCode = remoteConfig.getLong(KEY_LATEST_VERSION_CODE)
                val rawUrl = remoteConfig.getString(KEY_APK_DOWNLOAD_URL).trim()
                val apkDownloadUrl = if (rawUrl.isNotBlank() && 
                    !rawUrl.startsWith("http://", ignoreCase = true) && 
                    !rawUrl.startsWith("https://", ignoreCase = true)
                ) {
                    "https://$rawUrl"
                } else {
                    rawUrl
                }

                Log.d(
                    TAG,
                    "Remote Config fetched successfully. latest_version_code=$latestVersionCode, " +
                            "currentVersionCode=$currentVersionCode, apk_download_url=$apkDownloadUrl"
                )

                if (latestVersionCode > currentVersionCode && apkDownloadUrl.isNotBlank()) {
                    Log.i(TAG, "New update found! Version $latestVersionCode > $currentVersionCode. Triggering update callback.")
                    onUpdateAvailable(apkDownloadUrl)
                } else {
                    Log.d(TAG, "App is up to date (remote $latestVersionCode <= current $currentVersionCode).")
                }
            } else {
                val exception = task.exception
                Log.e(TAG, "Failed to fetch and activate Firebase Remote Config: ${exception?.message}", exception)
            }
        }
    }

    /**
     * Convenience overload using the app's compiled [BuildConfig.VERSION_CODE].
     */
    fun checkForUpdates(onUpdateAvailable: (String) -> Unit) {
        checkForUpdates(
            currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
            onUpdateAvailable = onUpdateAvailable
        )
    }
}

