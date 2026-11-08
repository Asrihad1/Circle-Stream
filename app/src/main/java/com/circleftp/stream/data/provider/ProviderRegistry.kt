package com.circleftp.stream.data.provider

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.circleftp.stream.data.config.ConfigManager
import com.circleftp.stream.data.provider.circle.CircleFtpProvider
import com.circleftp.stream.data.provider.iptv.IptvIdnProvider
import com.circleftp.stream.data.provider.model.MediaItemModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton Registry and Factory for all [MediaSourceProvider] streaming implementations.
 * Manages provider registration, selection, and active provider persistence in SharedPreferences.
 */
class ProviderRegistry private constructor(
    context: Context,
    configManager: ConfigManager?
) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val providers = ConcurrentHashMap<String, MediaSourceProvider>()

    private val circleFtpProvider = CircleFtpProvider(configManager)
    private val iptvIdnProvider = IptvIdnProvider(configManager)

    private val _activeProviderFlow = MutableStateFlow<MediaSourceProvider>(circleFtpProvider)
    val activeProviderFlow: StateFlow<MediaSourceProvider> = _activeProviderFlow.asStateFlow()

    init {
        // Register Core Providers
        registerProvider(circleFtpProvider)
        registerProvider(iptvIdnProvider)

        // Compatibility aliases
        providers["circle_ftp"] = circleFtpProvider
        providers["iptv"] = iptvIdnProvider
        providers["live_tv"] = iptvIdnProvider

        // Restore active provider choice from SharedPreferences, migrating away from deprecated sources
        val savedProviderId = prefs.getString(KEY_ACTIVE_PROVIDER, DEFAULT_PROVIDER_ID) ?: DEFAULT_PROVIDER_ID
        val initialProvider = providers[savedProviderId.trim().lowercase()] ?: circleFtpProvider
        _activeProviderFlow.value = initialProvider

        if (initialProvider.providerId != savedProviderId) {
            prefs.edit().putString(KEY_ACTIVE_PROVIDER, initialProvider.providerId).apply()
        }

        Log.i(TAG, "Initialized ProviderRegistry with active provider: ${initialProvider.providerName}")
    }

    /**
     * Distinct list of all available providers for UI dropdown / switcher.
     */
    val availableProviders: List<MediaSourceProvider>
        get() = listOf(circleFtpProvider, iptvIdnProvider)

    /**
     * Registers a new provider into the aggregation pool.
     */
    fun registerProvider(provider: MediaSourceProvider) {
        val key = provider.providerId.trim().lowercase()
        providers[key] = provider
    }

    /**
     * Resolves a provider by its unique identifier. Falls back to active provider if not found.
     */
    fun getProvider(id: String): MediaSourceProvider {
        val normalized = id.trim().lowercase()
        return providers[normalized] ?: _activeProviderFlow.value
    }

    /**
     * Returns the currently active provider for Home and Search.
     */
    fun getActiveProvider(): MediaSourceProvider {
        return _activeProviderFlow.value
    }

    /**
     * Sets and persists the user's active streaming provider choice.
     */
    fun setActiveProvider(providerId: String) {
        val target = getProvider(providerId)
        _activeProviderFlow.value = target
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, target.providerId).apply()
        Log.i(TAG, "Active provider switched to: ${target.providerName} (${target.providerId})")
    }

    /**
     * Concurrent search across all registered providers with failure isolation.
     */
    suspend fun searchAll(query: String): List<MediaItemModel> = supervisorScope {
        if (query.isBlank()) return@supervisorScope emptyList()
        val distinct = availableProviders
        val searchDeferred = distinct.map { provider ->
            async {
                try {
                    provider.search(query)
                } catch (e: Exception) {
                    Log.w(TAG, "Search failed for ${provider.providerId}: ${e.message}")
                    emptyList()
                }
            }
        }
        searchDeferred.awaitAll().flatten()
    }

    companion object {
        private const val TAG = "ProviderRegistry"
        private const val PREF_NAME = "circlestream_settings"
        private const val KEY_ACTIVE_PROVIDER = "active_provider"
        private const val DEFAULT_PROVIDER_ID = "circleftp"

        @Volatile
        private var INSTANCE: ProviderRegistry? = null

        fun getInstance(
            context: Context? = null,
            configManager: ConfigManager? = null
        ): ProviderRegistry {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    requireNotNull(context) { "Context must be provided for initial ProviderRegistry initialization" }
                    val cfg = configManager ?: ConfigManager.getInstance(context)
                    ProviderRegistry(context.applicationContext, cfg).also { INSTANCE = it }
                }
            }
        }
    }
}
