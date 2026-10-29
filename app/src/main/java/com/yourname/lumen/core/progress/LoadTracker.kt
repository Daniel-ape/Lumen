package com.yourname.lumen.core.progress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class TaskState { Waiting, Running, Done, Failed }

/** One thing being loaded, with how far it has got. Safe to update from background threads. */
class LoadTask(val label: String) {
    var state by mutableStateOf(TaskState.Waiting)
        private set
    var count by mutableIntStateOf(0)
        private set
    var note by mutableStateOf<String?>(null)
        private set

    fun running() {
        if (state != TaskState.Running) state = TaskState.Running
    }

    /** [loaded] is how many entries have arrived so far. */
    fun progress(loaded: Int) {
        state = TaskState.Running
        count = loaded
    }

    fun done(total: Int = count, message: String? = null) {
        count = total
        note = message
        state = TaskState.Done
    }

    fun fail(message: String) {
        note = message
        state = TaskState.Failed
    }

    fun reset() {
        state = TaskState.Waiting
        count = 0
        note = null
    }
}

/**
 * What is loading right now after a source is added or refreshed, shown by the progress pop-up.
 * The numbers are real counts of what the source has delivered so far; the total is unknown until
 * the source has finished sending, so the bar only shows how many of the steps are complete.
 */
class LoadTracker {
    val connection = LoadTask("Connection")
    val home = LoadTask("Home")
    val channels = LoadTask("TV channels")
    val movies = LoadTask("Movies")
    val series = LoadTask("Series")
    val all = listOf(connection, home, channels, movies, series)

    var visible by mutableStateOf(false)
        private set
    var session by mutableIntStateOf(0)
        private set

    /** Starts a fresh round and shows the pop-up. */
    fun begin() {
        all.forEach { it.reset() }
        session++
        visible = true
    }

    fun hide() {
        visible = false
    }

    /** True while anything is still loading or waiting its turn (unless the connection or Home has failed). */
    val pending: Boolean
        get() {
            val blocked = connection.state == TaskState.Failed || home.state == TaskState.Failed
            return all.any { it.state == TaskState.Running } ||
                (!blocked && all.any { it.state == TaskState.Waiting })
        }

    /** How many steps are finished, from 0 to 1. */
    val fraction: Float
        get() = all.count { it.state == TaskState.Done || it.state == TaskState.Failed } / all.size.toFloat()
}
