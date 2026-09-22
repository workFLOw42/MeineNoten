package de.workflow42.meinenoten.model

import kotlinx.serialization.Serializable

/**
 * Zoom and pan parameters for a specific page of a song's score.
 */
@Serializable
data class PageView(
    val scale: Float = 1f,
    /** Horizontal center offset as a ratio of the content width. */
    val offsetXRatio: Float = 0f,
    /** Vertical center offset as a ratio of the content height. */
    val offsetYRatio: Float = 0f,
)
