package com.blugaemand.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The arithmetic behind the latency line the pad logs.
 *
 * Worth testing because the numbers it produces are what the send interval will be argued about
 * with: a percentile off by one rank, or a ring buffer that summarises entries nobody wrote, is a
 * measurement that quietly justifies the wrong number.
 */
class LatencyProbeTest {

    private fun ms(value: Long) = value * 1_000_000L

    @Test
    fun `a probe nobody has fed has nothing to say`() {
        assertNull(LatencyProbe().summary())
    }

    @Test
    fun `nanoseconds come back as milliseconds`() {
        val probe = LatencyProbe()
        probe.record(ms(4), ms(1))

        val summary = probe.summary()!!
        assertEquals(1, summary.samples)
        assertEquals(4f, summary.waiting.median, 0.001f)
        assertEquals(1f, summary.sending.worst, 0.001f)
    }

    @Test
    fun `the percentiles are nearest-rank over the whole window`() {
        val probe = LatencyProbe()
        // 1..100 ms, deliberately out of order: a percentile is about the set of samples, not the
        // order they arrived in, and the ring buffer does not keep that order anyway.
        for (value in (1L..100L).shuffled()) probe.record(ms(value), ms(0))

        val waiting = probe.summary()!!.waiting
        assertEquals(50f, waiting.median, 0.001f)
        assertEquals(95f, waiting.p95, 0.001f)
        assertEquals(100f, waiting.worst, 0.001f)
    }

    @Test
    fun `the window holds the most recent samples and forgets the rest`() {
        // A running figure over a whole session would be dominated by whatever the pad was doing an
        // hour ago, which is exactly what a rate should not be judged on.
        val probe = LatencyProbe(capacity = 4)
        for (value in listOf(90L, 91L, 92L, 93L, 1L, 2L, 3L, 4L)) probe.record(ms(value), ms(0))

        val summary = probe.summary()!!
        assertEquals(4, summary.samples)
        assertEquals(4f, summary.waiting.worst, 0.001f)
    }

    @Test
    fun `a partly filled window summarises only what is in it`() {
        // The trap the ring buffer sets: the untouched tail is zeros, and counting them would make
        // every early median read as zero milliseconds.
        val probe = LatencyProbe(capacity = 100)
        probe.record(ms(8), ms(2))
        probe.record(ms(8), ms(2))

        val summary = probe.summary()!!
        assertEquals(2, summary.samples)
        assertEquals(8f, summary.waiting.median, 0.001f)
    }

    @Test
    fun `a reset window starts again from empty`() {
        val probe = LatencyProbe()
        probe.record(ms(50), ms(50))
        probe.reset()
        assertNull(probe.summary())
    }

    @Test
    fun `the log line names the numbers it is printing`() {
        val probe = LatencyProbe()
        probe.record(ms(2), ms(1))
        val line = probe.summary().toString()
        assertEquals("1 reports, waiting 2.0/2.0/2.0 ms, sending 1.0/1.0/1.0 ms (median/p95/worst)", line)
    }
}
