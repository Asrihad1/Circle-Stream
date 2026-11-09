package com.circleftp.stream.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.wortise.ads.WortiseSdk
import com.wortise.ads.AdError
import com.wortise.ads.interstitial.InterstitialAd

tailrec fun Context.getActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

object WortiseManager {
    private const val APP_ID = "09536a53-9f9a-4747-ace2-0125a0a27df1"
    const val DEFAULT_INTERSTITIAL_AD_UNIT = "f6c829c5-e75f-423e-8dff-85e65e9d468d"
    private var isInitialized = false

    fun init(context: Context) {
        WortiseSdk.initialize(context, APP_ID) {
            Log.d("Wortise_Debug", "SDK Initialized Successfully")
            isInitialized = true
        }
    }

    fun showAd(context: Context, adUnitId: String = DEFAULT_INTERSTITIAL_AD_UNIT, onAdDismissed: () -> Unit) {
        val activity = context.getActivity()
        if (activity == null || !isInitialized) {
            Log.e("Wortise_Debug", "Failed: Context is not Activity or SDK not initialized")
            onAdDismissed()
            return
        }

        Log.d("Wortise_Debug", "Loading Wortise Interstitial...")
        val interstitial = InterstitialAd(activity, adUnitId)

        interstitial.listener = object : InterstitialAd.Listener {
            override fun onInterstitialLoaded(ad: InterstitialAd) {
                Log.d("Wortise_Debug", "Ad Loaded. Showing now...")
                if (ad.isAvailable) {
                    ad.showAd()
                } else {
                    onAdDismissed()
                }
            }

            override fun onInterstitialFailedToLoad(ad: InterstitialAd, error: AdError) {
                Log.e("Wortise_Debug", "Load Failed: ${error.message}")
                onAdDismissed()
            }

            override fun onInterstitialFailedToShow(ad: InterstitialAd, error: AdError) {
                Log.e("Wortise_Debug", "Show Failed: ${error.message}")
                onAdDismissed()
            }

            override fun onInterstitialDismissed(ad: InterstitialAd) {
                Log.d("Wortise_Debug", "Ad Dismissed by User")
                onAdDismissed()
            }

            override fun onInterstitialShown(ad: InterstitialAd) {}
            override fun onInterstitialClicked(ad: InterstitialAd) {}
            override fun onInterstitialImpression(ad: InterstitialAd) {}
        }

        interstitial.loadAd()
    }
}
