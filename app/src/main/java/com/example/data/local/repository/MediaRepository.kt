package com.example.data.local.repository

import com.example.data.local.database.MediaDao
import com.example.data.local.database.MediaEntity
import com.example.domain.models.Media
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MediaRepository(private val mediaDao: MediaDao) {
    val allMedia: Flow<List<Media>> = mediaDao.getAllMedia().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getMediaById(id: String): Media? {
        return mediaDao.getMediaById(id)?.toDomain()
    }

    suspend fun insertMedia(media: Media) {
        mediaDao.insertMedia(MediaEntity.fromDomain(media))
    }

    suspend fun deleteMediaById(id: String) {
        mediaDao.deleteMediaById(id)
    }
}
