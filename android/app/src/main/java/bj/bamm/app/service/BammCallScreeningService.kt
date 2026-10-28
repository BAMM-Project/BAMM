package bj.bamm.app.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import bj.bamm.app.BammApp
import bj.bamm.app.core.Level
import bj.bamm.app.core.Prefs
import bj.bamm.app.core.RiskEngine
import bj.bamm.app.data.IncidentLog
import bj.bamm.app.ui.Alerts

/**
 * Couche 1 — chaque appel entrant passe ici (rôle « appli de filtrage des appels », Android 10+).
 * L'appel n'est jamais rejeté : BAMM alerte, l'utilisateur garde la main.
 */
class BammCallScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val allow = CallResponse.Builder().build()
        // Avant Android 10, seul l'entrant est filtré et callDirection n'existe pas
        if (Build.VERSION.SDK_INT >= 29 && details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, allow); return
        }
        val number = details.handle?.schemeSpecificPart.orEmpty()
        // Le système attend une réponse en quelques secondes : on répond tout de suite
        // et on analyse en parallèle.
        respondToCall(details, allow)
        if (number.isBlank()) return
        BammApp.io.execute { screen(number) }
    }

    private fun screen(number: String) {
        val prefs = Prefs(this)
        try {
            val a = RiskEngine(this).assessIncoming(number)
            prefs.lastCallNumber = a.number
            prefs.lastCallLevel = a.level.name
            prefs.lastCallRisk = a.risk ?: 0f
            Log.i(TAG, "appel ${a.number} → ${a.level} ${a.reasons}")
            if (a.level == Level.RED) {
                IncidentLog(this).add(IncidentLog.Type.CALL_RED, a.number, a.reasons.joinToString(" · "))
            }
            Alerts.incomingCall(this, a)
        } catch (e: Exception) {
            Log.e(TAG, "analyse impossible", e)
        }
    }

    companion object { private const val TAG = "BAMM/Screening" }
}
