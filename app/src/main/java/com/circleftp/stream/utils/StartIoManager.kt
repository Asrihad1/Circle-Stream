package com.circleftp.stream.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.circleftp.stream.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Start.io (StartApp) Monetization Helper Singleton.
 * Manages SDK initialization, test ad configuration, dynamic Firebase Remote Config
 * kill-switches and cooldowns, and fail-safe non-blocking ad triggers.
 */
object StartIoManager {
    private const val TAG = "StartIO"
    private const val PREFS_NAME = "startio_ad_prefs"
    private const val KEY_LAST_AD_TIME = "last_ad_timestamp"

    // Firebase Remote Config parameter keys
    const val KEY_STARTIO_ENABLED = "startio_enabled"
    const val KEY_STARTIO_COOLDOWN_SECONDS = "startio_cooldown_seconds"
    const val KEY_STARTIO_TEST_MODE = "startio_test_mode"

    // Default configuration values
    private const val DEFAULT_COOLDOWN_SECONDS = 180L // 3 minutes
    private const val LOAD_TIMEOUT_MILLIS = 2000L      // 2 seconds load fallback

    /**
     * Start.io App ID injected dynamically from BuildConfig (sourced from local.properties)
     * with fallback to production ID.
     */
    val START_IO_APP_ID: String
        get() = try {
            BuildConfig.STARTIO_APP_ID.ifBlank { "210959821" }
        } catch (_: Exception) {
            "210959821"
        }

    /**
     * Safely accesses Firebase Remote Config with registered defaults.
     */
    private fun getRemoteConfig(): FirebaseRemoteConfig? {
        return try {
            val rc = FirebaseRemoteConfig.getInstance()
            rc.setDefaultsAsync(
                mapOf(
                    KEY_STARTIO_ENABLED to true,
                    KEY_STARTIO_COOLDOWN_SECONDS to DEFAULT_COOLDOWN_SECONDS,
                    KEY_STARTIO_TEST_MODE to false
                )
            )
            rc
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Remote Config unavailable: ${e.message}")
            null
        }
    }

    /**
     * Global kill-switch: checks if ads are enabled via Firebase Remote Config.
     */
    fun isStartIoEnabled(): Boolean {
        return try {
            getRemoteConfig()?.getBoolean(KEY_STARTIO_ENABLED) ?: true
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Dynamic cooldown seconds between interstitial displays.
     */
    fun getCooldownSeconds(): Long {
        return try {
            val seconds = getRemoteConfig()?.getLong(KEY_STARTIO_COOLDOWN_SECONDS) ?: DEFAULT_COOLDOWN_SECONDS
            if (seconds > 0L) seconds else DEFAULT_COOLDOWN_SECONDS
        } catch (_: Exception) {
            DEFAULT_COOLDOWN_SECONDS
        }
    }

    /**
     * Checks if Remote Config test mode flag is active.
     */
    fun isRemoteTestModeEnabled(): Boolean {
        return try {
            getRemoteConfig()?.getBoolean(KEY_STARTIO_TEST_MODE) ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Initializes the Start.io SDK:
     * - Injects official App ID from BuildConfig/local.properties.
     * - In DEBUG variants: keeps test ads active to prevent invalid traffic penalties.
     * - In RELEASE variants: disables test ads (unless dynamically enabled via Remote Config).
     * - Disables intrusive return ads and automatic splash takeovers.
     */
    fun init(context: Context) {
        try {
            val appId = START_IO_APP_ID
            val testAdsEnabled = BuildConfig.DEBUG || isRemoteTestModeEnabled()
            Log.d(TAG, "SDK initialized with App ID: $appId (testAds: $testAdsEnabled, debug: ${BuildConfig.DEBUG})")

            StartAppSDK.init(context, appId, false)
            StartAppSDK.setTestAdsEnabled(testAdsEnabled)
            StartAppAd.disableSplash()

            Log.d(TAG, "SDK initialized successfully (Test ads: $testAdsEnabled, splash disabled)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Start.io SDK: ${e.message}", e)
        }
    }

    /**
     * Activity-based convenience overload.
     */
    fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit) {
        showInterstitial(activity as Context, onAdDismissed)
    }

    /**
     * Shows an interstitial ad with:
     * 1. Firebase Remote Config global kill-switch (`startio_enabled`).
     * 2. Dynamic cooldown enforcement (`startio_cooldown_seconds`, default: 180s).
     * 3. 2-second load timeout fallback ensuring playback is never blocked.
     * 4. Comprehensive Logcat logging under tag "StartIO".
     */
    fun showInterstitial(context: Context, onAdDismissed: () -> Unit) {
        // 1. Remote Config Kill-Switch Check
        if (!isStartIoEnabled()) {
            Log.d(TAG, "Ad skipped: Monetization globally disabled via Remote Config ('startio_enabled' = false).")
            onAdDismissed()
            return
        }

        // 2. Dynamic Cooldown Check (default: 180s)
        val cooldownMillis = getCooldownSeconds() * 1000L
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastAdTime = prefs.getLong(KEY_LAST_AD_TIME, 0L)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastAd = currentTime - lastAdTime

        if (timeSinceLastAd < cooldownMillis) {
            val remainingSec = (cooldownMillis - timeSinceLastAd) / 1000
            Log.d(TAG, "Ad skipped: Cooldown active (${remainingSec}s remaining of ${cooldownMillis / 1000}s). Proceeding to playback.")
            onAdDismissed()
            return
        }

        // 3. Thread-safe execution guard (guarantees onAdDismissed is invoked exactly once)
        val callbackInvoked = AtomicBoolean(false)
        fun safeProceed(reason: String) {
            if (callbackInvoked.compareAndSet(false, true)) {
                Log.d(TAG, "Proceeding to video playback ($reason)")
                val mainHandler = Handler(Looper.getMainLooper())
                mainHandler.post { onAdDismissed() }
            }
        }

        try {
            Log.d(TAG, "Ad request sent: Requesting interstitial ad...")
            val startAppAd = StartAppAd(context)
            val mainHandler = Handler(Looper.getMainLooper())

            // 4. Fallback Timeout: If ad doesn't load within 2 seconds, continue immediately
            val timeoutRunnable = Runnable {
                Log.w(TAG, "Ad timeout reached (${LOAD_TIMEOUT_MILLIS}ms). Falling back to playback.")
                safeProceed("Timeout fallback")
            }
            mainHandler.postDelayed(timeoutRunnable, LOAD_TIMEOUT_MILLIS)

            startAppAd.loadAd(object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    Log.d(TAG, "Ad loaded successfully. Displaying interstitial ad...")

                    startAppAd.showAd(object : AdDisplayListener {
                        override fun adHidden(ad: Ad?) {
                            Log.d(TAG, "Ad dismissed by user.")
                            prefs.edit().putLong(KEY_LAST_AD_TIME, System.currentTimeMillis()).apply()
                            safeProceed("Ad hidden")
                        }

                        override fun adDisplayed(ad: Ad?) {
                            Log.d(TAG, "Ad displayed on screen.")
                        }

                        override fun adClicked(ad: Ad?) {
                            Log.d(TAG, "Ad clicked by user.")
                        }

                        override fun adNotDisplayed(ad: Ad?) {
                            Log.w(TAG, "Ad skipped: Ad failed to display on screen.")
                            safeProceed("Ad not displayed")
                        }
                    })
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    val errorMsg = ad?.errorMessage ?: "Error code / reason unspecified"
                    Log.e(TAG, "Ad failed with error: $errorMsg")
                    safeProceed("Load failed: $errorMsg")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception during interstitial request: ${e.message}", e)
            safeProceed("Exception")
        }
    }
}
