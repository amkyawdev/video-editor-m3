package com.example.presentation.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.repository.MediaRepository
import com.example.data.local.repository.ProjectRepository
import com.example.domain.models.Clip
import com.example.domain.models.MediaType
import com.example.domain.models.Project
import com.example.domain.models.Track
import com.example.domain.usecases.ImportVideoUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class LibraryViewModel(
    private val projectRepository: ProjectRepository,
    private val mediaRepository: MediaRepository,
    private val importVideoUseCase: ImportVideoUseCase
) : ViewModel() {

    val projects: StateFlow<List<Project>> = projectRepository.allProjects
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val mediaItems: StateFlow<List<com.example.domain.models.Media>> = mediaRepository.allMedia
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createNewProject(name: String, aspectRatio: String = "16:9") {
        viewModelScope.launch {
            val videoTrack = Track(id = UUID.randomUUID().toString(), type = "VIDEO", order = 0)
            val audioTrack = Track(id = UUID.randomUUID().toString(), type = "AUDIO", order = 1)
            val overlayTrack = Track(id = UUID.randomUUID().toString(), type = "TEXT", order = 2)

            val newProject = Project(
                id = UUID.randomUUID().toString(),
                name = name,
                aspectRatio = aspectRatio,
                tracks = listOf(videoTrack, audioTrack, overlayTrack),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            projectRepository.insertProject(newProject)
        }
    }

    fun createProjectWithAssets(
        name: String,
        aspectRatio: String = "16:9",
        videoUri: String?,
        videoName: String?,
        videoDurationMs: Long,
        audioUri: String?,
        audioName: String?,
        audioDurationMs: Long,
        voiceUri: String?,
        voiceName: String?,
        voiceDurationMs: Long,
        srtContent: String?
    ) {
        viewModelScope.launch {
            val videoClips = mutableListOf<Clip>()
            val audioClips = mutableListOf<Clip>()
            val textClips = mutableListOf<Clip>()

            // 1. Import and insert video clip if provided
            if (!videoUri.isNullOrEmpty() && videoName != null) {
                val media = importVideoUseCase(
                    name = videoName,
                    path = videoUri,
                    type = MediaType.VIDEO,
                    durationMs = videoDurationMs
                )
                videoClips.add(
                    Clip(
                        id = UUID.randomUUID().toString(),
                        mediaId = media.id,
                        mediaPath = media.path,
                        mediaName = media.name,
                        mediaType = MediaType.VIDEO,
                        startInTimelineMs = 0L,
                        durationMs = videoDurationMs
                    )
                )
            }

            // 2. Import and insert audio clip (song) if provided
            if (!audioUri.isNullOrEmpty() && audioName != null) {
                val media = importVideoUseCase(
                    name = audioName,
                    path = audioUri,
                    type = MediaType.AUDIO,
                    durationMs = audioDurationMs
                )
                audioClips.add(
                    Clip(
                        id = UUID.randomUUID().toString(),
                        mediaId = media.id,
                        mediaPath = media.path,
                        mediaName = media.name,
                        mediaType = MediaType.AUDIO,
                        startInTimelineMs = 0L,
                        durationMs = audioDurationMs,
                        volume = 0.5f // softer volume for background audio
                    )
                )
            }

            // 3. Import and insert voice narration clip if provided
            if (!voiceUri.isNullOrEmpty() && voiceName != null) {
                val media = importVideoUseCase(
                    name = voiceName,
                    path = voiceUri,
                    type = MediaType.AUDIO,
                    durationMs = voiceDurationMs
                )
                audioClips.add(
                    Clip(
                        id = UUID.randomUUID().toString(),
                        mediaId = media.id,
                        mediaPath = media.path,
                        mediaName = media.name,
                        mediaType = MediaType.AUDIO,
                        startInTimelineMs = 0L,
                        durationMs = voiceDurationMs,
                        volume = 1.0f // full volume for voice
                    )
                )
            }

            // 4. Parse and insert subtitles if SRT is provided
            if (!srtContent.isNullOrEmpty()) {
                val parsedSubs = parseSrt(srtContent)
                parsedSubs.forEach { sub ->
                    textClips.add(
                        Clip(
                            id = UUID.randomUUID().toString(),
                            mediaId = "srt_subtitle_${UUID.randomUUID().toString().take(6)}",
                            mediaPath = "",
                            mediaName = "Subtitle: " + sub.text.take(12),
                            mediaType = MediaType.IMAGE,
                            startInTimelineMs = sub.startTimeMs,
                            durationMs = (sub.endTimeMs - sub.startTimeMs).coerceAtLeast(500L),
                            textOverlay = sub.text,
                            textOverlayColor = "#FFFF00",
                            textOverlaySize = 20
                        )
                    )
                }
            }

            val videoTrack = Track(
                id = UUID.randomUUID().toString(),
                type = "VIDEO",
                order = 0,
                clips = videoClips
            )
            val audioTrack = Track(
                id = UUID.randomUUID().toString(),
                type = "AUDIO",
                order = 1,
                clips = audioClips
            )
            val overlayTrack = Track(
                id = UUID.randomUUID().toString(),
                type = "TEXT",
                order = 2,
                clips = textClips
            )

            val allClips = videoClips + audioClips + textClips
            val maxDuration = allClips.maxOfOrNull { it.startInTimelineMs + it.durationMs } ?: 0L

            val newProject = Project(
                id = UUID.randomUUID().toString(),
                name = name,
                aspectRatio = aspectRatio,
                tracks = listOf(videoTrack, audioTrack, overlayTrack),
                durationMs = maxDuration,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            projectRepository.insertProject(newProject)
        }
    }

    private data class ParsedSub(val index: Int, val startTimeMs: Long, val endTimeMs: Long, val text: String)

    private fun parseSrt(srtContent: String): List<ParsedSub> {
        val list = mutableListOf<ParsedSub>()
        val lines = srtContent.lines().map { it.trim() }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isEmpty()) {
                i++
                continue
            }
            val index = line.toIntOrNull()
            if (index != null && i + 1 < lines.size) {
                val timesLine = lines[i + 1]
                if (timesLine.contains("-->")) {
                    val parts = timesLine.split("-->").map { it.trim() }
                    if (parts.size == 2) {
                        val startMs = parseSrtTime(parts[0])
                        val endMs = parseSrtTime(parts[1])
                        val textBuilder = StringBuilder()
                        i += 2
                        while (i < lines.size && lines[i].isNotEmpty() && lines[i].toIntOrNull() == null) {
                            if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                            textBuilder.append(lines[i])
                            i++
                        }
                        list.add(ParsedSub(index, startMs, endMs, textBuilder.toString()))
                        continue
                    }
                }
            }
            i++
        }
        return list
    }

    private fun parseSrtTime(timeStr: String): Long {
        try {
            val parts = timeStr.replace(',', '.').split(":")
            if (parts.size == 3) {
                val hours = parts[0].toLongOrNull() ?: 0L
                val minutes = parts[1].toLongOrNull() ?: 0L
                val secParts = parts[2].split(".")
                val seconds = secParts[0].toLongOrNull() ?: 0L
                val ms = if (secParts.size > 1) {
                    val rawMs = secParts[1]
                    val padded = rawMs.padEnd(3, '0').take(3)
                    padded.toLongOrNull() ?: 0L
                } else 0L
                return hours * 3600000L + minutes * 60000L + seconds * 1000L + ms
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0L
    }

    fun importMediaItem(name: String, path: String, type: MediaType, durationMs: Long) {
        viewModelScope.launch {
            importVideoUseCase(name, path, type, durationMs)
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProjectById(projectId)
        }
    }

    fun deleteMedia(mediaId: String) {
        viewModelScope.launch {
            mediaRepository.deleteMediaById(mediaId)
        }
    }
}
