package bj.bamm.app.core

/**
 * Analyse structurelle d'un code USSD, sans réseau.
 *
 * `*880*7721*45000#` → service 880 (MTN MoMo), destinataire 7721, montant 45 000 → TRANSFER.
 * `*880#` seul ouvre le menu MoMo → MENU. `*123#` (solde, forfaits…) → OTHER.
 */
object UssdAnalyzer {

    enum class Kind { TRANSFER, MENU, OTHER, NOT_USSD }

    data class Result(
        val code: String,
        val kind: Kind,
        val service: String? = null,
        val recipient: String? = null,
        val amount: Long? = null,
    ) {
        val isTransfer get() = kind == Kind.TRANSFER
    }

    /** Services de paiement mobile au Bénin : MTN MoMo (880) et Moov Money / Flooz (855). */
    val MONEY_SERVICES = setOf("880", "855")

    private val USSD = Regex("^[*#][0-9*#]*#$")

    fun isCompleteUssd(text: String?): Boolean = text != null && USSD.matches(text.replace(" ", ""))

    fun analyze(raw: String): Result {
        val code = raw.replace(" ", "").replace("%23", "#")
        if (!USSD.matches(code)) return Result(code, Kind.NOT_USSD)
        val parts = code.trim('*', '#').split('*', '#').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return Result(code, Kind.OTHER)
        val service = parts[0]
        val args = parts.drop(1)
        val numeric = args.filter { s -> s.all { it.isDigit() } }

        if (service in MONEY_SERVICES) {
            if (args.isEmpty()) return Result(code, Kind.MENU, service)
            // Raccourci de transfert : *880*<destinataire>*<montant>#. Les choix de menu
            // (*880*3*1#) ont des segments d'un chiffre et ne sont pas des transferts.
            if (numeric.size >= 2) {
                val recipient = numeric[numeric.size - 2]
                val amount = numeric.last().toLongOrNull()
                if (recipient.length >= 4 && amount != null && amount >= 50) {
                    return Result(code, Kind.TRANSFER, service, recipient, amount)
                }
            }
            // Variante menu chiffré (*880*1*1*0197…#) : présence d'un numéro de téléphone
            phoneIn(args)?.let { return Result(code, Kind.TRANSFER, service, it, null) }
            return Result(code, Kind.MENU, service)
        }
        // Service inconnu : un numéro de téléphone suivi d'un montant ressemble à un transfert
        val phone = phoneIn(args)
        if (phone != null) {
            val after = args.dropWhile { it != phone }.drop(1).firstOrNull { s -> s.all { it.isDigit() } }
            if (after != null) return Result(code, Kind.TRANSFER, service, phone, after.toLongOrNull())
        }
        return Result(code, Kind.OTHER, service)
    }

    /** Numéro béninois : 8 chiffres (ancien plan) ou 10 chiffres commençant par 01 (depuis 2024). */
    private fun phoneIn(args: List<String>) = args.firstOrNull { s ->
        s.all { it.isDigit() } && (s.length == 8 || (s.length == 10 && s.startsWith("01")))
    }
}
