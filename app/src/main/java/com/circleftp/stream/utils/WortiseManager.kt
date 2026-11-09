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
    private const val TAG = "Wortise_Debug"
    private const val APP_ID = "09536a53-9f9a-4747-ace2-0125a0a27df1"
    const val DEFAULT_INTERSTITIAL_AD_UNIT = "f6c829c5-e75f-423e-8dff-85e65e9d468d"
    private var isInitialized = false

    fun init(context: Context) {
        try {
            Log.d(TAG, "Initializing Wortise SDK with APP_ID: $APP_ID")
            WortiseSdk.initialize(context, APP_ID) {
                Log.d(TAG, "SDK Initialized Successfully")
                isInitialized = true
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exception during Wortise initialization: ${t.message}", t)
        }
    }

    fun showAd(context: Context, adUnitId: String = DEFAULT_INTERSTITIAL_AD_UNIT, onAdDismissed: () -> Unit) {
        try {
            val activity = context.getActivity()
            if (activity == null || !isInitialized) {
                Log.e(TAG, "Failed: Context is not Activity or SDK not initialized (activity=$activity, isInitialized=$isInitialized)")
                onAdDismissed()
                return
            }

            Log.d(TAG, "Loading Wortise Interstitial for ad unit: $adUnitId")
            val interstitial = InterstitialAd(activity, adUnitId)

            interstitial.listener = object : InterstitialAd.Listener {
                override fun onInterstitialLoaded(ad: InterstitialAd) {
                    try {
                        Log.d(TAG, "Ad Loaded. Showing now...")
                        if (ad.isAvailable) {
                            ad.showAd()
                        } else {
                            Log.w(TAG, "Ad loaded but not available")
                            onAdDismissed()
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Exception while showing interstitial: ${t.message}", t)
                        onAdDismissed()
                    }
                }

                override fun onInterstitialFailedToLoad(ad: InterstitialAd, error: AdError) {
                    Log.e(TAG, "Load Failed: ${error.name} - ${error.message}")
                    onAdDismissed()
                }

                override fun onInterstitialFailedToShow(ad: InterstitialAd, error: AdError) {
                    Log.e(TAG, "Show Failed: ${error.name} - ${error.message}")
                    onAdDismissed()
                }

                override fun onInterstitialDismissed(ad: InterstitialAd) {
                    Log.d(TAG, "Ad Dismissed by User")
                    onAdDismissed()
                }

                override fun onInterstitialShown(ad: InterstitialAd) {
                    Log.d(TAG, "Ad Shown successfully")
                }

                override fun onInterstitialClicked(ad: InterstitialAd) {
                    Log.d(TAG, "Ad Clicked by user")
                }

                override fun onInterstitialImpression(ad: InterstitialAd) {
                    Log.d(TAG, "Ad Impression logged")
                }
            }

            interstitial.loadAd()
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected exception in showAd: ${t.message}", t)
            onAdDismissed()
        }
    }
}
