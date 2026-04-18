package jp.linkserver.nittcsc.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.net.Uri
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import jp.linkserver.nittcsc.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import jp.linkserver.nittcsc.ml.*
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

data class VlmModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val assets: List<ModelAsset>
)

data class ModelAsset(
    val fileName: String,
    val sizeLabel: String,
    val downloadUrl: String
)

private fun VlmModelInfo.primaryFileName(): String = assets.first().fileName

val MODEL_FAMILIES = mapOf(
    "SmolVLM2" to listOf(
        VlmModelInfo(
            id = "smolvlm2_256m",
            name = "SmolVLM2 256M",
            description = "~279 MB",
            assets = listOf(
                ModelAsset(
                    "SmolVLM2-256M-Video-Instruct-Q8_0.gguf",
                    "175 MB",
                    "https://huggingface.co/ggml-org/SmolVLM2-256M-Video-Instruct-GGUF/resolve/main/SmolVLM2-256M-Video-Instruct-Q8_0.gguf"
                ),
                ModelAsset(
                    "mmproj-SmolVLM2-256M-Video-Instruct-Q8_0.gguf",
                    "104 MB",
                    "https://huggingface.co/ggml-org/SmolVLM2-256M-Video-Instruct-GGUF/resolve/main/mmproj-SmolVLM2-256M-Video-Instruct-Q8_0.gguf"
                )
            )
        ),
        VlmModelInfo(
            id = "smolvlm2_500m",
            name = "SmolVLM2 500M",
            description = "~546 MB",
            assets = listOf(
                ModelAsset(
                    "SmolVLM2-500M-Video-Instruct-Q8_0.gguf",
                    "437 MB",
                    "https://huggingface.co/ggml-org/SmolVLM2-500M-Video-Instruct-GGUF/resolve/main/SmolVLM2-500M-Video-Instruct-Q8_0.gguf"
                ),
                ModelAsset(
                    "mmproj-SmolVLM2-500M-Video-Instruct-Q8_0.gguf",
                    "109 MB",
                    "https://huggingface.co/ggml-org/SmolVLM2-500M-Video-Instruct-GGUF/resolve/main/mmproj-SmolVLM2-500M-Video-Instruct-Q8_0.gguf"
                )
            )
        ),
        VlmModelInfo(
            id = "smolvlm2_2.2b",
            name = "SmolVLM2 2.2B",
            description = "~1.7 GB",
            assets = listOf(
                ModelAsset(
                    "SmolVLM2-2.2B-Instruct-Q4_K_M.gguf",
                    "1.11 GB",
                    "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main/SmolVLM2-2.2B-Instruct-Q4_K_M.gguf"
                ),
                ModelAsset(
                    "mmproj-SmolVLM2-2.2B-Instruct-Q8_0.gguf",
                    "593 MB",
                    "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main/mmproj-SmolVLM2-2.2B-Instruct-Q8_0.gguf"
                )
            )
        )
    ),
    "Gemma" to listOf(
        VlmModelInfo(
            id = "gemma4_e2b",
            name = "Gemma 4 E2B",
            description = "~3.7 GB",
            assets = listOf(
                ModelAsset(
                    "gemma-4-E2B-it-Q4_K_M.gguf",
                    "2.46 GB",
                    "https://huggingface.co/bartowski/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q4_K_M.gguf"
                ),
                ModelAsset(
                    "mmproj-gemma-4-E2B-it-f16.gguf",
                    "1.24 GB",
                    "https://huggingface.co/bartowski/gemma-4-E2B-it-GGUF/resolve/main/mmproj-gemma-4-E2B-it-f16.gguf"
                )
            )
        ),
        VlmModelInfo(
            id = "gemma4_e4b",
            name = "Gemma 4 E4B-it",
            description = "~14.5 GB",
            assets = listOf(
                ModelAsset(
                    "gemma-4-E4B-it-Q4_K_M.gguf",
                    "10.2 GB",
                    "https://huggingface.co/bartowski/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q4_K_M.gguf"
                ),
                ModelAsset(
                    "mmproj-gemma-4-E4B-it-f16.gguf",
                    "4.3 GB",
                    "https://huggingface.co/bartowski/gemma-4-E4B-it-GGUF/resolve/main/mmproj-gemma-4-E4B-it-f16.gguf"
                )
            )
        ),
        VlmModelInfo(
            id = "gemma3_270m",
            name = "Gemma 3 270M",
            description = "292 MB",
            assets = listOf(
                ModelAsset(
                    "gemma-3-270m-it-Q8_0.gguf",
                    "292 MB",
                    "https://huggingface.co/ggml-org/gemma-3-270m-it-GGUF/resolve/main/gemma-3-270m-it-Q8_0.gguf"
                )
            )
        )
    ),
    "Qwen 3.5" to listOf(
        VlmModelInfo(
            id = "qwen3.5_0.8b",
            name = "Qwen 3.5 0.8B",
            description = "~1.7 GB",
            assets = listOf(
                ModelAsset(
                    "Qwen_Qwen3.5-0.8B-Q4_K_M.gguf",
                    "0.6 GB",
                    "https://huggingface.co/bartowski/Qwen_Qwen3.5-0.8B-GGUF/resolve/main/Qwen_Qwen3.5-0.8B-Q4_K_M.gguf"
                ),
                ModelAsset(
                    "mmproj-Qwen_Qwen3.5-0.8B-f16.gguf",
                    "1.1 GB",
                    "https://huggingface.co/bartowski/Qwen_Qwen3.5-0.8B-GGUF/resolve/main/mmproj-Qwen_Qwen3.5-0.8B-f16.gguf"
                )
            )
        ),
        VlmModelInfo(
            id = "qwen3.5_2b",
            name = "Qwen 3.5 2B",
            description = "~4.0 GB",
            assets = listOf(
                ModelAsset(
                    "Qwen_Qwen3.5-2B-Q4_K_M.gguf",
                    "1.4 GB",
                    "https://huggingface.co/bartowski/Qwen_Qwen3.5-2B-GGUF/resolve/main/Qwen_Qwen3.5-2B-Q4_K_M.gguf"
                ),
                ModelAsset(
                    "mmproj-Qwen_Qwen3.5-2B-f16.gguf",
                    "2.6 GB",
                    "https://huggingface.co/bartowski/Qwen_Qwen3.5-2B-GGUF/resolve/main/mmproj-Qwen_Qwen3.5-2B-f16.gguf"
                )
            )
        ),
        VlmModelInfo(
            id = "qwen3.5_4b",
            name = "Qwen 3.5 4B",
            description = "~7.5 GB",
            assets = listOf(
                ModelAsset(
                    "Qwen_Qwen3.5-4B-Q4_K_M.gguf",
                    "2.7 GB",
                    "https://huggingface.co/bartowski/Qwen_Qwen3.5-4B-GGUF/resolve/main/Qwen_Qwen3.5-4B-Q4_K_M.gguf"
                ),
                ModelAsset(
                    "mmproj-Qwen_Qwen3.5-4B-f16.gguf",
                    "4.8 GB",
                    "https://huggingface.co/bartowski/Qwen_Qwen3.5-4B-GGUF/resolve/main/mmproj-Qwen_Qwen3.5-4B-f16.gguf"
                )
            )
        ),
        VlmModelInfo(
            id = "qwen3.5_9b",
            name = "Qwen 3.5 9B",
            description = "~17.5 GB",
            assets = listOf(
                ModelAsset(
                    "Qwen_Qwen3.5-9B-Q4_K_M.gguf",
                    "6.2 GB",
                    "https://huggingface.co/bartowski/Qwen_Qwen3.5-9B-GGUF/resolve/main/Qwen_Qwen3.5-9B-Q4_K_M.gguf"
                ),
                ModelAsset(
                    "mmproj-Qwen_Qwen3.5-9B-f16.gguf",
                    "11.3 GB",
                    "https://huggingface.co/bartowski/Qwen_Qwen3.5-9B-GGUF/resolve/main/mmproj-Qwen_Qwen3.5-9B-f16.gguf"
                )
            )
        )
    )
)

class VlmDownloadViewModel(application: Application) : AndroidViewModel(application) {
    val downloadStates = mutableStateMapOf<String, DownloadState>()
    private val workManager = WorkManager.getInstance(application)
    private val observeJobs = mutableMapOf<String, Job>()
    var hfToken by mutableStateOf("")

    fun startDownload(model: VlmModelInfo, token: String?) {
        if (downloadStates[model.id] is DownloadState.Downloading) return
        val manager = ModelDownloadManager(getApplication())
        val requests = model.assets.map { asset ->
            manager.buildDownloadRequest(asset.downloadUrl, asset.fileName, token, model.id)
        }
        if (requests.isEmpty()) return

        var continuation = workManager.beginUniqueWork(
            "model_dl_${model.id}",
            ExistingWorkPolicy.KEEP,
            requests.first()
        )
        for (i in 1 until requests.size) {
            continuation = continuation.then(requests[i])
        }
        continuation.enqueue()
        observeModel(model.id, model.assets.size)
    }

    fun reconnectObservers(models: List<VlmModelInfo>) {
        models.forEach { model ->
            if (observeJobs[model.id]?.isActive != true) {
                observeModel(model.id, model.assets.size)
            }
        }
    }

    private fun observeModel(modelId: String, assetCount: Int) {
        observeJobs[modelId]?.cancel()
        observeJobs[modelId] = viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow("model_dl_$modelId")
                .collect { workInfos ->
                    if (workInfos.isEmpty()) return@collect
                    val running = workInfos.filter { it.state == WorkInfo.State.RUNNING }
                    val succeeded = workInfos.filter { it.state == WorkInfo.State.SUCCEEDED }
                    val activeCount = workInfos.count {
                        it.state in setOf(
                            WorkInfo.State.RUNNING,
                            WorkInfo.State.ENQUEUED,
                            WorkInfo.State.BLOCKED
                        )
                    }
                    when {
                        workInfos.any { it.state == WorkInfo.State.FAILED } ->
                            downloadStates[modelId] = DownloadState.Error(Exception("ダウンロードに失敗しました"))
                        workInfos.any { it.state == WorkInfo.State.CANCELLED } ->
                            downloadStates[modelId] = DownloadState.Idle
                        activeCount == 0 && succeeded.isNotEmpty() ->
                            downloadStates[modelId] = DownloadState.Success
                        activeCount > 0 -> {
                            val runningInfo = running.firstOrNull()
                            val fileProgress = runningInfo?.progress?.getInt("progress", 0) ?: 0
                            val speedMbps = runningInfo?.progress?.getFloat("speed_mbps", 0f) ?: 0f
                            val total = workInfos.size
                            val totalProgress =
                                if (total > 0) (succeeded.size + fileProgress / 100f) / total else 0f
                            downloadStates[modelId] = DownloadState.Downloading(
                                totalProgress.coerceIn(0f, 1f),
                                speedMbps
                            )
                        }
                    }
                }
        }
    }

    fun cancelDownload(modelId: String) {
        workManager.cancelUniqueWork("model_dl_$modelId")
        observeJobs[modelId]?.cancel()
        observeJobs.remove(modelId)
        downloadStates[modelId] = DownloadState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        observeJobs.values.forEach { it.cancel() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VlmImportScreen(
    hfToken: String?,
    onUpdateHfToken: (String?) -> Unit,
    onBack: () -> Unit,
    onLessonsGenerated: (List<LessonDraftWithSlot>) -> Unit,
    onAbTableGenerated: (Map<String, jp.linkserver.nittcsc.data.DayType>) -> Unit = {},
    existingLessons: Map<Pair<Int, Int>, LessonEntity> = emptyMap(),
    existingDayTypeMap: Map<java.time.LocalDate, jp.linkserver.nittcsc.data.DayType> = emptyMap(),
    state: SchedulerUiState? = null,
    downloadViewModel: VlmDownloadViewModel = viewModel()
) {
    val context = LocalContext.current
    val downloadManager = remember { ModelDownloadManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var downloadedModels by remember { mutableStateOf(downloadManager.getDownloadedModels().map { it.name }) }

    // ダウンロード完了時にファイル一覧を更新
    val downloadStatesSnapshot = downloadViewModel.downloadStates.values.count { it is DownloadState.Success }
    LaunchedEffect(downloadStatesSnapshot) {
        if (downloadStatesSnapshot > 0) {
            downloadedModels = downloadManager.getDownloadedModels().map { it.name }
        }
    }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var inferenceResult by remember { mutableStateOf<String?>(null) }
    var isEditingResult by remember { mutableStateOf(false) }
    var editedResultText by remember { mutableStateOf("") }
    var debugText by remember { mutableStateOf<String?>(null) }
    var showDebugDetail by remember { mutableStateOf(false) }
    var parsedLessons by remember { mutableStateOf<List<LessonDraftWithSlot>>(emptyList()) }
    var activeModelFile by remember { mutableStateOf<File?>(null) }
    val inferenceEngine = remember { VlmInferenceEngine(context) }
    var showCancelButton by remember { mutableStateOf(false) }
    
    val expandedFamilies = remember { mutableStateMapOf<String, Boolean>() }
    
    var parsingStatus by remember { mutableStateOf("") }
    var isParsing by remember { mutableStateOf(false) }
    var showSelectionDialog by remember { mutableStateOf(false) }
    var isPendingAbMode by remember { mutableStateOf(false) }
    var parsedAbTable by remember { mutableStateOf<Map<String, jp.linkserver.nittcsc.data.DayType>>(emptyMap()) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }
    var modelToDelete by remember { mutableStateOf<VlmModelInfo?>(null) }
    var showModelMenu by remember { mutableStateOf(false) }
    var jsonCorrectionModelFile by remember { mutableStateOf<File?>(null) }
    var disableJsonCorrection by remember { mutableStateOf(false) }

    val allModels = MODEL_FAMILIES.values.flatten()
    val allKnownFileNames = allModels.flatMap { model -> model.assets.map { it.fileName } }.toSet()
    val knownPrimaryFileNames = allModels.map { it.primaryFileName() }.toSet()
    val recognizedModels = downloadedModels.filter { it in knownPrimaryFileNames }
    val unknownModels = downloadedModels.filter { it !in allKnownFileNames }

    // アプリ再起動後も進行中ダウンロードを監視再接続
    LaunchedEffect(Unit) {
        downloadViewModel.reconnectObservers(allModels)
    }

    // 初期化時に最初のダウンロード済み（かつ認識されている）モデルをアクティブにする
    LaunchedEffect(recognizedModels) {
        if (activeModelFile == null && recognizedModels.isNotEmpty()) {
            activeModelFile = File(context.filesDir, "models/${recognizedModels.first()}")
        }
    }

    fun parseImage(uri: Uri, isAbTable: Boolean) {
        val modelToUse = activeModelFile ?: recognizedModels.firstOrNull()?.let { File(context.filesDir, "models/$it") }
        if (modelToUse == null) {
            inferenceResult = "エラー: ローカルモデルが選択されていません。モデルをダウンロードしてください。"
            return
        }
        activeModelFile = modelToUse
        val selectedModelInfo = allModels.find { it.primaryFileName() == modelToUse.name }
        val mmprojAsset = selectedModelInfo?.assets?.firstOrNull { it.fileName.startsWith("mmproj", ignoreCase = true) }
        val mmprojFile = mmprojAsset?.let { File(context.filesDir, "models/${it.fileName}") }?.takeIf { it.exists() }
        selectedImageUri = uri
        isParsing = true
        showCancelButton = true
        parsingStatus = ""
        inferenceResult = null
        parsedLessons = emptyList()
        parsedAbTable = emptyMap()
        
        // Foreground Service を開始
        val serviceIntent = android.content.Intent(context, jp.linkserver.nittcsc.ml.VlmInferenceService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        
        coroutineScope.launch {
            try {
                val correctionModelToUse = if (disableJsonCorrection) null else jsonCorrectionModelFile
                val correctionModelInfo = allModels.find { it.primaryFileName() == correctionModelToUse?.name }
                val correctionMmprojAsset = correctionModelInfo?.assets?.firstOrNull { it.fileName.startsWith("mmproj", ignoreCase = true) }
                val correctionMmprojFile = correctionMmprojAsset?.let { File(context.filesDir, "models/${it.fileName}") }?.takeIf { it.exists() }
                
                val fullResult = inferenceEngine.extractDataFromJsonVlm(
                    modelFile = modelToUse,
                    mmprojFile = mmprojFile,
                    imageUri = uri,
                    isAbTable = isAbTable,
                    disableJsonCorrection = disableJsonCorrection,
                    jsonCorrectionModelFile = correctionModelToUse,
                    jsonCorrectionMmprojFile = correctionMmprojFile,
                    onStatusUpdate = { parsingStatus = it }
                )
                val jsonOutput = fullResult.jsonOutput
                inferenceResult = jsonOutput
                debugText = fullResult.debugText
                
                if (isAbTable) {
                    try {
                        val root = org.json.JSONObject(jsonOutput)
                        val array = root.optJSONArray("dayTypes") ?: org.json.JSONArray()
                        val dayTypeResult = mutableMapOf<String, jp.linkserver.nittcsc.data.DayType>()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val dateStr = obj.getString("date")
                            val dayTypeStr = obj.getString("dayType")
                            try {
                                val dayType = jp.linkserver.nittcsc.data.DayType.valueOf(dayTypeStr)
                                dayTypeResult[dateStr] = dayType
                            } catch (_: Exception) {
                                // Invalid dayType, skip this entry
                            }
                        }
                        parsedAbTable = dayTypeResult
                        parsedLessons = emptyList()
                    } catch (e: Exception) { e.printStackTrace() }
                } else {
                    parsedLessons = inferenceEngine.parseLessonsFromJson(jsonOutput)
                    parsedAbTable = emptyMap()
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "未知のエラーが発生しました"
                inferenceResult = "エラー: $errorMsg"
                android.util.Log.e("VlmImportScreen", "推論エラー: ", e)
            } finally {
                isParsing = false
                showCancelButton = false
                // Service を停止
                val stopIntent = android.content.Intent(context, jp.linkserver.nittcsc.ml.VlmInferenceService::class.java)
                context.stopService(stopIntent)
            }
        }
    }

    fun reparseResult(editedJson: String) {
        if (isPendingAbMode) {
            try {
                val root = org.json.JSONObject(editedJson)
                val array = root.optJSONArray("dayTypes") ?: org.json.JSONArray()
                val dayTypeResult = mutableMapOf<String, jp.linkserver.nittcsc.data.DayType>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val dateStr = obj.getString("date")
                    val dayTypeStr = obj.getString("dayType")
                    try {
                        val dayType = jp.linkserver.nittcsc.data.DayType.valueOf(dayTypeStr)
                        dayTypeResult[dateStr] = dayType
                    } catch (_: Exception) { }
                }
                parsedAbTable = dayTypeResult
                parsedLessons = emptyList()
            } catch (e: Exception) { e.printStackTrace() }
        } else {
            parsedLessons = inferenceEngine.parseLessonsFromJson(editedJson)
            parsedAbTable = emptyMap()
        }
    }

    fun applyImport() {
        if (isPendingAbMode) {
            onAbTableGenerated(parsedAbTable)
        } else {
            onLessonsGenerated(parsedLessons)
        }
        onBack()
    }

    fun checkAndApply() {
        val hasConflict = if (isPendingAbMode) {
            parsedAbTable.keys.any { dateStr ->
                try {
                    val date = java.time.LocalDate.parse(dateStr)
                    val existing = existingDayTypeMap[date]
                    existing != null && existing != jp.linkserver.nittcsc.data.DayType.HOLIDAY
                } catch (e: Exception) { false }
            }
        } else {
            parsedLessons.any { lesson ->
                val existing = existingLessons[lesson.dayOfWeek to lesson.slotIndex]
                existing != null && (existing.weeklySubject.isNotBlank() || existing.aSubject.isNotBlank() || existing.bSubject.isNotBlank())
            }
        }

        if (hasConflict) {
            showOverwriteConfirm = true
        } else {
            applyImport()
        }
    }

    val cameraImageFile = remember { File(context.cacheDir, "camera_capture.jpg") }
    val cameraImageUri = remember { 
        // 念のためディレクトリを作成
        cameraImageFile.parentFile?.mkdirs()
        FileProvider.getUriForFile(context, "${context.packageName}.provider", cameraImageFile)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                parseImage(cameraImageUri, isPendingAbMode)
            }
        }
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(cameraImageUri)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                parseImage(uri, isPendingAbMode)
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_ai_import)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (downloadedModels.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.title_ai_center), style = MaterialTheme.typography.titleMedium)

                            val currentModelName = allModels.find { it.primaryFileName() == activeModelFile?.name }?.name
                                ?: activeModelFile?.name
                                ?: stringResource(R.string.label_no_model_selected)

                            Box {
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { if (recognizedModels.size > 1) showModelMenu = true },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(stringResource(R.string.label_model_to_use), style = MaterialTheme.typography.labelSmall)
                                            Text(currentModelName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        }
                                        if (recognizedModels.size > 1) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.desc_switch_models))
                                        }
                                    }
                                }

                                if (recognizedModels.size > 1) {
                                    DropdownMenu(
                                        expanded = showModelMenu,
                                        onDismissRequest = { showModelMenu = false },
                                        modifier = Modifier.fillMaxWidth(0.8f)
                                    ) {
                                        recognizedModels.forEach { fileName ->
                                            val displayName = allModels.find { it.primaryFileName() == fileName }?.name ?: fileName
                                            DropdownMenuItem(
                                                text = { Text(displayName) },
                                                onClick = {
                                                    activeModelFile = File(context.filesDir, "models/$fileName")
                                                    showModelMenu = false
                                                },
                                                leadingIcon = {
                                                    if (activeModelFile?.name == fileName) {
                                                        Icon(Icons.Default.Check, contentDescription = null)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // JSON校正用モデル選択
                            var showJsonCorrectionModelMenu by remember { mutableStateOf(false) }
                            val jsonCorrectionModelName = when {
                                disableJsonCorrection -> stringResource(R.string.menu_no_json_correction)
                                jsonCorrectionModelFile != null -> jsonCorrectionModelFile?.let { file ->
                                    allModels.find { it.primaryFileName() == file.name }?.name ?: file.name
                                } ?: stringResource(R.string.menu_same_as_main)
                                else -> stringResource(R.string.menu_same_as_main)
                            }

                            Box {
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { if (recognizedModels.isNotEmpty()) showJsonCorrectionModelMenu = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(stringResource(R.string.label_json_correction_model), style = MaterialTheme.typography.labelSmall)
                                            Text(jsonCorrectionModelName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        }
                                        if (recognizedModels.isNotEmpty()) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.desc_switch_models))
                                        }
                                    }
                                }

                                if (recognizedModels.isNotEmpty()) {
                                    DropdownMenu(
                                        expanded = showJsonCorrectionModelMenu,
                                        onDismissRequest = { showJsonCorrectionModelMenu = false },
                                        modifier = Modifier.fillMaxWidth(0.8f)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_same_as_main)) },
                                            onClick = {
                                                disableJsonCorrection = false
                                                jsonCorrectionModelFile = null
                                                showJsonCorrectionModelMenu = false
                                            },
                                            leadingIcon = {
                                                if (!disableJsonCorrection && jsonCorrectionModelFile == null) {
                                                    Icon(Icons.Default.Check, contentDescription = null)
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_no_json_correction)) },
                                            onClick = {
                                                disableJsonCorrection = true
                                                jsonCorrectionModelFile = null
                                                showJsonCorrectionModelMenu = false
                                            },
                                            leadingIcon = {
                                                if (disableJsonCorrection) {
                                                    Icon(Icons.Default.Check, contentDescription = null)
                                                }
                                            }
                                        )
                                        Divider()
                                        recognizedModels.forEach { fileName ->
                                            val displayName = allModels.find { it.primaryFileName() == fileName }?.name ?: fileName
                                            DropdownMenuItem(
                                                text = { Text(displayName) },
                                                onClick = {
                                                    disableJsonCorrection = false
                                                    jsonCorrectionModelFile = File(context.filesDir, "models/$fileName")
                                                    showJsonCorrectionModelMenu = false
                                                },
                                                leadingIcon = {
                                                    if (!disableJsonCorrection && jsonCorrectionModelFile?.name == fileName) {
                                                        Icon(Icons.Default.Check, contentDescription = null)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { 
                                        isPendingAbMode = false 
                                        showSelectionDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.TableChart, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.btn_timetable))
                                }
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { 
                                        isPendingAbMode = true
                                        showSelectionDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.btn_ab_table))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.msg_offline_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (hfToken.isNullOrEmpty()) {
                        OutlinedTextField(
                            value = downloadViewModel.hfToken,
                            onValueChange = { downloadViewModel.hfToken = it },
                            label = { Text(stringResource(R.string.label_hf_token)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = clipboard.primaryClip
                                        if (clip != null && clip.itemCount > 0) {
                                            downloadViewModel.hfToken = clip.getItemAt(0).text.toString()
                                        }
                                    }) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = stringResource(R.string.btn_paste))
                                    }
                                    if (downloadViewModel.hfToken.isNotEmpty()) {
                                        IconButton(onClick = { onUpdateHfToken(downloadViewModel.hfToken) }) {
                                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.btn_save))
                                        }
                                    }
                                }
                            }
                        )
                    } else {
                        val maskedToken = remember(hfToken) {
                            if (hfToken.length > 6) {
                                hfToken.take(2) + "••••" + hfToken.takeLast(4)
                            } else {
                                "••••"
                            }
                        }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.msg_token_configured), style = MaterialTheme.typography.labelMedium)
                                    Text(maskedToken, style = MaterialTheme.typography.bodyLarge)
                                }
                                IconButton(onClick = { onUpdateHfToken(null) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete_token), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://huggingface.co/settings/tokens"))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.btn_open_token_page))
                        }
                    }
                }
            }
            
            MODEL_FAMILIES.forEach { (family, models) ->
                item {
                    val isExpanded = expandedFamilies[family] ?: false
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { expandedFamilies[family] = !isExpanded },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(family, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) stringResource(R.string.desc_close) else stringResource(R.string.desc_expand)
                            )
                        }
                    }
                }
                if (expandedFamilies[family] == true) {
                    items(models) { model ->
                        val isDownloaded = model.assets.all { downloadedModels.contains(it.fileName) }
                        val downloadState = downloadViewModel.downloadStates[model.id] ?: DownloadState.Idle
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(model.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = stringResource(R.string.label_model_size_files, model.description, model.assets.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                model.assets.forEach { asset ->
                                    Text(
                                        text = "- ${asset.fileName} (${asset.sizeLabel})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                when (downloadState) {
                                    is DownloadState.Downloading -> {
                                        LinearProgressIndicator(
                                            progress = { downloadState.progress },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(stringResource(R.string.label_download_progress_info, (downloadState.progress * 100).toInt(), downloadState.speedMbps), style = MaterialTheme.typography.bodySmall)
                                            TextButton(onClick = { downloadViewModel.cancelDownload(model.id) }) { 
                                                Text(stringResource(R.string.btn_cancel), color = MaterialTheme.colorScheme.error) 
                                            }
                                        }
                                    }
                                    is DownloadState.Error -> {
                                        Text(stringResource(R.string.label_download_error, downloadState.exception.localizedMessage ?: ""), color = MaterialTheme.colorScheme.error)
                                        Button(onClick = { downloadViewModel.cancelDownload(model.id) }) { Text(stringResource(R.string.btn_reset)) }
                                    }
                                    else -> {
                                        if (isDownloaded) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TextButton(onClick = {
                                                    modelToDelete = model
                                                }) { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) }
                                            }
                                        } else {
                                            Button(onClick = {
                                                downloadViewModel.startDownload(model, hfToken)
                                            }) { Text(stringResource(R.string.btn_download)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (unknownModels.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.msg_old_models_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text(stringResource(R.string.msg_old_models_desc), style = MaterialTheme.typography.bodySmall)
                            
                            unknownModels.forEach { fileName ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(fileName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    IconButton(onClick = {
                                        downloadManager.deleteModel(fileName)
                                        downloadedModels = downloadManager.getDownloadedModels().map { it.name }
                                        if (activeModelFile?.name == fileName) activeModelFile = null
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (selectedImageUri != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(R.string.msg_analysis_result),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                val canEdit = !isParsing &&
                                    inferenceResult != null &&
                                    !inferenceResult!!.startsWith(stringResource(R.string.msg_error_prefix))
                                if (canEdit) {
                                    if (isEditingResult) {
                                        TextButton(
                                            onClick = { isEditingResult = false },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text(stringResource(R.string.btn_cancel))
                                        }
                                    } else {
                                        IconButton(onClick = {
                                            editedResultText = inferenceResult ?: ""
                                            isEditingResult = true
                                        }) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = stringResource(R.string.btn_edit_result)
                                            )
                                        }
                                    }
                                }
                            }

                            if (isEditingResult) {
                                OutlinedTextField(
                                    value = editedResultText,
                                    onValueChange = { editedResultText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 120.dp),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    label = { Text(stringResource(R.string.label_edit_result_json)) }
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        inferenceResult = editedResultText
                                        reparseResult(editedResultText)
                                        isEditingResult = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.btn_reparse))
                                }
                            } else {
                                Text(inferenceResult ?: stringResource(R.string.msg_preparing))
                            }
                            
                            if (!debugText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(
                                    onClick = { showDebugDetail = !showDebugDetail },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        if (showDebugDetail) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.msg_show_detail))
                                }
                                
                                AnimatedVisibility(visible = showDebugDetail) {
                                    OutlinedCard(
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        LazyColumn(modifier = Modifier.padding(8.dp)) {
                                            item {
                                                Text(
                                                    debugText ?: "",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (parsedLessons.isNotEmpty() || (isPendingAbMode && parsedAbTable.isNotEmpty())) {
                                Button(
                                    onClick = { checkAndApply() },
                                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.btn_apply_to_schedule))
                                }
                            } else if (inferenceResult != null && !inferenceResult!!.startsWith(stringResource(R.string.msg_error_prefix))) {
                                Button(
                                    onClick = { checkAndApply() },
                                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                                    enabled = false
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.btn_analyzing))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isParsing) {
            Dialog(
                onDismissRequest = { },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(parsingStatus, style = MaterialTheme.typography.bodyMedium)
                        
                        if (showCancelButton) {
                            Button(
                                onClick = { 
                                    inferenceEngine.cancelInference()
                                    isParsing = false
                                    showCancelButton = false
                                    val stopIntent = android.content.Intent(context, jp.linkserver.nittcsc.ml.VlmInferenceService::class.java)
                                    context.stopService(stopIntent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(stringResource(R.string.btn_cancel))
                            }
                        }
                    }
                }
            }
        }

        if (showSelectionDialog) {
            AlertDialog(
                onDismissRequest = { showSelectionDialog = false },
                title = { Text(if (isPendingAbMode) stringResource(R.string.dialog_title_import_ab) else stringResource(R.string.dialog_title_import_timetable)) },
                text = { Text(stringResource(R.string.dialog_text_select_import)) },
                confirmButton = {
                    TextButton(
                        onClick = { 
                            showSelectionDialog = false
                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_take_photo))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { 
                            showSelectionDialog = false
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_select_image))
                    }
                }
            )
        }

        if (showOverwriteConfirm) {
            AlertDialog(
                onDismissRequest = { showOverwriteConfirm = false },
                title = { Text(stringResource(R.string.dialog_title_data_warning)) },
                text = { Text(stringResource(R.string.dialog_text_overwrite_confirm)) },
                confirmButton = {
                    TextButton(onClick = { 
                        showOverwriteConfirm = false
                        applyImport()
                    }) { Text(stringResource(R.string.btn_overwrite)) }
                },
                dismissButton = {
                    TextButton(onClick = { showOverwriteConfirm = false }) { Text(stringResource(R.string.btn_cancel)) }
                }
            )
        }

        if (modelToDelete != null) {
            AlertDialog(
                onDismissRequest = { modelToDelete = null },
                title = { Text(stringResource(R.string.dialog_title_delete_model)) },
                text = {
                    val target = modelToDelete
                    Text(
                        stringResource(R.string.dialog_text_delete_model, target?.description ?: "数GB", target?.assets?.size ?: 0)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { 
                        modelToDelete?.let { model ->
                            model.assets.forEach { asset ->
                                downloadManager.deleteModel(asset.fileName)
                            }
                            downloadedModels = downloadManager.getDownloadedModels().map { it.name }
                            if (activeModelFile?.name == model.primaryFileName()) activeModelFile = null
                        }
                        modelToDelete = null
                    }) { Text(stringResource(R.string.btn_delete_confirm), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { modelToDelete = null }) { Text(stringResource(R.string.btn_cancel)) }
                }
            )
        }
    }
}

