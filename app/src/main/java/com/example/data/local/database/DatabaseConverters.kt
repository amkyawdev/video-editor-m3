package com.example.data.local.database

import androidx.room.TypeConverter
import com.example.domain.models.Track
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class DatabaseConverters {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val trackListType = Types.newParameterizedType(List::class.java, Track::class.java)
    private val trackListAdapter = moshi.adapter<List<Track>>(trackListType)

    @TypeConverter
    fun stringToTrackList(value: String?): List<Track>? {
        if (value == null) return emptyList()
        return try {
            trackListAdapter.fromJson(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun trackListToString(list: List<Track>?): String? {
        if (list == null) return "[]"
        return try {
            trackListAdapter.toJson(list)
        } catch (e: Exception) {
            "[]"
        }
    }
}
