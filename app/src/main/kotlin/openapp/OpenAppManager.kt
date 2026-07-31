package io.github.teslanav.app.openapp

object OpenAppManager {
    val allStrategies: List<OpenAppStrategy> = listOf(
        MapsStrategy,
        WazeStrategy
    )
}