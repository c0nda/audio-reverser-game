package com.vorontsov.audioreverser.domain.model

data class RoundDetails(
    val round: GameRound,
    val secretReversed: AudioAsset?,
    val secretOriginal: AudioAsset?,
    val attempts: List<Attempt>,
)
