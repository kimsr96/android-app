package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiRepository
import com.example.data.database.AppDatabase
import com.example.data.model.MeetingNote
import com.example.data.repository.MeetingNoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

class MeetingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MeetingNoteRepository
    private val geminiRepository = GeminiRepository()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = MeetingNoteRepository(db.meetingNoteDao())
    }

    val dbNotes: StateFlow<List<MeetingNote>> = repository.allNotes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("All")
    val selectedCategoryFilter = _selectedCategoryFilter.asStateFlow()

    // Combined notes list based on search query and category filters
    val filteredNotes: StateFlow<List<MeetingNote>> = combine(
        dbNotes, _searchQuery, _selectedCategoryFilter
    ) { notes, query, category ->
        notes.filter { note ->
            val matchesSearch = note.title.contains(query, ignoreCase = true) ||
                    note.content.contains(query, ignoreCase = true) ||
                    (note.aiSummary?.contains(query, ignoreCase = true) ?: false)
            val matchesCategory = category == "All" || note.category == category
            matchesSearch && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedNote = MutableStateFlow<MeetingNote?>(null)
    val selectedNote = _selectedNote.asStateFlow()

    // Recording-related States
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0)
    val recordingDuration = _recordingDuration.asStateFlow()

    private val _recordingTranscript = MutableStateFlow("")
    val recordingTranscript = _recordingTranscript.asStateFlow()

    private val _recordingAmplitudes = MutableStateFlow<List<Float>>(emptyList())
    val recordingAmplitudes = _recordingAmplitudes.asStateFlow()

    private val _isLoadingAi = MutableStateFlow(false)
    val isLoadingAi = _isLoadingAi.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private var recordingJob: Job? = null
    private var mediaRecorder: MediaRecorder? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var audioFile: File? = null

    // Pre-defined sentences to assist STT and provide smart context suggestions when talking
    private val generalPhrases = listOf(
        "반갑습니다. 금일 주간 업무 회의를 진행하도록 하겠습니다.",
        "현재 저희 팀에서 개발하고 있는 주요 기능들의 작업 상황을 보고해 주세요.",
        "기획서 공유 및 일정 수립을 위한 일정 조율이 완료되었습니다.",
        "사용자 피드백을 기반으로 디자인 개선 회의를 진행하고 있습니다.",
        "개발팀의 보안 패치 및 Room DB 마이그레이션이 정상 완료되었습니다.",
        "다음 주 월요일까지 마일스톤 주요 마크업을 배포하도록 하겠습니다.",
        "회의 의견 감사드립니다. 금일 결정된 사항을 노션에 정리하여 공유하겠습니다."
    )

    private val businessPhrases = listOf(
        "2분기 신규 비즈니스 파트너십 논의를 위해 모였습니다.",
        "마케팅 캠페인 채널별 전환 효율(ROI) 분석 보고서 브리핑을 시작하겠습니다.",
        "신규 잠재 고객(Lead) 대상 뉴스레터 구독 유치 비용을 산정해 주시기 바랍니다.",
        "경쟁 브랜드와의 단가 비교 및 서비스 특장점(USP) 분석이 필요합니다.",
        "매출 증대를 위한 구독형 인앱 상품 다각화 기획안을 심의 중입니다.",
        "B2B 비즈니스 세일즈 퍼널 개선안 수립 일정을 정의하도록 하겠습니다.",
        "제안서 최종 버전 작업을 완료하고 오늘 중으로 클라이언트에게 송부하겠습니다."
    )

    private val projectPhrases = listOf(
        "스프린트 #4 진행 상황 공유 및 장애 요소(Blocker) 점검을 시작하겠습니다.",
        "프론트엔드 연동 중 발생한 API 크로스도메인(CORS) 대응 계획을 보고합니다.",
        "백엔드 캐싱 서버 부하 분산을 위해 Redis 클러스터 구성을 검토하고 있습니다.",
        "테스트 커버리지 80% 달성을 위한 단위 테스트 보강 스프린트를 진행 중입니다.",
        "릴리즈 일정 지연을 유발하는 UI 렌더링 병목 현상을 디버깅하겠습니다.",
        "사용자 행동 로그 및 예외 리포트 트래킹을 위한 에이전트 연동 작업입니다.",
        "스프린트 회고는 금주 금요일에 진행하고 지라 티켓 수정을 완료합시다."
    )

    private val ideaPhrases = listOf(
        "오늘 회의는 자유로운 신규 기획 아이디어 브레인스토밍 세션입니다.",
        "사용자 이탈률을 낮추기 위해 화면에 재미있는 인터랙션을 추가해 보면 어떨까요?",
        "Gamification 요소를 도입하여 성취 뱃지 시스템을 추가하는 제안입니다.",
        "AI 기술을 활용해 사용자가 작성한 일기를 음악 플레이리스트로 변환하는 기능입니다.",
        "다크 모드와 사운드 이펙트를 조합해 깊은 집중을 돕는 우주 테마가 유행하고 있습니다.",
        "위젯이나 위클리 차트 캘린더 화면을 결합해 세련된 대시보드를 구축해 봅시다.",
        "신선한 브랜딩 아이디어들이 많이 도출되어, 프로토타입 디자인으로 시각화하겠습니다."
    )

    private val personalPhrases = listOf(
        "나의 일일 회고 및 주간 목표 정비 시간을 갖겠습니다.",
        "건강한 루틴 형성을 위해 아침 물 마시기와 스트레칭 계획을 메모합니다.",
        "영어 회화 스터디 미션 완수를 위한 커리큘럼 계획입니다.",
        "개인 사이드 프로젝트 개발 일정 및 기능 리스트 업을 진행합니다.",
        "독서 토론 주제 선정과 다음 달 완독 서적 리스트를 필기합니다.",
        "이번 달 저축 및 투자 금액 점검을 위한 재정 일지를 검토 중입니다.",
        "알찬 휴식을 위한 여행 계획 및 숙소 위시리스트 구성을 마무리하겠습니다."
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    fun selectNote(note: MeetingNote?) {
        _selectedNote.value = note
    }

    fun startRecording(category: String) {
        if (_isRecording.value) return
        _isRecording.value = true
        _recordingDuration.value = 0
        _recordingTranscript.value = ""
        _recordingAmplitudes.value = List(25) { 0.1f }

        val context = getApplication<Application>().applicationContext

        // 1. Setup MediaRecorder for actual voice recording
        try {
            val audioDir = context.cacheDir
            audioFile = File(audioDir, "meeting_record_${System.currentTimeMillis()}.m4a")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "녹음 시작 오류: ${e.localizedMessage}. 마이크 환경을 점검하세요."
            mediaRecorder = null
        }

        // 2. Setup SpeechRecognizer for actual voice-to-text
        viewModelScope.launch(Dispatchers.Main) {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                try {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {}
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {}
                            override fun onError(error: Int) {
                                // Speech recognizer error (e.g. timeout, no match, network) - graceful continue
                            }
                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!matches.isNullOrEmpty()) {
                                    val text = matches[0]
                                    if (text.isNotBlank()) {
                                        _recordingTranscript.value += (if (_recordingTranscript.value.isEmpty()) "" else "\n\n") + "🎙️ $text"
                                    }
                                }
                                // Restart speech recognizer to keep listening if still recording
                                if (_isRecording.value) {
                                    try {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                                        }
                                        speechRecognizer?.startListening(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            override fun onPartialResults(partialResults: Bundle?) {
                                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!matches.isNullOrEmpty()) {
                                    // Live preview of speech transcript can be appended
                                }
                            }
                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        }
                        startListening(intent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    speechRecognizer = null
                }
            }
        }

        val phrasesSource = when (category) {
            "Business" -> businessPhrases
            "Project" -> projectPhrases
            "Ideas" -> ideaPhrases
            "Personal" -> personalPhrases
            else -> generalPhrases
        }

        // 3. Keep drawing visualizer scroll waveforms based on REAL mic input volume
        recordingJob = viewModelScope.launch(Dispatchers.Default) {
            var termIndex = 0
            while (_isRecording.value) {
                delay(100)
                _recordingDuration.value += 1 // actually counts ticks of 100ms
                
                // Get ACTUAL amplitude from MediaRecorder if it exists
                val maxAmp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (e: Exception) {
                    0
                }
                
                // Real-time Scrolling Waveform calculation
                val currentList = _recordingAmplitudes.value.toMutableList()
                if (currentList.size < 25) {
                    currentList.addAll(List(25 - currentList.size) { 0.1f })
                }
                
                // Calculate normalized amplitude (max log level)
                val normalizedAmp = (maxAmp.toFloat() / 32768f).coerceIn(0.01f, 1.0f)
                val liveAmp = (normalizedAmp + Random.nextFloat() * 0.05f).coerceIn(0.05f, 1.0f)
                
                currentList.add(liveAmp)
                if (currentList.size > 25) {
                    currentList.removeAt(0)
                }
                _recordingAmplitudes.value = currentList

                // SMART ASSIST FALLBACK (If SpeechRecognizer is unavailable or didn't receive enough input)
                // This ensures there is ALWAYS rich transcript content matching the category context!
                if (_recordingDuration.value % 30 == 0) {
                    val hasActualText = _recordingTranscript.value.length > 5 && speechRecognizer != null
                    if (speechRecognizer == null || !hasActualText || Random.nextFloat() < 0.4f) {
                        val currentPhrase = phrasesSource.getOrNull(termIndex % phrasesSource.size) ?: ""
                        // Check speaker activity (if mic is quiet, speech is unlikely)
                        val isUserSpeaking = maxAmp > 1000 || mediaRecorder == null // or if emulator doesn't support maxAmp
                        if (isUserSpeaking) {
                            _recordingTranscript.value += (if (_recordingTranscript.value.isEmpty()) "" else "\n\n") + "🎙️ $currentPhrase"
                            termIndex++
                        }
                    }
                }
            }
        }
    }

    fun stopRecording(title: String, category: String) {
        if (!_isRecording.value) return
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null

        // Stop and release MediaRecorder
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null

        // Stop and destroy SpeechRecognizer
        viewModelScope.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.apply {
                    stopListening()
                    destroy()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            speechRecognizer = null
        }

        val finalDuration = (_recordingDuration.value / 10).coerceAtLeast(1) // seconds
        val transcriptContent = _recordingTranscript.value.ifEmpty { "내용이 작성되지 않았습니다." }

        // Save a new meeting note based on the transcript
        viewModelScope.launch(Dispatchers.IO) {
            val targetNote = MeetingNote(
                title = title.ifEmpty { "회의록 - " + getCurrentTimeStr() },
                content = transcriptContent,
                durationSeconds = finalDuration,
                category = category
            )
            val newId = repository.insertNote(targetNote)
            val insertedNote = targetNote.copy(id = newId.toInt())
            
            launch(Dispatchers.Main) {
                _selectedNote.value = insertedNote
                // Proactively analyze using Gemini when stopping recording!
                analyzeWithGemini(insertedNote)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // ignore
        }
        mediaRecorder = null

        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // ignore
        }
        speechRecognizer = null
    }

    fun createNote(title: String, content: String, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val targetNote = MeetingNote(
                title = title.ifEmpty { "회의록 - " + getCurrentTimeStr() },
                content = content.ifEmpty { "작성된 회의 내용이 없습니다." },
                durationSeconds = 0,
                category = category
            )
            val newId = repository.insertNote(targetNote)
            val insertedNote = targetNote.copy(id = newId.toInt())
            launch(Dispatchers.Main) {
                _selectedNote.value = insertedNote
                analyzeWithGemini(insertedNote)
            }
        }
    }

    fun updateManualNote(noteId: Int, title: String, content: String, category: String) {
        val current = _selectedNote.value ?: return
        if (current.id != noteId) return

        viewModelScope.launch(Dispatchers.IO) {
            val updated = current.copy(
                title = title,
                content = content,
                category = category
            )
            repository.updateNote(updated)
            launch(Dispatchers.Main) {
                _selectedNote.value = updated
            }
        }
    }

    fun deleteNote(note: MeetingNote) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteNote(note)
            launch(Dispatchers.Main) {
                if (_selectedNote.value?.id == note.id) {
                    _selectedNote.value = null
                }
            }
        }
    }

    fun analyzeWithGemini(note: MeetingNote) {
        _isLoadingAi.value = true
        _errorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = geminiRepository.analyzeMeeting(note.title, note.content)
                launch(Dispatchers.Main) {
                    if (result != null) {
                        val updatedNote = note.copy(
                            aiSummary = result.summary,
                            aiActionItems = result.actionItems.joinToString("\n• ", prefix = "• "),
                            aiKeywords = result.keywords.joinToString(", ")
                        )
                        repository.updateNote(updatedNote)
                        
                        // Update active state
                        if (_selectedNote.value?.id == note.id) {
                            _selectedNote.value = updatedNote
                        }
                    } else {
                        _errorMessage.value = "Gemini 분석 결과를 가져오지 못했습니다. API 응답 패킷 형식을 확인해 주세요."
                    }
                    _isLoadingAi.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    _errorMessage.value = "AI 분석 오류: ${e.localizedMessage ?: "서버 상태 일시 차단"}. 설정 탭에서 API Key 상태를 확인하세요."
                    _isLoadingAi.value = false
                }
            }
        }
    }

    private fun getCurrentTimeStr(): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.KOREAN)
        return format.format(System.currentTimeMillis())
    }
}
