package io.github.ratr2.distortion

import org.bukkit.Bukkit

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
    V1_21_4("1.21.4", "1.21.4", 769, 769),
    V1_21_5("1.21.5", "1.21.5", 770, 770),
    V1_21_8("1.21.8", "1.21.6 to 1.21.8", 771, 772),
    V1_21_10("1.21.10", "1.21.9 to 1.21.10", 773, 773),
    V1_21_11("1.21.11", "1.21.11", 774, 774),
    V26_1("26.1", "26.1", 775, 775),
    V26_2("26.2", "26.2", 776, 776),
    V26_3("26.3", "26.3", 777, 777, usesPostEffects = true),
    V26_4("26.4", "26.4", 778, 778, usesPostEffects = true);

    companion object {
        /** Protocol of the Minecraft version the server runs, used for players that do not connect through ViaVersion. */
        val SERVER_PROTOCOL: Int by lazy { detectServerProtocol() }

        val oldest = entries.first()
        val newest = entries.last()

        fun forProtocol(protocol: Int): PackEra? = entries.firstOrNull { protocol in it.minProtocol..it.maxProtocol }

        private val BY_VERSION = listOf(
            "1.21.4" to 769, "1.21.5" to 770, "1.21.6" to 771, "1.21.7" to 772, "1.21.8" to 772,
            "1.21.9" to 773, "1.21.10" to 773, "1.21.11" to 774,
            "26.1" to 775, "26.2" to 776, "26.3" to 777, "26.4" to 778,
        )

        private fun detectServerProtocol(): Int {
            // Paper exposes the protocol directly on newer builds; fall back to the version string otherwise.
            runCatching {
                val unsafe = Bukkit.getUnsafe()
                val value = unsafe.javaClass.getMethod("getProtocolVersion").invoke(unsafe) as Int
                if (value > 0) return value
            }
            val version = Bukkit.getMinecraftVersion()
            // Longest prefix first, so "1.21.11" is not read as "1.21.1".
            BY_VERSION.sortedByDescending { it.first.length }.firstOrNull { version == it.first || version.startsWith(it.first + ".") || version.startsWith(it.first + "-") }
                ?.let { return it.second }
            return newest.maxProtocol
        }
    }
}
