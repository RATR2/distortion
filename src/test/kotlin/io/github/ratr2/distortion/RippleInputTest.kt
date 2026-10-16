package io.github.ratr2.distortion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RippleInputTest {

    @Test
    fun `radius must be finite, positive and not absurd`() {
        assertTrue(RippleManager.validRadius(0.01))
        assertTrue(RippleManager.validRadius(RippleManager.MAX_RADIUS))
        assertFalse(RippleManager.validRadius(0.0))
        assertFalse(RippleManager.validRadius(-3.0))
        assertFalse(RippleManager.validRadius(Double.NaN))
        assertFalse(RippleManager.validRadius(Double.POSITIVE_INFINITY))
        assertFalse(RippleManager.validRadius(1e300))
        assertFalse(RippleManager.validRadius(RippleManager.MAX_RADIUS + 1))
    }

    @Test
    fun `a radius that passes stays finite as a float`() {
        assertTrue(RippleManager.MAX_RADIUS.toFloat().isFinite())
    }

    @Test
    fun `host routes only accept a source and a plain file name`() {
        assertEquals("distortion" to "pack.zip", HostRoutes.parse("/distortion/pack.zip"))
        assertNull(HostRoutes.parse("/distortion/../secret"))
        assertNull(HostRoutes.parse("/pack.zip"))
        assertNull(HostRoutes.parse("/a/b/c.zip"))
        assertNull(HostRoutes.parse("/.hidden/pack.zip"))
    }
}
