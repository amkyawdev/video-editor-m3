package com.example.presentation.ui.screens.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.models.MediaType
import com.example.domain.models.Project
import com.example.presentation.ui.components.BootstrapButton
import com.example.presentation.ui.components.BootstrapBtnType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToEditor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsState()
    val mediaItems by viewModel.mediaItems.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var selectedRatio by remember { mutableStateOf("16:9") }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Projects", "Media Bin")

    // State for Import Chooser Bottom Sheet / Dialog
    var showUploadMenu by remember { mutableStateOf(false) }

    // States for assets attached during creation of new project
    var projVideoUri by remember { mutableStateOf<Uri?>(null) }
    var projVideoName by remember { mutableStateOf<String?>(null) }
    var projVideoDuration by remember { mutableLongStateOf(0L) }

    var projAudioUri by remember { mutableStateOf<Uri?>(null) }
    var projAudioName by remember { mutableStateOf<String?>(null) }
    var projAudioDuration by remember { mutableLongStateOf(0L) }

    var projVoiceUri by remember { mutableStateOf<Uri?>(null) }
    var projVoiceName by remember { mutableStateOf<String?>(null) }
    var projVoiceDuration by remember { mutableLongStateOf(0L) }

    var projSrtContent by remember { mutableStateOf<String?>(null) }
    var projSrtFileName by remember { mutableStateOf<String?>(null) }
    var showSrtTextInput by remember { mutableStateOf(false) }
    var srtManualText by remember { mutableStateOf("") }

    // Launchers for Native Android File Pickers (Library Section)
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = getFileName(context, it) ?: "video_${System.currentTimeMillis()}.mp4"
            val duration = getMediaDuration(context, it, 10000L)
            viewModel.importMediaItem(name, it.toString(), MediaType.VIDEO, duration)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = getFileName(context, it) ?: "audio_${System.currentTimeMillis()}.mp3"
            val duration = getMediaDuration(context, it, 15000L)
            viewModel.importMediaItem(name, it.toString(), MediaType.AUDIO, duration)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = getFileName(context, it) ?: "image_${System.currentTimeMillis()}.png"
            viewModel.importMediaItem(name, it.toString(), MediaType.IMAGE, 5000L)
        }
    }

    // Launchers for Project Creation Wizard
    val projectVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            projVideoUri = it
            projVideoName = getFileName(context, it) ?: "video_${System.currentTimeMillis()}.mp4"
            projVideoDuration = getMediaDuration(context, it, 10000L)
        }
    }

    val projectAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            projAudioUri = it
            projAudioName = getFileName(context, it) ?: "song_${System.currentTimeMillis()}.mp3"
            projAudioDuration = getMediaDuration(context, it, 15000L)
        }
    }

    val projectVoiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            projVoiceUri = it
            projVoiceName = getFileName(context, it) ?: "voice_${System.currentTimeMillis()}.mp3"
            projVoiceDuration = getMediaDuration(context, it, 12000L)
        }
    }

    val projectSrtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val content = stream.bufferedReader().use { r -> r.readText() }
                    projSrtContent = content
                    projSrtFileName = getFileName(context, it) ?: "subtitles.srt"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Automatically check status on first load without demo data populating
    LaunchedEffect(Unit) {
        // No sample/demo assets loaded here to remain clean and professional (Don't Demo)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MovieFilter,
                            contentDescription = "App Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Video Edition M3",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        newProjectName = "My Masterpiece ${projects.size + 1}"
                        showCreateDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = "New Project") },
                    text = { Text("New Project") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("create_project_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Segmented / Tab buttons
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 16.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedTab == 0) {
                // Projects List
                if (projects.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Movie,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No projects found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Click 'New Project' below to create a video timeline.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(projects, key = { it.id }) { project ->
                            ProjectCard(
                                project = project,
                                onClick = { onNavigateToEditor(project.id) },
                                onDelete = { viewModel.deleteProject(project.id) }
                            )
                        }
                    }
                }
            } else {
                // Media Bin List
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${mediaItems.size} Media Assets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        BootstrapButton(
                            text = "Upload File (ဖိုင်ထည့်ရန်)",
                            onClick = { showUploadMenu = true },
                            type = BootstrapBtnType.PRIMARY,
                            icon = { Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    if (mediaItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No media assets imported.")
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(mediaItems, key = { it.id }) { media ->
                                MediaAssetCard(
                                    media = media,
                                    onDelete = { viewModel.deleteMedia(media.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Upload Selection Dialog
        if (showUploadMenu) {
            AlertDialog(
                onDismissRequest = { showUploadMenu = false },
                title = { Text("Upload File (ဖိုင်ရွေးချယ်ရန်)") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ဖုန်းထဲမှ Video, Audio, သို့မဟုတ် Image ဖိုင်များကို ရွေးချယ်ထည့်သွင်းပါ (စစ်မှန်သော ဖိုင်များကိုသာ အသုံးပြုရန်)",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Video upload card
                        Surface(
                            onClick = {
                                videoPicker.launch("video/*")
                                showUploadMenu = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Video File (ဗီဒီယို ဖိုင်)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("MP4, MKV and more", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }

                        // Audio upload card
                        Surface(
                            onClick = {
                                audioPicker.launch("audio/*")
                                showUploadMenu = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Song & Voice (အသံ/သီချင်း)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("MP3, WAV, AAC and voice records", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }

                        // Image upload card
                        Surface(
                            onClick = {
                                imagePicker.launch("image/*")
                                showUploadMenu = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Image File (ဓာတ်ပုံ ဖိုင်)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("JPG, PNG, WEBP and overlays", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showUploadMenu = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Create Project Dialog (Prone structure with subtitle/audio/video setup)
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VideoCall,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Project Setup (ပရောဂျက်အသစ်)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        OutlinedTextField(
                            value = newProjectName,
                            onValueChange = { newProjectName = it },
                            label = { Text("Project Name (အမည်)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("project_name_input"),
                            singleLine = true
                        )

                        Text("Aspect Ratio (ဗီဒီယို ပုံစံချိုး)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val ratios = listOf("16:9", "9:16", "1:1")
                            ratios.forEach { ratio ->
                                val isSelected = selectedRatio == ratio
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedRatio = ratio }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = when(ratio) {
                                                "16:9" -> "16:9 Landscape"
                                                "9:16" -> "9:16 Portrait"
                                                else -> "1:1 Square"
                                            },
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 1. Video Attachment
                        Text("1. Primary Video Upload (ဗီဒီယို ဖိုင်)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (projVideoUri != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(projVideoName ?: "Selected Video", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Duration: ${projVideoDuration / 1000}s", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        IconButton(onClick = {
                                            projVideoUri = null
                                            projVideoName = null
                                            projVideoDuration = 0L
                                        }, modifier = Modifier.size(20.dp)) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Red, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                } else {
                                    BootstrapButton(
                                        text = "Select Video File",
                                        onClick = { projectVideoLauncher.launch("video/*") },
                                        type = BootstrapBtnType.PRIMARY,
                                        icon = { Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // 2. Soundtrack Attachment
                        Text("2. Song Track (သီချင်းဖိုင်)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (projAudioUri != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(projAudioName ?: "Selected Song", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Duration: ${projAudioDuration / 1000}s", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        IconButton(onClick = {
                                            projAudioUri = null
                                            projAudioName = null
                                            projAudioDuration = 0L
                                        }, modifier = Modifier.size(20.dp)) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Red, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                } else {
                                    BootstrapButton(
                                        text = "Select Song/Soundtrack",
                                        onClick = { projectAudioLauncher.launch("audio/*") },
                                        type = BootstrapBtnType.SECONDARY,
                                        icon = { Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // 3. Voice Recording Attachment
                        Text("3. Voice Over Narration (အသံသွင်းဖိုင်)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (projVoiceUri != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(projVoiceName ?: "Selected Voice Recording", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Duration: ${projVoiceDuration / 1000}s", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        IconButton(onClick = {
                                            projVoiceUri = null
                                            projVoiceName = null
                                            projVoiceDuration = 0L
                                        }, modifier = Modifier.size(20.dp)) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Red, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                } else {
                                    BootstrapButton(
                                        text = "Select Voice Record",
                                        onClick = { projectVoiceLauncher.launch("audio/*") },
                                        type = BootstrapBtnType.WARNING,
                                        icon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // 4. Subtitles Attachment
                        Text("4. Subtitles SRT or Text (စာတန်းထိုး)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (projSrtFileName != null || !projSrtContent.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(projSrtFileName ?: "Pasted Subtitles", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Subtitle track initialized", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        IconButton(onClick = {
                                            projSrtContent = null
                                            projSrtFileName = null
                                            srtManualText = ""
                                        }, modifier = Modifier.size(20.dp)) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Red, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        BootstrapButton(
                                            text = "Upload SRT File",
                                            onClick = { projectSrtLauncher.launch("*/*") },
                                            type = BootstrapBtnType.INFO,
                                            icon = { Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                            modifier = Modifier.weight(1f)
                                        )

                                        BootstrapButton(
                                            text = "Paste Subtitle Text",
                                            onClick = { showSrtTextInput = !showSrtTextInput },
                                            type = BootstrapBtnType.SECONDARY,
                                            icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    if (showSrtTextInput) {
                                        OutlinedTextField(
                                            value = srtManualText,
                                            onValueChange = {
                                                srtManualText = it
                                                projSrtContent = it
                                            },
                                            placeholder = { Text("Paste SRT subtitle content here...", fontSize = 11.sp) },
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    BootstrapButton(
                        text = "Create Project & Import",
                        onClick = {
                            if (newProjectName.isNotBlank()) {
                                viewModel.createProjectWithAssets(
                                    name = newProjectName,
                                    aspectRatio = selectedRatio,
                                    videoUri = projVideoUri?.toString(),
                                    videoName = projVideoName,
                                    videoDurationMs = projVideoDuration,
                                    audioUri = projAudioUri?.toString(),
                                    audioName = projAudioName,
                                    audioDurationMs = projAudioDuration,
                                    voiceUri = projVoiceUri?.toString(),
                                    voiceName = projVoiceName,
                                    voiceDurationMs = projVoiceDuration,
                                    srtContent = projSrtContent
                                )
                                // Clear creation states
                                projVideoUri = null
                                projVideoName = null
                                projVideoDuration = 0L
                                projAudioUri = null
                                projAudioName = null
                                projAudioDuration = 0L
                                projVoiceUri = null
                                projVoiceName = null
                                projVoiceDuration = 0L
                                projSrtContent = null
                                projSrtFileName = null
                                srtManualText = ""
                                showCreateDialog = false
                            }
                        },
                        type = BootstrapBtnType.SUCCESS,
                        modifier = Modifier.testTag("dialog_confirm_button")
                    )
                },
                dismissButton = {
                    TextButton(onClick = {
                        // Clear creation states
                        projVideoUri = null
                        projVideoName = null
                        projVideoDuration = 0L
                        projAudioUri = null
                        projAudioName = null
                        projAudioDuration = 0L
                        projVoiceUri = null
                        projVoiceName = null
                        projVoiceDuration = 0L
                        projSrtContent = null
                        projSrtFileName = null
                        srtManualText = ""
                        showCreateDialog = false
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val dateString = formatter.format(Date(project.updatedAt))

    val ratioIcon = when (project.aspectRatio) {
        "16:9" -> Icons.Default.Rectangle
        "9:16" -> Icons.Default.SmartScreen
        else -> Icons.Default.Square
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("project_card_${project.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual aspect ratio representation thumbnail
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ratioIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Format: ${project.aspectRatio}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Timelapse,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${project.durationMs / 1000}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Edited: $dateString",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete project",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun MediaAssetCard(
    media: com.example.domain.models.Media,
    onDelete: () -> Unit
) {
    val typeIcon = when (media.type) {
        MediaType.VIDEO -> Icons.Default.PlayCircle
        MediaType.AUDIO -> Icons.Default.MusicNote
        MediaType.IMAGE -> Icons.Default.Image
    }

    val durationText = if (media.type == MediaType.IMAGE) {
        "Static image"
    } else {
        "${media.durationMs / 1000}s"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = media.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete media",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    result = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result
}

private fun getMediaDuration(context: Context, uri: Uri, defaultDuration: Long = 10000L): Long {
    var retriever: MediaMetadataRetriever? = null
    try {
        retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        if (time != null) {
            return time.toLong()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        try {
            retriever?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return defaultDuration
}

