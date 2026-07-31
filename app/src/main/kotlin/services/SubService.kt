package io.github.teslanav.app.services

/** A component with an explicit start/stop lifecycle, owned and driven by [ForegroundService]. */
interface SubService {
    fun start()
    fun stop()
}
