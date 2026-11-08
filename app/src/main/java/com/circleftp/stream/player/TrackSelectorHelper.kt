package com.circleftp.stream.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer

data class AudioTrackOption(
    val group: Tracks.Group,
    val trackIndex: Int,
    val displayName: String,
    val isSelected: Boolean
)

data class SubtitleTrackOption(
    val group: Tracks.Group?,
    val trackIndex: Int,
    val displayName: String,
    val isSelected: Boolean,
    val isOff: Boolean = false
)

object TrackSelectorHelper {

    fun getAudioTracks(player: ExoPlayer): List<AudioTrackOption> {
        val tracks = player.currentTracks
        val options = mutableListOf<AudioTrackOption>()

        var trackCounter = 1
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val isSelected = group.isTrackSelected(i)

                    val name = buildString {
                        val lang = format.language?.uppercase()
                        val label = format.label
                        val channels = when (format.channelCount) {
                            6 -> "5.1 Surround"
                            8 -> "7.1 Surround"
                            2 -> "Stereo"
                            1 -> "Mono"
                            else -> if (format.channelCount > 0) "${format.channelCount}ch" else ""
                        }
                        val codec = format.sampleMimeType?.substringAfterLast('/')?.uppercase() ?: ""

                        if (!label.isNullOrBlank()) {
                            append(label)
                        } else if (!lang.isNullOrBlank()) {
                            append(lang)
                        } else {
                            append("Audio Track $trackCounter")
                        }

                        val meta = listOf(channels, codec).filter { it.isNotBlank() }.joinToString(" • ")
                        if (meta.isNotBlank()) {
                            append(" ($meta)")
                        }
                    }

                    options.add(
                        AudioTrackOption(
                            group = group,
                            trackIndex = i,
                            displayName = name,
                            isSelected = isSelected
                        )
                    )
                    trackCounter++
                }
            }
        }
        return options
    }

    fun selectAudioTrack(player: ExoPlayer, option: AudioTrackOption) {
        val override = TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .build()
    }

    fun getSubtitleTracks(player: ExoPlayer): List<SubtitleTrackOption> {
        val tracks = player.currentTracks
        val options = mutableListOf<SubtitleTrackOption>()

        // Check if all text tracks are disabled
        val textDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

        // "Off" option
        options.add(
            SubtitleTrackOption(
                group = null,
                trackIndex = -1,
                displayName = "Off (Disabled)",
                isSelected = textDisabled,
                isOff = true
            )
        )

        var subCounter = 1
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val isSelected = !textDisabled && group.isTrackSelected(i)

                    val name = buildString {
                        val label = format.label
                        val lang = format.language?.uppercase()
                        if (!label.isNullOrBlank()) {
                            append(label)
                        } else if (!lang.isNullOrBlank()) {
                            append(lang)
                        } else {
                            append("Subtitle Track $subCounter")
                        }
                        val mime = format.sampleMimeType?.substringAfterLast('/')?.uppercase()
                        if (!mime.isNullOrBlank()) {
                            append(" [$mime]")
                        }
                    }

                    options.add(
                        SubtitleTrackOption(
                            group = group,
                            trackIndex = i,
                            displayName = name,
                            isSelected = isSelected,
                            isOff = false
                        )
                    )
                    subCounter++
                }
            }
        }
        return options
    }

    fun selectSubtitleTrack(player: ExoPlayer, option: SubtitleTrackOption) {
        if (option.isOff) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else if (option.group != null) {
            val override = TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex)
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(override)
                .build()
        }
    }

    fun injectLocalSubtitle(
        player: ExoPlayer,
        currentTitle: String,
        currentStreamUrl: String,
        subtitleUri: android.net.Uri
    ) {
        val currentPosition = player.currentPosition
        val wasPlaying = player.playWhenReady

        val subConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage("custom")
            .setLabel("Imported Local Subtitle")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val normalizedUrl = UrlNormalizer.normalize(currentStreamUrl)
        val newMediaItem = MediaItem.Builder()
            .setUri(normalizedUrl)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(currentTitle)
                    .build()
            )
            .setSubtitleConfigurations(listOf(subConfig))
            .build()

        player.setMediaItem(newMediaItem)
        player.prepare()
        player.seekTo(currentPosition)
        player.playWhenReady = wasPlaying

        // Ensure text tracks are enabled
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
    }
}

