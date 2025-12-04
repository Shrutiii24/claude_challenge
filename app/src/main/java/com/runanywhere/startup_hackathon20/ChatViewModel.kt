package com.runanywhere.startup_hackathon20

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.listAvailableModels
import com.runanywhere.sdk.models.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.regex.Matcher
import java.util.regex.Pattern

// Simple Message Data Class
data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

// ViewModel
class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress

    private val _currentModelId = MutableStateFlow<String?>(null)
    val currentModelId: StateFlow<String?> = _currentModelId

    private val _statusMessage = MutableStateFlow<String>("Initializing...")
    val statusMessage: StateFlow<String> = _statusMessage

    init {
        loadAvailableModels()
    }

    private fun loadAvailableModels() {
        viewModelScope.launch {
            try {
                val models = listAvailableModels()
                _availableModels.value = models
                _statusMessage.value = "Ready - Please download and load a model"
            } catch (e: Exception) {
                _statusMessage.value = "Error loading models: ${e.message}"
            }
        }
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            try {
                _statusMessage.value = "Downloading model..."
                RunAnywhere.downloadModel(modelId).collect { progress ->
                    _downloadProgress.value = progress
                    _statusMessage.value = "Downloading: ${(progress * 100).toInt()}%"
                }
                _downloadProgress.value = null
                _statusMessage.value = "Download complete! Please load the model."
            } catch (e: Exception) {
                _statusMessage.value = "Download failed: ${e.message}"
                _downloadProgress.value = null
            }
        }
    }

    fun loadModel(modelId: String) {
        viewModelScope.launch {
            try {
                val success = RunAnywhere.loadModel(modelId)
                if (success) {
                    _currentModelId.value = modelId
                    _statusMessage.value = "Model loaded! Ready to chat."
                } else {
                    _statusMessage.value = "Failed to load model"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error loading model: ${e.message}"
            }
        }
    }

    private fun parseReminder(text: String): Triple<String, Long, String>? {
        val calendar = Calendar.getInstance()
        val now = Calendar.getInstance()
        val lowerCaseText = text.lowercase()

        val keywords = listOf("remind me to ", "add a reminder for ", "set a reminder for ", "set a reminder to ")
        val keyword = keywords.firstOrNull { lowerCaseText.startsWith(it) } ?: return null
        var rest = text.substring(keyword.length)

        var dateDescription = ""
        var timeDescription = ""
        var title = ""

        // --- Date Parsing ---
        val monthMap = mapOf(
            "jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3, "may" to 4, "jun" to 5,
            "jul" to 6, "aug" to 7, "sep" to 8, "oct" to 9, "nov" to 10, "dec" to 11
        )

        val datePatterns = listOf(
            Pattern.compile("""(\d{1,2})(?:st|nd|rd|th)?\s+(${monthMap.keys.joinToString("|")})""", Pattern.CASE_INSENSITIVE), // 5th dec
            Pattern.compile("""(${monthMap.keys.joinToString("|")})\s+(\d{1,2})(?:st|nd|rd|th)?""", Pattern.CASE_INSENSITIVE)  // dec 5th
        )

        var dateFound = false
        for (pattern in datePatterns) {
            val matcher = pattern.matcher(rest)
            if (matcher.find()) {
                dateFound = true
                dateDescription = matcher.group(0)!!
                val dayOfMonth: Int
                val monthStr: String
                if (pattern.pattern().startsWith("(\\d")) {
                     dayOfMonth = matcher.group(1)!!.toInt()
                     monthStr = matcher.group(2)!!
                } else {
                     monthStr = matcher.group(1)!!
                     dayOfMonth = matcher.group(2)!!.toInt()
                }
                val month = monthMap[monthStr.lowercase().take(3)]!!

                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                calendar.set(Calendar.MONTH, month)

                // If the date is in the past for the current year, assume next year
                if (calendar.before(now)) {
                    calendar.add(Calendar.YEAR, 1)
                }
                rest = rest.replace(dateDescription, "").trim()
                break
            }
        }

        if (!dateFound) {
            val relativeDatePattern = Pattern.compile("(today|tonight|tomorrow|day after tomorrow)", Pattern.CASE_INSENSITIVE)
            val matcher = relativeDatePattern.matcher(rest)
            if (matcher.find()) {
                dateDescription = matcher.group(1)!!
                when (dateDescription.lowercase()) {
                    "tomorrow" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
                    "day after tomorrow" -> calendar.add(Calendar.DAY_OF_YEAR, 2)
                }
                rest = rest.replace(dateDescription, "", ignoreCase = true).trim()
            }
        }

        // --- Time Parsing ---
        val timePattern = Pattern.compile("""(\d{1,2})(?::(\d{2}))?(\s*(am|pm))?""", Pattern.CASE_INSENSITIVE)
        val timeMatcher = timePattern.matcher(rest)
        if (timeMatcher.find()) {
            timeDescription = timeMatcher.group(0)!!.trim()
            var hour = timeMatcher.group(1)!!.toInt()
            val minute = timeMatcher.group(2)?.toInt() ?: 0
            val ampm = timeMatcher.group(4)?.lowercase()?.trim()

            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0 // Midnight case

            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            rest = rest.replace(timeDescription, "", ignoreCase = true).trim()
        } else {
            return null // Reminder must have a time
        }

        // --- Title Parsing ---
        title = rest.replace(Regex("^\\s*(to|that|for)\\s+", RegexOption.IGNORE_CASE), "").trim()

        if (title.isBlank()) return null

        val fullDescription = "$dateDescription $timeDescription".trim()

        return Triple(title, calendar.timeInMillis, fullDescription)
    }

    private fun parseNote(text: String): Pair<String, String>? {
        // Define patterns for various ways of creating a list, from most to least specific.
        val patterns = linkedMapOf<Pattern, (Matcher) -> Pair<String, String>>(
            // e.g., "add apples, bananas, and milk to my grocery list"
            Pattern.compile("^(?:add|put)\\s+(.+?)\\s+to my (.+?) list", Pattern.CASE_INSENSITIVE) to { m ->
                Pair(m.group(2)!!.trim().replaceFirstChar { it.titlecase() } + " List", m.group(1)!!)
            },
            // e.g., "make a grocery list of apples, bananas, and milk"
            Pattern.compile("^(?:make|create|add) a (.+?) list(?: of|:)?\\s*(.+)", Pattern.CASE_INSENSITIVE) to { m ->
                Pair(m.group(1)!!.trim().replaceFirstChar { it.titlecase() } + " List", m.group(2)!!)
            },
            // e.g., "note down apples, bananas, and milk" or "add apples and bananas in it"
            Pattern.compile("^(?:note down|add)\\s+(.+)", Pattern.CASE_INSENSITIVE) to { m ->
                Pair("Note", m.group(1)!!)
            }
        )

        var title: String? = null
        var itemsRaw: String? = null

        // Find the first matching pattern
        for ((pattern, extractor) in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val (extractedTitle, extractedItems) = extractor(matcher)
                title = extractedTitle
                itemsRaw = extractedItems
                break
            }
        }

        if (title == null || itemsRaw == null) return null

        // Clean trailing filler words from the extracted items string
        val fillerRegex = "\\s+(?:in it|for me|please|to the list|for my list)$"
        val cleanedItems = itemsRaw.trim().replace(Regex(fillerRegex, RegexOption.IGNORE_CASE), "")

        if (cleanedItems.isBlank()) return null

        // Split the cleaned string into a bulleted list of items
        val itemsList = cleanedItems.split(Regex(""",\s*(?:and\s+)?|\s+and\s+"""))
            .map { it.trim().removeSuffix(",") }
            .filter { it.isNotBlank() }

        if (itemsList.isEmpty()) return null

        val content = itemsList.joinToString("\n") { "- $it" }

        return Pair(title, content)
    }

    private fun parseShowNote(text: String): String? {
        val lowerCaseText = text.lowercase()
        val pattern = Pattern.compile("^(?:show|view|display) (?:me )?(?:my |the )?(.+?)(?: list| note)?$", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(lowerCaseText)
        if (matcher.find()) {
            val titlePart = matcher.group(1)?.trim()
            if (!titlePart.isNullOrBlank()) {
                if (titlePart.equals("note", ignoreCase = true)) return "Note"
                return titlePart.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + " List"
            }
        }
        return null
    }

    private fun parseOpenApp(text: String): String? {
        val pattern = Pattern.compile("^(?:open|launch)\\s+(.+)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        return if (matcher.find()) {
            matcher.group(1)?.trim()
        } else {
            null
        }
    }

    fun addUserMessage(text: String) {
        _messages.value += ChatMessage(text, isUser = true)
    }

    fun processUserInput(text: String, onToggleFlashlight: (Boolean) -> Unit, onAddReminder: (String, Long, (Boolean) -> Unit) -> Unit, onCreateNote: (String, String, (Boolean) -> Unit) -> Unit, onShowNote: (String, (String?) -> Unit) -> Unit, onOpenApp: (String, (Boolean) -> Unit) -> Unit) {
        val lowerCaseText = text.lowercase()

        // Flashlight commands
        if (lowerCaseText.contains("torch") || lowerCaseText.contains("flashlight")) {
            if (lowerCaseText.contains("on")) {
                onToggleFlashlight(true)
                _messages.value += ChatMessage("Turning on the flashlight.", isUser = false)
                return
            } else if (lowerCaseText.contains("off")) {
                onToggleFlashlight(false)
                _messages.value += ChatMessage("Turning off the flashlight.", isUser = false)
                return
            }
        }

        // Reminder command
        val reminderDetails = parseReminder(text)
        if (reminderDetails != null) {
            val (title, timeInMillis, timeDescription) = reminderDetails
            onAddReminder(title, timeInMillis) { success ->
                if (success) {
                     _messages.value += ChatMessage("Okay, I saved a reminder for $timeDescription to remind you to \"$title\"", isUser = false)
                } else {
                    _messages.value += ChatMessage("Sorry, I couldn't add the reminder. Please check if a calendar is set up on your device.", isUser = false)
                }
            }
            return
        }

        // Show note command
        val noteTitleToShow = parseShowNote(text)
        if (noteTitleToShow != null) {
            onShowNote(noteTitleToShow) { content ->
                if (content != null) {
                    _messages.value += ChatMessage("Here is your '$noteTitleToShow':\n$content", isUser = false)
                } else {
                    _messages.value += ChatMessage("I couldn't find a note with the title '$noteTitleToShow'.", isUser = false)
                }
            }
            return
        }
        
        // Note creation command
        val noteDetails = parseNote(text)
        if (noteDetails != null) {
            val (title, content) = noteDetails
            onCreateNote(title, content) { success ->
                if (success) {
                    _messages.value += ChatMessage("Did it for you! I've saved your '$title' list.", isUser = false)
                } else {
                    _messages.value += ChatMessage("Sorry, I couldn't save the note. Please ensure I have the right permissions.", isUser = false)
                }
            }
            return
        }

        // Open app command
        val appNameToOpen = parseOpenApp(text)
        if (appNameToOpen != null) {
            onOpenApp(appNameToOpen) { success ->
                if (success) {
                    _messages.value += ChatMessage("Opening $appNameToOpen...", isUser = false)
                } else {
                    _messages.value += ChatMessage("Sorry, I couldn't find the app '$appNameToOpen' on your device.", isUser = false)
                }
            }
            return
        }

        sendMessage(text)
    }

    private fun sendMessage(text: String) {
        if (_currentModelId.value == null) {
            _statusMessage.value = "Please load a model first"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true

            try {
                var assistantResponse = ""
                RunAnywhere.generateStream(text).collect { token ->
                    assistantResponse += token

                    val currentMessages = _messages.value.toMutableList()
                    if (currentMessages.lastOrNull()?.isUser == false) {
                        currentMessages[currentMessages.lastIndex] =
                            ChatMessage(assistantResponse, isUser = false)
                    } else {
                        currentMessages.add(ChatMessage(assistantResponse, isUser = false))
                    }
                    _messages.value = currentMessages
                }
            } catch (e: Exception) {
                _messages.value += ChatMessage("Error: ${e.message}", isUser = false)
            }

            _isLoading.value = false
        }
    }

    fun refreshModels() {
        loadAvailableModels()
    }
}
