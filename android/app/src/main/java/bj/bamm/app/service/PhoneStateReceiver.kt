package bj.bamm.app.service

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import bj.bamm.app.BammApp
import bj.bamm.app.core.Level
import bj.bamm.app.core.Prefs
import bj.bamm.app.core.RiskEngine
import bj.bamm.app.data.IncidentLog
import bj.bamm.app.ui.Alerts

/**
 * Suit le cycle de vie des appels (sonnerie → décroché → raccroché) pour :
 * - mesurer la durée de l'appel (feature du modèle complet) ;
 * - savoir si un appel suspect est en cours ou vient de finir (contexte de la couche USSD) ;
 * - analyser l'appel entrant quand le rôle de filtrage n'est pas disponible (Android 8-9).
 */
class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val prefs = Prefs(ctx)
        val now = System.currentTimeMillis()
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (now - prefs.callRingingAt > 30_000) {
                    prefs.callRingingAt = now
                    prefs.callAnsweredAt = 0
                }
                if (!number.isNullOrBlank() && !screeningRoleHeld(ctx)) {
                    val pending = goAsync()
                    BammApp.io.execute {
                        try {
                            val a = RiskEngine(ctx).assessIncoming(number)
                            prefs.lastCallNumber = a.number
                            prefs.lastCallLevel = a.level.name
                            if (a.level == Level.RED) IncidentLog(ctx).add(IncidentLog.Type.CALL_RED, a.number, a.reasons.joinToString(" · "))
                            Alerts.incomingCall(ctx, a)
                        } finally {
                            pending.finish()
                        }
                    }
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (now - prefs.callRingingAt < 120_000 && prefs.callAnsweredAt == 0L) {
                    prefs.callAnsweredAt = now
                } else if (!prefs.callInProgress) {
                    // Appel sortant : pas d'appelant à évaluer
                    prefs.lastCallLevel = null
                    prefs.callAnsweredAt = now
                }
                prefs.callInProgress = true
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                val answered = prefs.callAnsweredAt
                prefs.callInProgress = false
                prefs.callEndedAt = now
                val level = prefs.lastCallLevel?.let { Level.valueOf(it) } ?: return
                val num = prefs.lastCallNumber ?: return
                val durationSec = if (answered > 0) ((now - answered) / 1000).toInt() else 0
                if (level == Level.GREEN || durationSec < 15) return
                val pending = goAsync()
                BammApp.io.execute {
                    try {
                        val a = RiskEngine(ctx).assessAfterCall(num, durationSec, ussdAfter = false, online = true)
                        Log.i(TAG, "fin d'appel $num ${durationSec}s → ${a.level} (${a.risk})")
                        if (a.level == Level.RED || level == Level.RED) {
                            prefs.lastCallLevel = Level.RED.name
                            Alerts.postCall(ctx, a.copy(level = Level.RED))
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun screeningRoleHeld(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 29 && ctx.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

    companion object { private const val TAG = "BAMM/PhoneState" }
}
