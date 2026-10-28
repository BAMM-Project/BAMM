package bj.bamm.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import bj.bamm.app.core.UssdAnalyzer
import bj.bamm.app.ui.UssdGuard

/**
 * Couche 2 (voie 1) — codes composés depuis le clavier : sur la plupart des appareils, un
 * USSD lancé avec le bouton d'appel passe par NEW_OUTGOING_CALL, ce qui permet de
 * l'annuler *avant* son envoi au réseau (`resultData = null`).
 * La voie 2 (accessibilité) couvre les claviers qui court-circuitent ce broadcast.
 */
class OutgoingCallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val dialed = resultData ?: intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER) ?: return
        if (!UssdAnalyzer.isCompleteUssd(dialed)) return
        val d = UssdGuard.check(ctx, dialed)
        if (d.block) {
            Log.w("BAMM/Outgoing", "USSD bloqué : ${d.ussd.code} ${d.reasons}")
            resultData = null   // annule la composition
        }
    }
}
