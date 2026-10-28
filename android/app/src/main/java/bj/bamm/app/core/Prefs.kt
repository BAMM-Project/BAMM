package bj.bamm.app.core

import android.content.Context
import bj.bamm.app.BuildConfig
import java.util.UUID

/** Préférences et état court terme de l'app (SharedPreferences). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("bamm", Context.MODE_PRIVATE)

    var language: Lang?
        get() = sp.getString("lang", null)?.let { Lang.of(it) }
        set(v) = sp.edit().putString("lang", v?.code).apply()

    var region: String
        get() = sp.getString("region", "Littoral")!!
        set(v) = sp.edit().putString("region", v).apply()

    var apiUrl: String
        get() = sp.getString("api_url", BuildConfig.DEFAULT_API_URL)!!
        set(v) = sp.edit().putString("api_url", v.trimEnd('/')).apply()

    /** Identifiant anonyme de l'appareil, pour dédoublonner les signalements côté serveur. */
    val deviceId: String
        get() = sp.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            sp.edit().putString("device_id", it).apply()
        }

    // --- Contexte du dernier appel (utilisé par la couche USSD) -----------------
    var lastCallNumber: String?
        get() = sp.getString("call_number", null)
        set(v) = sp.edit().putString("call_number", v).apply()
    var lastCallLevel: String?
        get() = sp.getString("call_level", null)
        set(v) = sp.edit().putString("call_level", v).apply()
    var lastCallRisk: Float
        get() = sp.getFloat("call_risk", 0f)
        set(v) = sp.edit().putFloat("call_risk", v).apply()
    var callRingingAt: Long
        get() = sp.getLong("call_ringing", 0)
        set(v) = sp.edit().putLong("call_ringing", v).apply()
    var callAnsweredAt: Long
        get() = sp.getLong("call_answered", 0)
        set(v) = sp.edit().putLong("call_answered", v).apply()
    var callEndedAt: Long
        get() = sp.getLong("call_ended", 0)
        set(v) = sp.edit().putLong("call_ended", v).apply()
    var callInProgress: Boolean
        get() = sp.getBoolean("call_active", false)
        set(v) = sp.edit().putBoolean("call_active", v).apply()

    /** Code USSD explicitement débloqué par l'utilisateur (double tap + 10 s) : laissé passer une fois. */
    var allowOnceUssd: String?
        get() = sp.getString("allow_once", null)
        set(v) = sp.edit().putString("allow_once", v).apply()
}
