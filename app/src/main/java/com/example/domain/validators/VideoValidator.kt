package com.example.domain.validators

import com.example.domain.models.MediaType

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

class VideoValidator {
    fun validateMediaImport(
        fileName: String,
        fileSizeBytes: Long,
        durationMs: Long,
        type: MediaType
    ): ValidationResult {
        if (fileName.isBlank()) {
            return ValidationResult.Invalid("File name cannot be empty.")
        }

        val extension = fileName.substringAfterLast('.', "").lowercase()
        val supportedExtensions = when (type) {
            MediaType.VIDEO -> listOf("mp4", "mkv", "mov", "webm", "3gp")
            MediaType.AUDIO -> listOf("mp3", "wav", "m4a", "ogg", "aac")
            MediaType.IMAGE -> listOf("jpg", "jpeg", "png", "webp", "gif")
        }

        if (extension !in supportedExtensions) {
            return ValidationResult.Invalid("Unsupported file format: .$extension. Supported formats: ${supportedExtensions.joinToString(", ")}")
        }

        if (fileSizeBytes > 100 * 1024 * 1024) { // 100 MB limit for demo
            return ValidationResult.Invalid("File exceeds size limit of 100MB.")
        }

        if (durationMs <= 0 && type != MediaType.IMAGE) {
            return ValidationResult.Invalid("Invalid file duration.")
        }

        return ValidationResult.Valid
    }

    fun validateTimelineClip(startMs: Long, durationMs: Long, mediaDurationMs: Long): ValidationResult {
        if (startMs < 0) {
            return ValidationResult.Invalid("Start time in media cannot be negative.")
        }
        if (durationMs <= 0) {
            return ValidationResult.Invalid("Clip duration must be greater than zero.")
        }
        if (startMs + durationMs > mediaDurationMs) {
            return ValidationResult.Invalid("Clip boundaries exceed media duration limit ($mediaDurationMs ms).")
        }
        return ValidationResult.Valid
    }
}
