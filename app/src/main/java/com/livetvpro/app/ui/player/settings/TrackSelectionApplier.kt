package com.livetvpro.app.ui.player.settings

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters

object TrackSelectionApplier {

    fun apply(
        player: Player,
        video: TrackUiModel.Video?,
        audio: TrackUiModel.Audio?,
        text: TrackUiModel.Text?,
        disableVideo: Boolean = false,
        disableAudio: Boolean = false,
        disableText: Boolean = true
    ) {
        val builder = player.trackSelectionParameters.buildUpon()

        if (disableVideo) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
        } else if (video == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            builder.setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            builder.setOverrideForType(
                TrackSelectionOverride(
                    player.currentTracks.groups[video.groupIndex].mediaTrackGroup,
                    listOf(video.trackIndex)
                )
            )
        }

        if (disableAudio) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
        } else if (audio == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            builder.setOverrideForType(
                TrackSelectionOverride(
                    player.currentTracks.groups[audio.groupIndex].mediaTrackGroup,
                    listOf(audio.trackIndex)
                )
            )
        }

        if (disableText) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else if (text == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        } else if (text.groupIndex != null && text.trackIndex != null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            builder.setOverrideForType(
                TrackSelectionOverride(
                    player.currentTracks.groups[text.groupIndex].mediaTrackGroup,
                    listOf(text.trackIndex)
                )
            )
        }

        player.trackSelectionParameters = builder.build()
    }

    fun applyMultipleVideo(
        player: Player,
        videoTracks: List<TrackUiModel.Video>,
        audio: TrackUiModel.Audio?,
        text: TrackUiModel.Text?,
        disableVideo: Boolean = false,
        disableAudio: Boolean = false,
        disableText: Boolean = true
    ) {
        val builder = player.trackSelectionParameters.buildUpon()

        if (disableVideo) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
        } else if (videoTracks.isEmpty()) {
            builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            builder.setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            
            val tracksByGroup = videoTracks.groupBy { it.groupIndex }
            
            tracksByGroup.forEach { (groupIndex, tracks) ->
                val mediaTrackGroup = player.currentTracks.groups[groupIndex].mediaTrackGroup
                
                val trackIndices = tracks.map { it.trackIndex }
                
                builder.setOverrideForType(
                    TrackSelectionOverride(
                        mediaTrackGroup,
                        trackIndices
                    )
                )
            }
        }

        if (disableAudio) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
        } else if (audio == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            builder.setOverrideForType(
                TrackSelectionOverride(
                    player.currentTracks.groups[audio.groupIndex].mediaTrackGroup,
                    listOf(audio.trackIndex)
                )
            )
        }

        if (disableText) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else if (text == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        } else if (text.groupIndex != null && text.trackIndex != null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            builder.setOverrideForType(
                TrackSelectionOverride(
                    player.currentTracks.groups[text.groupIndex].mediaTrackGroup,
                    listOf(text.trackIndex)
                )
            )
        }

        player.trackSelectionParameters = builder.build()
    }
}
