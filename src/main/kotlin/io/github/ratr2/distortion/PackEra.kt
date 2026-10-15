package io.github.ratr2.distortion

/**
 * One embedded resource pack (`packs/[id]` in the jar, refreshed by scripts/sync-packs.sh) and the client protocol
 * versions it works for. The protocol numbers are the ones ViaVersion lists for each release.
 */
enum class PackEra(
    val id: String,
    val label: String,
    val minProtocol: Int,
    val maxProtocol: Int,
    /**
     * True when the client takes the server driven post effect API. Older clients have no such packet, so their pack
     * overrides the Fabulous graphics transparency effect instead, which only runs with Fabulous graphics on.
     */
    val usesPostEffects: Boolean = false,
) {
    V1_21_4("1.21.4", "1.21.2 to 1.21.4", 768, 769),
    V1_21_5("1.21.5", "1.21.5", 770, 770),
    V1_21_8("1.21.8", "1.21.6 to 1.21.8", 771, 772),
    V1_21_10("1.21.10", "1.21.9 to 1.21.10", 773, 773),
    V1_21_11("1.21.11", "1.21.11", 774, 774),
    V26_1("26.1", "26.1", 775, 775),
    V26_2("26.2", "26.2", 776, 776),
    V26_3("26.3", "26.3", 777, 777, usesPostEffects = true);

    companion object {
        /** The protocol this build's Paper API speaks natively. */
        const val SERVER_PROTOCOL = 777

        val oldest = entries.first()
        val newest = entries.last()

        fun forProtocol(protocol: Int): PackEra? = entries.firstOrNull { protocol in it.minProtocol..it.maxProtocol }
    }
}
