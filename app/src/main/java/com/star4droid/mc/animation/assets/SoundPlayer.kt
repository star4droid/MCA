package com.star4droid.mc.animation.assets

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

object SoundPlayer {

    enum class SoundType {
        POP,
        STEP,
        DING,
        WHOOSH
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    fun playSound(type: SoundType) {
        scope.launch {
            try {
                when (type) {
                    SoundType.POP -> playPopTone()
                    SoundType.STEP -> playStepTone()
                    SoundType.DING -> playDingTone()
                    SoundType.WHOOSH -> playWhooshTone()
                }
            } catch (e: Exception) {
                // Ignore audio errors if hardware audio track unavailable
            }
        }
    }

    private fun playPopTone() {
        val sampleRate = 22050
        val durationMs = 60
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val freq = 500.0 + 700.0 * (1.0 - t / (durationMs / 1000.0))
            val envelope = (1.0 - (i.toDouble() / numSamples))
            val sample = (sin(2.0 * Math.PI * freq * t) * envelope * Short.MAX_VALUE * 0.4).toInt()
            buffer[i] = sample.toShort()
        }
        playPcm(buffer, sampleRate)
    }

    private fun playStepTone() {
        val sampleRate = 22050
        val durationMs = 50
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val noise = (Math.random() * 2.0 - 1.0)
            val envelope = (1.0 - (i.toDouble() / numSamples))
            val sample = (noise * envelope * Short.MAX_VALUE * 0.3).toInt()
            buffer[i] = sample.toShort()
        }
        playPcm(buffer, sampleRate)
    }

    private fun playDingTone() {
        val sampleRate = 22050
        val durationMs = 300
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val freq = 1200.0
            val envelope = Math.exp(-t * 12.0)
            val sample = (sin(2.0 * Math.PI * freq * t) * envelope * Short.MAX_VALUE * 0.5).toInt()
            buffer[i] = sample.toShort()
        }
        playPcm(buffer, sampleRate)
    }

    private fun playWhooshTone() {
        val sampleRate = 22050
        val durationMs = 150
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val freq = 300.0 - 150.0 * (t / (durationMs / 1000.0))
            val envelope = sin(Math.PI * (i.toDouble() / numSamples))
            val sample = (sin(2.0 * Math.PI * freq * t) * envelope * Short.MAX_VALUE * 0.35).toInt()
            buffer[i] = sample.toShort()
        }
        playPcm(buffer, sampleRate)
    }

    private fun playPcm(pcm: ShortArray, sampleRate: Int) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcm, 0, pcm.size)
        track.play()
    }
}
