package bj.bamm.app.data

import android.content.Context
import android.util.Log
import bj.bamm.app.core.PhoneNumbers
import bj.bamm.app.core.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Base communautaire (couche 3) : API BAMM + cache local pour fonctionner hors ligne.
 * Toutes les méthodes réseau sont bloquantes : à appeler hors du thread principal.
 */
class CommunityRepository(context: Context) {

    data class Status(
        val number: String,
        val official: Boolean,
        val officialLabel: String?,
        val reports: Int,
        val callFrequency7d: Int,
        val level: String,          // green · red · orange · unknown
        val online: Boolean,
    )

    private val prefs = Prefs(context)
    private val sp = context.getSharedPreferences("bamm_community", Context.MODE_PRIVATE)
    private val official: Map<String, String> = loadOfficial(context)

    fun officialLabel(number: String): String? = official[number]

    /** Vérifie un numéro (signalements + estimation de fréquence d'appels). Repli sur le cache. */
    fun lookup(rawNumber: String, timeoutMs: Int = 2000): Status {
        val n = PhoneNumbers.normalize(rawNumber)
        try {
            val id = URLEncoder.encode(prefs.deviceId, "UTF-8")
            val o = JSONObject(http("GET", "/numbers/$n?checker_id=$id", null, timeoutMs))
            val st = Status(n, o.getBoolean("official"), o.optString("official_label").ifEmpty { null },
                o.getInt("distinct_reporters"), o.getInt("call_frequency_7d"), o.getString("level"), true)
            cacheCount(n, st.reports)
            return mergeLocal(st)
        } catch (e: Exception) {
            Log.i(TAG, "lookup hors ligne ($n) : ${e.message}")
        }
        return cached(n)
    }

    /** Statut depuis le cache uniquement (utilisable sur le thread principal). */
    fun cached(rawNumber: String): Status {
        val n = PhoneNumbers.normalize(rawNumber)
        val count = cachedCount(n)
        val level = when {
            official.containsKey(n) -> "green"
            count >= RED_THRESHOLD -> "red"
            count > 0 -> "orange"
            else -> "unknown"
        }
        return mergeLocal(Status(n, official.containsKey(n), official[n], count, 0, level, false))
    }

    /** Un numéro que j'ai moi-même signalé reste rouge pour moi, même hors ligne. */
    private fun mergeLocal(s: Status): Status =
        if (!s.official && s.number in myReports()) s.copy(level = "red", reports = maxOf(s.reports, 1)) else s

    /** Signalement en un geste. Mis en file d'attente si le réseau est indisponible. */
    fun report(rawNumber: String): Boolean {
        val n = PhoneNumbers.normalize(rawNumber)
        if (n.isEmpty() || official.containsKey(n)) return false
        sp.edit().putStringSet(KEY_MINE, myReports() + n).apply()
        return try {
            postReport(n); true
        } catch (e: Exception) {
            sp.edit().putStringSet(KEY_PENDING, pending() + n).apply()
            Log.i(TAG, "signalement en attente ($n) : ${e.message}")
            false
        }
    }

    /** Envoie les signalements en attente et rafraîchit la liste rouge. */
    fun sync(): Boolean = try {
        for (n in pending()) {
            postReport(n)
            sp.edit().putStringSet(KEY_PENDING, pending() - n).apply()
        }
        val red = JSONObject(http("GET", "/blocklist", null, 5000)).getJSONArray("red")
        val counts = JSONObject()
        for (i in 0 until red.length()) red.getJSONObject(i).let { counts.put(it.getString("number"), it.getInt("reports")) }
        sp.edit().putString(KEY_COUNTS, counts.toString()).putLong("synced_at", System.currentTimeMillis()).apply()
        true
    } catch (e: Exception) {
        Log.i(TAG, "sync impossible : ${e.message}")
        false
    }

    fun myReports(): Set<String> = sp.getStringSet(KEY_MINE, emptySet())!!.toSet()
    fun pending(): Set<String> = sp.getStringSet(KEY_PENDING, emptySet())!!.toSet()
    fun redCount(): Int = counts().let { c -> c.keys().asSequence().count { c.getInt(it) >= RED_THRESHOLD } }
    val lastSync: Long get() = sp.getLong("synced_at", 0)

    private fun postReport(n: String) {
        val body = JSONObject().put("number", n).put("reporter_id", prefs.deviceId).put("region", prefs.region)
        http("POST", "/reports", body.toString(), 4000)
    }

    private fun counts() = JSONObject(sp.getString(KEY_COUNTS, "{}")!!)
    private fun cachedCount(n: String) = counts().optInt(n, 0)
    private fun cacheCount(n: String, c: Int) {
        if (c > 0) sp.edit().putString(KEY_COUNTS, counts().put(n, c).toString()).apply()
    }

    private fun http(method: String, path: String, body: String?, timeoutMs: Int): String {
        val c = URL(prefs.apiUrl + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            // 409 = numéro officiel : le serveur refuse le signalement, ce n'est pas une panne
            if (code !in 200..299 && code != 409) error("HTTP $code")
            return (if (code < 400) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val RED_THRESHOLD = 3
        private const val TAG = "BAMM/Community"
        private const val KEY_MINE = "mine"
        private const val KEY_PENDING = "pending"
        private const val KEY_COUNTS = "counts"

        private fun loadOfficial(ctx: Context): Map<String, String> {
            val a = JSONArray(ctx.assets.open("official_numbers.json").bufferedReader().use { it.readText() })
            return (0 until a.length()).associate { i ->
                a.getJSONObject(i).let { PhoneNumbers.normalize(it.getString("number")) to it.getString("label") }
            }
        }
    }
}
