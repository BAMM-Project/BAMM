package bj.bamm.app.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import bj.bamm.app.BammApp
import bj.bamm.app.R
import bj.bamm.app.data.CommunityRepository
import bj.bamm.app.data.IncidentLog

/** Bouton « Signaler » de la notification post-appel : un seul geste, pas de formulaire. */
class ReportActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val number = intent.getStringExtra("number") ?: return
        ctx.getSystemService(NotificationManager::class.java).cancelAll()
        Toast.makeText(ctx, R.string.report_done, Toast.LENGTH_SHORT).show()
        val pending = goAsync()
        BammApp.io.execute {
            try {
                CommunityRepository(ctx).report(number)
                IncidentLog(ctx).add(IncidentLog.Type.REPORT, number, "signalé depuis la notification")
            } finally {
                pending.finish()
            }
        }
    }
}
