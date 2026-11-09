package com.circleftp.stream.data.manifest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Dynamic Remote Provider Manifest.
 *
 * A small JSON file hosted online (GitHub raw / CDN) that tells the app, at runtime,
 * which streaming providers are alive, in which order to show them, and which ones
 * must be shut off immediately (kill switch) — without shipping a new APK.
 *
 * Pattern adapted from NetMirror's `/v2/resolvers/manifest` + `/v2/resolvers/killswitch`.
 *
 * Example:
 * ```json
 * {
 *   "manifest_version": 1,
 *   "cdn_ranking_enabled": true,
 *   "kill_switch": [],
 *   "providers": [
 *     { "id": "circleftp", "enabled": true, "priority": 1 },
 *     { "id": "iptvidn",   "enabled": true, "priority": 2 }
 *   ]
 * }
 * ```
 */
@Serializable
data class ProviderManifest(
    @SerialName("manifest_version") val manifestVersion: Int = 0,
    /** Globally enables/disables pre-flight mirror latency ranking before playback. */
    @SerialName("cdn_ranking_enabled") val cdnRankingEnabled: Boolean = true,
    /** Provider IDs that must be disabled immediately, regardless of [providers] entries. */
    @SerialName("kill_switch") val killSwitch: List<String> = emptyList(),
    val providers: List<ProviderRule> = emptyList()
)

/**
 * Remote rule for a single provider. Providers not listed in the manifest keep
 * their built-in default behaviour (enabled, original order).
 */
@Serializable
data class ProviderRule(
    val id: String,
    val enabled: Boolean = true,
    /** Lower number = shown first. */
    val priority: Int = 100,
    /** Optional base URL override; written into [com.circleftp.stream.data.config.ConfigManager]. */
    @SerialName("base_url") val baseUrl: String? = null,
    /** Provider is hidden on app builds older than this versionCode. */
    @SerialName("min_version_code") val minVersionCode: Int = 0
)

