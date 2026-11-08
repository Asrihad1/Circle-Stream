package com.circleftp.stream.data.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Remote Configuration Manager for Circle Stream Multi-Source Media Aggregator.
 *
 * Dynamically resolves domain endpoints for 13+ streaming providers by fetching
 * a remote JSON configuration (e.g. from GitHub releases) and caching in SharedPreferences.
 * Prevents hardcoded URL breakages when upstream mirrors or API endpoints change.
 */
class ConfigManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val memoryCache = ConcurrentHashMap<String, String>()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
                followRedirects(true)
                followSslRedirects(true)
            }
        }
    }

    init {
        loadCachedDomains()
    }

    /**
     * Loads previously saved domain configurations from SharedPreferences into memory.
     */
    private fun loadCachedDomains() {
        try {
            val allEntries = prefs.all
            for ((key, value) in allEntries) {
                if (key.startsWith(KEY_DOMAIN_PREFIX) && value is String && value.isNotBlank()) {
                    val providerKey = key.removePrefix(KEY_DOMAIN_PREFIX).lowercase()
                    memoryCache[providerKey] = cleanDomainUrl(value)
                }
            }
            Log.d(TAG, "Loaded ${memoryCache.size} remote provider domains from local cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached domains", e)
        }
    }

    /**
     * Resolves the active domain URL for a provider key (e.g. "circleftp", "ctgmovies").
     * Falls back to [defaultUrl] if no remote configuration is present.
     *
     * @param providerKey Case-insensitive provider identifier.
     * @param defaultUrl Fallback endpoint if remote config is missing or invalid.
     * @return Sanitized base URL with trailing slashes stripped.
     */
    fun getDomain(providerKey: String, defaultUrl: String): String {
        val normalizedKey = providerKey.trim().lowercase()
        val cached = memoryCache[normalizedKey]
            ?: prefs.getString("$KEY_DOMAIN_PREFIX$normalizedKey", null)

        val resolved = cached?.takeIf { it.isNotBlank() } ?: defaultUrl
        return cleanDomainUrl(resolved)
    }

    /**
     * Returns an immutable snapshot of all actively cached provider domains.
     */
    fun getAllDomains(): Map<String, String> = HashMap(memoryCache)

    /**
     * Persists a map of domains to memory and SharedPreferences.
     */
    fun saveDomains(domains: Map<String, String>) {
        if (domains.isEmpty()) return

        val editor = prefs.edit()
        for ((key, rawUrl) in domains) {
            if (rawUrl.isNotBlank()) {
                val normalizedKey = key.trim().lowercase()
                val cleanedUrl = cleanDomainUrl(rawUrl)
                memoryCache[normalizedKey] = cleanedUrl
                editor.putString("$KEY_DOMAIN_PREFIX$normalizedKey", cleanedUrl)
            }
        }
        editor.putLong(KEY_LAST_SYNCED, System.currentTimeMillis())
        editor.apply()
        Log.d(TAG, "Successfully persisted ${domains.size} provider domains to cache")
    }

    /**
     * Fetches the latest configuration JSON from the remote endpoint (GitHub),
     * parses both "domains" and "providers" maps, and refreshes the cache.
     *
     * @param configUrl URL of the remote JSON (defaults to GitHub releases update.json).
     * @return True if fetch and parse succeeded, false otherwise.
     */
    suspend fun fetchRemoteConfig(configUrl: String = DEFAULT_CONFIG_URL): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching remote configuration from $configUrl")
                val responseText = httpClient.get(configUrl).bodyAsText()
                if (responseText.isBlank()) {
                    Log.w(TAG, "Remote configuration response is blank")
                    return@withContext false
                }

                val domainsMap = parseDomainsFromJson(responseText)
                if (domainsMap.isNotEmpty()) {
                    saveDomains(domainsMap)
                    Log.i(TAG, "Remote configuration updated: ${domainsMap.keys}")
                    true
                } else {
                    Log.w(TAG, "No domain keys found in remote configuration")
                    false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching remote config: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Leniently extracts domains from JSON supporting both "domains" and "providers" keys.
     */
    private fun parseDomainsFromJson(jsonString: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val rootElement = jsonParser.parseToJsonElement(jsonString)
            if (rootElement is JsonObject) {
                // Check "domains" object
                rootElement["domains"]?.jsonObject?.let { domainsObj ->
                    for ((k, v) in domainsObj) {
                        v.jsonPrimitive.contentOrNull?.let { url ->
                            if (url.isNotBlank()) result[k.trim().lowercase()] = url
                        }
                    }
                }
                // Check "providers" object
                rootElement["providers"]?.jsonObject?.let { providersObj ->
                    for ((k, v) in providersObj) {
                        v.jsonPrimitive.contentOrNull?.let { url ->
                            if (url.isNotBlank()) result[k.trim().lowercase()] = url
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse domains from JSON: ${e.message}")
        }
        return result
    }

    /**
     * Cleans domain string by removing trailing slashes, markdown wrappers [url](url),
     * and leading/trailing whitespace.
     */
    private fun cleanDomainUrl(raw: String): String {
        var cleaned = raw.trim()
        // Extract URL if formatted as Markdown link: [https://...](https://...)
        val markdownMatch = REGEX_MARKDOWN_URL.find(cleaned)
        if (markdownMatch != null) {
            cleaned = markdownMatch.groupValues[1]
        }
        return cleaned.removeSuffix("/")
    }

    private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
        get() = try {
            if (isString) content else null
        } catch (_: Exception) {
            null
        }

    companion object {
        private const val TAG = "ConfigManager"
        private const val PREF_NAME = "circlestream_remote_config"
        private const val KEY_DOMAIN_PREFIX = "domain_"
        private const val KEY_LAST_SYNCED = "last_synced_timestamp"

        const val DEFAULT_CONFIG_URL =
            "https://raw.githubusercontent.com/circleftp/circlestream-releases/main/update.json"

        private val REGEX_MARKDOWN_URL = Regex("""\[.*?\]\((https?://[^\s)]+)\)""")

        @Volatile
        private var INSTANCE: ConfigManager? = null

        fun getInstance(context: Context): ConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConfigManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

