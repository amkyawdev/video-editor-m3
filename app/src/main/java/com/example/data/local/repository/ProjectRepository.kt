package com.example.data.local.repository

import com.example.data.local.database.DatabaseConverters
import com.example.data.local.database.ProjectDao
import com.example.data.local.database.ProjectEntity
import com.example.domain.models.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProjectRepository(private val projectDao: ProjectDao) {
    private val converters = DatabaseConverters()

    val allProjects: Flow<List<Project>> = projectDao.getAllProjects().map { entities ->
        entities.map { entity ->
            val tracks = converters.stringToTrackList(entity.tracksJson) ?: emptyList()
            entity.toDomain(tracks)
        }
    }

    fun getProjectById(id: String): Flow<Project?> {
        return projectDao.getProjectById(id).map { entity ->
            entity?.let {
                val tracks = converters.stringToTrackList(it.tracksJson) ?: emptyList()
                it.toDomain(tracks)
            }
        }
    }

    suspend fun getProjectByIdDirect(id: String): Project? {
        val entity = projectDao.getProjectByIdDirect(id)
        return entity?.let {
            val tracks = converters.stringToTrackList(it.tracksJson) ?: emptyList()
            it.toDomain(tracks)
        }
    }

    suspend fun insertProject(project: Project) {
        val tracksJson = converters.trackListToString(project.tracks) ?: "[]"
        val entity = ProjectEntity.fromDomain(project, tracksJson)
        projectDao.insertProject(entity)
    }

    suspend fun deleteProjectById(id: String) {
        projectDao.deleteProjectById(id)
    }
}
