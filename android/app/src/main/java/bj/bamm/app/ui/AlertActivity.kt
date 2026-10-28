package bj.bamm.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import bj.bamm.app.BammApp
import bj.bamm.app.R
import bj.bamm.app.core.Lang
import bj.bamm.app.core.Level
import bj.bamm.app.core.Messages
import bj.bamm.app.core.Msg
import bj.bamm.app.core.PhoneNumbers
import bj.bamm.app.core.Prefs
import bj.bamm.app.core.VoiceAlert
import bj.bamm.app.data.CommunityRepository
import bj.bamm.app.data.IncidentLog
import bj.bamm.app.databinding.ActivityAlertBinding

/**
 * Écran d'alerte pensé pour les non-lettrés : couleur + grande icône + message vocal.
 * Le texte n'est qu'un complément.
 */
class AlertActivity : AppCompatActivity() {

    enum class Mode { CALL, USSD, POST_CALL }

    private lateinit var b: ActivityAlertBinding
    private lateinit var voice: VoiceAlert
    private lateinit var lang: Lang
    private lateinit var mode: Mode
    private lateinit var level: Level
    private var number = ""
    private var code: String? = null
    private var timer: CountDownTimer? = null
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(b.root)
        voice = VoiceAlert(this)
        lang = Prefs(this).language ?: Lang.FRENCH
        bind(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        bind(intent)
    }

    private fun bind(i: Intent) {
        mode = Mode.valueOf(i.getStringExtra(EXTRA_MODE)!!)
        level = Level.valueOf(i.getStringExtra(EXTRA_LEVEL) ?: Level.RED.name)
        number = i.getStringExtra(EXTRA_NUMBER).orEmpty()
        code = i.getStringExtra(EXTRA_CODE)
        val amount = i.getLongExtra(EXTRA_AMOUNT, -1).takeIf { it >= 0 }
        b.reasons.text = i.getStringArrayExtra(EXTRA_REASONS)?.joinToString("\n") { "• $it" }.orEmpty()

        val (bg, icon, msg) = when {
            mode == Mode.USSD -> Triple(R.color.bamm_red, R.drawable.ic_lock, Msg.USSD_ALERT)
            level == Level.RED -> Triple(R.color.bamm_red, R.drawable.ic_stop_hand, Msg.CALL_ALERT)
            level == Level.GREEN -> Triple(R.color.bamm_green, R.drawable.ic_check, Msg.CALL_SAFE)
            else -> Triple(R.color.bamm_orange, R.drawable.ic_question, Msg.CALL_UNKNOWN)
        }
        b.root.setBackgroundColor(getColor(bg))
        b.icon.setImageResource(icon)
        b.message.text = Messages.text(lang, msg)
        b.subtitle.text = when (mode) {
            Mode.USSD -> listOfNotNull(code, amount?.let { "→ ${"%,d".format(it).replace(',', ' ')} FCFA" },
                number.takeIf { it.isNotEmpty() }?.let { "vers $it" }).joinToString("  ")
            else -> PhoneNumbers.pretty(number)
        }

        b.reportButton.visibility = if (mode != Mode.USSD && level != Level.GREEN) View.VISIBLE else View.GONE
        b.unlockGroup.visibility = if (mode == Mode.USSD) View.VISIBLE else View.GONE
        b.reportButton.setOnClickListener { report() }
        b.closeButton.setOnClickListener { close() }
        b.icon.setOnClickListener { voice.play(lang, msg) }   // toucher l'icône rejoue le message
        setupUnlock()

        if (level != Level.GREEN) VoiceAlert.vibrate(this)
        voice.play(lang, msg)
    }

    private fun report() {
        b.reportButton.isEnabled = false
        b.reportButton.alpha = 0.5f
        voice.play(lang, Msg.REPORT_THANKS)
        BammApp.io.execute {
            CommunityRepository(this).report(number)
            IncidentLog(this).add(IncidentLog.Type.REPORT, number, "signalé depuis l'alerte")
        }
    }

    /** Déblocage : double tap puis 10 secondes d'attente, pour casser l'urgence créée par le fraudeur. */
    private fun setupUnlock() {
        unlocked = false
        timer?.cancel()
        b.unlockButton.text = getString(R.string.unlock_double_tap)
        b.unlockProgress.progress = 0
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (timer == null && !unlocked) startCountdown() else if (unlocked) proceed()
                return true
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (unlocked) proceed()
                return true
            }
        })
        b.unlockButton.setOnTouchListener { v, e -> detector.onTouchEvent(e).also { if (e.action == MotionEvent.ACTION_UP) v.performClick() } }
    }

    private fun startCountdown() {
        timer = object : CountDownTimer(UNLOCK_DELAY, 100) {
            override fun onTick(left: Long) {
                b.unlockProgress.progress = ((UNLOCK_DELAY - left) * 100 / UNLOCK_DELAY).toInt()
                b.unlockButton.text = getString(R.string.unlock_wait, (left / 1000 + 1).toInt())
            }
            override fun onFinish() {
                unlocked = true
                b.unlockProgress.progress = 100
                b.unlockButton.text = getString(R.string.unlock_proceed)
            }
        }.start()
    }

    private fun proceed() {
        val c = code ?: return
        Prefs(this).allowOnceUssd = c
        IncidentLog(this).add(IncidentLog.Type.USSD_UNLOCKED, number, "$c débloqué manuellement")
        val action = if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
            Intent.ACTION_CALL else Intent.ACTION_DIAL
        startActivity(Intent(action, Uri.parse("tel:" + Uri.encode(c))))
        finish()
    }

    private fun close() {
        voice.stop()
        if (level != Level.GREEN) {
            val lesson = if (mode == Mode.USSD) Msg.LESSON_USSD else Msg.LESSON_MTN
            startActivity(LessonActivity.intent(this, lesson))
        }
        finish()
    }

    override fun onDestroy() {
        timer?.cancel()
        voice.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val UNLOCK_DELAY = 10_000L
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_LEVEL = "level"
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_REASONS = "reasons"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_AMOUNT = "amount"

        fun intent(ctx: Context, mode: Mode, number: String, level: Level, reasons: List<String>,
                   code: String? = null, amount: Long? = null): Intent =
            Intent(ctx, AlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_LEVEL, level.name)
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_REASONS, reasons.toTypedArray())
                .putExtra(EXTRA_CODE, code)
                .putExtra(EXTRA_AMOUNT, amount ?: -1L)
    }
}
