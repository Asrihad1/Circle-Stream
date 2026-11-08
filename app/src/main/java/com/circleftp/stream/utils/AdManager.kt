package com.circleftp.stream.utils

import android.content.Context
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener

object AdManager {
    private const val PREFS_NAME = "circle_stream_ad_prefs"
    private const val KEY_LAST_AD_TIME = "last_ad_time"
    private const val COOLDOWN_MILLIS = 90 * 60 * 1000L // 90 minutes

    fun showInterstitialIfEligible(context: Context, onContinue: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastAdTime = prefs.getLong(KEY_LAST_AD_TIME, 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastAdTime >= COOLDOWN_MILLIS) {
            val startAppAd = StartAppAd(context)
            startAppAd.showAd(object : AdDisplayListener {
                override fun adHidden(ad: Ad?) {
                    // Reset the 90-minute timer when the ad is closed
                    prefs.edit().putLong(KEY_LAST_AD_TIME, System.currentTimeMillis()).apply()
                    onContinue()
                }
                override fun adDisplayed(ad: Ad?) {}
                override fun adClicked(ad: Ad?) {}
                override fun adNotDisplayed(ad: Ad?) {
                    // If the ad fails to load, don't block the user. Let them watch the movie.
                    onContinue()
                }
            })
        } else {
            // User is still within the 90-minute cooldown zone. Skip ad.
            onContinue()
        }
    }
}

