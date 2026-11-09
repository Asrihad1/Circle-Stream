package com.circleftp.stream.player

import android.util.Log
import com.circleftp.stream.data.manifest.ProviderManifestManager
import com.circleftp.stream.data.provider.model.ResolvedStreamModel
import com.circleftp.stream.data.provider.model.StreamSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Pre-flight mirror health check & latency ranking (adapted from NetMirror's
 * `assessCdnHostsAsync` / `rankServersAsync` resolver bridge capabilities).
 *
 * When a provider returns several stream mirrors, each one is probed in parallel and
 * the list is reordered fastest-first with dead mirrors moved to the end.
 * Never removes streams and never throws: on any problem the original order is kept.
 */
object StreamMirrorRanker {

    private const val TAG = "MirrorRanker"
    private const val PROBE_TIMEOUT_MS = 3_000L
    private const val TOTAL_BUDGET_MS = 4_000L
    private const val MAX_PROBES = 8

    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private data class ProbeResult(val index: Int, val alive: Boolean, val latencyMs: Long)

    suspend fun rank(
        resolved: ResolvedStreamModel,
        enabled: Boolean = ProviderManifestManager.cdnRankingEnabled
    ): ResolvedStreamModel {
        if (!enabled || resolved.streams.size < 2) return resolved

        val candidates = resolved.streams.take(MAX_PROBES)
        val results = withTimeoutOrNull(TOTAL_BUDGET_MS) {
            coroutineScope {
                candidates.mapIndexed { index, stream ->
                    async(Dispatchers.IO) {
                        probe(index, stream, resolved.getEffectiveHeaders(stream))
                    }
                }.awaitAll()
            }
        }

        if (results == null || results.none { it.alive }) {
            Log.d(TAG, "No ranking applied (timeout or all probes failed); keeping provider order")
            return resolved
        }

        val ranked = results
            .sortedWith(compareByDescending<ProbeResult> { it.alive }.thenBy { it.latencyMs })
            .map { candidates[it.index] }
        val reordered = ranked + resolved.streams.drop(candidates.size)

        Log.i(
            TAG,
            "Ranked ${results.size} mirrors: " + results.joinToString {
                "#${it.index}=${if (it.alive) "${it.latencyMs}ms" else "dead"}"
            }
        )
        return resolved.copy(streams = reordered)
    }

    private suspend fun probe(index: Int, stream: StreamSource, headers: Map<String, String>): ProbeResult =
        withContext(Dispatchers.IO) {
            val start = System.nanoTime()
            val alive = try {
                // Many media servers reject HEAD, so fall back to a 1-byte ranged GET
                request(stream.url, headers, head = true) || request(stream.url, headers, head = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            val latency = (System.nanoTime() - start) / 1_000_000
            ProbeResult(index, alive, if (alive) latency else Long.MAX_VALUE)
        }

    private fun request(url: String, headers: Map<String, String>, head: Boolean): Boolean {
        return try {
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.header(k, v) }
            if (head) builder.head() else builder.get().header("Range", "bytes=0-0")
            probeClient.newCall(builder.build()).execute().use { it.code in 200..399 }
        } catch (_: Exception) {
            false
        }
    }
}

