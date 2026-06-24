package com.example.ffmpeg.wrapper

import com.example.domain.models.Clip
import com.example.domain.models.TransitionType
import com.example.domain.models.VideoFilter

class FfmpegCommandBuilder {
    fun buildTrimCommand(inputPath: String, startMs: Long, durationMs: Long, outputPath: String): List<String> {
        val startSec = startMs / 1000.0
        val durationSec = durationMs / 1000.0
        return listOf(
            "ffmpeg", "-y",
            "-ss", String.format("%.3f", startSec),
            "-i", inputPath,
            "-t", String.format("%.3f", durationSec),
            "-c:v", "libx264",
            "-c:a", "aac",
            "-preset", "ultrafast",
            outputPath
        )
    }

    fun buildFilterCommand(inputPath: String, filter: VideoFilter, outputPath: String): List<String> {
        val vf = when (filter) {
            VideoFilter.NONE -> ""
            VideoFilter.GRAYSCALE -> "colorchannelmixer=.3:.4:.3:0:.3:.4:.3:0:.3:.4:.3"
            VideoFilter.SEPIA -> "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131"
            VideoFilter.VINTAGE -> "curves=vintage"
            VideoFilter.BLUR -> "boxblur=5:1"
            VideoFilter.MONOCHROME -> "hue=s=0"
            VideoFilter.INVERT -> "negate"
            VideoFilter.WARM -> "colorbalance=rg=0.1:bg=-0.1"
            VideoFilter.COOL -> "colorbalance=rg=-0.1:bg=0.1"
        }

        return if (vf.isEmpty()) {
            listOf("ffmpeg", "-y", "-i", inputPath, "-c", "copy", outputPath)
        } else {
            listOf("ffmpeg", "-y", "-i", inputPath, "-vf", vf, "-preset", "ultrafast", outputPath)
        }
    }

    fun buildTextOverlayCommand(
        inputPath: String,
        text: String,
        color: String,
        size: Int,
        outputPath: String
    ): List<String> {
        val cleanColor = color.replace("#", "0x")
        val drawtextFilter = "drawtext=text='$text':fontcolor=$cleanColor:fontsize=$size:x=(w-text_w)/2:y=(h-text_h)/2"
        return listOf(
            "ffmpeg", "-y",
            "-i", inputPath,
            "-vf", drawtextFilter,
            "-preset", "ultrafast",
            outputPath
        )
    }

    fun buildAudioMixCommand(videoPath: String, audioPath: String, videoVolume: Float, audioVolume: Float, outputPath: String): List<String> {
        return listOf(
            "ffmpeg", "-y",
            "-i", videoPath,
            "-i", audioPath,
            "-filter_complex", "[0:a]volume=$videoVolume[a1];[1:a]volume=$audioVolume[a2];[a1][a2]amix=inputs=2:duration=first[a]",
            "-map", "0:v",
            "-map", "[a]",
            "-c:v", "copy",
            "-preset", "ultrafast",
            outputPath
        )
    }

    fun buildRenderTimelineCommand(clips: List<Clip>, outputPath: String): List<String> {
        // Advanced multi-input rendering query with concat/filter graph representation
        val sb = java.lang.StringBuilder()
        sb.append("ffmpeg -y ")
        clips.forEachIndexed { index, clip ->
            val startSec = clip.startInMediaMs / 1000.0
            val durSec = clip.durationMs / 1000.0
            sb.append("-ss ").append(String.format("%.3f", startSec))
              .append(" -t ").append(String.format("%.3f", durSec))
              .append(" -i \"").append(clip.mediaPath).append("\" ")
        }
        sb.append("-filter_complex \"")
        clips.forEachIndexed { index, clip ->
            // Filter graph formatting
            var filterString = ""
            if (clip.filter != VideoFilter.NONE) {
                filterString = when (clip.filter) {
                    VideoFilter.GRAYSCALE -> ",hue=s=0"
                    VideoFilter.SEPIA -> ",colorbalance=rg=0.1:bg=-0.1"
                    VideoFilter.VINTAGE -> ",curves=vintage"
                    else -> ""
                }
            }
            if (clip.textOverlay != null) {
                filterString += ",drawtext=text='${clip.textOverlay}':fontsize=${clip.textOverlaySize}"
            }
            sb.append("[").append(index).append(":v]scale=1280:720,setpts=PTS-STARTPTS").append(filterString).append("[v").append(index).append("]; ")
        }
        clips.forEachIndexed { index, _ ->
            sb.append("[v").append(index).append("]")
        }
        sb.append("concat=n=").append(clips.size).append(":v=1:a=0[outv]\" -map \"[outv]\" ").append(outputPath)
        return sb.toString().split(" ")
    }
}
