package com.circleftp.stream.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.circleftp.stream.player.AspectRatioMode
import com.circleftp.stream.player.DecoderMode

class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("circlestream_settings", Context.MODE_PRIVATE)

    var defaultDecoder: DecoderMode
        get() {
            val name = prefs.getString(KEY_DECODER, DecoderMode.HW.name) ?: DecoderMode.HW.name
            return try { DecoderMode.valueOf(name) } catch (_: Exception) { DecoderMode.HW }
        }
        set(value) = prefs.edit().putString(KEY_DECODER, value.name).apply()

    var defaultAspect: AspectRatioMode
        get() {
            val name = prefs.getString(KEY_ASPECT, AspectRatioMode.FIT.name) ?: AspectRatioMode.FIT.name
            return try { AspectRatioMode.valueOf(name) } catch (_: Exception) { AspectRatioMode.FIT }
        }
        set(value) = prefs.edit().putString(KEY_ASPECT, value.name).apply()

    var seekIntervalSeconds: Int
        get() = prefs.getInt(KEY_SEEK_INTERVAL, 10)
        set(value) = prefs.edit().putInt(KEY_SEEK_INTERVAL, value).apply()

    var gesturesEnabled: Boolean
        get() = prefs.getBoolean(KEY_GESTURES, true)
        set(value) = prefs.edit().putBoolean(KEY_GESTURES, value).apply()

    var pinchZoomEnabled: Boolean
        get() = prefs.getBoolean(KEY_PINCH_ZOOM, true)
        set(value) = prefs.edit().putBoolean(KEY_PINCH_ZOOM, value).apply()

    var hardwareFallbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HW_FALLBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_HW_FALLBACK, value).apply()

    var autoPlayNextEpisode: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PLAY_NEXT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_PLAY_NEXT, value).apply()

    fun clearCache() {
        // Method invoked when user taps Clear Cache
    }

    companion object {
        private const val KEY_DECODER = "pref_decoder"
        private const val KEY_ASPECT = "pref_aspect"
        private const val KEY_SEEK_INTERVAL = "pref_seek_interval"
        private const val KEY_GESTURES = "pref_gestures"
        private const val KEY_PINCH_ZOOM = "pref_pinch_zoom"
        private const val KEY_HW_FALLBACK = "pref_hw_fallback"
        private const val KEY_AUTO_PLAY_NEXT = "pref_auto_play_next"

        @Volatile
        private var INSTANCE: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

