package com.example.core.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.database.AppDatabase
import com.example.data.local.repository.MediaRepository
import com.example.data.local.repository.ProjectRepository
import com.example.domain.usecases.AddTextOverlayUseCase
import com.example.domain.usecases.AddTransitionUseCase
import com.example.domain.usecases.ApplyFilterUseCase
import com.example.domain.usecases.ExportVideoUseCase
import com.example.domain.usecases.ImportVideoUseCase
import com.example.domain.usecases.TrimVideoUseCase
import com.example.ffmpeg.wrapper.FfmpegCommandBuilder
import com.example.ffmpeg.wrapper.FfmpegWrapper
import com.example.presentation.ui.screens.editor.EditorViewModel
import com.example.presentation.ui.screens.export.ExportViewModel
import com.example.presentation.ui.screens.library.LibraryViewModel

class AppModule(private val context: Context) {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(context) }
    
    val projectRepository: ProjectRepository by lazy { ProjectRepository(database.projectDao()) }
    val mediaRepository: MediaRepository by lazy { MediaRepository(database.mediaDao()) }
    
    val ffmpegWrapper: FfmpegWrapper by lazy { FfmpegWrapper() }
    val ffmpegCommandBuilder: FfmpegCommandBuilder by lazy { FfmpegCommandBuilder() }

    // Use Cases
    val importVideoUseCase: ImportVideoUseCase by lazy { ImportVideoUseCase(mediaRepository) }
    val exportVideoUseCase: ExportVideoUseCase by lazy { ExportVideoUseCase(projectRepository, ffmpegWrapper, ffmpegCommandBuilder) }
    val trimVideoUseCase: TrimVideoUseCase by lazy { TrimVideoUseCase(projectRepository) }
    val applyFilterUseCase: ApplyFilterUseCase by lazy { ApplyFilterUseCase(projectRepository) }
    val addTextOverlayUseCase: AddTextOverlayUseCase by lazy { AddTextOverlayUseCase(projectRepository) }
    val addTransitionUseCase: AddTransitionUseCase by lazy { AddTransitionUseCase(projectRepository) }

    fun provideViewModelFactory(projectId: String = ""): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(LibraryViewModel::class.java) -> {
                        LibraryViewModel(projectRepository, mediaRepository, importVideoUseCase) as T
                    }
                    modelClass.isAssignableFrom(EditorViewModel::class.java) -> {
                        EditorViewModel(
                            projectId = projectId,
                            projectRepository = projectRepository,
                            mediaRepository = mediaRepository,
                            trimVideoUseCase = trimVideoUseCase,
                            applyFilterUseCase = applyFilterUseCase,
                            addTextOverlayUseCase = addTextOverlayUseCase,
                            addTransitionUseCase = addTransitionUseCase
                        ) as T
                    }
                    modelClass.isAssignableFrom(ExportViewModel::class.java) -> {
                        ExportViewModel(
                            projectId = projectId,
                            projectRepository = projectRepository,
                            exportVideoUseCase = exportVideoUseCase
                        ) as T
                    }
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
