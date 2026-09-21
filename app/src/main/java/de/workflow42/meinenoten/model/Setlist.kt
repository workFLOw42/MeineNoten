package de.workflow42.meinenoten.model

import kotlinx.serialization.Serializable

@Serializable
data class Setlist(
    val id: String,
    val title: String,
    val date: String = "",
    val songIds: List<String>,
    val notes: String = ""
)
