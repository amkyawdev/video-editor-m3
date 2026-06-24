package com.example.domain.models

import com.squareup.moshi.JsonClass

enum class MediaType {
    VIDEO, AUDIO, IMAGE
}

enum class VideoFilter {
    NONE, GRAYSCALE, SEPIA, VINTAGE, BLUR, MONOCHROME, INVERT, WARM, COOL
}

enum class TransitionType {
    NONE, FADE, SLIDE, ZOOM
}

@JsonClass(generateAdapter = true)
data class Clip(
    val id: String,
    val mediaId: String,
    val mediaPath: String,
    val mediaName: String,
    val mediaType: MediaType,
    val startInTimelineMs: Long,
    val durationMs: Long,
    val startInMediaMs: Long = 0L,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val filter: VideoFilter = VideoFilter.NONE,
    val textOverlay: String? = null,
    val textOverlayColor: String? = "#FFFFFF",
    val textOverlaySize: Int = 24,
    val transition: TransitionType = TransitionType.NONE
)

@JsonClass(generateAdapter = true)
data class Track(
    val id: String,
    val type: String, // "VIDEO", "AUDIO", "TEXT"
    val order: Int,
    val clips: List<Clip> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Project(
    val id: String,
    val name: String,
    val aspectRatio: String = "16:9", // "16:9", "9:16", "1:1"
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val tracks: List<Track> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Media(
    val id: String,
    val name: String,
    val path: String,
    val type: MediaType,
    val durationMs: Long,
    val width: Int = 1920,
    val height: Int = 1080,
    val sizeBytes: Long = 0L,
    val thumbnailUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
