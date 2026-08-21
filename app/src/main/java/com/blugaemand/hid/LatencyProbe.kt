package com.blugaemand.hid

import kotlin.math.roundToInt

/**
 * What the pad's own share of input lag actually is, measured rather than assumed.
 *
 * **It measures the part the app controls**, and only that: from the moment a change to the
 * gamepad's state is recorded to the moment the Bluetooth stack accepts the report carrying it.
 * The radio, the host's stack and the game's own polling are past the end of this and need a
 * measurement of their own; see the *Latency* section of the README for how the rest is done.
 *
 * Two numbers per report, because they answer different questions:
 *
 * - **waiting** — how long the change sat before a send was attempted. This is what the pump's rate
 *   cap costs, and it is the number the send interval is chosen against.
 * - **sending** — how long `sendReport` itself took. This is the stack's, not the pump's, and it is
 *   here because a pump tuned against a number that is really L2CAP back-pressure would be tuned
 *   against the wrong thing.
 *
 * A ring of the most recent [capacity] samples: percentiles over a bounded window are what a rate
 * is judged by, and a running mean over a whole session would be dominated by whatever the pad was
 * doing an hour ago. Every method is called from the report thread alone, so none of this is
 * synchronised.
 */
class LatencyProbe(private val capacity: Int = CAPACITY) {

    private val waiting = LongArray(capacity)
    private val sending = LongArray(capacity)
    private var count = 0
    private var next = 0

    /** Records one report, in nanoseconds. */
    fun record(waitingNanos: Long, sendingNanos: Long) {
        waiting[next] = waitingNanos
        sending[next] = sendingNanos
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    /** What the samples held say, or null if there are none. */
    fun summary(): LatencySummary? {
        if (count == 0) return null
        return LatencySummary(
            samples = count,
            waiting = Latencies.of(waiting, count),
            sending = Latencies.of(sending, count),
        )
    }

    /** Drops every sample, so the next summary covers only what happened after this. */
    fun reset() {
        count = 0
        next = 0
    }

    private companion object {
        /** Ten seconds of reports at the pump's ceiling, which is the window a summary covers. */
        const val CAPACITY = 1024
    }
}

/** One measurement's spread, in milliseconds. */
data class Latencies(val median: Float, val p95: Float, val worst: Float) {

    override fun toString(): String = "${median.round()}/${p95.round()}/${worst.round()} ms"

    private fun Float.round(): Float = (this * 100f).roundToInt() / 100f

    companion object {
        /**
         * The spread over the first [count] entries of [nanos], which is a ring buffer — the order
         * within it is not the order they arrived in, and does not need to be: a percentile is
         * about the set, not the sequence.
         */
        fun of(nanos: LongArray, count: Int): Latencies {
            val sorted = nanos.copyOf(count).apply { sort() }
            return Latencies(
                median = sorted.percentile(0.5f),
                p95 = sorted.percentile(0.95f),
                worst = sorted.last() / 1_000_000f,
            )
        }

        /**
         * The nearest-rank percentile, which is the one that answers "how bad is it that often"
         * without inventing a value between two samples that were both really measured.
         */
        private fun LongArray.percentile(fraction: Float): Float {
            val rank = Math.round(fraction * size).coerceIn(1, size)
            return this[rank - 1] / 1_000_000f
        }
    }
}

/** A window of reports, as the log line the pad writes while connected. */
data class LatencySummary(val samples: Int, val waiting: Latencies, val sending: Latencies) {
    /** Median, 95th and worst of each, which is what a rate is judged by. */
    override fun toString(): String =
        "$samples reports, waiting $waiting, sending $sending (median/p95/worst)"
}
