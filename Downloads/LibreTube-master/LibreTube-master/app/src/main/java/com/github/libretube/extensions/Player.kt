package com.github.libretube.extensions

import androidx.media3.common.Player

typealias ProgressCallback = suspend (progress: Int, message: String) -> Unit

fun Player.togglePlayPauseState() {
    when {
        playerError != null -> {
            prepare()
            play()
        }

        !isPlaying && playbackState == Player.STATE_ENDED -> {
            seekTo(0)
        }

        !isPlaying -> play()

        else -> pause()
    }
}

