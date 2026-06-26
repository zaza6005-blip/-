package com.example.data

import kotlinx.serialization.Serializable

@Serializable
data class MorphologicalWord(
    val word: String,
    val root: String,
    val lemma: String,
    val pattern: String,
    val affixes: String
)
