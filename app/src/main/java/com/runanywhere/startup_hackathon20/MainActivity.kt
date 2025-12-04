package com.runanywhere.startup_hackathon20

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.CalendarContract
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.startup_hackathon20.ui.theme.Startup_hackathon20Theme
import java.io.File
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList[0]
        } catch (e: Exception) {
            // Handle camera not available
        }

        enableEdgeToEdge()
        setContent {
            Startup_hackathon20Theme {
                ChatScreen(
                    onToggleFlashlight = ::toggleFlashlight,
                    onAddReminder = ::addReminder,
                    onCreateNote = ::createNote,
                    onShowNote = ::showNote,
                    onOpenApp = ::openApp
                )
            }
        }
    }

    private fun toggleFlashlight(enable: Boolean) {
        cameraId?.let {
            try {
                cameraManager.setTorchMode(it, enable)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun addReminder(title: String, timeInMillis: Long, onResult: (Boolean) -> Unit) {
        try {
            val contentResolver = contentResolver
            val projection = arrayOf(CalendarContract.Calendars._ID)
            val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_OWNER}"
            val cursor = contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                null,
                null
            )

            var calendarId: Long? = null
            if (cursor != null && cursor.moveToFirst()) {
                calendarId = cursor.getLong(0) // Use the first writable calendar
                cursor.close()
            }

            if (calendarId != null) {
                val values = ContentValues().apply {
                    put(CalendarContract.Events.DTSTART, timeInMillis)
                    put(CalendarContract.Events.DTEND, timeInMillis + 60 * 60 * 1000) // 1 hour duration
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.EVENT_TIMEZONE, Calendar.getInstance().timeZone.id)
                }
                val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                onResult(uri != null)
            } else {
                onResult(false)
            }
        } catch (e: SecurityException) {
            onResult(false)
        } catch (e: Exception) {
            onResult(false)
        }
    }

    private fun createNote(title: String, content: String, onResult: (Boolean) -> Unit) {
        try {
            val documentsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (documentsDir != null) {
                if (!documentsDir.exists()) {
                    documentsDir.mkdirs()
                }
                val file = File(documentsDir, "$title.txt")
                file.writeText(content)
                onResult(true)
            } else {
                onResult(false)
            }
        } catch (e: Exception) {
            onResult(false)
        }
    }

    private fun showNote(title: String, onResult: (String?) -> Unit) {
        try {
            val documentsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (documentsDir != null) {
                val file = File(documentsDir, "$title.txt")
                if (file.exists()) {
                    onResult(file.readText())
                } else {
                    onResult(null)
                }
            } else {
                onResult(null)
            }
        } catch (e: Exception) {
            onResult(null)
        }
    }

    private fun openApp(appName: String, onResult: (Boolean) -> Unit) {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val appsList: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val exactMatch = appsList.firstOrNull { resolveInfo ->
            resolveInfo.loadLabel(pm).toString().equals(appName, ignoreCase = true)
        }
        val startsWithMatch = appsList.firstOrNull { resolveInfo ->
            resolveInfo.loadLabel(pm).toString().startsWith(appName, ignoreCase = true)
        }
        val containsMatch = appsList.firstOrNull { resolveInfo ->
            resolveInfo.loadLabel(pm).toString().contains(appName, ignoreCase = true)
        }

        val bestMatch = exactMatch ?: startsWithMatch ?: containsMatch

        if (bestMatch != null) {
            val intent = pm.getLaunchIntentForPackage(bestMatch.activityInfo.packageName)
            if (intent != null) {
                startActivity(intent)
                onResult(true)
                return
            }
        }

        onResult(false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    onToggleFlashlight: (Boolean) -> Unit,
    onAddReminder: (String, Long, (Boolean) -> Unit) -> Unit,
    onCreateNote: (String, String, (Boolean) -> Unit) -> Unit,
    onShowNote: (String, (String?) -> Unit) -> Unit,
    onOpenApp: (String, (Boolean) -> Unit) -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val currentModelId by viewModel.currentModelId.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showModelSelector by remember { mutableStateOf(false) }

    var textToProcessOnPermission by remember { mutableStateOf<String?>(null) }

    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        textToProcessOnPermission?.let { text ->
            if (isGranted) {
                viewModel.processUserInput(text, onToggleFlashlight, onAddReminder, onCreateNote, onShowNote, onOpenApp)
            }
        }
        textToProcessOnPermission = null
    }

    val requestCalendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        textToProcessOnPermission?.let { text ->
            if (permissions[Manifest.permission.WRITE_CALENDAR] == true) {
                viewModel.processUserInput(text, onToggleFlashlight, onAddReminder, onCreateNote, onShowNote, onOpenApp)
            }
        }
        textToProcessOnPermission = null
    }

    fun handleUserInput(text: String) {
        viewModel.addUserMessage(text)
        val lowerCaseText = text.lowercase()

        if (lowerCaseText.contains("torch") || lowerCaseText.contains("flashlight")) {
            textToProcessOnPermission = text
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else if (lowerCaseText.contains("remind")) {
            textToProcessOnPermission = text
            requestCalendarPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        } else {
            viewModel.processUserInput(text, onToggleFlashlight, onAddReminder, onCreateNote, onShowNote, onOpenApp)
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0)
            if (!spokenText.isNullOrBlank()) {
                handleUserInput(spokenText)
            }
        }
    }

    val requestAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
            }
            speechRecognizerLauncher.launch(intent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Chat") },
                actions = {
                    TextButton(onClick = { showModelSelector = !showModelSelector }) {
                        Text("Models")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Status bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    downloadProgress?.let { progress ->
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                }
            }

            // Model selector (collapsible)
            if (showModelSelector) {
                ModelSelector(
                    models = availableModels,
                    currentModelId = currentModelId,
                    onDownload = { modelId -> viewModel.downloadModel(modelId) },
                    onLoad = { modelId -> viewModel.loadModel(modelId) },
                    onRefresh = { viewModel.refreshModels() }
                )
            }

            // Messages List
            val listState = rememberLazyListState()

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                }
            }

            // Auto-scroll to bottom when new messages arrive
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }

            // Input Field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    enabled = !isLoading && currentModelId != null
                )

                IconButton(onClick = {
                    requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    Icon(Icons.Default.Mic, contentDescription = "Record voice input")
                }

                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val text = inputText
                            inputText = ""
                            handleUserInput(text)
                        }
                    },
                    enabled = !isLoading && inputText.isNotBlank() && currentModelId != null
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (message.isUser) "You" else "AI",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ModelSelector(
    models: List<com.runanywhere.sdk.models.ModelInfo>,
    currentModelId: String?,
    onDownload: (String) -> Unit,
    onLoad: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Available Models",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (models.isEmpty()) {
                Text(
                    text = "No models available. Initializing...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(models) { model ->
                        ModelItem(
                            model = model,
                            isLoaded = model.id == currentModelId,
                            onDownload = { onDownload(model.id) },
                            onLoad = { onLoad(model.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModelItem(
    model: com.runanywhere.sdk.models.ModelInfo,
    isLoaded: Boolean,
    onDownload: () -> Unit,
    onLoad: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoaded)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.titleSmall
            )

            if (isLoaded) {
                Text(
                    text = "✓ Currently Loaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.weight(1f),
                        enabled = !model.isDownloaded
                    ) {
                        Text(if (model.isDownloaded) "Downloaded" else "Download")
                    }

                    Button(
                        onClick = onLoad,
                        modifier = Modifier.weight(1f),
                        enabled = model.isDownloaded
                    ) {
                        Text("Load")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Startup_hackathon20Theme {
        //ChatScreen()
    }
}
