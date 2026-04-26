package com.snow.safetalk.settings

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Lightweight sound feedback for toggle interactions.
 * Uses SoundPool for low-latency playback of short mp3 clips.
 */
class UiSoundManager(context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val toggleOnId: Int  = soundPool.load(context, com.snow.safetalk.R.raw.toggle_on, 1)
    private val toggleOffId: Int = soundPool.load(context, com.snow.safetalk.R.raw.toggle_off, 1)

    fun playToggleOn() {
        soundPool.play(toggleOnId, 1f, 1f, 1, 0, 1f)
    }

    fun playToggleOff() {
        soundPool.play(toggleOffId, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}
