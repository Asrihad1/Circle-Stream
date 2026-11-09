package com.circleftp.stream.data.manifest

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.circleftp.stream.BuildConfig
import com.circleftp.stream.data.config.ConfigManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Downloads, validates, caches and exposes the [ProviderManifest].
 *
 * Lifecycle:
 * 1. [init] loads the last good manifest from SharedPreferences (instant, offline-safe).
 * 2. [refresh] fetches the remote JSON using `If-None-Match` (ETag) so unchanged
 *    manifests cost only a tiny 304 response.
 * 3. Any failure (offline, 404, invalid JSON) keeps the cached / built-in behaviour,
 *    so a broken manifest can never break the app.
 */
object ProviderManifestManager {

    private const val TAG = "ProviderManifest"
    private const val PREF_NAME = "circlestream_provider_manifest"
    private const val KEY_JSON = "manifest_json"
    private const val KEY_ETAG = "manifest_etag"
    private const val KEY_LAST_FETCH = "manifest_last_fetch"

    /** Hosted in the project repository root: `provider_manifest.json`. */
    const val DEFAULT_MANIFEST_URL =
        "https://raw.githubusercontent.com/Asrihad1/Circle-Stream/main/provider_manifest.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _manifest = MutableStateFlow<ProviderManifest?>(null)
    val manifestFlow: StateFlow<ProviderManifest?> = _manifest.asStateFlow()

    val currentManifest: ProviderManifest? get() = _manifest.value

    val cdnRankingEnabled: Boolean get() = _manifest.value?.cdnRankingEnabled ?: true

    /** Loads the cached manifest. Safe to call multiple times. */
    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs = p
            p.getString(KEY_JSON, null)?.let { cached ->
                parse(cached)?.let {
                    _manifest.value = it
                    Log.d(TAG, "Loaded cached manifest v${it.manifestVersion} (${it.providers.size} rules)")
                }
            }
        }
    }

    /**
     * Fetches the remote manifest. Returns true if a valid manifest is active afterwards
     * (either freshly downloaded or unchanged via HTTP 304).
     */
    suspend fun refresh(
        context: Context,
        url: String = DEFAULT_MANIFEST_URL
    ): Boolean = withContext(Dispatchers.IO) {
        init(context)
        val p = prefs ?: return@withContext false
        try {
            val requestBuilder = Request.Builder().url(url).get()
            val etag = p.getString(KEY_ETAG, null)
            if (etag != null && _manifest.value != null) {
                requestBuilder.header("If-None-Match", etag)
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                when {
                    response.code == 304 -> {
                        Log.d(TAG, "Manifest unchanged (304)")
                        p.edit().putLong(KEY_LAST_FETCH, System.currentTimeMillis()).apply()
                        true
                    }
                    response.isSuccessful -> {
                        val body = response.body?.string().orEmpty()
                        val parsed = parse(body)
                        if (parsed == null) {
                            Log.w(TAG, "Remote manifest invalid; keeping previous manifest")
                            return@use false
                        }
                        p.edit()
                            .putString(KEY_JSON, body)
                            .putString(KEY_ETAG, response.header("ETag"))
                            .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
                            .apply()
                        _manifest.value = parsed
                        applyBaseUrlOverrides(context, parsed)
                        Log.i(
                            TAG,
                            "Manifest v${parsed.manifestVersion} active: " +
                                "${parsed.providers.size} rules, killSwitch=${parsed.killSwitch}"
                        )
                        true
                    }
                    else -> {
                        Log.w(TAG, "Manifest fetch failed: HTTP ${response.code}")
                        false
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Manifest fetch error: ${e.message}")
            false
        }
    }

    /**
     * True if the provider may be shown/used. Unknown providers (not in the manifest)
     * and the "no manifest yet" state default to enabled.
     */
    fun isProviderEnabled(providerId: String): Boolean {
        val manifest = _manifest.value ?: return true
        val id = providerId.trim().lowercase()
        if (manifest.killSwitch.any { it.trim().lowercase() == id }) return false
        val rule = ruleFor(id) ?: return true
        if (!rule.enabled) return false
        return BuildConfig.VERSION_CODE >= rule.minVersionCode
    }

    /** Remote priority for ordering; providers without a rule keep [fallback]. */
    fun priorityOf(providerId: String, fallback: Int): Int =
        ruleFor(providerId)?.priority ?: fallback

    private fun ruleFor(providerId: String): ProviderRule? {
        val id = providerId.trim().lowercase()
        return _manifest.value?.providers?.firstOrNull { it.id.trim().lowercase() == id }
    }

    private fun parse(raw: String): ProviderManifest? {
        if (raw.isBlank()) return null
        return try {
            json.decodeFromString(ProviderManifest.serializer(), raw)
        } catch (e: Exception) {
            Log.w(TAG, "Manifest parse error: ${e.message}")
            null
        }
    }

    private fun applyBaseUrlOverrides(context: Context, manifest: ProviderManifest) {
        val overrides = manifest.providers
            .mapNotNull { rule ->
                val url = rule.baseUrl?.trim()
                if (url.isNullOrBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) null
                else rule.id.trim().lowercase() to url
            }
            .toMap()
        if (overrides.isNotEmpty()) {
            ConfigManager.getInstance(context).saveDomains(overrides)
        }
    }
}

