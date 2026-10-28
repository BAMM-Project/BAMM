package bj.bamm.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import bj.bamm.app.core.RiskEngine
import bj.bamm.app.core.UssdDecision
import bj.bamm.app.data.IncidentLog

/** Décision + effets (alerte, journal) partagés par toutes les voies d'interception USSD. */
object UssdGuard {

    private var lastCode: String? = null
    private var lastAt = 0L

    fun check(ctx: Context, code: String): UssdDecision {
        val d = RiskEngine(ctx).checkUssd(code)
        if (d.block) {
            val now = System.currentTimeMillis()
            // Le même code peut être vu par plusieurs voies (broadcast + accessibilité)
            if (d.ussd.code != lastCode || now - lastAt > 5_000) {
                lastCode = d.ussd.code; lastAt = now
                IncidentLog(ctx).add(IncidentLog.Type.USSD_BLOCKED, d.ussd.recipient ?: "?",
                    "${d.ussd.code} · ${d.ussd.amount ?: "?"} FCFA · ${d.reasons.joinToString(" · ")}")
                Alerts.ussdBlocked(ctx, d)
            }
        }
        return d
    }

    /**
     * Exécute un code USSD après validation : `sendUssdRequest` (réponse affichée via
     * UssdResponseCallback) ou, à défaut, le composeur système.
     */
    @SuppressLint("MissingPermission")
    fun execute(ctx: Context, code: String, onResponse: (String) -> Unit) {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        try {
            tm.sendUssdRequest(code, object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(t: TelephonyManager, req: String, resp: CharSequence) = onResponse(resp.toString())
                override fun onReceiveUssdResponseFailed(t: TelephonyManager, req: String, failureCode: Int) =
                    onResponse("Échec USSD (code $failureCode)")
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            ctx.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(code))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
