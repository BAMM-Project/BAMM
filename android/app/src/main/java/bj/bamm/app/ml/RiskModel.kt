package bj.bamm.app.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Les deux modèles TFLite entraînés dans `data_science/notebooks/BAMM_pipeline.ipynb` :
 * - `precall` : quand le téléphone sonne (heure, signalements, fréquence, whitelist, région) ;
 * - `full`    : après l'appel / au moment d'un USSD (+ durée et USSD post-appel).
 * Inférence 100 % locale : fonctionne hors ligne.
 */
class RiskModel private constructor(val meta: ModelMeta, private val pre: Interpreter, private val full: Interpreter) {

    fun scorePrecall(f: CallFeatures): Float = run(pre, FeatureEncoder.encode(f, meta.precall.features))
    fun scoreFull(f: CallFeatures): Float = run(full, FeatureEncoder.encode(f, meta.full.features))

    private fun run(it: Interpreter, x: FloatArray): Float {
        val out = Array(1) { FloatArray(1) }
        synchronized(it) { it.run(arrayOf(x), out) }
        return out[0][0]
    }

    companion object {
        @Volatile private var instance: RiskModel? = null

        fun get(context: Context): RiskModel = instance ?: synchronized(this) {
            instance ?: load(context.applicationContext).also { instance = it }
        }

        private fun load(ctx: Context): RiskModel {
            val meta = ModelMeta.parse(ctx.assets.open("model_meta.json").bufferedReader().use { it.readText() })
            return RiskModel(meta, Interpreter(map(ctx, meta.precall.file)), Interpreter(map(ctx, meta.full.file)))
        }

        private fun map(ctx: Context, name: String): MappedByteBuffer {
            ctx.assets.openFd(name).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { ch ->
                    return ch.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        }
    }
}
