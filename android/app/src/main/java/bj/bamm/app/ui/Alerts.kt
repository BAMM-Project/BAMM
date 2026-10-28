package bj.bamm.app.ui

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import bj.bamm.app.BammApp
import bj.bamm.app.R
import bj.bamm.app.core.Assessment
import bj.bamm.app.core.Level
import bj.bamm.app.core.UssdDecision
import bj.bamm.app.service.ReportActionReceiver

/** Point d'entrée unique pour afficher une alerte depuis un service ou un receiver. */
object Alerts {

    /** Rouge : plein écran par-dessus l'appel. Orange / vert : simple notification colorée. */
    fun incomingCall(ctx: Context, a: Assessment) {
        val i = AlertActivity.intent(ctx, AlertActivity.Mode.CALL, a.number, a.level, a.reasons)
        if (a.level == Level.RED) {
            show(ctx, i, ID_CALL, R.string.alert_call_title, a.number)
            return
        }
        val green = a.level == Level.GREEN
        val n = NotificationCompat.Builder(ctx, BammApp.CH_INFO)
            .setSmallIcon(if (green) R.drawable.ic_check else R.drawable.ic_question)
            .setColor(ctx.getColor(if (green) R.color.bamm_green else R.color.bamm_orange))
            .setContentTitle(ctx.getString(if (green) R.string.call_safe_title else R.string.alert_unknown_title))
            .setContentText(a.number)
            .setContentIntent(PendingIntent.getActivity(ctx, ID_CALL, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setTimeoutAfter(60_000)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_CALL, n)
    }

    fun ussdBlocked(ctx: Context, d: UssdDecision) {
        val i = AlertActivity.intent(ctx, AlertActivity.Mode.USSD, d.ussd.recipient ?: "", Level.RED, d.reasons, d.ussd.code, d.ussd.amount)
        show(ctx, i, ID_USSD, R.string.alert_ussd_title, d.ussd.code)
    }

    /** Après un appel suspect : invitation à signaler (gros bouton pouce vers le bas). */
    fun postCall(ctx: Context, a: Assessment) {
        val open = AlertActivity.intent(ctx, AlertActivity.Mode.POST_CALL, a.number, a.level, a.reasons)
        val report = PendingIntent.getBroadcast(ctx, 3, Intent(ctx, ReportActionReceiver::class.java).putExtra("number", a.number),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, BammApp.CH_ALERT)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(ctx.getString(R.string.post_call_title))
            .setContentText(a.number)
            .setColor(ctx.getColor(R.color.bamm_red))
            .setContentIntent(PendingIntent.getActivity(ctx, 2, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(R.drawable.ic_thumb_down, ctx.getString(R.string.report), report)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_POST, n)
    }

    /**
     * Avec la permission de superposition, l'activité s'ouvre par-dessus l'écran d'appel.
     * Sinon : notification plein écran (affichée immédiatement si l'écran est éteint).
     */
    private fun show(ctx: Context, intent: Intent, id: Int, title: Int, text: String) {
        if (Settings.canDrawOverlays(ctx)) {
            runCatching { ctx.startActivity(intent); return }
        }
        val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, BammApp.CH_ALERT)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(ctx.getString(title))
            .setContentText(text)
            .setColor(ctx.getColor(R.color.bamm_red))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    private const val ID_CALL = 10
    private const val ID_USSD = 11
    private const val ID_POST = 12
}
