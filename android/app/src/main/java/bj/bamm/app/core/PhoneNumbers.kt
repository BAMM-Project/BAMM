package bj.bamm.app.core

/** Normalisation des numéros béninois — même règle que `backend/app/numbers.py`. */
object PhoneNumbers {
    fun normalize(raw: String?): String {
        var d = raw.orEmpty().filter { it.isDigit() }
        if (d.startsWith("00229")) d = d.substring(5)
        else if (d.startsWith("229") && d.length > 10) d = d.substring(3)
        return if (d.length in 3..15) d else ""
    }

    /** Affichage lisible : 0197123456 → 01 97 12 34 56 */
    fun pretty(n: String): String = n.chunked(2).joinToString(" ")
}
