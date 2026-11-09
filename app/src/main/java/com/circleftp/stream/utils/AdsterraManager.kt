package com.circleftp.stream.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent

object AdsterraManager {
    private const val TAG = "AdsterraManager"

    // The exact Direct Link URL provided by the product owner
    private const val SMARTLINK_URL = "https://asiafilm.org/4/1f296a1f2e89cdb045d6d4e49fa97e0c"

    fun showAd(context: Context, onAdOpened: () -> Unit) {
        try {
            val builder = CustomTabsIntent.Builder()
            builder.setShowTitle(false) // Hides the URL bar title for a cleaner ad experience
            val customTabsIntent = builder.build()

            if (context !is Activity) {
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Launch the Adsterra link
            customTabsIntent.launchUrl(context, Uri.parse(SMARTLINK_URL))

            // Trigger the callback immediately so the video/navigation executes in the background
            onAdOpened()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Custom Tab: ${e.message}", e)
            // Fallback in case the device doesn't have a compatible browser, still allow the user to proceed
            onAdOpened()
        }
    }
}

