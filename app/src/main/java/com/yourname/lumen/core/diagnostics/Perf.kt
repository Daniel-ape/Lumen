package com.yourname.lumen.core.diagnostics

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableStateListOf

/**
 * Real timings measured on this device while Lumen loads, shown under Settings > About > Loading times.
 * They are measurements, not estimates: each one is the clock time a step actually took. Nothing sensitive
 * is recorded (only the step name, its duration and a count).
 */
object Perf {
    class Timing(val label: String, val millis: Long, val detail: String?, val milestone: Boolean)

    private val items = mutableStateListOf<Timing>()
    private val seenMilestones = HashSet<String>()
    private val main = Handler(Looper.getMainLooper())
    private var origin = SystemClock.elapsedRealtime()

    val timings: List<Timing> get() = items

    /** Called as the app opens; milestones are measured from here. */
    fun markStart() {
        origin = SystemClock.elapsedRealtime()
        seenMilestones.clear()
        main.post { items.clear() }
    }

    fun record(label: String, millis: Long, detail: String? = null, milestone: Boolean = false) {
        main.post {
            items.add(0, Timing(label, millis, detail, milestone))
            while (items.size > 40) items.removeAt(items.lastIndex)
        }
    }

    /** "How long after opening the app did this happen", recorded once per launch. */
    fun milestone(label: String) {
        val first = synchronized(seenMilestones) { seenMilestones.add(label) }
        if (first) record(label, SystemClock.elapsedRealtime() - origin, "since opening the app", milestone = true)
    }

    /** Runs [block], records how long it took, and returns its result. Works inside suspend functions too. */
    inline fun <T> time(label: String, noinline detail: ((T) -> String?)? = null, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        var failed = true
        try {
            val result = block()
            failed = false
            record(label, SystemClock.elapsedRealtime() - start, detail?.invoke(result))
            return result
        } finally {
            if (failed) record("$label (did not finish)", SystemClock.elapsedRealtime() - start)
        }
    }

    fun clear() {
        main.post { items.clear() }
    }
}

fun formatMillis(ms: Long): String = if (ms < 1000) "$ms ms" else "%.1f s".format(ms / 1000.0)
