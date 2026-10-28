package bj.bamm.app.core

import android.content.Context
import bj.bamm.app.data.CommunityRepository
import bj.bamm.app.ml.CallFeatures
import bj.bamm.app.ml.RiskModel
import java.util.Calendar

enum class Level { GREEN, ORANGE, RED }

data class Assessment(
    val number: String,
    val level: Level,
    val risk: Float?,
    val reasons: List<String>,
    val reports: Int,
)

data class UssdDecision(
    val ussd: UssdAnalyzer.Result,
    val block: Boolean,
    val reasons: List<String>,
)

/**
 * Vérification en 3 étapes du document projet :
 * 1. whitelist opérateur → 2. base communautaire → 3. modèle ML embarqué.
 */
class RiskEngine(context: Context) {
    private val ctx = context.applicationContext
    private val prefs = Prefs(ctx)
    private val repo = CommunityRepository(ctx)
    private val model by lazy { RiskModel.get(ctx) }

    /** À la sonnerie (modèle « avant décroché »). Réseau autorisé : hors thread principal. */
    fun assessIncoming(rawNumber: String, online: Boolean = true, hour: Int = currentHour()): Assessment {
        val st = if (online) repo.lookup(rawNumber, 1500) else repo.cached(rawNumber)
        if (st.official) return Assessment(st.number, Level.GREEN, 0f, listOf("numéro officiel : ${st.officialLabel}"), 0)

        val risk = model.scorePrecall(CallFeatures(hour, st.reports, st.callFrequency7d, false, prefs.region))
        val thr = model.meta.precall.threshold
        val reasons = buildList {
            if (st.level == "red") add("signalé par ${st.reports} utilisateurs")
            else if (st.reports > 0) add("déjà signalé (${st.reports})")
            if (st.callFrequency7d >= 30) add("appelle beaucoup de monde (${st.callFrequency7d} en 7 j)")
            add("score du modèle : ${"%.2f".format(risk)} (seuil ${"%.2f".format(thr)})")
            if (!st.online) add("hors ligne : cache local")
        }
        val level = if (st.level == "red" || risk >= thr) Level.RED else Level.ORANGE
        return Assessment(st.number, level, risk, reasons, st.reports)
    }

    /** Après l'appel (modèle complet : + durée, + USSD composé ensuite). */
    fun assessAfterCall(rawNumber: String, durationSec: Int, ussdAfter: Boolean, online: Boolean = false): Assessment {
        val st = if (online) repo.lookup(rawNumber, 1500) else repo.cached(rawNumber)
        if (st.official) return Assessment(st.number, Level.GREEN, 0f, emptyList(), 0)
        val hour = Calendar.getInstance().apply {
            prefs.callRingingAt.takeIf { it > 0 }?.let { timeInMillis = it }
        }.get(Calendar.HOUR_OF_DAY)
        val risk = model.scoreFull(CallFeatures(hour, st.reports, st.callFrequency7d, false, prefs.region, durationSec, ussdAfter))
        val level = if (st.level == "red" || risk >= model.meta.full.threshold) Level.RED else Level.ORANGE
        return Assessment(st.number, level, risk, listOf("score après appel : ${"%.2f".format(risk)}"), st.reports)
    }

    /**
     * Couche 2 : faut-il bloquer ce code USSD ? Réseau interdit (appelé depuis un
     * BroadcastReceiver / l'accessibilité sur le thread principal) : cache local uniquement.
     */
    fun checkUssd(code: String, now: Long = System.currentTimeMillis()): UssdDecision {
        val u = UssdAnalyzer.analyze(code)
        if (!u.isTransfer) return UssdDecision(u, false, emptyList())
        if (prefs.allowOnceUssd == u.code) {
            prefs.allowOnceUssd = null
            return UssdDecision(u, false, listOf("débloqué par l'utilisateur"))
        }
        val reasons = mutableListOf<String>()
        val lastLevel = prefs.lastCallLevel?.let { Level.valueOf(it) }
        val suspiciousCall = lastLevel != null && lastLevel != Level.GREEN
        val recent = prefs.callEndedAt > 0 && now - prefs.callEndedAt < FIVE_MIN

        if (suspiciousCall && prefs.callInProgress) reasons += "un appel suspect est en cours"
        if (suspiciousCall && recent) reasons += "un appel suspect vient de se terminer"

        u.recipient?.let { r ->
            val st = repo.cached(r)
            if (st.level == "red" || st.level == "orange") reasons += "destinataire signalé par la communauté"
        }
        // Rescore du dernier appel avec le modèle complet : un USSD vient d'être tapé après lui
        val num = prefs.lastCallNumber
        if (num != null && (prefs.callInProgress || recent)) {
            val dur = ((if (prefs.callInProgress) now else prefs.callEndedAt) - prefs.callAnsweredAt)
                .takeIf { prefs.callAnsweredAt > 0 }?.div(1000)?.toInt() ?: 0
            val a = assessAfterCall(num, dur, ussdAfter = true)
            if (a.level == Level.RED) reasons += "le modèle juge le dernier appel frauduleux (${"%.2f".format(a.risk)})"
        }
        return UssdDecision(u, reasons.isNotEmpty(), reasons)
    }

    companion object {
        const val FIVE_MIN = 5 * 60 * 1000L
        fun currentHour() = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }
}
