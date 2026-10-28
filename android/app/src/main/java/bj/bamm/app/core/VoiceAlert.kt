package bj.bamm.app.core

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Alertes vocales. Priorité à un enregistrement natif `res/raw/<langue>_<message>`
 * (ex. `fon_call_alert.mp3`) ; à défaut, synthèse vocale française.
 */
class VoiceAlert(context: Context) : TextToSpeech.OnInitListener {
    private val ctx = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingText: String? = null
    private var player: MediaPlayer? = null

    fun play(lang: Lang, msg: Msg) {
        stop()
        val res = ctx.resources.getIdentifier("${lang.code}_${msg.name.lowercase()}", "raw", ctx.packageName)
        if (res != 0) {
            player = MediaPlayer.create(ctx, res)?.apply { setOnCompletionListener { it.release() }; start() }
            if (player != null) return
        }
        speak(Messages.french(msg))
    }

    private fun speak(text: String) {
        val t = tts
        if (t != null && ttsReady) t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bamm")
        else {
            pendingText = text
            if (t == null) tts = TextToSpeech(ctx, this)
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts?.language = Locale.FRENCH
        tts?.setSpeechRate(0.9f)
        ttsReady = true
        pendingText?.let { speak(it) }
        pendingText = null
    }

    fun stop() {
        runCatching { player?.stop(); player?.release() }
        player = null
        tts?.stop()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        /** Vibration forte : 3 impulsions longues. */
        fun vibrate(context: Context) {
            val v = if (Build.VERSION.SDK_INT >= 31)
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 250, 700, 250, 700), -1))
        }
    }
}
