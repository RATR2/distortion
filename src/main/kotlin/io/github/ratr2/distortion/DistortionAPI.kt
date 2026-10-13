package io.github.ratr2.distortion

import org.bukkit.Location

/**
 * Static entry points for other plugins, callable directly or via reflection, on the main thread:
 *
 * ```java
 * Class<?> api = Bukkit.getPluginManager().getPlugin("distortion").getClass().getClassLoader()
 *     .loadClass("io.github.ratr2.distortion.DistortionAPI");
 * boolean ok = (boolean) api.getMethod("spawn", Location.class, int.class, double.class, int.class)
 *     .invoke(null, location, 0x55CCFF, 8.0, 40);
 * ```
 */
object DistortionAPI {

    @Volatile
    internal var manager: RippleManager? = null

    /** Spawns a ripple of color [rgb] (0xRRGGBB) growing to [radius] blocks over [ticks]; false if disabled or all 16 slots are busy. */
    @JvmStatic
    fun spawn(location: Location, rgb: Int, radius: Double, ticks: Int): Boolean =
        manager?.spawn(location, rgb, radius, ticks) ?: false

    @JvmStatic
    fun activeRipples(): Int = manager?.activeCount ?: 0

    @JvmStatic
    fun maxRipples(): Int = RippleManager.SLOTS
}
