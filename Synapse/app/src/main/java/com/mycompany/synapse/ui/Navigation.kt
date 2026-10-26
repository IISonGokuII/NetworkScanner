package com.mycompany.synapse.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.game.GameMode

data class WorkoutEntry(val gameId: String, val score: Int, val levelBefore: Int, val levelAfter: Int, val accuracy: Double)

data class WorkoutRun(val games: List<String>, val index: Int, val entries: List<WorkoutEntry>) {
    val gameId: String get() = games[index]
    val isLast: Boolean get() = index == games.size - 1
}

sealed interface Screen {
    /** Hauptreiter mit Navigationsleiste. */
    val isTab: Boolean get() = false

    data object Home : Screen { override val isTab = true }
    data object Progress : Screen { override val isTab = true }
    data object Awards : Screen { override val isTab = true }
    data object More : Screen { override val isTab = true }
    data class DomainDetail(val domain: Domain) : Screen
    data object AllGames : Screen
    data class Play(val gameId: String, val workout: WorkoutRun? = null, val presetMode: GameMode? = null, val nonce: Long = System.nanoTime()) : Screen
    data class GameStats(val gameId: String) : Screen
    data class WorkoutDone(val entries: List<WorkoutEntry>) : Screen
    data object Duel : Screen
}

class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    var forward by mutableStateOf(true)
        private set

    val current: Screen get() = stack.last()

    fun push(s: Screen) {
        forward = true
        stack.add(s)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        forward = false
        stack.removeAt(stack.size - 1)
        return true
    }

    fun replace(s: Screen) {
        forward = true
        stack[stack.size - 1] = s
    }

    /** Hauptreiter: Zurück führt immer zum Neuronen-Netz. */
    fun tab(s: Screen) {
        forward = true
        stack.clear()
        stack.add(Screen.Home)
        if (s != Screen.Home) stack.add(s)
    }

    fun home() = tab(Screen.Home)
}
