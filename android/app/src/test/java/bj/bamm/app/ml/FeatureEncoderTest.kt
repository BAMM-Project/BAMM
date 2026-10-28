package bj.bamm.app.ml

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Vérifie que l'encodeur Kotlin respecte le contrat écrit par le notebook dans model_meta.json. */
class FeatureEncoderTest {

    private val meta = ModelMeta.parse(File("src/main/assets/model_meta.json").readText())

    @Test fun `ordre et one-hot conformes au notebook`() {
        val f = CallFeatures(callHour = 11, nReports = 4, callFrequency7d = 120, isRegisteredOperator = false,
            region = "Littoral", callDurationSec = 180, ussdAfterCall = true)
        val x = FeatureEncoder.encode(f, meta.full.features)
        assertEquals(meta.full.features.size, x.size)
        val byName = meta.full.features.zip(x.toList()).toMap()
        assertEquals(11f, byName["call_hour"])
        assertEquals(180f, byName["call_duration_sec"])
        assertEquals(1f, byName["ussd_after_call"])
        assertEquals(1f, byName["region_Littoral"])
        assertEquals(1f, meta.regions.sumOf { byName.getValue("region_$it").toDouble() }.toFloat())
    }

    @Test fun `le modele avant decroche n'utilise ni la duree ni l'USSD`() {
        val names = meta.precall.features
        assertEquals(false, "call_duration_sec" in names || "ussd_after_call" in names)
        val x = FeatureEncoder.encode(CallFeatures(9, 0, 3, true, "Borgou"), names)
        assertArrayEquals(floatArrayOf(9f, 0f, 3f, 1f), x.copyOfRange(0, 4), 0f)
    }
}
