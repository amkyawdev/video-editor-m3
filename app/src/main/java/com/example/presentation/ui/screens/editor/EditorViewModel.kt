package com.example.presentation.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.repository.MediaRepository
import com.example.data.local.repository.ProjectRepository
import com.example.domain.models.Clip
import com.example.domain.models.Media
import com.example.domain.models.MediaType
import com.example.domain.models.Project
import com.example.domain.models.Track
import com.example.domain.models.TransitionType
import com.example.domain.models.VideoFilter
import com.example.domain.usecases.AddTextOverlayUseCase
import com.example.domain.usecases.AddTransitionUseCase
import com.example.domain.usecases.ApplyFilterUseCase
import com.example.domain.usecases.TrimVideoUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class EditorViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    private val mediaRepository: MediaRepository,
    private val trimVideoUseCase: TrimVideoUseCase,
    private val applyFilterUseCase: ApplyFilterUseCase,
    private val addTextOverlayUseCase: AddTextOverlayUseCase,
    private val addTransitionUseCase: AddTransitionUseCase
) : ViewModel() {

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _mediaItems = MutableStateFlow<List<Media>>(emptyList())
    val mediaItems: StateFlow<List<Media>> = _mediaItems.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    // Interactive Player States
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTimeMs = MutableStateFlow(0L)
    val currentTimeMs: StateFlow<Long> = _currentTimeMs.asStateFlow()

    private val _timelineDurationMs = MutableStateFlow(0L)
    val timelineDurationMs: StateFlow<Long> = _timelineDurationMs.asStateFlow()

    private val _activeClipAtCursor = MutableStateFlow<Clip?>(null)
    val activeClipAtCursor: StateFlow<Clip?> = _activeClipAtCursor.asStateFlow()

    private var playbackJob: Job? = null

    init {
        viewModelScope.launch {
            projectRepository.getProjectById(projectId).collectLatest { updated ->
                _project.value = updated
                val duration = calculateTimelineDuration(updated?.tracks ?: emptyList())
                _timelineDurationMs.value = duration
                updateActiveClip(currentTimeMs.value)
            }
        }

        viewModelScope.launch {
            mediaRepository.allMedia.collectLatest { list ->
                _mediaItems.value = list
            }
        }
    }

    private fun calculateTimelineDuration(tracks: List<Track>): Long {
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

    fun selectClip(clipId: String?) {
        _selectedClipId.value = clipId
    }

    fun togglePlayback() {
        if (_isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val stepMs = 50L
            while (_isPlaying.value) {
                delay(stepMs)
                val nextTime = _currentTimeMs.value + stepMs
                if (nextTime >= _timelineDurationMs.value) {
                    _currentTimeMs.value = _timelineDurationMs.value
                    pausePlayback()
                    break
                } else {
                    _currentTimeMs.value = nextTime
                    updateActiveClip(nextTime)
                }
            }
        }
    }

    fun pausePlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
    }

    fun seekTo(timeMs: Long) {
        val bounded = timeMs.coerceIn(0L, _timelineDurationMs.value)
        _currentTimeMs.value = bounded
        updateActiveClip(bounded)
    }

    private fun updateActiveClip(timeMs: Long) {
        val currentProj = _project.value ?: return
        // Find first playing video clip
        val videoTrack = currentProj.tracks.firstOrNull { it.type == "VIDEO" }
        val active = videoTrack?.clips?.firstOrNull { clip ->
            timeMs >= clip.startInTimelineMs && timeMs < (clip.startInTimelineMs + clip.durationMs)
        }
        _activeClipAtCursor.value = active
    }

    fun addMediaToTimeline(media: Media) {
        val currentProj = _project.value ?: return
        viewModelScope.launch {
            val trackType = when (media.type) {
                MediaType.VIDEO, MediaType.IMAGE -> "VIDEO"
                MediaType.AUDIO -> "AUDIO"
            }

            // Find track of correct type
            val targetTrack = currentProj.tracks.firstOrNull { it.type == trackType } ?: return@launch
            
            // Calculate start point (end of last clip)
            val startMs = targetTrack.clips.maxOfOrNull { it.startInTimelineMs + it.durationMs } ?: 0L

            val newClip = Clip(
                id = UUID.randomUUID().toString(),
                mediaId = media.id,
                mediaPath = media.path,
                mediaName = media.name,
                mediaType = media.type,
                startInTimelineMs = startMs,
                durationMs = media.durationMs
            )

            val updatedClips = targetTrack.clips + newClip
            val updatedTracks = currentProj.tracks.map { track ->
                if (track.id == targetTrack.id) track.copy(clips = updatedClips) else track
            }

            val updatedProject = currentProj.copy(
                tracks = updatedTracks,
                durationMs = calculateTimelineDuration(updatedTracks),
                updatedAt = System.currentTimeMillis()
            )
            projectRepository.insertProject(updatedProject)
        }
    }

    fun deleteClip(clipId: String) {
        val currentProj = _project.value ?: return
        viewModelScope.launch {
            val updatedTracks = currentProj.tracks.map { track ->
                val filteredClips = track.clips.filter { it.id != clipId }
                // Re-align remaining clips to be contiguous (optional design choice, lets keep it so spacing is compact)
                var currentTimelineStart = 0L
                val alignedClips = filteredClips.map { clip ->
                    val aligned = clip.copy(startInTimelineMs = currentTimelineStart)
                    currentTimelineStart += clip.durationMs
                    aligned
                }
                track.copy(clips = alignedClips)
            }

            val updatedProject = currentProj.copy(
                tracks = updatedTracks,
                durationMs = calculateTimelineDuration(updatedTracks),
                updatedAt = System.currentTimeMillis()
            )
            projectRepository.insertProject(updatedProject)
            if (_selectedClipId.value == clipId) {
                _selectedClipId.value = null
            }
        }
    }

    fun updateClipFilter(clipId: String, filter: VideoFilter) {
        val currentProj = _project.value ?: return
        viewModelScope.launch {
            val updated = applyFilterUseCase(currentProj, clipId, filter)
            _project.value = updated
        }
    }

    fun updateClipTextOverlay(clipId: String, text: String?, color: String = "#FFFFFF", size: Int = 24) {
        val currentProj = _project.value ?: return
        viewModelScope.launch {
            val updated = addTextOverlayUseCase(currentProj, clipId, text, color, size)
            _project.value = updated
        }
    }

    fun updateClipTransition(clipId: String, transition: TransitionType) {
        val currentProj = _project.value ?: return
        viewModelScope.launch {
            val updated = addTransitionUseCase(currentProj, clipId, transition)
            _project.value = updated
        }
    }

    fun trimClip(clipId: String, startInMediaMs: Long, durationMs: Long) {
        val currentProj = _project.value ?: return
        viewModelScope.launch {
            val updated = trimVideoUseCase(currentProj, clipId, startInMediaMs, durationMs)
            _project.value = updated
        }
    }

    fun updateClipVolume(clipId: String, volume: Float) {
        val currentProj = _project.value ?: return
        viewModelScope.launch {
            val updatedTracks = currentProj.tracks.map { track ->
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(volume = volume) else clip
                }
                track.copy(clips = updatedClips)
            }
            val updatedProject = currentProj.copy(
                tracks = updatedTracks,
                updatedAt = System.currentTimeMillis()
            )
            projectRepository.insertProject(updatedProject)
            _project.value = updatedProject
        }
    }

    fun updateClipSpeed(clipId: String, speed: Float) {
        val currentProj = _project.value ?: return
        viewModelScope.launch {
            val updatedTracks = currentProj.tracks.map { track ->
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(speed = speed) else clip
                }
                track.copy(clips = updatedClips)
            }
            val updatedProject = currentProj.copy(
                tracks = updatedTracks,
                updatedAt = System.currentTimeMillis()
            )
            projectRepository.insertProject(updatedProject)
            _project.value = updatedProject
        }
    }

    fun importSrtToTextTrack(srtContent: String) {
        val currentProj = _project.value ?: return
        viewModelScope.launch {
            val subtitles = parseSrt(srtContent)
            if (subtitles.isEmpty()) return@launch

            // Find the TEXT track
            val targetTrack = currentProj.tracks.firstOrNull { it.type == "TEXT" } ?: return@launch

            // Convert parsed subtitles to text Clips
            val newClips = subtitles.map { subtitle ->
                Clip(
                    id = UUID.randomUUID().toString(),
                    mediaId = "srt_subtitle_${UUID.randomUUID().toString().take(6)}",
                    mediaPath = "",
                    mediaName = subtitle.text.take(15) + "...",
                    mediaType = MediaType.IMAGE, // Overlay text
                    startInTimelineMs = subtitle.startTimeMs,
                    durationMs = (subtitle.endTimeMs - subtitle.startTimeMs).coerceAtLeast(500L),
                    textOverlay = subtitle.text,
                    textOverlayColor = "#FFFF00", // Default beautiful high-contrast yellow
                    textOverlaySize = 20
                )
            }

            val updatedClips = targetTrack.clips + newClips
            val updatedTracks = currentProj.tracks.map { track ->
                if (track.id == targetTrack.id) track.copy(clips = updatedClips) else track
            }

            val updatedProject = currentProj.copy(
                tracks = updatedTracks,
                durationMs = calculateTimelineDuration(updatedTracks),
                updatedAt = System.currentTimeMillis()
            )
            projectRepository.insertProject(updatedProject)
            _project.value = updatedProject
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
                        // Now read all lines until empty line
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

    fun addSubtitleAtCurrentTime(text: String, durationMs: Long = 3000L) {
        val currentProj = _project.value ?: return
        val currentPlayhead = _currentTimeMs.value
        viewModelScope.launch {
            val targetTrack = currentProj.tracks.firstOrNull { it.type == "TEXT" } ?: return@launch
            val newClip = Clip(
                id = UUID.randomUUID().toString(),
                mediaId = "sub_${UUID.randomUUID().toString().take(6)}",
                mediaPath = "",
                mediaName = "Subtitle: " + text.take(12),
                mediaType = MediaType.IMAGE,
                startInTimelineMs = currentPlayhead,
                durationMs = durationMs,
                textOverlay = text,
                textOverlayColor = "#FFFF00",
                textOverlaySize = 20
            )
            val updatedClips = targetTrack.clips + newClip
            val updatedTracks = currentProj.tracks.map { track ->
                if (track.id == targetTrack.id) track.copy(clips = updatedClips) else track
            }
            val updatedProject = currentProj.copy(
                tracks = updatedTracks,
                durationMs = calculateTimelineDuration(updatedTracks),
                updatedAt = System.currentTimeMillis()
            )
            projectRepository.insertProject(updatedProject)
            _project.value = updatedProject
        }
    }

    fun updateSubtitleClip(
        clipId: String,
        text: String,
        startInTimelineMs: Long,
        durationMs: Long,
        color: String,
        size: Int
    ) {
        val currentProj = _project.value ?: return
        viewModelScope.launch {
            val targetTrack = currentProj.tracks.firstOrNull { it.type == "TEXT" } ?: return@launch
            val updatedClips = targetTrack.clips.map { clip ->
                if (clip.id == clipId) {
                    clip.copy(
                        textOverlay = text,
                        mediaName = "Subtitle: " + text.take(12),
                        startInTimelineMs = startInTimelineMs,
                        durationMs = durationMs,
                        textOverlayColor = color,
                        textOverlaySize = size
                    )
                } else clip
            }
            val updatedTracks = currentProj.tracks.map { track ->
                if (track.id == targetTrack.id) track.copy(clips = updatedClips) else track
            }
            val updatedProject = currentProj.copy(
                tracks = updatedTracks,
                durationMs = calculateTimelineDuration(updatedTracks),
                updatedAt = System.currentTimeMillis()
            )
            projectRepository.insertProject(updatedProject)
            _project.value = updatedProject
        }
    }

    fun deleteSubtitleClip(clipId: String) {
        val currentProj = _project.value ?: return
        viewModelScope.launch {
            val targetTrack = currentProj.tracks.firstOrNull { it.type == "TEXT" } ?: return@launch
            val updatedClips = targetTrack.clips.filter { it.id != clipId }
            val updatedTracks = currentProj.tracks.map { track ->
                if (track.id == targetTrack.id) track.copy(clips = updatedClips) else track
            }
            val updatedProject = currentProj.copy(
                tracks = updatedTracks,
                durationMs = calculateTimelineDuration(updatedTracks),
                updatedAt = System.currentTimeMillis()
            )
            projectRepository.insertProject(updatedProject)
            _project.value = updatedProject
        }
    }
}
