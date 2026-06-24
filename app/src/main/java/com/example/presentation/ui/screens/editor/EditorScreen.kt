package com.example.presentation.ui.screens.editor

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import com.example.domain.models.*
import com.example.presentation.ui.components.BootstrapButton
import com.example.presentation.ui.components.BootstrapBtnType
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToExport: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val project by viewModel.project.collectAsState()
    val mediaItems by viewModel.mediaItems.collectAsState()
    val selectedClipId by viewModel.selectedClipId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentTimeMs by viewModel.currentTimeMs.collectAsState()
    val timelineDurationMs by viewModel.timelineDurationMs.collectAsState()
    val activeClipAtCursor by viewModel.activeClipAtCursor.collectAsState()

    var showMediaSheet by remember { mutableStateOf(false) }
    var showSrtDialog by remember { mutableStateOf(false) }
    var srtPasteText by remember { mutableStateOf("") }

    val srtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val reader = BufferedReader(InputStreamReader(stream))
                    val content = reader.readText()
                    viewModel.importSrtToTextTrack(content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val currentProject = project

    if (currentProject == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Collect currently selected Clip object if any
    val selectedClip = currentProject.tracks
        .flatMap { it.clips }
        .firstOrNull { it.id == selectedClipId }

    // Look up the active subtitle text overlay clip at playhead
    val activeTextClip = currentProject.tracks
        .firstOrNull { it.type == "TEXT" }
        ?.clips
        ?.firstOrNull { clip ->
            currentTimeMs >= clip.startInTimelineMs && currentTimeMs < (clip.startInTimelineMs + clip.durationMs)
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentProject.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Format: ${currentProject.aspectRatio} | Duration: ${formatTime(timelineDurationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    BootstrapButton(
                        text = "Export",
                        onClick = { onNavigateToExport(currentProject.id) },
                        type = BootstrapBtnType.PRIMARY,
                        icon = { Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("export_button")
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 1. VIDEO PREVIEW PLAYER (PREVIEW MONITOR WITH SHADERS)
            VideoPreviewMonitor(
                activeClip = activeClipAtCursor,
                activeTextClip = activeTextClip,
                aspectRatio = currentProject.aspectRatio,
                isPlaying = isPlaying,
                currentTimeMs = currentTimeMs,
                totalDurationMs = timelineDurationMs
            )

            // 2. PLAYER SCRUBBER CONTROLS
            PlayerControlsBar(
                isPlaying = isPlaying,
                currentTimeMs = currentTimeMs,
                totalDurationMs = timelineDurationMs,
                onPlayPauseToggle = { viewModel.togglePlayback() },
                onSeek = { viewModel.seekTo(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. ACTIONS PANEL (Add Media, Reset, Select elements)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Timeline Multi-Track",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                BootstrapButton(
                    text = "Add Media",
                    onClick = { showMediaSheet = true },
                    type = BootstrapBtnType.SUCCESS,
                    icon = { Icon(Icons.Default.VideoCall, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("add_media_to_timeline")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. THE MULTI-TRACK TIMELINE CANVAS VIEW
            TimelineCanvasView(
                project = currentProject,
                currentTimeMs = currentTimeMs,
                selectedClipId = selectedClipId,
                onClipSelect = { viewModel.selectClip(it) },
                onClipDelete = { viewModel.deleteClip(it) },
                onSeek = { viewModel.seekTo(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. CONTEXTUAL CLIP EDITOR PANEL (Visible only when a clip is selected)
            AnimatedVisibility(
                visible = selectedClip != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (selectedClip != null) {
                    ClipEditorPanel(
                        clip = selectedClip,
                        project = currentProject,
                        currentTimeMs = currentTimeMs,
                        onSeekTo = { viewModel.seekTo(it) },
                        onFilterChange = { filter ->
                            viewModel.updateClipFilter(selectedClip.id, filter)
                        },
                        onTextOverlayChange = { text, color, size ->
                            viewModel.updateClipTextOverlay(selectedClip.id, text, color, size)
                        },
                        onTransitionChange = { trans ->
                            viewModel.updateClipTransition(selectedClip.id, trans)
                        },
                        onTrimChange = { startMedia, dur ->
                            viewModel.trimClip(selectedClip.id, startMedia, dur)
                        },
                        onVolumeChange = { vol ->
                            viewModel.updateClipVolume(selectedClip.id, vol)
                        },
                        onSpeedChange = { sp ->
                            viewModel.updateClipSpeed(selectedClip.id, sp)
                        },
                        onAddSubtitle = { text ->
                            viewModel.addSubtitleAtCurrentTime(text)
                        },
                        onUpdateSubtitle = { subId, text, start, dur, col, sz ->
                            viewModel.updateSubtitleClip(subId, text, start, dur, col, sz)
                        },
                        onDeleteSubtitle = { subId ->
                            viewModel.deleteSubtitleClip(subId)
                        },
                        onImportSrtClick = {
                            showSrtDialog = true
                        }
                    )
                }
            }
        }

        // SRT Subtitle Paste/Upload Dialog
        if (showSrtDialog) {
            AlertDialog(
                onDismissRequest = { showSrtDialog = false },
                title = { Text("Import Subtitles (SRT စာတန်းထိုးထည့်ရန်)") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ဖုန်းထဲမှ .srt ဖိုင်တစ်ခုကို ရွေးချယ်ပါ သို့မဟုတ် Subtitle စာသားများကို အောက်ပါအကွက်တွင် တိုက်ရိုက်ကူးယူထည့်သွင်း (Paste) ပြုလုပ်ပါ။",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                srtPickerLauncher.launch("*/*")
                                showSrtDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select .srt File (ဖုန်းထဲမှ ရွေးရန်)")
                        }

                        Text("OR Paste SRT content below:", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                        OutlinedTextField(
                            value = srtPasteText,
                            onValueChange = { srtPasteText = it },
                            placeholder = { Text("1\n00:00:01,000 --> 00:00:03,000\nHello World!") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (srtPasteText.isNotBlank()) {
                                viewModel.importSrtToTextTrack(srtPasteText)
                                showSrtDialog = false
                                srtPasteText = ""
                            }
                        }
                    ) {
                        Text("Import Subtitles")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSrtDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Add Media Bottom Sheet Modal dialog
        if (showMediaSheet) {
            AlertDialog(
                onDismissRequest = { showMediaSheet = false },
                title = { Text("Import Media Into Timeline") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        Text(
                            "Select from imported media assets to place onto the active tracks chronologically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (mediaItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No imported media available in your bin.")
                            }
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(mediaItems) { media ->
                                    Card(
                                        onClick = {
                                            viewModel.addMediaToTimeline(media)
                                            showMediaSheet = false
                                        },
                                        modifier = Modifier
                                            .width(130.dp)
                                            .fillMaxHeight(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            val typeIcon = when (media.type) {
                                                MediaType.VIDEO -> Icons.Default.PlayCircle
                                                MediaType.AUDIO -> Icons.Default.MusicNote
                                                MediaType.IMAGE -> Icons.Default.Image
                                            }
                                            Icon(
                                                imageVector = typeIcon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = media.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = "${media.durationMs / 1000}s",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showMediaSheet = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

// ---------------- PLAYER MONITOR COMPOSABLE -----------------
@Composable
fun VideoPreviewMonitor(
    activeClip: Clip?,
    activeTextClip: Clip? = null,
    aspectRatio: String,
    isPlaying: Boolean,
    currentTimeMs: Long,
    totalDurationMs: Long
) {
    val containerRatio = when (aspectRatio) {
        "16:9" -> 16f / 9f
        "9:16" -> 9f / 16f
        else -> 1f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .aspectRatio(1.77f) // Landscape container to hold dynamic ratios comfortably
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (activeClip != null) {
                // Determine matrix filter to apply
                val colorMatrix = remember(activeClip.filter) {
                    when (activeClip.filter) {
                        VideoFilter.NONE -> ColorMatrix()
                        VideoFilter.GRAYSCALE -> ColorMatrix().apply { setToSaturation(0f) }
                        VideoFilter.SEPIA -> ColorMatrix(floatArrayOf(
                            0.393f, 0.769f, 0.189f, 0f, 0f,
                            0.349f, 0.686f, 0.168f, 0f, 0f,
                            0.272f, 0.534f, 0.131f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                        VideoFilter.MONOCHROME -> ColorMatrix().apply { setToSaturation(0f) }
                        VideoFilter.INVERT -> ColorMatrix(floatArrayOf(
                            -1.0f, 0f, 0f, 0f, 255f,
                            0f, -1.0f, 0f, 0f, 255f,
                            0f, 0f, -1.0f, 0f, 255f,
                            0f, 0f, 0f, 1.0f, 0f
                        ))
                        VideoFilter.VINTAGE -> ColorMatrix(floatArrayOf(
                            0.95f, 0.05f, 0f, 0f, 0f,
                            0f, 0.90f, 0.10f, 0f, 0f,
                            0f, 0f, 0.80f, 0.20f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                        VideoFilter.WARM -> ColorMatrix(floatArrayOf(
                            1.15f, 0f, 0f, 0f, 10f,
                            0f, 1.05f, 0f, 0f, 5f,
                            0f, 0f, 0.90f, 0f, -10f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                        VideoFilter.COOL -> ColorMatrix(floatArrayOf(
                            0.90f, 0f, 0f, 0f, -10f,
                            0f, 1.05f, 0f, 0f, 0f,
                            0f, 0f, 1.20f, 0f, 15f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                        VideoFilter.BLUR -> ColorMatrix()
                    }
                }

                // Render dynamic preview canvas representation of active video frame
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(containerRatio)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                ) {
                    // Aesthetic vector patterns inside drawing background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val cols = 5
                                val rows = 5
                                val stepX = size.width / cols
                                val stepY = size.height / rows
                                for (i in 1 until cols) {
                                    drawLine(
                                        Color.White.copy(alpha = 0.06f),
                                        Offset(i * stepX, 0f),
                                        Offset(i * stepX, size.height),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                                for (i in 1 until rows) {
                                    drawLine(
                                        Color.White.copy(alpha = 0.06f),
                                        Offset(0f, i * stepY),
                                        Offset(size.width, i * stepY),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                            }
                    ) {
                        // Big Play indicator or moving dynamic canvas graphics
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Videocam else Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = activeClip.mediaName,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }

                        // Simulation Blur Overlay
                        if (activeClip.filter == VideoFilter.BLUR) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White.copy(alpha = 0.25f))
                            )
                        }

                        // TRANSITION OVERLAY INDICATORS
                        if (activeClip.transition != TransitionType.NONE) {
                            val transitionProgress = (currentTimeMs - activeClip.startInTimelineMs).toFloat() / activeClip.durationMs
                            if (transitionProgress < 0.25f || transitionProgress > 0.75f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "[Transition: ${activeClip.transition}]",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // TEXT OVERLAY DRAWINGS
                        if (!activeClip.textOverlay.isNullOrEmpty()) {
                            val textColor = try {
                                Color(android.graphics.Color.parseColor(activeClip.textOverlayColor))
                            } catch (e: Exception) {
                                Color.White
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 54.dp, start = 16.dp, end = 16.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text(
                                    text = activeClip.textOverlay,
                                    color = textColor,
                                    fontSize = activeClip.textOverlaySize.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // DEDICATED SRT SUBTITLE TRACK OVERLAY DRAWINGS
                        if (activeTextClip != null && !activeTextClip.textOverlay.isNullOrEmpty()) {
                            val textColor = try {
                                Color(android.graphics.Color.parseColor(activeTextClip.textOverlayColor ?: "#FFFF00"))
                            } catch (e: Exception) {
                                Color.Yellow
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text(
                                    text = activeTextClip.textOverlay ?: "",
                                    color = textColor,
                                    fontSize = (activeTextClip.textOverlaySize).sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Empty timeline black card placeholder
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VideoCameraBack,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scrubber empty. Place video clips.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ---------------- PLAYER CONTROLS COMPOSABLE -----------------
@Composable
fun PlayerControlsBar(
    isPlaying: Boolean,
    currentTimeMs: Long,
    totalDurationMs: Long,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val progress = if (totalDurationMs > 0) currentTimeMs.toFloat() / totalDurationMs else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Slider scrubber
        Slider(
            value = progress,
            onValueChange = { percent ->
                onSeek((percent * totalDurationMs).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.testTag("timeline_scrubber_slider")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatTime(currentTimeMs)} / ${formatTime(totalDurationMs)}",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Playback controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSeek(0L) }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Restart")
                }
                IconButton(onClick = { onSeek((currentTimeMs - 2000L).coerceAtLeast(0L)) }) {
                    Icon(Icons.Default.Replay5, contentDescription = "Back 5s")
                }
                FilledIconButton(
                    onClick = onPlayPauseToggle,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(52.dp).testTag("play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { onSeek((currentTimeMs + 2000L).coerceAtMost(totalDurationMs)) }) {
                    Icon(Icons.Default.Forward5, contentDescription = "Forward 5s")
                }
                IconButton(onClick = { onSeek(totalDurationMs) }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Skip End")
                }
            }

            Spacer(modifier = Modifier.width(36.dp)) // padding balance
        }
    }
}

// ---------------- TIMELINE VISUAL CANVAS COMPOSABLE -----------------
@Composable
fun TimelineCanvasView(
    project: Project,
    currentTimeMs: Long,
    selectedClipId: String?,
    onClipSelect: (String?) -> Unit,
    onClipDelete: (String) -> Unit,
    onSeek: (Long) -> Unit
) {
    val totalTimelineDur = project.durationMs.coerceAtLeast(1000L)
    
    // Compute total screen-width factor to let scrolling feel natural
    val scaleFactor = 0.08f // pixel pixels per ms

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Ruler/Header axis
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                // Render small second ticks
                val tickStepMs = 1000L
                val maxTicks = (totalTimelineDur / tickStepMs).toInt() + 1
                for (i in 0 until maxTicks) {
                    val xOffset = i * tickStepMs * scaleFactor
                    Box(
                        modifier = Modifier
                            .offset(x = xOffset.dp)
                            .width(50.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Text(
                            text = "${i}s",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Scrollable tracks list
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                project.tracks.forEach { track ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Track header label box
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(56.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val trackIcon = when (track.type) {
                                    "VIDEO" -> Icons.Default.Movie
                                    "AUDIO" -> Icons.Default.AudioFile
                                    else -> Icons.Default.TextFields
                                }
                                Icon(
                                    imageVector = trackIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = track.type,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Render track clips chain container
                        Box(
                            modifier = Modifier
                                .height(56.dp)
                                .width((totalTimelineDur * scaleFactor).dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            track.clips.forEach { clip ->
                                val clipWidth = (clip.durationMs * scaleFactor).dp
                                val clipOffset = (clip.startInTimelineMs * scaleFactor).dp

                                val isSelected = clip.id == selectedClipId
                                val containerColor = when (track.type) {
                                    "VIDEO" -> MaterialTheme.colorScheme.primaryContainer
                                    "AUDIO" -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                }

                                Card(
                                    onClick = { onClipSelect(if (isSelected) null else clip.id) },
                                    modifier = Modifier
                                        .offset(x = clipOffset)
                                        .width(clipWidth)
                                        .fillMaxHeight()
                                        .padding(vertical = 4.dp, horizontal = 2.dp)
                                        .testTag("clip_item_${clip.id}"),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else containerColor
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = clip.mediaName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${clip.durationMs / 1000}s",
                                                    fontSize = 9.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                )
                                                if (clip.filter != VideoFilter.NONE) {
                                                    Icon(
                                                        imageVector = Icons.Default.FilterVintage,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(10.dp),
                                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                if (!clip.textOverlay.isNullOrEmpty()) {
                                                    Icon(
                                                        imageVector = Icons.Default.TextFields,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(10.dp),
                                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                            }
                                        }

                                        // Tiny close/delete button inside clip
                                        IconButton(
                                            onClick = { onClipDelete(clip.id) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(14.dp)
                                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Delete",
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // RED PLAYHEAD VERTICAL SCRUBBER LINE
                            val playheadOffset = (currentTimeMs * scaleFactor).dp
                            Box(
                                modifier = Modifier
                                    .offset(x = playheadOffset)
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(Color.Red)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------- CONTEXTUAL CLIP EDITOR PANEL -----------------
@Composable
fun ClipEditorPanel(
    clip: Clip,
    project: Project? = null,
    currentTimeMs: Long = 0L,
    onSeekTo: (Long) -> Unit = {},
    onFilterChange: (VideoFilter) -> Unit,
    onTextOverlayChange: (String?, String, Int) -> Unit,
    onTransitionChange: (TransitionType) -> Unit,
    onTrimChange: (Long, Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onAddSubtitle: (String) -> Unit = {},
    onUpdateSubtitle: (String, String, Long, Long, String, Int) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteSubtitle: (String) -> Unit = {},
    onImportSrtClick: () -> Unit
) {
    var expandedDropdown by remember { mutableStateOf(false) }
    val categories = listOf(
        "VISUAL_FX" to "Visual Effects & Transitions (ရုပ်ထွက်နှင့် ကူးပြောင်းမှုများ)",
        "AUDIO_VOLUME" to "Audio, Speed & Trim (အသံ၊ နှုန်းနှင့် ဖြတ်တောက်ရန်)",
        "SUBTITLES" to "Subtitles & Text Overlay (စာတန်းထိုးနှင့် စာသားများ)"
    )
    var selectedCategory by remember { mutableStateOf("VISUAL_FX") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Clip Settings (မွမ်းမံမှုများ): ${clip.mediaName}",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Dropdown Selector Box
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedCard(
                    onClick = { expandedDropdown = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Editing Mode (မွမ်းမံမှု အမျိုးအစား)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(
                                text = categories.firstOrNull { it.first == selectedCategory }?.second ?: "",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Mode")
                    }
                }

                DropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    categories.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                selectedCategory = key
                                expandedDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Category Panel Content
            when (selectedCategory) {
                "VISUAL_FX" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Cinematic Filter (ရုပ်ထွက်အရောင်):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(VideoFilter.values()) { filter ->
                                val active = clip.filter == filter
                                InputChip(
                                    selected = active,
                                    onClick = { onFilterChange(filter) },
                                    label = { Text(filter.name, fontSize = 11.sp) },
                                    leadingIcon = {
                                        if (active) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Transition Entry (ဗီဒီယိုဝင်ချိန် ညှိရန်):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(TransitionType.values()) { transition ->
                                val active = clip.transition == transition
                                InputChip(
                                    selected = active,
                                    onClick = { onTransitionChange(transition) },
                                    label = { Text(transition.name, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Playback Speed (ဗီဒီယို အမြန်နှုန်း): ${(clip.speed ?: 1.0f)}x", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Slider(
                            value = clip.speed ?: 1.0f,
                            onValueChange = { onSpeedChange(it) },
                            valueRange = 0.5f..2.0f,
                            steps = 2
                        )
                    }
                }
                "AUDIO_VOLUME" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Clip Volume (အသံကျယ်နှုန်း): ${((clip.volume ?: 1.0f) * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Slider(
                            value = clip.volume ?: 1.0f,
                            onValueChange = { onVolumeChange(it) },
                            valueRange = 0.0f..2.0f
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Trim Clip Segment (ကြာချိန် ဖြတ်ညှိရန်):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        
                        var trimStartMs by remember(clip.id) { mutableLongStateOf(clip.startInMediaMs) }
                        var trimDurMs by remember(clip.id) { mutableLongStateOf(clip.durationMs) }

                        Text(
                            "Start Offset: ${formatTime(trimStartMs)} | Active Seg: ${formatTime(trimDurMs)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )

                        Slider(
                            value = trimDurMs.toFloat(),
                            onValueChange = { dur ->
                                trimDurMs = dur.toLong().coerceAtLeast(1000L)
                                onTrimChange(trimStartMs, trimDurMs)
                            },
                            valueRange = 1000f..15000f
                        )
                    }
                }
                "SUBTITLES" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Add Subtitle at Playhead (စာတန်းထိုးအသစ်ထည့်ရန်)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        var newSubText by remember { mutableStateOf("") }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newSubText,
                                onValueChange = { newSubText = it },
                                placeholder = { Text("Enter subtitle text...", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            
                            BootstrapButton(
                                text = "Add (+)",
                                onClick = {
                                    if (newSubText.isNotBlank()) {
                                        onAddSubtitle(newSubText)
                                        newSubText = ""
                                    }
                                },
                                type = BootstrapBtnType.SUCCESS
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // List of existing subtitles on TEXT track
                        val textTrack = project?.tracks?.firstOrNull { it.type == "TEXT" }
                        val subtitleClips = textTrack?.clips?.sortedBy { it.startInTimelineMs } ?: emptyList()

                        Text(
                            text = "Subtitle Segments (${subtitleClips.size}) (ရှိပြီးသား စာတန်းထိုးများ):",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )

                        if (subtitleClips.isEmpty()) {
                            Text(
                                text = "No subtitles added yet. Use form above or import SRT.",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        } else {
                            // Render scrollable subtitle rows
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                subtitleClips.forEach { subClip ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (currentTimeMs >= subClip.startInTimelineMs && currentTimeMs < (subClip.startInTimelineMs + subClip.durationMs)) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                            }
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Time stamp indicator
                                                Text(
                                                    text = "${formatTime(subClip.startInTimelineMs)} - ${formatTime(subClip.startInTimelineMs + subClip.durationMs)}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    // Jump / Seek button
                                                    IconButton(
                                                        onClick = { onSeekTo(subClip.startInTimelineMs) },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = "Seek",
                                                            tint = MaterialTheme.colorScheme.secondary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    
                                                    // Delete button
                                                    IconButton(
                                                        onClick = { onDeleteSubtitle(subClip.id) },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete",
                                                            tint = Color.Red,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            // Subtitle Text edit
                                            var editText by remember(subClip.id) { mutableStateOf(subClip.textOverlay ?: "") }
                                            var editSize by remember(subClip.id) { mutableIntStateOf(subClip.textOverlaySize) }
                                            var editColor by remember(subClip.id) { mutableStateOf(subClip.textOverlayColor ?: "#FFFF00") }

                                            OutlinedTextField(
                                                value = editText,
                                                onValueChange = {
                                                    editText = it
                                                    onUpdateSubtitle(subClip.id, it, subClip.startInTimelineMs, subClip.durationMs, editColor, editSize)
                                                },
                                                placeholder = { Text("Subtitle text", fontSize = 11.sp) },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodySmall
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            // Row with color chips & size controls
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    listOf("#FFFFFF" to "W", "#FFFF00" to "Y", "#FF0000" to "R", "#00FFFF" to "C").forEach { (hex, char) ->
                                                        val selected = editColor == hex
                                                        Box(
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .background(
                                                                    color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Yellow },
                                                                    shape = RoundedCornerShape(2.dp)
                                                                )
                                                                .border(
                                                                    width = if (selected) 2.dp else 1.dp,
                                                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                                    shape = RoundedCornerShape(2.dp)
                                                                )
                                                                .clickable {
                                                                    editColor = hex
                                                                    onUpdateSubtitle(subClip.id, editText, subClip.startInTimelineMs, subClip.durationMs, hex, editSize)
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(char, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (hex == "#FFFFFF" || hex == "#FFFF00" || hex == "#00FFFF") Color.Black else Color.White)
                                                        }
                                                    }
                                                }

                                                // Size row
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text("Size:", fontSize = 10.sp)
                                                    IconButton(
                                                        onClick = {
                                                            if (editSize > 12) {
                                                                editSize -= 2
                                                                onUpdateSubtitle(subClip.id, editText, subClip.startInTimelineMs, subClip.durationMs, editColor, editSize)
                                                            }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Text("-", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Text("$editSize", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    IconButton(
                                                        onClick = {
                                                            if (editSize < 40) {
                                                                editSize += 2
                                                                onUpdateSubtitle(subClip.id, editText, subClip.startInTimelineMs, subClip.durationMs, editColor, editSize)
                                                            }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(4.dp))

                        BootstrapButton(
                            text = "Import SRT Subtitles (SRT စာတန်းထိုးထည့်သွင်းရန်)",
                            onClick = onImportSrtClick,
                            type = BootstrapBtnType.SECONDARY,
                            icon = { Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// ---------------- TIME FORMATTER UTILS -----------------
private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val fraction = (timeMs % 1000) / 10
    return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, fraction)
}
