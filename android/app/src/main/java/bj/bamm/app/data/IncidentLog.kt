package bj.bamm.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Journal local des incidents affiché sur l'écran principal. */
class IncidentLog(context: Context) {

    enum class Type { CALL_RED, CALL_ORANGE, USSD_BLOCKED, USSD_UNLOCKED, REPORT }

    data class Incident(val at: Long, val type: Type, val number: String, val detail: String)

    private val sp = context.getSharedPreferences("bamm_incidents", Context.MODE_PRIVATE)

    fun add(type: Type, number: String, detail: String) {
        val a = JSONArray(sp.getString("items", "[]"))
        val items = JSONArray().put(JSONObject().put("at", System.currentTimeMillis())
            .put("type", type.name).put("number", number).put("detail", detail))
        for (i in 0 until minOf(a.length(), MAX - 1)) items.put(a.get(i))
        sp.edit().putString("items", items.toString()).apply()
    }

    fun all(): List<Incident> {
        val a = JSONArray(sp.getString("items", "[]"))
        return List(a.length()) { i ->
            a.getJSONObject(i).let {
                Incident(it.getLong("at"), Type.valueOf(it.getString("type")), it.getString("number"), it.getString("detail"))
            }
        }
    }

    companion object { private const val MAX = 50 }
}
