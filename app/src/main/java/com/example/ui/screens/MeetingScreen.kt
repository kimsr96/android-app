package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MeetingNote
import com.example.viewmodel.MeetingViewModel

@Composable
fun MeetingScreen(
    viewModel: MeetingViewModel,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val notes by viewModel.filteredNotes.collectAsStateWithLifecycle()
    val selectedNote by viewModel.selectedNote.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordingDuration by viewModel.recordingDuration.collectAsStateWithLifecycle()
    val recordingTranscript by viewModel.recordingTranscript.collectAsStateWithLifecycle()
    val recordingAmplitudes by viewModel.recordingAmplitudes.collectAsStateWithLifecycle()
    val isLoadingAi by viewModel.isLoadingAi.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()

    // Screen navigation/mode state inside single-screen
    var isCreatingDraft by remember { mutableStateOf(false) }
    var draftTitle by remember { mutableStateOf("") }
    var draftContent by remember { mutableStateOf("") }
    var draftCategory by remember { mutableStateOf("General") }

    // Selected note workspace editing states
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf("General") }

    // Populate editor states whenever selected note changes
    LaunchedEffect(selectedNote) {
        selectedNote?.let {
            editTitle = it.title
            editContent = it.content
            editCategory = it.category
        }
    }

    // Audio Permission Launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording(draftCategory)
        } else {
            Toast.makeText(context, "회의 녹음을 시작하려면 음성 마이크 장치 권한 승인이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // Handle generic error message Toast
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isCompact = maxWidth < 600.dp

        // App top header + main body layout
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            HeaderBar(
                searchQuery = searchQuery,
                onSearchChange = { viewModel.setSearchQuery(it) },
                showBackButton = isCompact && (selectedNote != null || isCreatingDraft || isRecording),
                onBackClick = {
                    if (isRecording) {
                        viewModel.stopRecording("", "General")
                        viewModel.selectNote(null)
                        isCreatingDraft = false
                    } else if (isCreatingDraft) {
                        isCreatingDraft = false
                    } else if (selectedNote != null) {
                        viewModel.selectNote(null)
                    }
                },
                isCompact = isCompact
            )

            // Category quick tags (Only show on list screen in compact mode to preserve vertical screen space)
            val showCategories = !isCompact || (!isRecording && !isCreatingDraft && selectedNote == null)
            if (showCategories) {
                CategoryFilters(
                    selectedCategory = selectedCategory,
                    onCategorySelect = { viewModel.setCategoryFilter(it) }
                )
            }

            if (isCompact) {
                // Mobile Portrait layout - shows screen sequentially with zero squished horizontal columns
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        // 1. ACTIVE VOICE RECORDING SIMULATOR SCREEN
                        isRecording -> {
                            RecordingScreen(
                                title = draftTitle.ifEmpty { "새로운 회의록 기록" },
                                durationTicks = recordingDuration,
                                transcript = recordingTranscript,
                                amplitudes = recordingAmplitudes,
                                category = draftCategory,
                                onStopAndSave = {
                                    viewModel.stopRecording(draftTitle, draftCategory)
                                    isCreatingDraft = false
                                },
                                onCancel = {
                                    viewModel.stopRecording("", "General")
                                    viewModel.selectNote(null)
                                    isCreatingDraft = false
                                }
                            )
                        }

                        // 2. CREATING NEW DRAFT SCREEN
                        isCreatingDraft -> {
                            DraftCreatorWorkspace(
                                title = draftTitle,
                                onTitleChange = { draftTitle = it },
                                content = draftContent,
                                onContentChange = { draftContent = it },
                                category = draftCategory,
                                onCategoryChange = { draftCategory = it },
                                onStartVoiceRecord = {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    
                                    if (hasPermission) {
                                        viewModel.startRecording(draftCategory)
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                onSaveManual = {
                                    viewModel.createNote(draftTitle, draftContent, draftCategory)
                                    isCreatingDraft = false
                                },
                                onCancel = {
                                    isCreatingDraft = false
                                }
                            )
                        }

                        // 3. EDIT & SUMMARY WORKSPACE FOR AN EXISTING SELECTED NOTE
                        selectedNote != null -> {
                            NoteEditorSummaryWorkspace(
                                note = selectedNote!!,
                                editTitle = editTitle,
                                onTitleChange = { editTitle = it },
                                editContent = editContent,
                                onContentChange = { editContent = it },
                                editCategory = editCategory,
                                onCategoryChange = { editCategory = it },
                                isLoadingAi = isLoadingAi,
                                onSaveChanges = {
                                    viewModel.updateManualNote(selectedNote!!.id, editTitle, editContent, editCategory)
                                    Toast.makeText(context, "회의 내용이 성공적으로 업데이트되었습니다.", Toast.LENGTH_SHORT).show()
                                },
                                onAskGemini = {
                                    viewModel.analyzeWithGemini(selectedNote!!)
                                },
                                onCopySummary = { textToCopy ->
                                    clipboardManager.setText(AnnotatedString(textToCopy))
                                    Toast.makeText(context, "AI 요약본이 클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // 4. LANDING LIST PANEL FOR SAVED MEETINGS
                        else -> {
                            MeetingListPanel(
                                notes = notes,
                                selectedNote = selectedNote,
                                onNoteSelect = {
                                    isCreatingDraft = false
                                    viewModel.selectNote(it)
                                },
                                onDeleteNote = { viewModel.deleteNote(it) },
                                modifier = Modifier.fillMaxSize(),
                                onAddNewClick = {
                                    viewModel.selectNote(null)
                                    isCreatingDraft = true
                                    draftTitle = ""
                                    draftContent = ""
                                    draftCategory = "General"
                                }
                            )
                        }
                    }
                }
            } else {
                // Wide Screen / Tablet split screen layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // PANEL 1: Saved Meeting List
                    MeetingListPanel(
                        notes = notes,
                        selectedNote = selectedNote,
                        onNoteSelect = {
                            isCreatingDraft = false
                            viewModel.selectNote(it)
                        },
                        onDeleteNote = { viewModel.deleteNote(it) },
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight(),
                        onAddNewClick = {
                            viewModel.selectNote(null)
                            isCreatingDraft = true
                            draftTitle = ""
                            draftContent = ""
                            draftCategory = "General"
                        }
                    )

                    // Split divider
                    VerticalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // PANEL 2: Dynamic Workspace (Active Creator/Recorder OR View Editor)
                    Box(
                        modifier = Modifier
                            .weight(1.8f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                    ) {
                        when {
                            // 1. ACTIVE VOICE RECORDING SIMULATOR SCREEN
                            isRecording -> {
                                RecordingScreen(
                                    title = draftTitle.ifEmpty { "새로운 회의록 기록" },
                                    durationTicks = recordingDuration,
                                    transcript = recordingTranscript,
                                    amplitudes = recordingAmplitudes,
                                    category = draftCategory,
                                    onStopAndSave = {
                                        viewModel.stopRecording(draftTitle, draftCategory)
                                        isCreatingDraft = false
                                    },
                                    onCancel = {
                                        viewModel.stopRecording("", "General")
                                        viewModel.selectNote(null)
                                        isCreatingDraft = false
                                    }
                                )
                            }

                            // 2. CREATING NEW DRAFT SCREEN
                            isCreatingDraft -> {
                                DraftCreatorWorkspace(
                                    title = draftTitle,
                                    onTitleChange = { draftTitle = it },
                                    content = draftContent,
                                    onContentChange = { draftContent = it },
                                    category = draftCategory,
                                    onCategoryChange = { draftCategory = it },
                                    onStartVoiceRecord = {
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        
                                        if (hasPermission) {
                                            viewModel.startRecording(draftCategory)
                                        } else {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    onSaveManual = {
                                        viewModel.createNote(draftTitle, draftContent, draftCategory)
                                        isCreatingDraft = false
                                    },
                                    onCancel = {
                                        isCreatingDraft = false
                                    }
                                )
                            }

                            // 3. EDIT & SUMMARY WORKSPACE FOR AN EXISTING SELECTED NOTE
                            selectedNote != null -> {
                                NoteEditorSummaryWorkspace(
                                    note = selectedNote!!,
                                    editTitle = editTitle,
                                    onTitleChange = { editTitle = it },
                                    editContent = editContent,
                                    onContentChange = { editContent = it },
                                    editCategory = editCategory,
                                    onCategoryChange = { editCategory = it },
                                    isLoadingAi = isLoadingAi,
                                    onSaveChanges = {
                                        viewModel.updateManualNote(selectedNote!!.id, editTitle, editContent, editCategory)
                                        Toast.makeText(context, "회의 내용이 성공적으로 업데이트되었습니다.", Toast.LENGTH_SHORT).show()
                                    },
                                    onAskGemini = {
                                        viewModel.analyzeWithGemini(selectedNote!!)
                                    },
                                    onCopySummary = { textToCopy ->
                                        clipboardManager.setText(AnnotatedString(textToCopy))
                                        Toast.makeText(context, "AI 요약본이 클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            // 4. LANDING EMPTY WORKSPACE STATE
                            else -> {
                                EmptyWorkspaceLanding(
                                    onStartMeeting = {
                                        isCreatingDraft = true
                                        draftTitle = ""
                                        draftContent = ""
                                        draftCategory = "General"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- SUB-COMPONENTS ---

@Composable
fun HeaderBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    isCompact: Boolean = false
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isCompact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Row: Brand Info + Profile Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showBackButton) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.padding(end = 6.dp).size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back to List",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Summarize,
                            contentDescription = "Meeting App Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(28.dp)
                                .padding(end = 6.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Meeting Notes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = (-0.5).sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "로컬 오프라인 데이터베이스 활성",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "KI",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                // Bottom Row: Custom Beautiful Search Capsule
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        ),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "회의록 검색...",
                                        style = TextStyle(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_input")
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBackButton) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.padding(end = 6.dp).size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to List",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    // Elegant minimal Brand Group
                    Icon(
                        imageVector = Icons.Default.Summarize,
                        contentDescription = "Meeting App Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(end = 6.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Meeting Notes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = (-0.5).sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "로컬 오프라인 데이터베이스 활성",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Beautiful minimal capsule search matching the HTML template perfectly!
                Row(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { /* Focus search */ }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        ),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "회의록 검색...",
                                        style = TextStyle(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_input")
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Elegant circular user initial avatar from the HTML guideline template!
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "KI",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryFilters(
    selectedCategory: String,
    onCategorySelect: (String) -> Unit
) {
    val categories = listOf("All", "General", "Business", "Project", "Ideas", "Personal")
    val koCategories = mapOf(
        "All" to "전체보기",
        "General" to "일반",
        "Business" to "업무기획",
        "Project" to "프로젝트",
        "Ideas" to "아이디어",
        "Personal" to "개인기록"
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(categories) { category ->
            val isSelected = selectedCategory == category
            val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(containerColor)
                    .clickable { onCategorySelect(category) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("category_filter_${category.lowercase()}")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (category) {
                        "General" -> Icons.Outlined.Description
                        "Business" -> Icons.Outlined.BusinessCenter
                        "Project" -> Icons.Outlined.Assignment
                        "Ideas" -> Icons.Outlined.Lightbulb
                        "Personal" -> Icons.Outlined.Person
                        else -> Icons.Outlined.GridView
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = contentColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = koCategories[category] ?: category,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun MeetingListPanel(
    notes: List<MeetingNote>,
    selectedNote: MeetingNote?,
    onNoteSelect: (MeetingNote) -> Unit,
    onDeleteNote: (MeetingNote) -> Unit,
    modifier: Modifier = Modifier,
    onAddNewClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "내 회의기록 (${notes.size})",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.5).sp
            )

            Button(
                onClick = onAddNewClick,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("new_meeting_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("회의 추가", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SpeakerNotesOff,
                        contentDescription = "No Notes",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "등록된 회의록이 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(notes) { note ->
                    val isSelected = selectedNote?.id == note.id

                    val noteBgColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer // bg-[#E8DEF8]
                    } else {
                        MaterialTheme.colorScheme.surface // bg-white
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(if (isSelected) RoundedCornerShape(28.dp) else RoundedCornerShape(24.dp))
                            .then(
                                if (!isSelected) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                } else Modifier
                            )
                            .background(noteBgColor)
                            .clickable { onNoteSelect(note) }
                            .padding(16.dp)
                            .testTag("note_item_${note.id}")
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = note.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                
                                val formattedDate = remember(note.dateMillis) {
                                    val format = java.text.SimpleDateFormat("MM.dd HH:mm", java.util.Locale.KOREAN)
                                    format.format(note.dateMillis)
                                }
                                Text(
                                    text = formattedDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Text(
                                text = note.content,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val formattedDuration = if (note.durationSeconds > 0) {
                                        val min = note.durationSeconds / 60
                                        val sec = note.durationSeconds % 60
                                        String.format("%02d:%02d 녹음", min, sec)
                                    } else {
                                        "텍스트 메모"
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = formattedDuration,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                when (note.category) {
                                                    "Business" -> Color(0xFF1E88E5).copy(alpha = 0.15f)
                                                    "Project" -> Color(0xFF43A047).copy(alpha = 0.15f)
                                                    "Ideas" -> Color(0xFFFFB300).copy(alpha = 0.15f)
                                                    "Personal" -> Color(0xFF8E24AA).copy(alpha = 0.15f)
                                                    else -> Color(0xFF757575).copy(alpha = 0.15f)
                                                }
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = when (note.category) {
                                                "Business" -> "업무기획"
                                                "Project" -> "프로젝트"
                                                "Ideas" -> "아이디어"
                                                "Personal" -> "개인기록"
                                                else -> "일반"
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (note.category) {
                                                "Business" -> Color(0xFF1E88E5)
                                                "Project" -> Color(0xFF43A047)
                                                "Ideas" -> Color(0xFFF57F17)
                                                "Personal" -> Color(0xFF8E24AA)
                                                else -> Color(0xFF616161)
                                            }
                                        )
                                    }

                                    if (note.aiSummary != null) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFE0F2F1))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = Color(0xFF00796B),
                                                    modifier = Modifier.size(9.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "AI 요약됨",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF00796B)
                                                )
                                            }
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { onDeleteNote(note) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete note",
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DraftCreatorWorkspace(
    title: String,
    onTitleChange: (String) -> Unit,
    content: String,
    onContentChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    onStartVoiceRecord: () -> Unit,
    onSaveManual: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "새로운 회의록 등록",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("회의 명칭 / 제목") },
            placeholder = { Text("예: 주간 기획 공유회") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "회의 카테고리 기재",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        val categories = listOf("General", "Business", "Project", "Ideas", "Personal")
        val categoryLabels = mapOf(
            "General" to "일반",
            "Business" to "기획",
            "Project" to "프로젝트",
            "Ideas" to "아이디어",
            "Personal" to "개인"
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                val active = category == cat
                val tintColor = when (cat) {
                    "Business" -> Color(0xFF1E88E5)
                    "Project" -> Color(0xFF43A047)
                    "Ideas" -> Color(0xFFF57F17)
                    "Personal" -> Color(0xFF8E24AA)
                    else -> MaterialTheme.colorScheme.primary
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            1.dp,
                            if (active) tintColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                        .background(
                            if (active) tintColor.copy(alpha = 0.12f) else Color.Transparent
                        )
                        .clickable { onCategoryChange(cat) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = categoryLabels[cat] ?: cat,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) tintColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "스마트 보이스 실시간 회의 녹음기",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "안드로이드 마이크 장치를 연동하여 실제 회의 목소리를 녹음하고 실시간 음성 인식(STT)을 가동하여 기록합니다.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onStartVoiceRecord,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("start_record_btn")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("녹음 시작 (STT 자동 작성)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "또는 수동 직접 필기 작성",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            placeholder = { Text("회의 발언 내용을 자유롭게 적어보세요. (예: 홍길동 책임연구원: 2.5버전 DB설계를 이번주 내 매듭짓겠습니다.)", fontSize = 13.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("취소")
            }
            Button(
                onClick = onSaveManual,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_draft_btn")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("수동 저장 & 요약")
            }
        }
    }
}

@Composable
fun RecordingScreen(
    title: String,
    durationTicks: Int,
    transcript: String,
    amplitudes: List<Float>,
    category: String,
    onStopAndSave: () -> Unit,
    onCancel: () -> Unit
) {
    val durationSecs = durationTicks / 10
    val minutes = durationSecs / 60
    val seconds = durationSecs % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = pulseAlpha))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "스마트 속기사 녹음 중",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "동작모드: " + when (category) {
                    "Business" -> "업무기획 최적화"
                    "Project" -> "프로젝트 진행 점검"
                    "Ideas" -> "브레인스토밍 아이디어"
                    "Personal" -> "개인 회고 기록"
                    else -> "일반 대화 수집"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barCount = amplitudes.size
                val gap = 6.dp.toPx()
                val totalGap = gap * (barCount - 1)
                val barWidth = (size.width - totalGap) / barCount
                val centerY = size.height / 2

                for (i in 0 until barCount) {
                    val amp = amplitudes.getOrElse(i) { 0.1f }
                    val barHeight = size.height * amp * 0.9f
                    val left = i * (barWidth + gap)
                    val top = centerY - (barHeight / 2)

                    drawRoundRect(
                        color = if (i % 2 == 0) Color(0xFF1E88E5) else Color(0xFF26A69A),
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                    )
                }
            }
        }

        Text(
            text = formattedTime,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 1.sp
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            Text(
                text = "실시간 받아쓰기 속기록 (STT 엔진 동작 중)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (transcript.isEmpty()) {
                    Text(
                        text = "마이크로부터 음성을 감지하고 있습니다. 목소리를 들려주세요...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                } else {
                    Text(
                        text = transcript,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("녹음 취소", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onStopAndSave,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1.5f)
                    .testTag("stop_record_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("기록 중지 및 분석", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun NoteEditorSummaryWorkspace(
    note: MeetingNote,
    editTitle: String,
    onTitleChange: (String) -> Unit,
    editContent: String,
    onContentChange: (String) -> Unit,
    editCategory: String,
    onCategoryChange: (String) -> Unit,
    isLoadingAi: Boolean,
    onSaveChanges: () -> Unit,
    onAskGemini: () -> Unit,
    onCopySummary: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val formattedLongDate = remember(note.dateMillis) {
                val format = java.text.SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", java.util.Locale.KOREAN)
                format.format(note.dateMillis)
            }
            Text(
                text = "$formattedLongDate 회의",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                val categories = listOf("General", "Business", "Project", "Ideas", "Personal")
                var showDrop by remember { mutableStateOf(false) }
                
                Box {
                    AssistChip(
                        onClick = { showDrop = true },
                        label = {
                            Text(
                                text = when (editCategory) {
                                    "Business" -> "업무기획"
                                    "Project" -> "프로젝트"
                                    "Ideas" -> "아이디어"
                                    "Personal" -> "개인기록"
                                    else -> "일반회의"
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Category,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                    DropdownMenu(expanded = showDrop, onDismissRequest = { showDrop = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (cat) {
                                            "Business" -> "업무기획"
                                            "Project" -> "프로젝트"
                                            "Ideas" -> "아이디어"
                                            "Personal" -> "개인기록"
                                            else -> "일반회의"
                                        }
                                    )
                                },
                                onClick = {
                                    onCategoryChange(cat)
                                    showDrop = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onSaveChanges,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("수정 저장", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = editTitle,
            onValueChange = onTitleChange,
            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = editContent,
            onValueChange = onContentChange,
            textStyle = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 220.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(24.dp)
                ),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Gemini Icon",
                            tint = Color(0xFF009688),
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(if (isLoadingAi) 180f else 0f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Gemini AI 자동 요약 비서",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "핵심 안건 추출 및 액션 아이템 자동 분류",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Box {
                        if (isLoadingAi) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF009688)
                            )
                        } else {
                            IconButton(
                                onClick = onAskGemini,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("run_gemini_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Re-analyze",
                                    tint = Color(0xFF009688),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                when {
                    isLoadingAi -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Gemini가 대화 내용을 분석하고 있습니다...",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    note.aiSummary != null -> {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📋 회의 한 줄 요약",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { onCopySummary(note.aiSummary) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Summary",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note.aiSummary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("ai_summary_txt")
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "✅ 실천 업무 및 마일스톤 (Action Items)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE91E63)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = note.aiActionItems ?: "도출된 작업 사항이 없습니다.",
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("ai_actions_txt")
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "🔑 주요 키워드 해시태그",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            val keywordList = note.aiKeywords?.split(",")?.map { it.trim() } ?: emptyList()
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(keywordList) { keyword ->
                                    if (keyword.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "#$keyword",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "작성된 회의기록 요약이 아직 없거나 편집되었습니다.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = onAskGemini,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF009688)),
                                    modifier = Modifier.testTag("analyze_cta_btn")
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Gemini AI 요약 실행하기", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyWorkspaceLanding(
    onStartMeeting: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardVoice,
                contentDescription = "Mic logo",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "작업 공간이 대기 중입니다",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "좌측 목록에서 열람할 회의록을 선택하거나,\n새로운 회의를 가동하여 스마트 속기를 진행해 보세요.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Button(
                onClick = onStartMeeting,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("신규 회의 시작하기", fontWeight = FontWeight.Bold)
            }
        }
    }
}
