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
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.telephony.SmsManager
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
import androidx.compose.material.icons.filled.MicOff
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
import android.net.Uri
import android.util.Log
import androidx.activity.viewModels
import android.provider.AlarmClock
import android.widget.Toast
import android.app.AlarmManager
import android.app.PendingIntent
import android.provider.Settings
import android.net.wifi.WifiManager
import android.bluetooth.BluetoothAdapter
import android.app.NotificationManager
import android.content.ComponentName
import android.telephony.TelephonyManager
import java.lang.reflect.Method
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Display
import android.view.WindowManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf


class MainActivity : ComponentActivity() {
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    // Screenshot and Screen Recording
    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    internal var isRecording = false
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    // Wake word detection and TTS
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isWakeWordListening = false
    internal var isWakeWordEnabled = mutableStateOf(false)
    private var onWakeWordDetected: (() -> Unit)? = null
    internal var shouldRespondWithVoice = false // Track if current interaction is voice-based

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList[0]
        } catch (e: Exception) {
            // Handle camera not available
        }

        // Initialize media projection manager
        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Get screen metrics
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val metrics = android.util.DisplayMetrics()
        display.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        // Initialize Text-to-Speech
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = java.util.Locale.US
                Log.d("TTS", "TextToSpeech initialized successfully")
            } else {
                Log.e("TTS", "TextToSpeech initialization failed")
            }
        }

        // Initialize Speech Recognizer for wake word detection
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        enableEdgeToEdge()
        setContent {
            Startup_hackathon20Theme {
                ChatScreen(
                    activity = this,
                    onToggleFlashlight = ::toggleFlashlight,
                    onAddReminder = ::addReminder,
                    onCreateNote = ::createNote,
                    onShowNote = ::showNote,
                    onOpenApp = ::openApp,
                    onCall = ::makeCall,
                    onSendSms = ::sendSms,
                    onSearchYoutube = ::searchYoutube,
                    onSendWhatsApp = ::sendWhatsAppMessage,
                    onSendEmail = ::sendEmailViaGmail,
                    onWhatsAppAudioCall = ::makeWhatsAppAudioCall,
                    onWhatsAppVideoCall = ::makeWhatsAppVideoCall,
                    onSetAlarm = ::setAlarm,
                    onSetTimer = ::setTimer,
                    onManageAlarms = ::manageAlarms,
                    onToggleWifi = ::toggleWifi,
                    onToggleBluetooth = ::toggleBluetooth,
                    onToggleAirplaneMode = ::toggleAirplaneMode,
                    onToggleDnd = ::toggleDnd,
                    onToggleMobileData = ::toggleMobileData,
                    onPlayMusic = ::playMusic,
                    onStopRecording = ::stopScreenRecording
                )
            }
        }
    }

    private fun makeCall(contactNameOrNumber: String) {
        try {
            // Try to find the contact's phone number
            val phoneNumber = findContactPhoneNumber(contactNameOrNumber) ?: contactNameOrNumber

            // Check if we have CALL_PHONE permission
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                }
                startActivity(intent)
            } else {
                // Fallback to dial if permission not granted
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            // Handle exceptions, e.g., no phone app available
        }
    }

    private fun findContactPhoneNumber(contactName: String): String? {
        try {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return null
            }

            val contentResolver = contentResolver
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$contactName%"),
                null
            )

            var phoneNumber: String? = null
            var exactMatch: String? = null
            var startsWithMatch: String? = null
            var containsMatch: String? = null

            if (cursor != null) {
                val numberIndex =
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIndex =
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

                if (numberIndex >= 0 && nameIndex >= 0) {
                    while (cursor.moveToNext()) {
                        val currentNumber = cursor.getString(numberIndex)
                        val currentName = cursor.getString(nameIndex)

                        // Priority 1: Exact match (case-insensitive)
                        if (currentName.equals(contactName, ignoreCase = true)) {
                            exactMatch = currentNumber
                            break // Found exact match, stop searching
                        }

                        // Priority 2: Starts with the search term
                        if (startsWithMatch == null && currentName.startsWith(
                                contactName,
                                ignoreCase = true
                            )
                        ) {
                            startsWithMatch = currentNumber
                        }

                        // Priority 3: Contains the search term
                        if (containsMatch == null && currentName.contains(
                                contactName,
                                ignoreCase = true
                            )
                        ) {
                            containsMatch = currentNumber
                        }
                    }
                }
                cursor.close()
            }

            // Return the best match with priority: exact > starts with > contains
            phoneNumber = exactMatch ?: startsWithMatch ?: containsMatch

            return phoneNumber
        } catch (e: Exception) {
            return null
        }
    }

    private fun sendSms(contactNameOrNumber: String, message: String) {
        try {
            // Try to find the contact's phone number
            val phoneNumber = findContactPhoneNumber(contactNameOrNumber) ?: contactNameOrNumber

            // Check if we have SEND_SMS permission
            if (checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                // Split message if it's too long
                val parts = smsManager.divideMessage(message)
                if (parts.size == 1) {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                } else {
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                }
            } else {
                // Fallback to SMS intent if permission not granted
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("sms:$phoneNumber")
                    putExtra("sms_body", message)
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            // Handle exceptions, e.g., no SMS app available
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
            val selection =
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_OWNER}"
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
                    put(
                        CalendarContract.Events.DTEND,
                        timeInMillis + 60 * 60 * 1000
                    ) // 1 hour duration
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
            // Method 1: Try to save to Google Keep (most common notes app)
            val keepIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, content)
                setPackage("com.google.android.keep")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pm = packageManager
            if (keepIntent.resolveActivity(pm) != null) {
                Log.d("CreateNote", "Saving to Google Keep")
                startActivity(keepIntent)
                onResult(true)
                return
            }

            // Method 2: Try Samsung Notes
            val samsungIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, content)
                setPackage("com.samsung.android.app.notes")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (samsungIntent.resolveActivity(pm) != null) {
                Log.d("CreateNote", "Saving to Samsung Notes")
                startActivity(samsungIntent)
                onResult(true)
                return
            }

            // Method 3: Try ColorNote
            val colorNoteIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, content)
                setPackage("com.socialnmobile.dictapps.notepad.color.note")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (colorNoteIntent.resolveActivity(pm) != null) {
                Log.d("CreateNote", "Saving to ColorNote")
                startActivity(colorNoteIntent)
                onResult(true)
                return
            }

            // Method 4: Generic note creation intent
            val genericNoteIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, content)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Show chooser to let user select their preferred notes app
            val chooserIntent = Intent.createChooser(genericNoteIntent, "Save note to")
            if (genericNoteIntent.resolveActivity(pm) != null) {
                Log.d("CreateNote", "Opening chooser for notes apps")
                startActivity(chooserIntent)
                onResult(true)
                return
            }

            // Fallback: Save to app's internal storage and also try to open any notes app
            Log.d("CreateNote", "No notes app found, saving locally")
            val documentsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (documentsDir != null) {
                if (!documentsDir.exists()) {
                    documentsDir.mkdirs()
                }
                val file = File(documentsDir, "$title.txt")
                file.writeText(content)

                // Try to open the file with any app that can handle text
                try {
                    val openIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, title)
                        putExtra(Intent.EXTRA_TEXT, content)
                    }
                    startActivity(Intent.createChooser(openIntent, "Create note"))
                } catch (e: Exception) {
                    Log.e("CreateNote", "Failed to open chooser: ${e.message}")
                }

                onResult(true)
            } else {
                onResult(false)
            }
        } catch (e: Exception) {
            Log.e("CreateNote", "Error creating note: ${e.message}")
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
        Log.d("OpenApp", "Attempting to open app: $appName")

        // Map of common app names to package names for direct launch
        val commonAppPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "gmail" to "com.google.android.gm",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "twitter" to "com.twitter.android",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "telegram" to "org.telegram.messenger",
            "snapchat" to "com.snapchat.android",
            "tiktok" to "com.zhiliaoapp.musically",
            "amazon" to "com.amazon.mShop.android.shopping",
            "uber" to "com.ubercab",
            "phone" to "com.google.android.dialer",
            "contacts" to "com.google.android.contacts",
            "messages" to "com.google.android.apps.messaging",
            "camera" to "com.google.android.GoogleCamera",
            "photos" to "com.google.android.apps.photos",
            "play store" to "com.android.vending",
            "playstore" to "com.android.vending",
            "settings" to "com.android.settings",
            "calculator" to "com.google.android.calculator",
            "clock" to "com.google.android.deskclock",
            "calendar" to "com.google.android.calendar"
        )

        // Try direct package name launch first for common apps
        val lowerAppName = appName.lowercase().trim()
        val packageName = commonAppPackages[lowerAppName]
        if (packageName != null) {
            Log.d("OpenApp", "Found package in common apps: $packageName")
            try {
                val intent = pm.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    Log.d("OpenApp", "Launching via direct package: $packageName")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    onResult(true)
                    return
                } else {
                    Log.d("OpenApp", "Launch intent is null for package: $packageName")
                }
            } catch (e: Exception) {
                Log.e("OpenApp", "Error launching app via package: ${e.message}")
                // If direct launch fails, fall through to search
            }
        }

        // Query all launchable apps
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val appsList: List<ResolveInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            }
        } catch (e: Exception) {
            Log.e("OpenApp", "Error querying activities: ${e.message}")
            emptyList()
        }

        Log.d("OpenApp", "Found ${appsList.size} launchable apps")

        // Search with multiple matching strategies
        val exactMatch = appsList.firstOrNull { resolveInfo ->
            val label = resolveInfo.loadLabel(pm).toString()
            label.equals(appName, ignoreCase = true)
        }
        val startsWithMatch = appsList.firstOrNull { resolveInfo ->
            val label = resolveInfo.loadLabel(pm).toString()
            label.startsWith(appName, ignoreCase = true)
        }
        val containsMatch = appsList.firstOrNull { resolveInfo ->
            val label = resolveInfo.loadLabel(pm).toString()
            label.contains(appName, ignoreCase = true)
        }

        // Also try matching by package name
        val packageMatch = appsList.firstOrNull { resolveInfo ->
            resolveInfo.activityInfo.packageName.contains(appName, ignoreCase = true)
        }

        val bestMatch = exactMatch ?: startsWithMatch ?: containsMatch ?: packageMatch

        if (bestMatch != null) {
            val matchedLabel = bestMatch.loadLabel(pm).toString()
            val matchedPackage = bestMatch.activityInfo.packageName
            Log.d("OpenApp", "Found match - Label: $matchedLabel, Package: $matchedPackage")

            try {
                val intent = pm.getLaunchIntentForPackage(matchedPackage)
                if (intent != null) {
                    Log.d("OpenApp", "Launching app: $matchedLabel")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    onResult(true)
                    return
                } else {
                    Log.e("OpenApp", "Launch intent is null for: $matchedPackage")
                }
            } catch (e: Exception) {
                Log.e("OpenApp", "Error launching app: ${e.message}")
            }
        } else {
            Log.d("OpenApp", "No match found for: $appName")
        }

        onResult(false)
    }

    private fun searchYoutube(query: String) {
        try {
            Log.d("SearchYoutube", "Searching for: $query")

            // Try to open in YouTube app first
            val youtubeAppIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Check if YouTube app is installed
            val pm = packageManager
            if (youtubeAppIntent.resolveActivity(pm) != null) {
                Log.d("SearchYoutube", "Opening in YouTube app")
                startActivity(youtubeAppIntent)
            } else {
                // Fallback to browser
                Log.d("SearchYoutube", "YouTube app not found, using browser")
                val searchQuery = Uri.encode(query)
                val webIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://www.youtube.com/results?search_query=$searchQuery")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(webIntent)
            }
        } catch (e: Exception) {
            Log.e("SearchYoutube", "Error searching YouTube: ${e.message}")
            // Last resort - try generic web search
            try {
                val searchQuery = Uri.encode("$query site:youtube.com")
                val webIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://www.google.com/search?q=$searchQuery")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(webIntent)
            } catch (e2: Exception) {
                Log.e("SearchYoutube", "All YouTube search methods failed: ${e2.message}")
            }
        }
    }

    private fun sendEmailViaGmail(recipient: String, subject: String, body: String) {
        try {
            Log.d("Email", "Sending email to: $recipient")

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:") // Only email apps
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Check if there's an email app
            if (intent.resolveActivity(packageManager) != null) {
                Log.d("Email", "Opening email app")
                startActivity(intent)
            } else {
                Log.e("Email", "No email app found")
            }
        } catch (e: Exception) {
            Log.e("Email", "Error opening email app: ${e.message}")
        }
    }

    private fun sendWhatsAppMessage(contactNameOrNumber: String, message: String) {
        try {
            Log.d("WhatsApp", "Sending message to: $contactNameOrNumber")

            // Try to find the contact's phone number
            var phoneNumber = findContactPhoneNumber(contactNameOrNumber)

            // If no contact found, assume it's a phone number
            if (phoneNumber == null) {
                phoneNumber = contactNameOrNumber
            }

            // Remove any non-numeric characters except +
            val cleanedNumber = phoneNumber.replace(Regex("[^0-9+]"), "")

            try {
                // Try to open WhatsApp directly with the contact
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    // Use WhatsApp's API format: https://wa.me/phonenumber?text=message
                    val whatsappUrl = "https://wa.me/$cleanedNumber?text=${Uri.encode(message)}"
                    data = Uri.parse(whatsappUrl)
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                // Check if WhatsApp is installed
                val pm = packageManager
                if (intent.resolveActivity(pm) != null) {
                    Log.d("WhatsApp", "Opening WhatsApp with phone: $cleanedNumber")
                    startActivity(intent)
                } else {
                    // WhatsApp not installed, try WhatsApp Business
                    Log.d("WhatsApp", "WhatsApp not found, trying WhatsApp Business")
                    intent.setPackage("com.whatsapp.w4b")
                    if (intent.resolveActivity(pm) != null) {
                        startActivity(intent)
                    } else {
                        // Neither installed, open in browser
                        Log.d("WhatsApp", "No WhatsApp found, opening in browser")
                        val webIntent = Intent(Intent.ACTION_VIEW).apply {
                            data =
                                Uri.parse("https://wa.me/$cleanedNumber?text=${Uri.encode(message)}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(webIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e("WhatsApp", "Error opening WhatsApp: ${e.message}")
                // Fallback: Try generic share with WhatsApp
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(shareIntent)
                } catch (e2: Exception) {
                    Log.e("WhatsApp", "All WhatsApp methods failed: ${e2.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("WhatsApp", "Error in sendWhatsAppMessage: ${e.message}")
        }
    }

    private fun makeWhatsAppAudioCall(contactNameOrNumber: String) {
        try {
            Log.d("WhatsAppCall", "Making audio call to: $contactNameOrNumber")

            // Try to find the contact's phone number
            var phoneNumber = findContactPhoneNumber(contactNameOrNumber)

            // If no contact found, assume it's a phone number
            if (phoneNumber == null) {
                phoneNumber = contactNameOrNumber
            }

            // Remove any non-numeric characters except +
            val cleanedNumber = phoneNumber.replace(Regex("[^0-9+]"), "")

            try {
                // Try WhatsApp's voice call intent
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    // Use tel: URI with WhatsApp package - this is the closest we can get
                    data = Uri.parse("tel:$cleanedNumber")
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                // Check if WhatsApp is installed and can handle this intent
                val pm = packageManager
                if (intent.resolveActivity(pm) != null) {
                    Log.d(
                        "WhatsAppCall",
                        "Initiating WhatsApp audio call with phone: $cleanedNumber"
                    )
                    startActivity(intent)
                } else {
                    // Fallback: Try WhatsApp Business
                    Log.d("WhatsAppCall", "WhatsApp not found, trying WhatsApp Business")
                    intent.setPackage("com.whatsapp.w4b")
                    if (intent.resolveActivity(pm) != null) {
                        startActivity(intent)
                    } else {
                        Log.e("WhatsAppCall", "WhatsApp is not installed")
                    }
                }
            } catch (e: Exception) {
                Log.e("WhatsAppCall", "Error making WhatsApp audio call: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("WhatsAppCall", "Error in makeWhatsAppAudioCall: ${e.message}")
        }
    }

    private fun makeWhatsAppVideoCall(contactNameOrNumber: String) {
        try {
            Log.d("WhatsAppVideoCall", "Making video call to: $contactNameOrNumber")

            // Try to find the contact's phone number
            var phoneNumber = findContactPhoneNumber(contactNameOrNumber)

            // If no contact found, assume it's a phone number
            if (phoneNumber == null) {
                phoneNumber = contactNameOrNumber
            }

            // Remove any non-numeric characters except +
            val cleanedNumber = phoneNumber.replace(Regex("[^0-9+]"), "")

            try {
                // Use tel: URI with WhatsApp package for video call
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("tel:$cleanedNumber")
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                // Check if WhatsApp is installed
                val pm = packageManager
                if (intent.resolveActivity(pm) != null) {
                    Log.d(
                        "WhatsAppVideoCall",
                        "Initiating WhatsApp video call with phone: $cleanedNumber"
                    )
                    startActivity(intent)
                } else {
                    // Fallback: Try WhatsApp Business
                    Log.d("WhatsAppVideoCall", "WhatsApp not found, trying WhatsApp Business")
                    intent.setPackage("com.whatsapp.w4b")
                    if (intent.resolveActivity(pm) != null) {
                        startActivity(intent)
                    } else {
                        Log.e("WhatsAppVideoCall", "WhatsApp is not installed")
                    }
                }
            } catch (e: Exception) {
                Log.e("WhatsAppVideoCall", "Error making WhatsApp video call: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("WhatsAppVideoCall", "Error in makeWhatsAppVideoCall: ${e.message}")
        }
    }

    private fun setAlarm(hour: Int, minute: Int, message: String = "Alarm") {
        try {
            Log.d("SetAlarm", "===== ALARM SET REQUEST =====")
            Log.d("SetAlarm", "Hour: $hour, Minute: $minute, Message: $message")
            Toast.makeText(this, "Setting alarm for $hour:$minute", Toast.LENGTH_SHORT).show()

            // Check for SCHEDULE_EXACT_ALARM permission on Android 12+ (API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.d("SetAlarm", "❌ Missing SCHEDULE_EXACT_ALARM permission")
                    Toast.makeText(this, "Opening permission settings...", Toast.LENGTH_LONG).show()

                    try {
                        // Open settings to grant permission
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                        Toast.makeText(
                            this,
                            "Please allow 'Alarms & reminders', then try again",
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    } catch (e: Exception) {
                        Log.e("SetAlarm", "Failed to open permission settings: ${e.message}")
                        Toast.makeText(
                            this,
                            "Go to Settings > Apps > ${applicationInfo.loadLabel(packageManager)} > Allow 'Alarms & reminders'",
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    }
                }
                Log.d("SetAlarm", "✅ SCHEDULE_EXACT_ALARM permission granted")
            }

            // Create the alarm intent (standard Android way - works on ALL devices including Samsung)
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false) // Show UI for user confirmation
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            Log.d("SetAlarm", "Intent created with: hour=$hour, minute=$minute, message=$message")

            // Check if ANY app can handle this intent
            val resolveInfo =
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo != null) {
                Log.d("SetAlarm", "✅ Handler found: ${resolveInfo.activityInfo.packageName}")
                Toast.makeText(this, "Opening Clock app...", Toast.LENGTH_SHORT).show()

                try {
                    startActivity(intent)
                    Log.d("SetAlarm", "✅ Clock app launched successfully")
                    Toast.makeText(this, "Clock opened - tap Save to set alarm", Toast.LENGTH_LONG)
                        .show()
                    return
                } catch (e: Exception) {
                    Log.e("SetAlarm", "❌ Failed to start activity: ${e.message}", e)
                    e.printStackTrace()
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e("SetAlarm", "❌ No app can handle SET_ALARM intent")
                Toast.makeText(this, "No clock app found on device", Toast.LENGTH_LONG).show()

                // Fallback: Try to open Samsung Clock directly
                try {
                    val samsungClockPackage = "com.sec.android.app.clockpackage"
                    val clockIntent = packageManager.getLaunchIntentForPackage(samsungClockPackage)
                    if (clockIntent != null) {
                        clockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(clockIntent)
                        Toast.makeText(
                            this,
                            "Samsung Clock opened - add alarm manually",
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    }
                } catch (e: Exception) {
                    Log.e("SetAlarm", "Failed to open Samsung Clock: ${e.message}")
                }
            }


            Log.d("SetAlarm", "===== END ALARM SET REQUEST =====")
        } catch (e: Exception) {
            Log.e("SetAlarm", "❌ Exception in setAlarm: ${e.message}", e)
            e.printStackTrace()
        }
    }

    private fun setTimer(seconds: Int, message: String = "Timer") {
        try {
            Log.d("SetTimer", "Setting timer for $seconds seconds with message: $message")

            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true) // Skip UI - start timer silently
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(packageManager) != null) {
                Log.d("SetTimer", "Starting timer for $seconds seconds with SKIP_UI=true")
                startActivity(intent)
                Log.d("SetTimer", "Successfully started timer intent - timer should start silently")
            } else {
                Log.e("SetTimer", "No clock app found")

                // Fallback: Try to open clock app directly
                Log.d("SetTimer", "Trying fallback: opening clock app directly")
                val clockPackages = listOf(
                    "com.google.android.deskclock",  // Google Clock
                    "com.android.deskclock",         // AOSP Clock
                    "com.sec.android.app.clockpackage", // Samsung Clock
                    "com.oneplus.deskclock"          // OnePlus Clock
                )

                for (pkg in clockPackages) {
                    val clockIntent = packageManager.getLaunchIntentForPackage(pkg)
                    if (clockIntent != null) {
                        clockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(clockIntent)
                        Log.d("SetTimer", "Opened $pkg as fallback")
                        return
                    }
                }

                Log.e("SetTimer", "Could not find any clock app")
            }
        } catch (e: Exception) {
            Log.e("SetTimer", "Error setting timer: ${e.message}", e)
        }
    }

    private fun manageAlarms(action: String) {
        try {
            Log.d("ManageAlarms", "Managing alarms: $action")

            val intent = when (action) {
                "show" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
                "dismiss" -> Intent(AlarmClock.ACTION_DISMISS_ALARM)
                "snooze" -> Intent(AlarmClock.ACTION_SNOOZE_ALARM)
                else -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(packageManager) != null) {
                Log.d("ManageAlarms", "Opening clock app for: $action")
                startActivity(intent)
            } else {
                Log.e("ManageAlarms", "No clock app found")
            }
        } catch (e: Exception) {
            Log.e("ManageAlarms", "Error managing alarms: ${e.message}")
        }
    }

    private fun toggleWifi(enable: Boolean, onResult: (Boolean) -> Unit) {
        try {
            Log.d("ToggleWifi", "Attempting to ${if (enable) "enable" else "disable"} WiFi")

            // On Android 10+ (API 29+), we can't programmatically toggle WiFi
            // We need to open WiFi settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d("ToggleWifi", "Android 10+: Opening WiFi settings")
                try {
                    // Try to use the WiFi panel first (Android 10+)
                    val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                    startActivity(panelIntent)
                    Log.d("ToggleWifi", "WiFi panel opened successfully")
                } catch (e: Exception) {
                    Log.e(
                        "ToggleWifi",
                        "WiFi panel not available, falling back to settings: ${e.message}"
                    )
                    // Fallback to regular WiFi settings if panel is not available
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    startActivity(intent)
                }
                Toast.makeText(
                    this,
                    "Please toggle WiFi manually",
                    Toast.LENGTH_SHORT
                ).show()
                onResult(true)
            } else {
                // For older Android versions (pre-Android 10)
                try {
                    @Suppress("DEPRECATION")
                    val wifiManager =
                        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

                    @Suppress("DEPRECATION")
                    val success = wifiManager.setWifiEnabled(enable)
                    Log.d("ToggleWifi", "WiFi toggle result: $success")
                    Toast.makeText(
                        this,
                        "WiFi ${if (enable) "enabled" else "disabled"}",
                        Toast.LENGTH_SHORT
                    ).show()
                    onResult(success)
                } catch (e: Exception) {
                    Log.e("ToggleWifi", "Error toggling WiFi: ${e.message}")
                    // Fallback: Open settings
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    startActivity(intent)
                    onResult(true)
                }
            }
        } catch (e: Exception) {
            Log.e("ToggleWifi", "Error in toggleWifi: ${e.message}")
            Toast.makeText(this, "Unable to toggle WiFi", Toast.LENGTH_SHORT).show()
            onResult(false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun toggleBluetooth(enable: Boolean, onResult: (Boolean) -> Unit) {
        try {
            Log.d(
                "ToggleBluetooth",
                "Attempting to ${if (enable) "enable" else "disable"} Bluetooth"
            )

            // On Android 13+ (API 33+), we need BLUETOOTH_CONNECT permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(
                        "ToggleBluetooth",
                        "Missing BLUETOOTH_CONNECT permission, opening settings"
                    )
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    startActivity(intent)
                    Toast.makeText(
                        this,
                        "Please toggle Bluetooth manually",
                        Toast.LENGTH_SHORT
                    ).show()
                    onResult(true)
                    return
                }
            }

            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                Log.e("ToggleBluetooth", "Device doesn't support Bluetooth")
                Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
                onResult(false)
                return
            }

            // On Android 13+, direct enable/disable is restricted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Log.d("ToggleBluetooth", "Android 13+: Opening Bluetooth settings")
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                startActivity(intent)
                Toast.makeText(
                    this,
                    "Please toggle Bluetooth manually",
                    Toast.LENGTH_SHORT
                ).show()
                onResult(true)
            } else {
                // For older Android versions
                try {
                    @Suppress("DEPRECATION")
                    val success = if (enable) {
                        bluetoothAdapter.enable()
                    } else {
                        bluetoothAdapter.disable()
                    }
                    Log.d("ToggleBluetooth", "Bluetooth toggle result: $success")
                    Toast.makeText(
                        this,
                        "Bluetooth ${if (enable) "enabled" else "disabled"}",
                        Toast.LENGTH_SHORT
                    ).show()
                    onResult(success)
                } catch (e: Exception) {
                    Log.e("ToggleBluetooth", "Error toggling Bluetooth: ${e.message}")
                    // Fallback: Open settings
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    startActivity(intent)
                    onResult(true)
                }
            }
        } catch (e: Exception) {
            Log.e("ToggleBluetooth", "Error in toggleBluetooth: ${e.message}")
            Toast.makeText(this, "Unable to toggle Bluetooth", Toast.LENGTH_SHORT).show()
            onResult(false)
        }
    }

    private fun toggleAirplaneMode(enable: Boolean, onResult: (Boolean) -> Unit) {
        try {
            Log.d(
                "ToggleAirplaneMode",
                "Attempting to ${if (enable) "enable" else "disable"} Airplane Mode"
            )

            // Airplane mode can no longer be toggled programmatically on modern Android
            // We need to open settings
            Log.d("ToggleAirplaneMode", "Opening Airplane Mode settings")
            val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "Please toggle Airplane Mode manually",
                Toast.LENGTH_SHORT
            ).show()
            onResult(true)
        } catch (e: Exception) {
            Log.e("ToggleAirplaneMode", "Error in toggleAirplaneMode: ${e.message}")
            Toast.makeText(this, "Unable to open Airplane Mode settings", Toast.LENGTH_SHORT)
                .show()
            onResult(false)
        }
    }

    private fun toggleDnd(enable: Boolean, onResult: (Boolean) -> Unit) {
        try {
            Log.d(
                "ToggleDnd",
                "Attempting to ${if (enable) "enable" else "disable"} Do Not Disturb"
            )

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Check if we have DND access permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!notificationManager.isNotificationPolicyAccessGranted) {
                    Log.d("ToggleDnd", "Missing DND permission, opening settings")
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    startActivity(intent)
                    Toast.makeText(
                        this,
                        "Please grant Do Not Disturb access, then try again",
                        Toast.LENGTH_LONG
                    ).show()
                    onResult(false)
                    return
                }

                // Toggle DND
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val mode = if (enable) {
                        NotificationManager.INTERRUPTION_FILTER_ALARMS // DND with alarms only
                    } else {
                        NotificationManager.INTERRUPTION_FILTER_ALL // Normal mode
                    }
                    notificationManager.setInterruptionFilter(mode)
                    Log.d("ToggleDnd", "DND toggled successfully")
                    Toast.makeText(
                        this,
                        "Do Not Disturb ${if (enable) "enabled" else "disabled"}",
                        Toast.LENGTH_SHORT
                    ).show()
                    onResult(true)
                }
            } else {
                Log.e("ToggleDnd", "DND not supported on this Android version")
                Toast.makeText(this, "DND not supported", Toast.LENGTH_SHORT).show()
                onResult(false)
            }
        } catch (e: Exception) {
            Log.e("ToggleDnd", "Error in toggleDnd: ${e.message}")
            Toast.makeText(this, "Unable to toggle Do Not Disturb", Toast.LENGTH_SHORT).show()
            onResult(false)
        }
    }

    private fun toggleMobileData(enable: Boolean, onResult: (Boolean) -> Unit) {
        try {
            Log.d(
                "ToggleMobileData",
                "Attempting to ${if (enable) "enable" else "disable"} Mobile Data"
            )

            // Mobile data can no longer be toggled programmatically on modern Android
            // We need to open settings
            Log.d("ToggleMobileData", "Opening Mobile Data settings")

            // Try to open mobile network settings
            try {
                val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general network settings
                val intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
                startActivity(intent)
            }

            Toast.makeText(
                this,
                "Please toggle Mobile Data manually",
                Toast.LENGTH_SHORT
            ).show()
            onResult(true)
        } catch (e: Exception) {
            Log.e("ToggleMobileData", "Error in toggleMobileData: ${e.message}")
            Toast.makeText(this, "Unable to open Mobile Data settings", Toast.LENGTH_SHORT).show()
            onResult(false)
        }
    }

    private fun playMusic(query: String, artist: String? = null) {
        try {
            val searchQuery = if (artist != null) {
                "$query $artist"
            } else {
                query
            }

            Log.d("PlayMusic", "Attempting to play: $searchQuery")

            // List of music apps to try in order
            val musicApps = listOf(
                "com.spotify.music" to "Spotify",
                "com.google.android.apps.youtube.music" to "YouTube Music",
                "com.amazon.mp3" to "Amazon Music",
                "com.apple.android.music" to "Apple Music",
                "deezer.android.app" to "Deezer",
                "com.soundcloud.android" to "SoundCloud",
                "com.gaana" to "Gaana",
                "com.jio.media.jiobeats" to "JioSaavn"
            )

            var musicAppFound = false

            // Try each music app
            for ((packageName, appName) in musicApps) {
                try {
                    val pm = packageManager
                    val appInfo = pm.getApplicationInfo(packageName, 0)

                    // App is installed, try to play music
                    Log.d("PlayMusic", "$appName is installed")

                    when (packageName) {
                        "com.spotify.music" -> {
                            // Try multiple Spotify intents in order of preference

                            // Method 1: Try to use Spotify's play URI (most direct, but may not work for search)
                            // This works if we have a specific track URI, but we're searching

                            // Method 2: Use media play intent with Spotify
                            try {
                                val mediaIntent =
                                    Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                                        setPackage("com.spotify.music")
                                        putExtra(
                                            android.provider.MediaStore.EXTRA_MEDIA_FOCUS,
                                            "vnd.android.cursor.item/*"
                                        )
                                        putExtra("query", searchQuery)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }

                                if (mediaIntent.resolveActivity(pm) != null) {
                                    Log.d(
                                        "PlayMusic",
                                        "Using Spotify media play intent: $searchQuery"
                                    )
                                    startActivity(mediaIntent)
                                    Toast.makeText(this, "Playing on Spotify", Toast.LENGTH_SHORT)
                                        .show()
                                    musicAppFound = true
                                    return
                                }
                            } catch (e: Exception) {
                                Log.d("PlayMusic", "Media play intent failed: ${e.message}")
                            }

                            // Method 3: Spotify search deep link (opens to search results)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("spotify:search:$searchQuery")
                                setPackage("com.spotify.music")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }

                            if (intent.resolveActivity(pm) != null) {
                                Log.d("PlayMusic", "Opening Spotify with search: $searchQuery")
                                startActivity(intent)
                                Toast.makeText(
                                    this,
                                    "Opening Spotify - tap to play",
                                    Toast.LENGTH_SHORT
                                ).show()
                                musicAppFound = true
                                return
                            } else {
                                // Fallback to Spotify web link
                                val webIntent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://open.spotify.com/search/$searchQuery")
                                    setPackage("com.spotify.music")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                if (webIntent.resolveActivity(pm) != null) {
                                    startActivity(webIntent)
                                    musicAppFound = true
                                    return
                                }
                            }
                        }

                        "com.google.android.apps.youtube.music" -> {
                            // YouTube Music search
                            val intent = Intent(Intent.ACTION_SEARCH).apply {
                                setPackage("com.google.android.apps.youtube.music")
                                putExtra("query", searchQuery)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }

                            if (intent.resolveActivity(pm) != null) {
                                Log.d(
                                    "PlayMusic",
                                    "Opening YouTube Music with search: $searchQuery"
                                )
                                startActivity(intent)
                                musicAppFound = true
                                return
                            }
                        }

                        else -> {
                            // Generic music intent for other apps
                            val intent = Intent(Intent.ACTION_SEARCH).apply {
                                setPackage(packageName)
                                putExtra("query", searchQuery)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }

                            if (intent.resolveActivity(pm) != null) {
                                Log.d("PlayMusic", "Opening $appName with search: $searchQuery")
                                startActivity(intent)
                                musicAppFound = true
                                return
                            }
                        }
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    // App not installed, continue to next
                    Log.d("PlayMusic", "$appName not installed")
                    continue
                }
            }

            // If no dedicated music app found, try generic media intent
            if (!musicAppFound) {
                Log.d("PlayMusic", "No music app found, trying generic media intent")

                try {
                    // Try Android's generic music search intent
                    val intent =
                        Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                            putExtra(
                                android.provider.MediaStore.EXTRA_MEDIA_FOCUS,
                                android.provider.MediaStore.Audio.Media.ENTRY_CONTENT_TYPE
                            )
                            putExtra(android.provider.MediaStore.EXTRA_MEDIA_TITLE, query)
                            if (artist != null) {
                                putExtra(android.provider.MediaStore.EXTRA_MEDIA_ARTIST, artist)
                            }
                            putExtra("query", searchQuery)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                    if (intent.resolveActivity(packageManager) != null) {
                        Log.d("PlayMusic", "Using generic media play intent")
                        startActivity(intent)
                        return
                    }
                } catch (e: Exception) {
                    Log.e("PlayMusic", "Generic media intent failed: ${e.message}")
                }

                // Last resort: open YouTube in browser
                try {
                    Log.d("PlayMusic", "Opening YouTube in browser as last resort")
                    val searchQueryEncoded = Uri.encode(searchQuery)
                    val webIntent = Intent(Intent.ACTION_VIEW).apply {
                        data =
                            Uri.parse("https://www.youtube.com/results?search_query=$searchQueryEncoded")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(webIntent)
                    Toast.makeText(this, "Opening YouTube in browser", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("PlayMusic", "All music playback methods failed: ${e.message}")
                    Toast.makeText(
                        this,
                        "Unable to play music. Please install a music app.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e("PlayMusic", "Error in playMusic: ${e.message}")
            Toast.makeText(this, "Unable to play music", Toast.LENGTH_SHORT).show()
        }
    }

    // Store callbacks for screenshot and recording
    var screenshotCallback: ((Boolean, String) -> Unit)? = null
    var recordingCallback: ((Boolean, String) -> Unit)? = null

    fun requestScreenshotPermission(): Intent? {
        return mediaProjectionManager?.createScreenCaptureIntent()
    }

    fun requestRecordingPermission(): Intent? {
        if (isRecording) {
            Toast.makeText(this, "Already recording", Toast.LENGTH_SHORT).show()
            return null
        }
        return mediaProjectionManager?.createScreenCaptureIntent()
    }

    fun stopScreenRecording(onResult: (Boolean, String) -> Unit) {
        try {
            if (!isRecording) {
                onResult(false, "Not currently recording")
                return
            }

            Log.d("ScreenRecording", "Stopping screen recording...")

            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            virtualDisplay?.release()
            virtualDisplay = null

            mediaProjection?.stop()
            mediaProjection = null

            isRecording = false

            Log.d("ScreenRecording", "Recording stopped successfully")
            onResult(true, "Recording saved to gallery")
            Toast.makeText(this, "Recording saved!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("ScreenRecording", "Error stopping recording: ${e.message}")
            isRecording = false
            onResult(false, "Error stopping recording: ${e.message}")
        }
    }

    internal fun captureScreenshot(resultCode: Int, data: Intent) {
        try {
            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)

            val imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                1
            )

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "Screenshot",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null
            )

            // Wait a bit for the display to be ready
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val image = imageReader.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * screenWidth

                        val bitmap = Bitmap.createBitmap(
                            screenWidth + rowPadding / pixelStride,
                            screenHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                        image.close()

                        // Save bitmap
                        saveScreenshot(bitmap)

                        // Clean up
                        virtualDisplay?.release()
                        mediaProjection?.stop()
                        imageReader.close()

                        Toast.makeText(this, "Screenshot saved!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("Screenshot", "Error capturing: ${e.message}")
                    Toast.makeText(this, "Screenshot failed", Toast.LENGTH_SHORT).show()
                }
            }, 100)

        } catch (e: Exception) {
            Log.e("Screenshot", "Error in captureScreenshot: ${e.message}")
            Toast.makeText(this, "Screenshot failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveScreenshot(bitmap: Bitmap) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "Screenshot_$timestamp.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Screenshots"
                    )
                }

                val uri =
                    contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    Log.d("Screenshot", "Saved to gallery: $filename")
                }
            } else {
                // Legacy method for older Android versions
                val picturesDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val screenshotsDir = File(picturesDir, "Screenshots")
                if (!screenshotsDir.exists()) {
                    screenshotsDir.mkdirs()
                }

                val file = File(screenshotsDir, filename)
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                // Notify media scanner
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                intent.data = Uri.fromFile(file)
                sendBroadcast(intent)

                Log.d("Screenshot", "Saved to: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("Screenshot", "Error saving screenshot: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    internal fun startRecording(resultCode: Int, data: Intent) {
        try {
            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)

            // Create output file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "Recording_$timestamp.mp4"

            val outputFile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/ScreenRecordings"
                    )
                }

                val uri =
                    contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openFileDescriptor(it, "w")?.fileDescriptor
                }
            } else {
                // Legacy method
                val moviesDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val recordingsDir = File(moviesDir, "ScreenRecordings")
                if (!recordingsDir.exists()) {
                    recordingsDir.mkdirs()
                }

                val file = File(recordingsDir, filename)
                FileOutputStream(file).fd
            }

            if (outputFile == null) {
                Log.e("ScreenRecording", "Could not create output file")
                Toast.makeText(this, "Failed to create recording file", Toast.LENGTH_SHORT).show()
                return
            }

            // Setup MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(outputFile)
                setVideoSize(screenWidth, screenHeight)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(5 * 1024 * 1024) // 5Mbps
                setVideoFrameRate(30)
                prepare()
            }

            // Create virtual display
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenRecording",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface,
                null,
                null
            )

            // Start recording
            mediaRecorder?.start()
            isRecording = true

            Log.d("ScreenRecording", "Recording started successfully")
            Toast.makeText(this, "Recording started!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("ScreenRecording", "Error in startRecording: ${e.message}", e)
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show()
            isRecording = false
        }
    }

    fun startWakeWordDetection(onWakeWordDetected: () -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("WakeWord", "Speech recognition not available")
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
            return
        }

        this.onWakeWordDetected = onWakeWordDetected
        isWakeWordListening = true
        isWakeWordEnabled.value = true

        Log.d("WakeWord", "Starting wake word detection...")
        startListeningForWakeWord()
    }

    fun stopWakeWordDetection() {
        isWakeWordListening = false
        isWakeWordEnabled.value = false
        speechRecognizer?.cancel()
        Log.d("WakeWord", "Stopped wake word detection")
    }

    internal fun startListeningForWakeWord() {
        if (!isWakeWordListening) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("WakeWord", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("WakeWord", "Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Audio level changed - can be used for visualization
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("WakeWord", "Speech ended")
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }
                Log.d("WakeWord", "Error: $errorMessage")

                // Restart listening after a short delay (except for permission errors)
                if (error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS && isWakeWordListening) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        startListeningForWakeWord()
                    }, 100)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                var wakeWordFound = false

                matches?.let {
                    for (result in it) {
                        Log.d("WakeWord", "Heard: $result")
                        val lowerResult = result.lowercase()

                        // Check for wake words
                        if (lowerResult.contains("hey jarvis") ||
                            lowerResult.contains("hey assistant") ||
                            lowerResult.contains("ok jarvis") ||
                            lowerResult.contains("jarvis")
                        ) {

                            Log.d("WakeWord", "Wake word detected!")
                            wakeWordFound = true

                            // Speak response
                            speakResponse("Hey, I'm listening")

                            // Trigger the callback after a short delay for TTS
                            Handler(Looper.getMainLooper()).postDelayed({
                                onWakeWordDetected?.invoke()
                                // Note: Wake word will restart after voice input completes
                            }, 1000)

                            return
                        }
                    }
                }

                // Restart listening if wake word not detected
                if (isWakeWordListening && !wakeWordFound) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        startListeningForWakeWord()
                    }, 100)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.let {
                    for (result in it) {
                        val lowerResult = result.lowercase()
                        if (lowerResult.contains("hey jarvis") ||
                            lowerResult.contains("hey assistant") ||
                            lowerResult.contains("ok jarvis") ||
                            lowerResult.contains("jarvis")
                        ) {

                            Log.d("WakeWord", "Wake word detected in partial results!")
                            speechRecognizer?.cancel()

                            // Speak response
                            speakResponse("Hey, I'm listening")

                            // Trigger the callback
                            Handler(Looper.getMainLooper()).postDelayed({
                                onWakeWordDetected?.invoke()
                                // Note: Wake word will restart after voice input completes
                            }, 1000)

                            return
                        }
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("WakeWord", "Error starting speech recognition: ${e.message}")
            if (isWakeWordListening) {
                Handler(Looper.getMainLooper()).postDelayed({
                    startListeningForWakeWord()
                }, 1000)
            }
        }
    }

    private fun speakResponse(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "WAKE_WORD_RESPONSE")
        Log.d("TTS", "Speaking: $text")
    }

    fun speakText(text: String) {
        if (textToSpeech != null) {
            // Clean up the text for better speech
            val cleanText = text
                .replace(Regex("\\*\\*"), "") // Remove markdown bold
                .replace(Regex("\\*"), "") // Remove markdown italic
                .replace(Regex("```[a-z]*"), "") // Remove code blocks
                .replace(Regex("```"), "")
                .replace(Regex("\\n+"), ". ") // Replace newlines with pauses
                .trim()

            if (cleanText.isNotEmpty()) {
                textToSpeech?.speak(
                    cleanText,
                    TextToSpeech.QUEUE_ADD,
                    null,
                    "RESPONSE_${System.currentTimeMillis()}"
                )
                Log.d("TTS", "Speaking response: $cleanText")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWakeWordDetection()
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
    }

    companion object {
        private const val SCREENSHOT_REQUEST_CODE = 1001
        private const val SCREEN_RECORD_REQUEST_CODE = 1002
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    activity: MainActivity,
    viewModel: ChatViewModel = viewModel(),
    onToggleFlashlight: (Boolean) -> Unit,
    onAddReminder: (String, Long, (Boolean) -> Unit) -> Unit,
    onCreateNote: (String, String, (Boolean) -> Unit) -> Unit,
    onShowNote: (String, (String?) -> Unit) -> Unit,
    onOpenApp: (String, (Boolean) -> Unit) -> Unit,
    onCall: (String) -> Unit,
    onSendSms: (String, String) -> Unit,
    onSearchYoutube: (String) -> Unit,
    onSendWhatsApp: (String, String) -> Unit,
    onSendEmail: (String, String, String) -> Unit,
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
    onStopRecording: ((Boolean, String) -> Unit) -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val currentModelId by viewModel.currentModelId.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showModelSelector by remember { mutableStateOf(false) }
    val isWakeWordEnabled by activity.isWakeWordEnabled

    var textToProcessOnPermission by remember { mutableStateOf<String?>(null) }

    // Wrapper function for email generation that uses ViewModel
    val onGenerateEmailWrapper: (String, String, String) -> Unit = { recipient, subject, context ->
        viewModel.generateEmailDraft(recipient, subject, context) { emailBody ->
            onSendEmail(recipient, subject, emailBody)
        }
    }

    // Screenshot permission launcher
    val screenshotPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            activity.captureScreenshot(result.resultCode, result.data!!)
            activity.screenshotCallback?.invoke(true, "Screenshot captured!")
        } else {
            Log.e("Screenshot", "Permission denied")
            Toast.makeText(activity, "Screenshot permission denied", Toast.LENGTH_SHORT).show()
            activity.screenshotCallback?.invoke(false, "Permission denied")
        }
        activity.screenshotCallback = null
    }

    // Screen recording permission launcher
    val recordingPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            activity.startRecording(result.resultCode, result.data!!)
            activity.recordingCallback?.invoke(true, "Recording started!")
        } else {
            Log.e("ScreenRecording", "Permission denied")
            Toast.makeText(activity, "Screen recording permission denied", Toast.LENGTH_SHORT)
                .show()
            activity.recordingCallback?.invoke(false, "Permission denied")
        }
        activity.recordingCallback = null
    }

    // Screenshot callback
    val onTakeScreenshot: ((Boolean, String) -> Unit) -> Unit = { callback ->
        activity.screenshotCallback = callback
        val intent = activity.requestScreenshotPermission()
        if (intent != null) {
            screenshotPermissionLauncher.launch(intent)
        } else {
            callback(false, "Unable to create permission request")
            activity.screenshotCallback = null
        }
    }

    // Recording start callback
    val onStartRecording: ((Boolean, String) -> Unit) -> Unit =
        fun(callback: (Boolean, String) -> Unit) {
            if (activity.isRecording) {
                callback(false, "Already recording")
                return
        }
        activity.recordingCallback = callback
        val intent = activity.requestRecordingPermission()
        if (intent != null) {
            recordingPermissionLauncher.launch(intent)
        } else {
            callback(false, "Unable to create permission request")
            activity.recordingCallback = null
        }
    }

    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        textToProcessOnPermission?.let { text ->
            val shouldSpeak = activity.shouldRespondWithVoice
            if (isGranted) {
                viewModel.processInput(
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
                    onGenerateEmailWrapper,
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
            activity.shouldRespondWithVoice = false
        }
        textToProcessOnPermission = null
    }

    val requestCalendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        textToProcessOnPermission?.let { text ->
            val shouldSpeak = activity.shouldRespondWithVoice
            if (permissions[Manifest.permission.WRITE_CALENDAR] == true) {
                viewModel.processInput(
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
                    onGenerateEmailWrapper,
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
            activity.shouldRespondWithVoice = false
        }
        textToProcessOnPermission = null
    }

    val requestCallPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        textToProcessOnPermission?.let { text ->
            val shouldSpeak = activity.shouldRespondWithVoice
            if (permissions[Manifest.permission.CALL_PHONE] == true ||
                permissions[Manifest.permission.READ_CONTACTS] == true
            ) {
                viewModel.processInput(
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
                    onGenerateEmailWrapper,
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
            activity.shouldRespondWithVoice = false
        }
        textToProcessOnPermission = null
    }

    val requestSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        textToProcessOnPermission?.let { text ->
            val shouldSpeak = activity.shouldRespondWithVoice
            if (permissions[Manifest.permission.SEND_SMS] == true ||
                permissions[Manifest.permission.READ_CONTACTS] == true
            ) {
                viewModel.processInput(
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
                    onGenerateEmailWrapper,
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
            activity.shouldRespondWithVoice = false
        }
        textToProcessOnPermission = null
    }

    // Set up TTS callback
    LaunchedEffect(Unit) {
        viewModel.onSpeakResponse = { text ->
            activity.speakText(text)
        }
    }

    fun handleUserInput(text: String) {
        viewModel.addUserMessage(text)
        val lowerCaseText = text.lowercase()
        val shouldSpeak = activity.shouldRespondWithVoice

        if (lowerCaseText.contains("torch") || lowerCaseText.contains("flashlight")) {
            textToProcessOnPermission = text
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else if (lowerCaseText.contains("remind")) {
            textToProcessOnPermission = text
            requestCalendarPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        } else if (lowerCaseText.startsWith("call ")) {
            textToProcessOnPermission = text
            requestCallPermissionLauncher.launch(
                arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)
            )
        } else if (lowerCaseText.startsWith("text ") || lowerCaseText.startsWith("sms ") ||
            lowerCaseText.startsWith("message ") || lowerCaseText.startsWith("send a text") ||
            lowerCaseText.startsWith("send a message") || lowerCaseText.startsWith("send a sms")
        ) {
            textToProcessOnPermission = text
            requestSmsPermissionLauncher.launch(
                arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
            )
        } else {
            // Use the new processInput which handles multi-task and single-task
            viewModel.processInput(
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
                onGenerateEmailWrapper,
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

        // Reset voice flag after processing
        activity.shouldRespondWithVoice = false
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0)
            if (!spokenText.isNullOrBlank()) {
                // Mark that this input came from voice
                activity.shouldRespondWithVoice = true
                handleUserInput(spokenText)
            }
        } else {
            // User cancelled, no voice response needed
            activity.shouldRespondWithVoice = false
        }
        // Restart wake word detection after voice input is done (whether successful or cancelled)
        if (activity.isWakeWordEnabled.value) {
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d("WakeWord", "Restarting wake word detection after voice input")
                activity.startListeningForWakeWord()
            }, 500)
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

    // Wake word permission launcher
    val wakeWordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            activity.startWakeWordDetection {
                // When wake word is detected, trigger voice input
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")
                }
                speechRecognizerLauncher.launch(intent)
            }
            Toast.makeText(
                activity,
                "Wake word detection enabled! Say 'Hey Jarvis'",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                activity,
                "Microphone permission required for wake word",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Chat") },
                actions = {
                    // Wake word toggle button
                    IconButton(onClick = {
                        if (isWakeWordEnabled) {
                            activity.stopWakeWordDetection()
                            Toast.makeText(
                                activity,
                                "Wake word detection disabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // Request microphone permission
                            wakeWordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }) {
                        Icon(
                            imageVector = if (isWakeWordEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (isWakeWordEnabled) "Disable wake word" else "Enable wake word",
                            tint = if (isWakeWordEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
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
