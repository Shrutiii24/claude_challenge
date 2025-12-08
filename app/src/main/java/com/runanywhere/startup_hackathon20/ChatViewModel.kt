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
import android.util.Log

// Simple Message Data Class
data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

// Task data class for multi-task execution
data class Task(
    val id: Int,
    val description: String,
    val command: String,
    val priority: Int, // 1 = highest, 5 = lowest
    var status: TaskStatus = TaskStatus.PENDING
)

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

enum class TaskPriority {
    URGENT,      // Calls, alarms, critical actions
    HIGH,        // Messages, reminders, system settings
    MEDIUM,      // App launches, media playback
    LOW,         // Notes, screenshots
    NORMAL       // Everything else
}

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

    // Callback for speaking responses
    var onSpeakResponse: ((String) -> Unit)? = null

    // Multi-task execution state
    private val _activeTasks = MutableStateFlow<List<Task>>(emptyList())
    val activeTasks: StateFlow<List<Task>> = _activeTasks

    private val _isExecutingTasks = MutableStateFlow(false)
    val isExecutingTasks: StateFlow<Boolean> = _isExecutingTasks

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

        // --- Time and Title Parsing ---
        val timePattern = Pattern.compile("""(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)?""", Pattern.CASE_INSENSITIVE)
        val timeMatcher = timePattern.matcher(rest)
        var title: String? = null

        if (timeMatcher.find()) {
            timeDescription = timeMatcher.group(0)!!.trim()
            var hour = timeMatcher.group(1)!!.toInt()
            val minute = timeMatcher.group(2)?.toInt() ?: 0
            val ampm = timeMatcher.group(3)?.replace(".", "")?.lowercase()

            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0

            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            val titlePart1 = rest.substring(0, timeMatcher.start()).trim()
            val titlePart2 = rest.substring(timeMatcher.end()).trim()
            
            title = "$titlePart1 $titlePart2".trim()
                .replace(Regex("^\\s*(to|that|for|at)\\s+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+(to|that|for|at)$", RegexOption.IGNORE_CASE), "").trim()

        } else {
            return null // No time found
        }

        if (title.isNullOrBlank()) return null

        val fullDescription = "$dateDescription $timeDescription".trim()
        return Triple(title, calendar.timeInMillis, fullDescription)
    }

    private fun parseNote(text: String): Pair<String, String>? {
        var title: String? = null
        var itemsRaw: String? = null

        // Pattern 1: "create/make a to-do list for [TITLE] and add [items/item] [ITEMS] [in it/to it]"
        // Example: "create a to do list for groceries and add items apples and bananas in it"
        val pattern1 = Pattern.compile(
            "^(?:make|create)\\s+(?:a\\s+)?(?:to-?do\\s+|todo\\s+)?list\\s+(?:for|about)\\s+([a-z]+(?:\\s+[a-z]+)*)\\s+and\\s+add\\s+(?:items?\\s+)?(.+?)(?:\\s+(?:in|to)\\s+it)?$",
            Pattern.CASE_INSENSITIVE
        )
        val matcher1 = pattern1.matcher(text)
        if (matcher1.find()) {
            title = matcher1.group(1)!!.trim()
            itemsRaw = matcher1.group(2)!!.trim()
        }

        // Pattern 2: "create/make a to-do list for [TITLE] and add [ITEMS]"
        // Example: "make a to-do list for groceries and add apples, bananas and eggs"
        if (title == null) {
            val pattern2 = Pattern.compile(
                "^(?:make|create)\\s+(?:a\\s+)?(?:to-?do\\s+|todo\\s+)?list\\s+(?:for|about)\\s+(.+?)\\s+and\\s+add\\s+(.+)",
                Pattern.CASE_INSENSITIVE
            )
            val matcher2 = pattern2.matcher(text)
            if (matcher2.find()) {
                title = matcher2.group(1)!!.trim()
                itemsRaw = matcher2.group(2)!!.trim()
            }
        }

        // Pattern 3: "create to-do list [TITLE] with [ITEMS]"
        if (title == null) {
            val pattern3 = Pattern.compile(
                "^(?:make|create)\\s+(?:a\\s+)?(?:to-?do\\s+|todo\\s+)?list\\s+(.+?)\\s+(?:with|having|containing)\\s+(.+)",
                Pattern.CASE_INSENSITIVE
            )
            val matcher3 = pattern3.matcher(text)
            if (matcher3.find()) {
                title = matcher3.group(1)!!.trim()
                itemsRaw = matcher3.group(2)!!.trim()
            }
        }

        // Pattern 4: "add [ITEMS] to my [TITLE] list"
        if (title == null) {
            val pattern4 = Pattern.compile(
                "^(?:add|put)\\s+(.+?)\\s+to\\s+my\\s+(.+?)\\s+list",
                Pattern.CASE_INSENSITIVE
            )
            val matcher4 = pattern4.matcher(text)
            if (matcher4.find()) {
                title = matcher4.group(2)!!.trim()
                itemsRaw = matcher4.group(1)!!.trim()
            }
        }

        // Pattern 5: "make a [TITLE] list of [ITEMS]"
        if (title == null) {
            val pattern5 = Pattern.compile(
                "^(?:make|create|add)\\s+(?:a\\s+)?(.+?)\\s+list(?:\\s+of|:)?\\s+(.+)",
                Pattern.CASE_INSENSITIVE
            )
            val matcher5 = pattern5.matcher(text)
            if (matcher5.find()) {
                title = matcher5.group(1)!!.trim()
                itemsRaw = matcher5.group(2)!!.trim()
            }
        }

        // Pattern 6: "note down [ITEMS]"
        if (title == null) {
            val pattern6 = Pattern.compile(
                "^(?:note\\s+down|add)\\s+(.+)",
                Pattern.CASE_INSENSITIVE
            )
            val matcher6 = pattern6.matcher(text)
            if (matcher6.find()) {
                title = "Note"
                itemsRaw = matcher6.group(1)!!.trim()
            }
        }

        if (title == null || itemsRaw == null) return null

        // Clean up the title - capitalize first letter
        title = title.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        // Clean up items - remove filler words
        var cleanedItems = itemsRaw
            .replace(
                Regex("^(?:items?\\s+)", RegexOption.IGNORE_CASE),
                ""
            ) // Remove "items" or "item" at start
            .replace(
                Regex("\\s+(?:in|to)\\s+it$", RegexOption.IGNORE_CASE),
                ""
            ) // Remove "in it" or "to it" at end
            .replace(
                Regex("\\s+(?:for\\s+me|please)$", RegexOption.IGNORE_CASE),
                ""
            ) // Remove other fillers
            .trim()

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
    
    private fun parseCall(text: String): String? {
        val pattern = Pattern.compile("^call\\s+(.+)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        return if (matcher.find()) {
            matcher.group(1)?.trim()
        } else {
            null
        }
    }

    private fun parseSms(text: String): Pair<String, String>? {
        // Pattern: "text/sms/message [contact] [message]"
        // Examples: 
        // - "text john hey how are you"
        // - "sms mom I'll be late"
        // - "message alice meeting at 5pm"
        // - "send a text to john saying hello"
        // - "send a message to mom saying I love you"

        val patterns = listOf(
            // "send a text/message to [contact] saying [message]"
            Pattern.compile(
                "^send (?:a )?(?:text|message|sms) to (.+?) saying (.+)",
                Pattern.CASE_INSENSITIVE
            ),
            // "text/sms/message [contact] [message]"
            Pattern.compile("^(?:text|sms|message)\\s+(.+?)\\s+(.+)", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val contact = matcher.group(1)?.trim()
                val message = matcher.group(2)?.trim()
                if (!contact.isNullOrBlank() && !message.isNullOrBlank()) {
                    return Pair(contact, message)
                }
            }
        }

        return null
    }

    private fun parseYoutubeSearch(text: String): String? {
        // Patterns for YouTube search:
        // - "search youtube for [query]"
        // - "search for [query] on youtube"
        // - "play [query] on youtube"
        // - "watch [query] on youtube"
        // - "youtube [query]"
        // - "show me [query] on youtube"

        val patterns = listOf(
            Pattern.compile(
                "^(?:search|find|look for|look up)\\s+(?:for\\s+)?(.+?)\\s+on youtube",
                Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                "^(?:search|find)\\s+youtube\\s+(?:for\\s+)?(.+)",
                Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                "^(?:play|watch|show me)\\s+(.+?)\\s+on youtube",
                Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile("^youtube\\s+(.+)", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val query = matcher.group(1)?.trim()
                if (!query.isNullOrBlank()) {
                    return query
                }
            }
        }

        return null
    }

    private fun parseWhatsAppMessage(text: String): Pair<String, String>? {
        // Patterns for WhatsApp messages:
        // - "whatsapp [contact] [message]"
        // - "send [contact] [message] on whatsapp"
        // - "message [contact] [message] on whatsapp"
        // - "send whatsapp message to [contact] saying [message]"
        // - "whatsapp message to [contact] saying [message]"

        val patterns = listOf(
            // "send whatsapp message to [contact] saying [message]"
            Pattern.compile(
                "^send (?:a )?whatsapp (?:message )?to (.+?) saying (.+)",
                Pattern.CASE_INSENSITIVE
            ),
            // "whatsapp message to [contact] saying [message]"
            Pattern.compile(
                "^whatsapp (?:message )?to (.+?) saying (.+)",
                Pattern.CASE_INSENSITIVE
            ),
            // "send [contact] [message] on whatsapp"
            Pattern.compile("^send (.+?) (.+?) on whatsapp", Pattern.CASE_INSENSITIVE),
            // "message [contact] on whatsapp saying [message]"
            Pattern.compile("^message (.+?) on whatsapp saying (.+)", Pattern.CASE_INSENSITIVE),
            // "whatsapp [contact] [message]"
            Pattern.compile("^whatsapp\\s+(.+?)\\s+(.+)", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val contact = matcher.group(1)?.trim()
                val message = matcher.group(2)?.trim()
                if (!contact.isNullOrBlank() && !message.isNullOrBlank()) {
                    return Pair(contact, message)
                }
            }
        }

        return null
    }

    private fun parseEmailRequest(text: String): Triple<String, String, String>? {
        // Patterns for email generation:
        // - "generate email to [email] about [subject] context [context]"
        // - "write email to [email] subject [subject] context [context]"
        // - "compose email to [email] regarding [subject] with context [context]"

        val patterns = listOf(
            // "generate/write/compose email to [email] about/subject/regarding [subject] context [context]"
            Pattern.compile(
                "^(?:generate|write|compose|create) (?:an? )?email to (.+?) (?:about|subject|regarding) (.+?) (?:context|with context|about) (.+)",
                Pattern.CASE_INSENSITIVE
            ),
            // "email [email] subject [subject] context [context]"
            Pattern.compile("^email (.+?) subject (.+?) context (.+)", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val email = matcher.group(1)?.trim()
                val subject = matcher.group(2)?.trim()
                val context = matcher.group(3)?.trim()
                if (!email.isNullOrBlank() && !subject.isNullOrBlank() && !context.isNullOrBlank()) {
                    return Triple(email, subject, context)
                }
            }
        }

        return null
    }

    private fun parseWhatsAppAudioCall(text: String): String? {
        // Patterns for WhatsApp audio call:
        // - "whatsapp audio call [contact]"
        // - "whatsapp call [contact]"
        // - "call [contact] on whatsapp"
        // - "voice call [contact] on whatsapp"
        // - "audio call [contact] on whatsapp"

        val patterns = listOf(
            Pattern.compile(
                "^whatsapp (?:audio |voice )?call(?: to)? (.+)",
                Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                "^(?:audio |voice )?call (.+?) on whatsapp",
                Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                "^make (?:a )?(?:audio |voice )?whatsapp call(?: to)? (.+)",
                Pattern.CASE_INSENSITIVE
            )
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val contact = matcher.group(1)?.trim()
                if (!contact.isNullOrBlank()) {
                    return contact
                }
            }
        }

        return null
    }

    private fun parseWhatsAppVideoCall(text: String): String? {
        // Patterns for WhatsApp video call:
        // - "whatsapp video call [contact]"
        // - "video call [contact] on whatsapp"
        // - "make whatsapp video call to [contact]"

        val patterns = listOf(
            Pattern.compile(
                "^whatsapp video call(?: to)? (.+)",
                Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                "^video call (.+?) on whatsapp",
                Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                "^make (?:a )?whatsapp video call(?: to)? (.+)",
                Pattern.CASE_INSENSITIVE
            )
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val contact = matcher.group(1)?.trim()
                if (!contact.isNullOrBlank()) {
                    return contact
                }
            }
        }

        return null
    }

    private fun parseSetAlarm(text: String): Triple<Int, Int, String>? {
        // Patterns for setting alarms:
        // - "set alarm for 7am"
        // - "set alarm for 7:30 am"
        // - "set alarm for 7:00 a.m."
        // - "wake me up at 6am"
        // - "set an alarm for 8 o'clock"

        val lowerText = text.lowercase().trim()
            .replace("a.m.", "am")  // Convert a.m. to am
            .replace("p.m.", "pm")  // Convert p.m. to pm
            .replace("a. m.", "am") // Handle space variations
            .replace("p. m.", "pm")

        Log.d("ParseAlarm", "Input text: '$text'")
        Log.d("ParseAlarm", "Cleaned text: '$lowerText'")

        // Pattern 1: HH:MM am/pm (e.g., "set alarm for 7:30am" or "set alarm for 7:00 am")
        var pattern = Pattern.compile(
            "(?:set|create|add|wake me up).*?(?:alarm)?.*?(?:for|at)\\s*(\\d{1,2}):(\\d{2})\\s*(?:am|pm)",
            Pattern.CASE_INSENSITIVE
        )
        var matcher = pattern.matcher(lowerText)
        if (matcher.find()) {
            Log.d(
                "ParseAlarm",
                "Pattern 1 matched! Groups: ${matcher.group(1)}, ${matcher.group(2)}"
            )
            var hour = matcher.group(1)?.toIntOrNull() ?: return null
            val minute = matcher.group(2)?.toIntOrNull() ?: 0

            // Extract am/pm from the matched text
            val matchedText = matcher.group(0)?.lowercase() ?: ""
            val ampm = if (matchedText.contains("pm")) "pm" else "am"

            // Convert to 24-hour format
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0

            Log.d("ParseAlarm", "Returning: hour=$hour, minute=$minute (converted from $ampm)")
            return Triple(hour, minute, "Alarm")
        }

        // Pattern 2: H am/pm (e.g., "set alarm for 7am" or "set alarm for 7 am")
        pattern = Pattern.compile(
            "(?:set|create|add|wake me up).*?(?:alarm)?.*?(?:for|at)\\s*(\\d{1,2})\\s*(?:am|pm)",
            Pattern.CASE_INSENSITIVE
        )
        matcher = pattern.matcher(lowerText)
        if (matcher.find()) {
            Log.d("ParseAlarm", "Pattern 2 matched! Groups: ${matcher.group(1)}")
            var hour = matcher.group(1)?.toIntOrNull() ?: return null

            // Extract am/pm from the matched text
            val matchedText = matcher.group(0)?.lowercase() ?: ""
            val ampm = if (matchedText.contains("pm")) "pm" else "am"

            // Convert to 24-hour format
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0

            Log.d("ParseAlarm", "Returning: hour=$hour, minute=0 (converted from $ampm)")
            return Triple(hour, 0, "Alarm")
        }

        Log.d("ParseAlarm", "No pattern matched!")
        return null
    }

    private fun parseSetTimer(text: String): Pair<Int, String>? {
        // Patterns for setting timers:
        // - "set timer for 10 minutes"
        // - "set a 5 minute timer"
        // - "timer for 30 seconds"
        // - "set timer for 1 hour"

        val lowerText = text.lowercase().trim()

        // Pattern: captures N and unit (seconds/minutes/hours)
        val pattern = Pattern.compile(
            "(?:set|start|create|timer).*?(?:timer)?.*?(?:for)?\\s*(\\d+)\\s*(second|minute|hour)s?",
            Pattern.CASE_INSENSITIVE
        )

        val matcher = pattern.matcher(lowerText)
        if (matcher.find()) {
            val value = matcher.group(1)?.toIntOrNull() ?: return null
            val unit = matcher.group(2)?.lowercase() ?: "minute"

            val seconds = when {
                unit.startsWith("hour") -> value * 3600
                unit.startsWith("minute") -> value * 60
                else -> value // seconds
            }

            val message = "Timer: $value ${unit}${if (value > 1) "s" else ""}"
            return Pair(seconds, message)
        }

        return null
    }

    private fun parseManageAlarms(text: String): String? {
        // Patterns for managing alarms:
        // - "show my alarms"
        // - "cancel all alarms"
        // - "dismiss alarm"
        // - "snooze alarm"

        val lowerText = text.lowercase()

        return when {
            lowerText.matches(Regex("^(?:show|view|display) (?:my |all )?alarms?")) -> "show"
            lowerText.matches(Regex("^(?:cancel|delete|remove) (?:all )?alarms?")) -> "dismiss"
            lowerText.matches(Regex("^dismiss alarm")) -> "dismiss"
            lowerText.matches(Regex("^snooze alarm")) -> "snooze"
            else -> null
        }
    }

    private fun parseToggleWifi(text: String): Boolean? {
        // Patterns for WiFi toggle:
        // - "turn on wifi" / "enable wifi" / "wifi on"
        // - "turn off wifi" / "disable wifi" / "wifi off"
        val lowerText = text.lowercase().trim()

        return when {
            lowerText.matches(Regex("^(?:turn on|enable|switch on|activate) (?:the )?wifi?$")) -> true
            lowerText.matches(Regex("^wifi? (?:on|enable)$")) -> true
            lowerText.matches(Regex("^(?:turn off|disable|switch off|deactivate) (?:the )?wifi?$")) -> false
            lowerText.matches(Regex("^wifi? (?:off|disable)$")) -> false
            else -> null
        }
    }

    private fun parseToggleBluetooth(text: String): Boolean? {
        // Patterns for Bluetooth toggle:
        // - "turn on bluetooth" / "enable bluetooth" / "bluetooth on"
        // - "turn off bluetooth" / "disable bluetooth" / "bluetooth off"
        val lowerText = text.lowercase().trim()

        return when {
            lowerText.matches(Regex("^(?:turn on|enable|switch on|activate) (?:the )?bluetooth$")) -> true
            lowerText.matches(Regex("^bluetooth (?:on|enable)$")) -> true
            lowerText.matches(Regex("^(?:turn off|disable|switch off|deactivate) (?:the )?bluetooth$")) -> false
            lowerText.matches(Regex("^bluetooth (?:off|disable)$")) -> false
            else -> null
        }
    }

    private fun parseToggleAirplaneMode(text: String): Boolean? {
        // Patterns for Airplane Mode toggle:
        // - "turn on airplane mode" / "enable airplane mode"
        // - "turn off airplane mode" / "disable airplane mode"
        val lowerText = text.lowercase().trim()

        return when {
            lowerText.matches(Regex("^(?:turn on|enable|switch on|activate) (?:the )?(?:airplane|flight) mode$")) -> true
            lowerText.matches(Regex("^(?:airplane|flight) mode (?:on|enable)$")) -> true
            lowerText.matches(Regex("^(?:turn off|disable|switch off|deactivate) (?:the )?(?:airplane|flight) mode$")) -> false
            lowerText.matches(Regex("^(?:airplane|flight) mode (?:off|disable)$")) -> false
            else -> null
        }
    }

    private fun parseToggleDnd(text: String): Boolean? {
        // Patterns for DND toggle:
        // - "turn on dnd" / "enable dnd" / "dnd on"
        // - "turn on do not disturb" / "enable do not disturb"
        // - "turn off dnd" / "disable dnd" / "dnd off"
        val lowerText = text.lowercase().trim()

        return when {
            lowerText.matches(Regex("^(?:turn on|enable|switch on|activate) (?:the )?(?:dnd|do not disturb|silent mode)$")) -> true
            lowerText.matches(Regex("^(?:dnd|do not disturb|silent mode) (?:on|enable)$")) -> true
            lowerText.matches(Regex("^(?:turn off|disable|switch off|deactivate) (?:the )?(?:dnd|do not disturb|silent mode)$")) -> false
            lowerText.matches(Regex("^(?:dnd|do not disturb|silent mode) (?:off|disable)$")) -> false
            else -> null
        }
    }

    private fun parseToggleMobileData(text: String): Boolean? {
        // Patterns for Mobile Data toggle:
        // - "turn on mobile data" / "enable mobile data" / "mobile data on"
        // - "turn off mobile data" / "disable mobile data" / "mobile data off"
        val lowerText = text.lowercase().trim()

        return when {
            lowerText.matches(Regex("^(?:turn on|enable|switch on|activate) (?:the )?(?:mobile data|cellular data|data)$")) -> true
            lowerText.matches(Regex("^(?:mobile data|cellular data|data) (?:on|enable)$")) -> true
            lowerText.matches(Regex("^(?:turn off|disable|switch off|deactivate) (?:the )?(?:mobile data|cellular data|data)$")) -> false
            lowerText.matches(Regex("^(?:mobile data|cellular data|data) (?:off|disable)$")) -> false
            else -> null
        }
    }

    private fun parsePlayMusic(text: String): Pair<String, String?>? {
        // Patterns for playing music:
        // - "play [song name]"
        // - "play [song name] by [artist]"
        // - "play song [song name]"
        // - "play music [song name]"
        // - "listen to [song name]"
        // - "play [song name] on spotify/youtube music"

        val lowerText = text.lowercase().trim()

        val patterns = listOf(
            // "play [song] by [artist]" or "play [song] from [artist]"
            Pattern.compile(
                "^(?:play|listen to|start)\\s+(?:song|music)?\\s*(.+?)\\s+(?:by|from)\\s+(.+?)(?:\\s+on\\s+(?:spotify|youtube music|music))?$",
                Pattern.CASE_INSENSITIVE
            ),
            // "play [song] on [app]"
            Pattern.compile(
                "^(?:play|listen to|start)\\s+(?:song|music)?\\s*(.+?)\\s+on\\s+(?:spotify|youtube music|music|the music app)$",
                Pattern.CASE_INSENSITIVE
            ),
            // "play [song]"
            Pattern.compile(
                "^(?:play|listen to|start)\\s+(?:song|music|the song)?\\s*(.+?)$",
                Pattern.CASE_INSENSITIVE
            )
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val song = matcher.group(1)?.trim()
                val artist = if (matcher.groupCount() >= 2) matcher.group(2)?.trim() else null

                if (!song.isNullOrBlank()) {
                    return Pair(song, artist)
                }
            }
        }

        return null
    }

    private fun parseScreenshotCommand(text: String): Boolean {
        val lowerText = text.lowercase().trim()
        return lowerText.matches(Regex("^(?:take|capture|grab)\\s+(?:a\\s+)?screenshot$"))
    }

    private fun parseStartRecordingCommand(text: String): Boolean {
        val lowerText = text.lowercase().trim()
        return lowerText.matches(Regex("^(?:start|begin)\\s+(?:screen\\s+)?recording$")) ||
                lowerText.matches(Regex("^(?:record|recording)\\s+(?:screen|my screen)$")) ||
                lowerText.matches(Regex("^(?:start|begin)\\s+(?:screen\\s+)?record$"))
    }

    private fun parseStopRecordingCommand(text: String): Boolean {
        val lowerText = text.lowercase().trim()
        return lowerText.matches(Regex("^(?:stop|end)\\s+(?:screen\\s+)?recording$")) ||
                lowerText.matches(Regex("^(?:stop|end)\\s+(?:the\\s+)?record(?:ing)?$"))
    }

    fun addUserMessage(text: String) {
        _messages.value += ChatMessage(text, isUser = true)
    }

    private fun addAssistantMessage(text: String, shouldSpeak: Boolean = false) {
        _messages.value += ChatMessage(text, isUser = false)
        if (shouldSpeak) {
            onSpeakResponse?.invoke(text)
        }
    }

    fun processUserInput(text: String,
                         shouldSpeak: Boolean = false,
                         onToggleFlashlight: (Boolean) -> Unit,
                         onAddReminder: (String, Long, (Boolean) -> Unit) -> Unit,
                         onCreateNote: (String, String, (Boolean) -> Unit) -> Unit,
                         onShowNote: (String, (String?) -> Unit) -> Unit,
                         onOpenApp: (String, (Boolean) -> Unit) -> Unit,
                         onCall: (String) -> Unit,
                         onSendSms: (String, String) -> Unit,
                         onSearchYoutube: (String) -> Unit,
                         onSendWhatsApp: (String, String) -> Unit,
                         onGenerateEmail: (String, String, String) -> Unit,
                         onWhatsAppAudioCall: (String) -> Unit,
                         onWhatsAppVideoCall: (String) -> Unit,
                         onSetAlarm: (Int, Int, String) -> Unit,
                         onSetTimer: (Int, String) -> Unit,
                         onManageAlarms: (String) -> Unit,
                         onToggleWifi: (Boolean, (Boolean) -> Unit) -> Unit,
                         onToggleBluetooth: (Boolean, (Boolean) -> Unit) -> Unit,
                         onToggleAirplaneMode: (Boolean, (Boolean) -> Unit) -> Unit,
                         onToggleDnd: (Boolean, (Boolean) -> Unit) -> Unit,
                         onToggleMobileData: (Boolean, (Boolean) -> Unit) -> Unit,
                         onPlayMusic: (String, String?) -> Unit,
                         onTakeScreenshot: ((Boolean, String) -> Unit) -> Unit,
                         onStartRecording: ((Boolean, String) -> Unit) -> Unit,
                         onStopRecording: ((Boolean, String) -> Unit) -> Unit) {
        val lowerCaseText = text.lowercase()

        // Screenshot command - CHECK FIRST before music commands
        if (parseScreenshotCommand(text)) {
            onTakeScreenshot { success, message ->
                addAssistantMessage(
                    if (success) "Screenshot captured!" else "Screenshot failed: $message",
                    shouldSpeak
                )
            }
            addAssistantMessage("Taking screenshot", shouldSpeak)
            return
        }

        // Start recording command - CHECK BEFORE music commands
        if (parseStartRecordingCommand(text)) {
            onStartRecording { success, message ->
                addAssistantMessage(
                    if (success) "Screen recording started!" else "Recording failed: $message",
                    shouldSpeak
                )
            }
            addAssistantMessage("Starting screen recording", shouldSpeak)
            return
        }

        // Stop recording command - CHECK BEFORE music commands
        if (parseStopRecordingCommand(text)) {
            onStopRecording { success, message ->
                addAssistantMessage(
                    if (success) "Screen recording saved!" else "Stop recording failed: $message",
                    shouldSpeak
                )
            }
            addAssistantMessage("Stopping screen recording", shouldSpeak)
            return
        }

        // Set Alarm command
        val alarmDetails = parseSetAlarm(text)
        if (alarmDetails != null) {
            val (hour, minute, message) = alarmDetails
            onSetAlarm(hour, minute, message)
            val timeStr = String.format("%02d:%02d", hour, minute)
            addAssistantMessage("Setting alarm for $timeStr", shouldSpeak)
            return
        }

        // Set Timer command
        val timerDetails = parseSetTimer(text)
        if (timerDetails != null) {
            val (seconds, message) = timerDetails
            onSetTimer(seconds, message)
            val minutes = seconds / 60
            val secs = seconds % 60
            val timeStr = if (minutes > 0) {
                "$minutes minute${if (minutes > 1) "s" else ""}"
            } else {
                "$secs second${if (secs > 1) "s" else ""}"
            }
            addAssistantMessage("Setting timer for $timeStr", shouldSpeak)
            return
        }

        // Manage Alarms command
        val manageAction = parseManageAlarms(text)
        if (manageAction != null) {
            onManageAlarms(manageAction)
            val actionMsg = when (manageAction) {
                "show" -> "Showing your alarms"
                "dismiss" -> "Dismissing alarms"
                "snooze" -> "Snoozing alarm"
                else -> "Managing alarms"
            }
            addAssistantMessage(actionMsg, shouldSpeak)
            return
        }

        // WiFi toggle command
        val wifiToggle = parseToggleWifi(text)
        if (wifiToggle != null) {
            onToggleWifi(wifiToggle) { success ->
                val message = if (success) {
                    "WiFi ${if (wifiToggle) "turned on" else "turned off"}"
                } else {
                    "Unable to toggle WiFi. Please check settings."
                }
                addAssistantMessage(message, shouldSpeak)
            }
            return
        }

        // Bluetooth toggle command
        val bluetoothToggle = parseToggleBluetooth(text)
        if (bluetoothToggle != null) {
            onToggleBluetooth(bluetoothToggle) { success ->
                val message = if (success) {
                    "Bluetooth ${if (bluetoothToggle) "turned on" else "turned off"}"
                } else {
                    "Unable to toggle Bluetooth. Please check settings."
                }
                addAssistantMessage(message, shouldSpeak)
            }
            return
        }

        // Airplane Mode toggle command
        val airplaneModeToggle = parseToggleAirplaneMode(text)
        if (airplaneModeToggle != null) {
            onToggleAirplaneMode(airplaneModeToggle) { success ->
                val message = if (success) {
                    "Opening Airplane Mode settings"
                } else {
                    "Unable to open Airplane Mode settings"
                }
                addAssistantMessage(message, shouldSpeak)
            }
            return
        }

        // DND toggle command
        val dndToggle = parseToggleDnd(text)
        if (dndToggle != null) {
            onToggleDnd(dndToggle) { success ->
                val message = if (success) {
                    "Do Not Disturb ${if (dndToggle) "enabled" else "disabled"}"
                } else {
                    "Unable to toggle Do Not Disturb. Please grant permission."
                }
                addAssistantMessage(message, shouldSpeak)
            }
            return
        }

        // Mobile Data toggle command
        val mobileDataToggle = parseToggleMobileData(text)
        if (mobileDataToggle != null) {
            onToggleMobileData(mobileDataToggle) { success ->
                val message = if (success) {
                    "Opening Mobile Data settings"
                } else {
                    "Unable to open Mobile Data settings"
                }
                addAssistantMessage(message, shouldSpeak)
            }
            return
        }

        // Music playback command - MOVED AFTER screenshot/recording commands
        val musicDetails = parsePlayMusic(text)
        if (musicDetails != null) {
            val (song, artist) = musicDetails
            onPlayMusic(song, artist)
            val searchText = if (artist != null) "$song by $artist" else song
            addAssistantMessage("Playing $searchText", shouldSpeak)
            return
        }

        // Flashlight commands
        if (lowerCaseText.contains("torch") || lowerCaseText.contains("flashlight")) {
            if (lowerCaseText.contains("on")) {
                onToggleFlashlight(true)
                addAssistantMessage("Turning on the flashlight", shouldSpeak)
                return
            } else if (lowerCaseText.contains("off")) {
                onToggleFlashlight(false)
                addAssistantMessage("Turning off the flashlight", shouldSpeak)
                return
            }
        }

        // Reminder command
        val reminderDetails = parseReminder(text)
        if (reminderDetails != null) {
            val (title, timeInMillis, timeDescription) = reminderDetails
            onAddReminder(title, timeInMillis) { success ->
                val message = if (success) {
                    "Okay, I saved a reminder for $timeDescription to remind you to $title"
                } else {
                    "Sorry, I couldn't add the reminder. Please check if a calendar is set up on your device."
                }
                addAssistantMessage(message, shouldSpeak)
            }
            return
        }

        // Show note command
        val noteTitleToShow = parseShowNote(text)
        if (noteTitleToShow != null) {
            onShowNote(noteTitleToShow) { content ->
                val message = if (content != null) {
                    "Here is your $noteTitleToShow: $content"
                } else {
                    "I couldn't find a note with the title $noteTitleToShow"
                }
                addAssistantMessage(message, shouldSpeak)
            }
            return
        }

        // Note creation command
        val noteDetails = parseNote(text)
        if (noteDetails != null) {
            val (title, content) = noteDetails
            onCreateNote(title, content) { success ->
                val message = if (success) {
                    "Did it for you! I've saved your $title list"
                } else {
                    "Sorry, I couldn't save the note. Please ensure I have the right permissions."
                }
                addAssistantMessage(message, shouldSpeak)
            }
            return
        }

        // Open app command
        val appNameToOpen = parseOpenApp(text)
        if (appNameToOpen != null) {
            onOpenApp(appNameToOpen) { success ->
                val message = if (success) {
                    "Opening $appNameToOpen"
                } else {
                    "I couldn't find an app called $appNameToOpen"
                }
                // Only speak if voice input was used
                if (shouldSpeak) {
                    addAssistantMessage(message, true)
                }
            }
            return
        }

        // WhatsApp video call command (check before audio call to avoid conflicts)
        val videoCallContact = parseWhatsAppVideoCall(text)
        if (videoCallContact != null) {
            onWhatsAppVideoCall(videoCallContact)
            if (shouldSpeak) {
                addAssistantMessage("Starting WhatsApp video call with $videoCallContact", true)
            }
            return
        }

        // WhatsApp audio call command
        val audioCallContact = parseWhatsAppAudioCall(text)
        if (audioCallContact != null) {
            onWhatsAppAudioCall(audioCallContact)
            if (shouldSpeak) {
                addAssistantMessage("Starting WhatsApp call with $audioCallContact", true)
            }
            return
        }

        // Email generation command
        val emailDetails = parseEmailRequest(text)
        if (emailDetails != null) {
            val (recipient, subject, context) = emailDetails
            if (shouldSpeak) {
                addAssistantMessage("Generating email to $recipient", true)
            }
            onGenerateEmail(recipient, subject, context)
            return
        }

        // WhatsApp message command
        val whatsappDetails = parseWhatsAppMessage(text)
        if (whatsappDetails != null) {
            val (contact, message) = whatsappDetails
            onSendWhatsApp(contact, message)
            if (shouldSpeak) {
                addAssistantMessage("Sending WhatsApp message to $contact", true)
            }
            return
        }

        // YouTube search command
        val youtubeQuery = parseYoutubeSearch(text)
        if (youtubeQuery != null) {
            onSearchYoutube(youtubeQuery)
            if (shouldSpeak) {
                addAssistantMessage("Searching YouTube for $youtubeQuery", true)
            }
            return
        }

        // SMS command
        val smsDetails = parseSms(text)
        if (smsDetails != null) {
            val (contact, message) = smsDetails
            onSendSms(contact, message)
            if (shouldSpeak) {
                addAssistantMessage("Sending message to $contact", true)
            }
            return
        }

        // Call command
        val contactToCall = parseCall(text)
        if (contactToCall != null) {
            onCall(contactToCall)
            if (shouldSpeak) {
                addAssistantMessage("Calling $contactToCall", true)
            }
            return
        }

        sendMessage(text, shouldSpeak)
    }

    private fun sendMessage(text: String, shouldSpeak: Boolean = false) {
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

                // Speak the final response if shouldSpeak is true
                if (shouldSpeak && assistantResponse.isNotBlank()) {
                    onSpeakResponse?.invoke(assistantResponse)
                }
            } catch (e: Exception) {
                val errorMessage = "Error: ${e.message}"
                _messages.value += ChatMessage(errorMessage, isUser = false)
                if (shouldSpeak) {
                    onSpeakResponse?.invoke(errorMessage)
                }
            }

            _isLoading.value = false
        }
    }

    fun generateEmailDraft(
        recipient: String,
        subject: String,
        context: String,
        onEmailGenerated: (String) -> Unit
    ) {
        if (_currentModelId.value == null) {
            _statusMessage.value = "Please load a model first"
            _messages.value += ChatMessage(
                "Please load an AI model first to generate emails.",
                isUser = false
            )
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _messages.value += ChatMessage("Generating professional email draft...", isUser = false)

            try {
                val prompt = """Generate a professional email based on the following details:

To: $recipient
Subject: $subject
Context: $context

Please write a well-structured, professional email with proper greeting, body, and closing. Keep it concise and professional. Only provide the email body text, no additional commentary."""

                var emailDraft = ""
                RunAnywhere.generateStream(prompt).collect { token ->
                    emailDraft += token
                }

                // Clean up the email draft
                emailDraft = emailDraft.trim()

                // Show the generated email to the user
                _messages.value += ChatMessage(
                    "Email draft generated! Opening Gmail...\n\n$emailDraft",
                    isUser = false
                )

                // Call the callback to send the email
                onEmailGenerated(emailDraft)

            } catch (e: Exception) {
                _messages.value += ChatMessage("Error generating email: ${e.message}", isUser = false)
            }

            _isLoading.value = false
        }
    }

    fun refreshModels() {
        loadAvailableModels()
    }

    // ============ MULTI-TASK EXECUTION SYSTEM ============

    /**
     * Detects if the input is a multi-task rambling voice memo
     */
    private fun isMultiTaskInput(text: String): Boolean {
        val lowerText = text.lowercase()

        // Check for multiple task indicators
        val taskIndicators = listOf(
            "and then", "also", "after that", "next", "then",
            "and also", "as well", "plus", "additionally",
            "first", "second", "third", "finally"
        )

        val indicatorCount = taskIndicators.count { lowerText.contains(it) }

        // Check for sentence endings (periods or "and")
        val sentenceCount = text.split(Regex("[.!?]|\\band\\b")).size

        // If multiple indicators or multiple sentences, likely multi-task
        return indicatorCount >= 2 || sentenceCount >= 3
    }

    /**
     * Extracts individual tasks from a rambling voice memo using AI
     */
    private suspend fun extractTasksFromMemo(memo: String): List<String> {
        if (_currentModelId.value == null) {
            // Fallback to simple sentence splitting if no AI model
            return extractTasksFallback(memo)
        }

        try {
            val prompt = """Analyze this voice memo and extract ONLY the individual tasks/commands. 
List each task on a new line, starting with a dash (-).
Keep each task concise and actionable. Do not add explanations or commentary.

Voice memo: "$memo"

Tasks:"""

            var response = ""
            RunAnywhere.generateStream(prompt).collect { token ->
                response += token
            }

            // Parse the AI response to extract tasks
            val tasks = response.lines()
                .map { it.trim() }
                .filter { it.startsWith("-") || it.matches(Regex("^\\d+\\..*")) }
                .map { it.removePrefix("-").removePrefix(Regex("^\\d+\\.").toString()).trim() }
                .filter { it.isNotBlank() }

            return if (tasks.isEmpty()) extractTasksFallback(memo) else tasks

        } catch (e: Exception) {
            Log.e("MultiTask", "AI extraction failed: ${e.message}")
            return extractTasksFallback(memo)
        }
    }

    /**
     * Fallback task extraction using simple sentence splitting
     */
    private fun extractTasksFallback(memo: String): List<String> {
        // Split by common delimiters
        val tasks =
            memo.split(Regex("(?:and then|also|after that|next|then|and also|\\.|,\\s*and)"))
                .map { it.trim() }
                .filter { it.isNotBlank() && it.length > 3 }
                .distinct()

        return tasks
    }

    /**
     * Determines task priority based on command type
     */
    private fun getTaskPriority(command: String): Int {
        val lowerCommand = command.lowercase()

        return when {
            // URGENT (Priority 1): Calls, emergencies
            lowerCommand.contains("call") ||
                    lowerCommand.contains("emergency") -> 1

            // HIGH (Priority 2): Messages, alarms, important time-sensitive
            lowerCommand.contains("alarm") ||
                    lowerCommand.contains("message") ||
                    lowerCommand.contains("text") ||
                    lowerCommand.contains("whatsapp") ||
                    lowerCommand.contains("sms") ||
                    lowerCommand.contains("email") ||
                    lowerCommand.contains("reminder") -> 2

            // MEDIUM (Priority 3): System settings, app launches
            lowerCommand.contains("open") ||
                    lowerCommand.contains("launch") ||
                    lowerCommand.contains("turn on") ||
                    lowerCommand.contains("turn off") ||
                    lowerCommand.contains("enable") ||
                    lowerCommand.contains("disable") ||
                    lowerCommand.contains("wifi") ||
                    lowerCommand.contains("bluetooth") -> 3

            // LOW (Priority 4): Media, notes, screenshots
            lowerCommand.contains("play") ||
                    lowerCommand.contains("screenshot") ||
                    lowerCommand.contains("record") ||
                    lowerCommand.contains("note") ||
                    lowerCommand.contains("list") ||
                    lowerCommand.contains("youtube") -> 4

            // NORMAL (Priority 5): Everything else
            else -> 5
        }
    }

    /**
     * Processes a rambling voice memo and executes all tasks
     */
    fun processMultiTaskMemo(
        memo: String,
        shouldSpeak: Boolean = false,
        onToggleFlashlight: (Boolean) -> Unit,
        onAddReminder: (String, Long, (Boolean) -> Unit) -> Unit,
        onCreateNote: (String, String, (Boolean) -> Unit) -> Unit,
        onShowNote: (String, (String?) -> Unit) -> Unit,
        onOpenApp: (String, (Boolean) -> Unit) -> Unit,
        onCall: (String) -> Unit,
        onSendSms: (String, String) -> Unit,
        onSearchYoutube: (String) -> Unit,
        onSendWhatsApp: (String, String) -> Unit,
        onGenerateEmail: (String, String, String) -> Unit,
        onWhatsAppAudioCall: (String) -> Unit,
        onWhatsAppVideoCall: (String) -> Unit,
        onSetAlarm: (Int, Int, String) -> Unit,
        onSetTimer: (Int, String) -> Unit,
        onManageAlarms: (String) -> Unit,
        onToggleWifi: (Boolean, (Boolean) -> Unit) -> Unit,
        onToggleBluetooth: (Boolean, (Boolean) -> Unit) -> Unit,
        onToggleAirplaneMode: (Boolean, (Boolean) -> Unit) -> Unit,
        onToggleDnd: (Boolean, (Boolean) -> Unit) -> Unit,
        onToggleMobileData: (Boolean, (Boolean) -> Unit) -> Unit,
        onPlayMusic: (String, String?) -> Unit,
        onTakeScreenshot: ((Boolean, String) -> Unit) -> Unit,
        onStartRecording: ((Boolean, String) -> Unit) -> Unit,
        onStopRecording: ((Boolean, String) -> Unit) -> Unit
    ) {
        viewModelScope.launch {
            _isExecutingTasks.value = true

            // Notify user
            addAssistantMessage(
                "I've received your tasks. Let me extract and organize them...",
                shouldSpeak
            )

            try {
                // Extract individual tasks
                val taskCommands = extractTasksFromMemo(memo)

                if (taskCommands.isEmpty()) {
                    addAssistantMessage(
                        "I couldn't identify any specific tasks. Please try again with clearer commands.",
                        shouldSpeak
                    )
                    _isExecutingTasks.value = false
                    return@launch
                }

                // Create Task objects with priorities
                val tasks = taskCommands.mapIndexed { index, command ->
                    Task(
                        id = index + 1,
                        description = command.take(100), // Truncate for display
                        command = command,
                        priority = getTaskPriority(command)
                    )
                }.sortedBy { it.priority } // Sort by priority

                _activeTasks.value = tasks

                // Show extracted tasks
                val taskSummary = tasks.joinToString("\n") { "${it.id}. ${it.description}" }
                addAssistantMessage(
                    "I found ${tasks.size} task${if (tasks.size > 1) "s" else ""}:\n$taskSummary\n\nExecuting now...",
                    shouldSpeak
                )

                // Execute tasks sequentially
                for (task in tasks) {
                    // Update task status
                    updateTaskStatus(task.id, TaskStatus.IN_PROGRESS)

                    addAssistantMessage("Executing: ${task.description}", shouldSpeak)

                    // Execute the task
                    val success = executeSingleTask(
                        task.command,
                        shouldSpeak,
                        onToggleFlashlight,
                        onAddReminder,
                        onCreateNote,
                        onShowNote,
                        onOpenApp,
                        onCall,
                        onSendSms,
                        onSearchYoutube,
                        onSendWhatsApp,
                        onGenerateEmail,
                        onWhatsAppAudioCall,
                        onWhatsAppVideoCall,
                        onSetAlarm,
                        onSetTimer,
                        onManageAlarms,
                        onToggleWifi,
                        onToggleBluetooth,
                        onToggleAirplaneMode,
                        onToggleDnd,
                        onToggleMobileData,
                        onPlayMusic,
                        onTakeScreenshot,
                        onStartRecording,
                        onStopRecording
                    )

                    // Update task status and clean from memory
                    updateTaskStatus(
                        task.id,
                        if (success) TaskStatus.COMPLETED else TaskStatus.FAILED
                    )

                    // Small delay between tasks
                    kotlinx.coroutines.delay(500)
                }

                // Final summary
                val completedCount = _activeTasks.value.count { it.status == TaskStatus.COMPLETED }
                val failedCount = _activeTasks.value.count { it.status == TaskStatus.FAILED }

                val summary = buildString {
                    append("All tasks processed!\n\n")
                    append("✅ Completed: $completedCount\n")
                    if (failedCount > 0) {
                        append("❌ Failed: $failedCount\n")
                    }
                    append("\nTask list cleared from memory.")
                }

                addAssistantMessage(summary, shouldSpeak)

                // Clear tasks from memory
                kotlinx.coroutines.delay(2000)
                _activeTasks.value = emptyList()

            } catch (e: Exception) {
                Log.e("MultiTask", "Error processing tasks: ${e.message}", e)
                addAssistantMessage("Error processing tasks: ${e.message}", shouldSpeak)
            } finally {
                _isExecutingTasks.value = false
            }
        }
    }

    /**
     * Updates the status of a specific task
     */
    private fun updateTaskStatus(taskId: Int, status: TaskStatus) {
        _activeTasks.value = _activeTasks.value.map {
            if (it.id == taskId) it.copy(status = status) else it
        }
    }

    /**
     * Executes a single task and returns success status
     */
    private suspend fun executeSingleTask(
        command: String,
        shouldSpeak: Boolean,
        onToggleFlashlight: (Boolean) -> Unit,
        onAddReminder: (String, Long, (Boolean) -> Unit) -> Unit,
        onCreateNote: (String, String, (Boolean) -> Unit) -> Unit,
        onShowNote: (String, (String?) -> Unit) -> Unit,
        onOpenApp: (String, (Boolean) -> Unit) -> Unit,
        onCall: (String) -> Unit,
        onSendSms: (String, String) -> Unit,
        onSearchYoutube: (String) -> Unit,
        onSendWhatsApp: (String, String) -> Unit,
        onGenerateEmail: (String, String, String) -> Unit,
        onWhatsAppAudioCall: (String) -> Unit,
        onWhatsAppVideoCall: (String) -> Unit,
        onSetAlarm: (Int, Int, String) -> Unit,
        onSetTimer: (Int, String) -> Unit,
        onManageAlarms: (String) -> Unit,
        onToggleWifi: (Boolean, (Boolean) -> Unit) -> Unit,
        onToggleBluetooth: (Boolean, (Boolean) -> Unit) -> Unit,
        onToggleAirplaneMode: (Boolean, (Boolean) -> Unit) -> Unit,
        onToggleDnd: (Boolean, (Boolean) -> Unit) -> Unit,
        onToggleMobileData: (Boolean, (Boolean) -> Unit) -> Unit,
        onPlayMusic: (String, String?) -> Unit,
        onTakeScreenshot: ((Boolean, String) -> Unit) -> Unit,
        onStartRecording: ((Boolean, String) -> Unit) -> Unit,
        onStopRecording: ((Boolean, String) -> Unit) -> Unit
    ): Boolean {
        return try {
            // Use the existing processUserInput logic but capture success
            var taskSuccess = true

            // Process the command (reusing existing parsing logic)
            processUserInput(
                command,
                false, // Don't speak individual task results in multi-task mode
                onToggleFlashlight,
                onAddReminder,
                onCreateNote,
                onShowNote,
                onOpenApp,
                onCall,
                onSendSms,
                onSearchYoutube,
                onSendWhatsApp,
                onGenerateEmail,
                onWhatsAppAudioCall,
                onWhatsAppVideoCall,
                onSetAlarm,
                onSetTimer,
                onManageAlarms,
                onToggleWifi,
                onToggleBluetooth,
                onToggleAirplaneMode,
                onToggleDnd,
                onToggleMobileData,
                onPlayMusic,
                onTakeScreenshot,
                onStartRecording,
                onStopRecording
            )

            taskSuccess
        } catch (e: Exception) {
            Log.e("MultiTask", "Task execution failed: ${e.message}")
            false
        }
    }

    /**
     * Main entry point for processing user input - checks for multi-task first
     */
    fun processInput(
        text: String,
        shouldSpeak: Boolean = false,
        onToggleFlashlight: (Boolean) -> Unit,
        onAddReminder: (String, Long, (Boolean) -> Unit) -> Unit,
        onCreateNote: (String, String, (Boolean) -> Unit) -> Unit,
        onShowNote: (String, (String?) -> Unit) -> Unit,
        onOpenApp: (String, (Boolean) -> Unit) -> Unit,
        onCall: (String) -> Unit,
        onSendSms: (String, String) -> Unit,
        onSearchYoutube: (String) -> Unit,
        onSendWhatsApp: (String, String) -> Unit,
        onGenerateEmail: (String, String, String) -> Unit,
        onWhatsAppAudioCall: (String) -> Unit,
        onWhatsAppVideoCall: (String) -> Unit,
        onSetAlarm: (Int, Int, String) -> Unit,
        onSetTimer: (Int, String) -> Unit,
        onManageAlarms: (String) -> Unit,
        onToggleWifi: (Boolean, (Boolean) -> Unit) -> Unit,
        onToggleBluetooth: (Boolean, (Boolean) -> Unit) -> Unit,
        onToggleAirplaneMode: (Boolean, (Boolean) -> Unit) -> Unit,
        onToggleDnd: (Boolean, (Boolean) -> Unit) -> Unit,
        onToggleMobileData: (Boolean, (Boolean) -> Unit) -> Unit,
        onPlayMusic: (String, String?) -> Unit,
        onTakeScreenshot: ((Boolean, String) -> Unit) -> Unit,
        onStartRecording: ((Boolean, String) -> Unit) -> Unit,
        onStopRecording: ((Boolean, String) -> Unit) -> Unit
    ) {
        // Check if this is a multi-task input
        if (isMultiTaskInput(text)) {
            processMultiTaskMemo(
                text,
                shouldSpeak,
                onToggleFlashlight,
                onAddReminder,
                onCreateNote,
                onShowNote,
                onOpenApp,
                onCall,
                onSendSms,
                onSearchYoutube,
                onSendWhatsApp,
                onGenerateEmail,
                onWhatsAppAudioCall,
                onWhatsAppVideoCall,
                onSetAlarm,
                onSetTimer,
                onManageAlarms,
                onToggleWifi,
                onToggleBluetooth,
                onToggleAirplaneMode,
                onToggleDnd,
                onToggleMobileData,
                onPlayMusic,
                onTakeScreenshot,
                onStartRecording,
                onStopRecording
            )
        } else {
            // Single task - use normal processing
            processUserInput(
                text,
                shouldSpeak,
                onToggleFlashlight,
                onAddReminder,
                onCreateNote,
                onShowNote,
                onOpenApp,
                onCall,
                onSendSms,
                onSearchYoutube,
                onSendWhatsApp,
                onGenerateEmail,
                onWhatsAppAudioCall,
                onWhatsAppVideoCall,
                onSetAlarm,
                onSetTimer,
                onManageAlarms,
                onToggleWifi,
                onToggleBluetooth,
                onToggleAirplaneMode,
                onToggleDnd,
                onToggleMobileData,
                onPlayMusic,
                onTakeScreenshot,
                onStartRecording,
                onStopRecording
            )
        }
    }
}
