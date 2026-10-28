package bj.bamm.app.core

/**
 * Langues proposées au premier lancement. Pour le hackathon, les messages existent en
 * français et en fon ; yoruba, dendi et bariba utilisent le français en attendant les
 * traductions et enregistrements de locuteurs natifs (voir `res/raw/README.md`).
 */
enum class Lang(val code: String, val nativeName: String, val translated: Boolean) {
    FON("fon", "Fɔ̀ngbè", true),
    YORUBA("yo", "Yorùbá", false),
    DENDI("ddn", "Dendi", false),
    BARIBA("bba", "Baatɔnum", false),
    FRENCH("fr", "Français", true);

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code } ?: FRENCH
    }
}

enum class Msg { CALL_ALERT, USSD_ALERT, CALL_SAFE, CALL_UNKNOWN, REPORT_THANKS, LESSON_MTN, LESSON_USSD }

object Messages {
    private val fr = mapOf(
        Msg.CALL_ALERT to "Cet appel vient probablement d'un arnaqueur. Ne donne ton code à personne.",
        Msg.USSD_ALERT to "Ce code va transférer ton argent. Ne le fais pas.",
        Msg.CALL_SAFE to "Numéro officiel de l'opérateur.",
        Msg.CALL_UNKNOWN to "Numéro inconnu. Ne donne jamais ton code secret.",
        Msg.REPORT_THANKS to "Merci. Ce numéro est signalé pour protéger tout le monde.",
        Msg.LESSON_MTN to "MTN ne t'appellera jamais pour te demander ton code. Le seul numéro MTN est le 97 97 001.",
        Msg.LESSON_USSD to "Un code qui commence par 880 suivi d'un numéro, c'est un transfert d'argent. Ne le tape jamais si quelqu'un te le dicte au téléphone.",
    )

    // Textes fon issus du dossier projet ; à faire relire par un locuteur natif.
    private val fon = mapOf(
        Msg.CALL_ALERT to "Alɔ elɔ nyí ajotɔ́ sín alɔ wɛ. Ma ná mɛɖebǔ hùn towe ó.",
        Msg.USSD_ALERT to "Klɛ́nu elɔ na sɛ́ akwɛ́ towe yì. Ma blo ó.",
    )

    fun text(lang: Lang, msg: Msg): String = when (lang) {
        Lang.FON -> fon[msg] ?: fr.getValue(msg)
        else -> fr.getValue(msg)
    }

    fun french(msg: Msg): String = fr.getValue(msg)

    /** Existe-t-il un texte natif (et donc potentiellement un audio natif) pour ce message ? */
    fun hasNative(lang: Lang, msg: Msg) = lang == Lang.FRENCH || (lang == Lang.FON && msg in fon)
}
