package bj.bamm.app.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import bj.bamm.app.R
import bj.bamm.app.core.Lang
import bj.bamm.app.core.Messages
import bj.bamm.app.core.Msg
import bj.bamm.app.core.Prefs
import bj.bamm.app.core.VoiceAlert
import bj.bamm.app.databinding.ActivityLessonBinding

/** Micro-leçon de 15 secondes après chaque incident : voix + icônes + animation simple. */
class LessonActivity : AppCompatActivity() {
    private lateinit var b: ActivityLessonBinding
    private lateinit var voice: VoiceAlert
    private var anim: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLessonBinding.inflate(layoutInflater)
        setContentView(b.root)
        val msg = Msg.valueOf(intent.getStringExtra("msg")!!)
        val lang = Prefs(this).language ?: Lang.FRENCH
        voice = VoiceAlert(this)

        b.lessonText.text = Messages.text(lang, msg)
        if (msg == Msg.LESSON_USSD) {
            b.lessonIconLeft.setImageResource(R.drawable.ic_dialpad)
            b.lessonIconRight.setImageResource(R.drawable.ic_lock)
        } else {
            b.lessonIconLeft.setImageResource(R.drawable.ic_phone)
            b.lessonIconRight.setImageResource(R.drawable.ic_stop_hand)
        }
        b.lessonIconRight.animate().scaleX(1.25f).scaleY(1.25f).setDuration(700).withEndAction {
            b.lessonIconRight.animate().scaleX(1f).scaleY(1f).setDuration(700).start()
        }.start()
        b.lessonClose.setOnClickListener { finish() }
        b.lessonReplay.setOnClickListener { voice.play(lang, msg) }

        anim = ObjectAnimator.ofInt(b.lessonProgress, "progress", 0, 1000).apply {
            duration = 15_000
            interpolator = LinearInterpolator()
            doOnEnd { if (!isFinishing) finish() }
            start()
        }
        voice.play(lang, msg)
    }

    override fun onDestroy() {
        anim?.cancel()
        voice.shutdown()
        super.onDestroy()
    }

    companion object {
        fun intent(ctx: Context, msg: Msg) = Intent(ctx, LessonActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("msg", msg.name)
    }
}
