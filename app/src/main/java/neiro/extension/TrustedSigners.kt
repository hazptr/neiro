package neiro.extension

/**
 * Signing keys whose extensions Neiro trusts without asking, whether or not their store has been
 * added: the Keiyoushi extension store and Neiro's own. Values are the SHA-256 of the signing
 * certificate, as the stores publish them in `signingKeyFingerprint`.
 */
object TrustedSigners {

    const val KEIYOUSHI = "9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2"
    const val NEIRO_EXTENSIONS = "c97cee281ebe11f5e28806e96ff52c93d9bf8bccd6d8efb7415fd910780c870d"

    val fingerprints: Set<String> = setOf(KEIYOUSHI, NEIRO_EXTENSIONS)
}
