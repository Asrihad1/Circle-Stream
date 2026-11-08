package com.circleftp.stream.data.provider

import android.util.Log
import com.circleftp.stream.data.provider.model.MediaItemModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe Central Registry for all [MediaSourceProvider] implementations.
 * Manages provider registration, selection, and cross-source aggregated searches.
 */
class MediaSourceRegistry private constructor() {

    private val providers = ConcurrentHashMap<String, MediaSourceProvider>()

    /**
     * Registers a new provider into the aggregation pool.
     */
    fun registerProvider(provider: MediaSourceProvider) {
        val key = provider.providerId.trim().lowercase()
        providers[key] = provider
        Log.d(TAG, "Registered provider: ${provider.providerName} (id=$key, bdixOnly=${provider.isBdixOnly})")
    }

    /**
     * Unregisters a provider by its identifier.
     */
    fun unregisterProvider(providerId: String) {
        providers.remove(providerId.trim().lowercase())
    }

    /**
     * Retrieves a provider by identifier.
     */
    fun getProvider(providerId: String): MediaSourceProvider? {
        return providers[providerId.trim().lowercase()]
    }

    /**
     * Returns an unmodifiable list of all registered providers.
     */
    fun getAllProviders(): List<MediaSourceProvider> {
        return providers.values.toList()
    }

    /**
     * Returns providers optimized for high-speed BDIX routing.
     */
    fun getBdixProviders(): List<MediaSourceProvider> {
        return providers.values.filter { it.isBdixOnly }
    }

    /**
     * Returns global providers accessible without BDIX ISP peering.
     */
    fun getGlobalProviders(): List<MediaSourceProvider> {
        return providers.values.filter { !it.isBdixOnly }
    }

    /**
     * Executes concurrent searches across all registered providers using [supervisorScope].
     * Isolates failures so a network failure or parsing error in one provider does not
     * fail the overall search operation.
     */
    suspend fun searchAll(query: String): List<MediaItemModel> = supervisorScope {
        if (query.isBlank()) return@supervisorScope emptyList()

        val activeProviders = providers.values.toList()
        val searchDeferred = activeProviders.map { provider ->
            async {
                try {
                    provider.search(query)
                } catch (e: Exception) {
                    Log.w(TAG, "Search failed for provider: ${provider.providerId} (${e.message})")
                    emptyList()
                }
            }
        }

        val results = searchDeferred.awaitAll().flatten()
        Log.d(TAG, "Aggregated search for '$query' yielded ${results.size} items from ${activeProviders.size} providers")
        results
    }

    /**
     * Fetches trending items from all registered providers concurrently.
     */
    suspend fun getAggregatedTrending(): List<MediaItemModel> = supervisorScope {
        val activeProviders = providers.values.toList()
        val trendingDeferred = activeProviders.map { provider ->
            async {
                try {
                    provider.getTrending()
                } catch (e: Exception) {
                    Log.w(TAG, "Trending fetch failed for provider: ${provider.providerId} (${e.message})")
                    emptyList()
                }
            }
        }
        trendingDeferred.awaitAll().flatten()
    }

    companion object {
        private const val TAG = "MediaSourceRegistry"

        @Volatile
        private var INSTANCE: MediaSourceRegistry? = null

        fun getInstance(): MediaSourceRegistry {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaSourceRegistry().also { INSTANCE = it }
            }
        }
    }
}

