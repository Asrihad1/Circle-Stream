package com.circleftp.stream.utils

import android.app.Activity
import android.content.Context

/**
 * AdManager delegation singleton ensuring backwards compatibility
 * with existing callers while leveraging StartIoManager.
 */
object AdManager {
    fun showInterstitialIfEligible(context: Context, onContinue: () -> Unit) {
        if (context is Activity) {
            StartIoManager.showInterstitial(context, onContinue)
        } else {
            onContinue()
        }
    }

    fun loadAndShowInterstitial(context: Context, onAdClosedOrSkipped: () -> Unit = {}) {
        if (context is Activity) {
            StartIoManager.showInterstitial(context, onAdClosedOrSkipped)
        } else {
            onAdClosedOrSkipped()
        }
    }
}
