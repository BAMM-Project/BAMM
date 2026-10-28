package bj.bamm.app.ml

import org.json.JSONObject

/** Métadonnées d'un modèle, lues depuis `assets/model_meta.json` (écrit par le notebook). */
data class ModelSpec(val file: String, val features: List<String>, val threshold: Float)

data class ModelMeta(val regions: List<String>, val full: ModelSpec, val precall: ModelSpec) {
    companion object {
        fun parse(json: String): ModelMeta {
            val o = JSONObject(json)
            val regions = o.getJSONArray("regions").let { a -> List(a.length()) { a.getString(it) } }
            val models = o.getJSONObject("models")
            fun spec(key: String) = models.getJSONObject(key).let { m ->
                val f = m.getJSONArray("features")
                ModelSpec(m.getString("file"), List(f.length()) { f.getString(it) }, m.getDouble("threshold").toFloat())
            }
            return ModelMeta(regions, spec("full"), spec("precall"))
        }
    }
}

/** Métadonnées d'un appel, en valeurs brutes (la normalisation est dans le modèle). */
data class CallFeatures(
    val callHour: Int,
    val nReports: Int,
    val callFrequency7d: Int,
    val isRegisteredOperator: Boolean,
    val region: String,
    val callDurationSec: Int = 0,
    val ussdAfterCall: Boolean = false,
)

/**
 * Construit le vecteur d'entrée dans l'ordre exact de `features` (contrat défini par
 * `data_science/bamm/features.py`). Une feature inconnue fait échouer bruyamment :
 * mieux vaut une erreur qu'une prédiction silencieusement fausse.
 */
object FeatureEncoder {
    fun encode(f: CallFeatures, names: List<String>): FloatArray = FloatArray(names.size) { i ->
        when (val n = names[i]) {
            "call_hour" -> f.callHour.toFloat()
            "call_duration_sec" -> f.callDurationSec.toFloat()
            "n_reports" -> f.nReports.toFloat()
            "call_frequency_7d" -> f.callFrequency7d.toFloat()
            "is_registered_operator" -> if (f.isRegisteredOperator) 1f else 0f
            "ussd_after_call" -> if (f.ussdAfterCall) 1f else 0f
            else -> if (n.startsWith("region_")) {
                if (n.removePrefix("region_") == f.region) 1f else 0f
            } else error("feature inconnue : $n")
        }
    }
}
