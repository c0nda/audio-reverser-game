package com.vorontsov.audioreverser.presentation.navigation

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val ROUND = "round/{roundId}"
    const val ARG_ROUND_ID = "roundId"

    fun round(roundId: String): String = "round/$roundId"
}
