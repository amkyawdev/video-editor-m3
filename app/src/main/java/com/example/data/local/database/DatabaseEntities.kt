package com.example.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.models.Media
import com.example.domain.models.MediaType
import com.example.domain.models.Project
import com.example.domain.models.Track

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val aspectRatio: String,
    val durationMs: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val tracksJson: String // Serialized List<Track> using Moshi
) {
    fun toDomain(tracks: List<Track>): Project {
        return Project(
            id = id,
            name = name,
            aspectRatio = aspectRatio,
            durationMs = durationMs,
            createdAt = createdAt,
            updatedAt = updatedAt,
            tracks = tracks
        )
    }

    companion object {
        fun fromDomain(project: Project, tracksJson: String): ProjectEntity {
            return ProjectEntity(
                id = project.id,
                name = project.name,
                aspectRatio = project.aspectRatio,
                durationMs = project.durationMs,
                createdAt = project.createdAt,
                updatedAt = project.updatedAt,
                tracksJson = tracksJson
            )
        }
    }
}

@Entity(tableName = "media_items")
data class MediaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    val type: String, // "VIDEO", "AUDIO", "IMAGE"
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val thumbnailUri: String?,
    val createdAt: Long
) {
    fun toDomain(): Media {
        return Media(
            id = id,
            name = name,
            path = path,
            type = MediaType.valueOf(type),
            durationMs = durationMs,
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            thumbnailUri = thumbnailUri,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(media: Media): MediaEntity {
            return MediaEntity(
                id = media.id,
                name = media.name,
                path = media.path,
                type = media.type.name,
                durationMs = media.durationMs,
                width = media.width,
                height = media.height,
                sizeBytes = media.sizeBytes,
                thumbnailUri = media.thumbnailUri,
                createdAt = media.createdAt
            )
        }
    }
}
