package com.presence

import com.presence.evidence.MotionProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Synthetic 100 Hz accelerometer traces: a phone on a table, a finger tap, an injected tap, a phone in hand. */
class MotionProbeTest {
    private val rnd = Random(7)

    private fun MotionProbe.run(seconds: Int, tapAt: Double?, jolt: Float, handNoise: Float = 0f) {
        for (k in 0 until seconds * 100) {
            val ts = 1_000_000_000L + k * 10_000_000L
            val tapping = tapAt != null && k in (tapAt * 100).toInt() until (tapAt * 100).toInt() + 3
            val noise = (rnd.nextFloat() - 0.5f) * 0.01f + (rnd.nextFloat() - 0.5f) * handNoise
            feed(ts, 0f, 0f, 9.81f + noise + if (tapping) jolt else 0f)
            if (tapAt != null && k == (tapAt * 100).toInt()) onTap(ts)
        }
    }

    private fun probe() = MotionProbe(null, null, measuring = true)

    @Test
    fun fingerTapOnATableIsFeltAndThePhoneCountsAsStill() {
        val (still, touch) = probe().apply { run(30, tapAt = 12.0, jolt = 0.3f) }.takeWindow()
        assertTrue("still=$still", still >= 80)
        assertTrue("touch=$touch", touch in 250..350)
    }

    @Test
    fun injectedTapMovesNothing() {
        val (_, touch) = probe().apply { run(30, tapAt = 12.0, jolt = 0f) }.takeWindow()
        assertTrue("touch=$touch", touch < 40)  // below the missions' min_touch_motion
    }

    @Test
    fun phoneHeldInHandIsNotStill() {
        val (still, _) = probe().apply { run(30, tapAt = null, jolt = 0f, handNoise = 0.6f) }.takeWindow()
        assertTrue("still=$still", still < 60)
    }

    @Test
    fun noSensorMeansNotMeasured() {
        assertEquals(-1 to -1, MotionProbe(null).takeWindow())
    }
}
