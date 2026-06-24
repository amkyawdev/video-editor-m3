package com.example.domain.usecases

import com.example.data.local.repository.MediaRepository
import com.example.data.local.repository.ProjectRepository
import com.example.domain.models.Clip
import com.example.domain.models.Media
import com.example.domain.models.MediaType
import com.example.domain.models.Project
import com.example.domain.models.Track
import com.example.domain.models.TransitionType
import com.example.domain.models.VideoFilter
import com.example.ffmpeg.wrapper.FfmpegCommandBuilder
import com.example.ffmpeg.wrapper.FfmpegWrapper
import java.util.UUID

class ImportVideoUseCase(private val mediaRepository: MediaRepository) {
    suspend operator fun invoke(name: String, path: String, type: MediaType, durationMs: Long, width: Int = 1280, height: Int = 720, sizeBytes: Long = 1024L * 100): Media {
        val media = Media(
            id = UUID.randomUUID().toString(),
            name = name,
            path = path,
            type = type,
            durationMs = durationMs,
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            thumbnailUri = null,
            createdAt = System.currentTimeMillis()
        )
        mediaRepository.insertMedia(media)
        return media
    }
}

class ExportVideoUseCase(
    private val projectRepository: ProjectRepository,
    private val ffmpegWrapper: FfmpegWrapper,
    private val commandBuilder: FfmpegCommandBuilder
) {
    suspend operator fun invoke(
        project: Project,
        outputPath: String,
        onProgress: (Float) -> Unit,
        onLog: (String) -> Unit
    ): Boolean {
        // Collect all clips in chronological order from video/image tracks
        val activeClips = project.tracks
            .filter { it.type == "VIDEO" || it.type == "TEXT" }
            .flatMap { it.clips }
            .sortedBy { it.startInTimelineMs }

        val command = if (activeClips.isEmpty()) {
            listOf("ffmpeg", "-y", "-f", "lavfi", "-i", "color=c=black:s=1280x720:d=5", outputPath)
        } else {
            commandBuilder.buildRenderTimelineCommand(activeClips, outputPath)
        }

        val success = ffmpegWrapper.executeCommand(command, onProgress, onLog)
        if (success) {
            val updated = project.copy(
                updatedAt = System.currentTimeMillis()
            )
            projectRepository.insertProject(updated)
        }
        return success
    }
}

class TrimVideoUseCase(private val projectRepository: ProjectRepository) {
    suspend operator fun invoke(project: Project, clipId: String, startInMediaMs: Long, durationMs: Long): Project {
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    clip.copy(startInMediaMs = startInMediaMs, durationMs = durationMs)
                } else {
                    clip
                }
            }
            track.copy(clips = updatedClips)
        }
        val updatedProject = project.copy(
            tracks = updatedTracks,
            durationMs = calculateNewDuration(updatedTracks),
            updatedAt = System.currentTimeMillis()
        )
        projectRepository.insertProject(updatedProject)
        return updatedProject
    }
}

class ExtractAudioUseCase {
    operator fun invoke(media: Media): String {
        return media.path.substringBeforeLast(".") + ".mp3"
    }
}

class MixAudioUseCase(private val projectRepository: ProjectRepository) {
    suspend operator fun invoke(project: Project, clipId: String, volume: Float): Project {
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    clip.copy(volume = volume)
                } else {
                    clip
                }
            }
            track.copy(clips = updatedClips)
        }
        val updatedProject = project.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
        projectRepository.insertProject(updatedProject)
        return updatedProject
    }
}

class ApplyFilterUseCase(private val projectRepository: ProjectRepository) {
    suspend operator fun invoke(project: Project, clipId: String, filter: VideoFilter): Project {
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    clip.copy(filter = filter)
                } else {
                    clip
                }
            }
            track.copy(clips = updatedClips)
        }
        val updatedProject = project.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
        projectRepository.insertProject(updatedProject)
        return updatedProject
    }
}

class AddTextOverlayUseCase(private val projectRepository: ProjectRepository) {
    suspend operator fun invoke(
        project: Project,
        clipId: String,
        text: String?,
        color: String = "#FFFFFF",
        size: Int = 24
    ): Project {
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    clip.copy(textOverlay = text, textOverlayColor = color, textOverlaySize = size)
                } else {
                    clip
                }
            }
            track.copy(clips = updatedClips)
        }
        val updatedProject = project.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
        projectRepository.insertProject(updatedProject)
        return updatedProject
    }
}

class AddTransitionUseCase(private val projectRepository: ProjectRepository) {
    suspend operator fun invoke(project: Project, clipId: String, transition: TransitionType): Project {
        val updatedTracks = project.tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId) {
                    clip.copy(transition = transition)
                } else {
                    clip
                }
            }
            track.copy(clips = updatedClips)
        }
        val updatedProject = project.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
        projectRepository.insertProject(updatedProject)
        return updatedProject
    }
}

private fun calculateNewDuration(tracks: List<Track>): Long {
    var maxEndMs = 0L
    tracks.forEach { track ->
        track.clips.forEach { clip ->
            val endMs = clip.startInTimelineMs + clip.durationMs
            if (endMs > maxEndMs) {
                maxEndMs = endMs
            }
        }
    }
    return maxEndMs
}
