package com.snow.safetalk.core

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.snow.safetalk.R

object ToggleSoundPlayer {
    private var soundPool: SoundPool? = null
    private var soundId: Int = 0

    private fun init(context: Context) {
        if (soundPool == null) {
            soundPool = SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            
            // Context needs to be application context to avoid memory leaks
            soundId = soundPool?.load(context.applicationContext, R.raw.toggle_sound, 1) ?: 0
        }
    }

    /**
     * Plays the universal toggle sound if the global sound setting is enabled.
     */
    fun playToggleSound(context: Context, isSoundEnabled: Boolean) {
        if (!isSoundEnabled) return
        
        if (soundPool == null) {
            init(context)
        }
        
        soundPool?.play(soundId, 1f, 1f, 1, 0, 1f)
    }
}
