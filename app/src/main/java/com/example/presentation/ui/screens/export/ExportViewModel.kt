package com.example.presentation.ui.screens.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.repository.ProjectRepository
import com.example.domain.models.Project
import com.example.domain.usecases.ExportVideoUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ExportState {
    object Idle : ExportState()
    object Processing : ExportState()
    data class Success(val outputPath: String) : ExportState()
    data class Failure(val error: String) : ExportState()
}

class ExportViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    private val exportVideoUseCase: ExportVideoUseCase
) : ViewModel() {

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    init {
        viewModelScope.launch {
            projectRepository.getProjectById(projectId).collect { proj ->
                _project.value = proj
            }
        }
    }

    fun startExport() {
        val currentProj = _project.value ?: return
        if (_exportState.value is ExportState.Processing) return

        _exportState.value = ExportState.Processing
        _progress.value = 0f
        _logs.value = emptyList()

        viewModelScope.launch {
            val outputName = "output_${System.currentTimeMillis()}.mp4"
            val outputPath = "/sdcard/Movies/VideoEditionM3/$outputName"

            addLog("Preparing timeline tracks...")
            addLog("Aspect Ratio: ${currentProj.aspectRatio}")
            addLog("Total Clips detected: ${currentProj.tracks.flatMap { it.clips }.size}")

            try {
                val success = exportVideoUseCase(
                    project = currentProj,
                    outputPath = outputPath,
                    onProgress = { percent ->
                        _progress.value = percent
                    },
                    onLog = { logLine ->
                        addLog(logLine)
                    }
                )

                if (success) {
                    _exportState.value = ExportState.Success(outputPath)
                } else {
                    _exportState.value = ExportState.Failure("FFmpeg compilation failed.")
                }
            } catch (e: Exception) {
                _exportState.value = ExportState.Failure(e.localizedMessage ?: "Unknown error occurred.")
                addLog("ERROR: ${e.localizedMessage}")
            }
        }
    }

    private fun addLog(line: String) {
        _logs.value = _logs.value + line
    }
}
