package com.example.spysim

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var tokenInput: EditText
    private lateinit var chatIdInput: EditText
    private lateinit var statusView: TextView
    private lateinit var folderView: TextView
    private lateinit var sendButton: Button

    private val testImageFolder: File
        get() = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "TelegramTestQueue")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showConsentWarning()
    }

    private fun showConsentWarning() {
        AlertDialog.Builder(this)
            .setTitle("Test image transfer — permission required")
            .setMessage(
                "Press Start only if you have permission to share every image in this app's " +
                "dedicated TelegramTestQueue folder. The app will send all supported image files " +
                "in that folder to the Telegram chat ID you enter. It will not scan your gallery " +
                "or other device folders. Images leave this device when sent. The bot token is " +
                "entered at runtime and is not saved by this app or committed to GitHub."
            )
            .setCancelable(false)
            .setPositiveButton("Continue") { _, _ -> showDashboard() }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .show()
    }

    private fun showDashboard() {
        testImageFolder.mkdirs()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        root.addView(TextView(this).apply {
            text = "Send test-folder images to Telegram"
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = "Place only images you intend to transmit in the folder below. Start sends every supported image currently in that folder."
            textSize = 14f
            setPadding(0, 10, 0, 12)
        })
        folderView = TextView(this).apply {
            text = "Test folder:\n${testImageFolder.absolutePath}"
            textSize = 12f
            setPadding(0, 0, 0, 12)
        }
        root.addView(folderView)
        tokenInput = EditText(this).apply {
            hint = "Telegram bot token"
            singleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(tokenInput)
        chatIdInput = EditText(this).apply {
            hint = "Destination chat ID"
            singleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        root.addView(chatIdInput)
        sendButton = button("Start sending test-folder images") { sendFolderImages() }
        root.addView(sendButton)
        statusView = TextView(this).apply {
            text = "Status: READY"
            textSize = 15f
            setPadding(0, 16, 0, 8)
        }
        root.addView(statusView)
        root.addView(button("Refresh folder count") { updateFolderCount() })
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        updateFolderCount()
    }

    private fun button(label: String, action: () -> Unit) =
        Button(this).apply { text = label; setOnClickListener { action() } }

    private fun listImages(): List<File> {
        testImageFolder.mkdirs()
        val extensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
        return testImageFolder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in extensions }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    private fun updateFolderCount() {
        val count = listImages().size
        folderView.text = "Test folder:\n${testImageFolder.absolutePath}\nImages ready: $count"
        if (::statusView.isInitialized) statusView.text = "Status: READY — $count image(s) in test folder"
    }

    private fun sendFolderImages() {
        val token = tokenInput.text.toString().trim()
        val chatId = chatIdInput.text.toString().trim()
        if (token.isBlank() || chatId.isBlank()) {
            statusView.text = "Status: Enter bot token and chat ID first"
            return
        }
        val images = listImages()
        if (images.isEmpty()) {
            statusView.text = "Status: No images found. Add test images to the folder shown above."
            return
        }
        sendButton.isEnabled = false
        statusView.text = "Status: Sending ${images.size} test-folder image(s)…"
        executor.execute {
            var sent = 0
            val errors = mutableListOf<String>()
            images.forEachIndexed { index, file ->
                try {
                    sendPhoto(token, chatId, file, index + 1)
                    sent++
                } catch (e: Exception) {
                    errors.add("${file.name}: ${e.message ?: e.javaClass.simpleName}")
                }
            }
            runOnUiThread {
                statusView.text = if (errors.isEmpty()) {
                    "Status: Sent $sent/${images.size} image(s) successfully"
                } else {
                    "Status: Sent $sent/${images.size}; failed: ${errors.joinToString("; ").take(500)}"
                }
                sendButton.isEnabled = true
            }
        }
    }

    private fun sendPhoto(token: String, chatId: String, file: File, index: Int) {
        val boundary = "----AndroidTelegram" + UUID.randomUUID().toString().replace("-", "")
        val connection = (URL("https://api.telegram.org/bot$token/sendPhoto").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            DataOutputStream(connection.outputStream).use { out ->
                fun field(name: String, value: String) {
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                    out.write(value.toByteArray(Charsets.UTF_8))
                    out.writeBytes("\r\n")
                }
                field("chat_id", chatId)
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"${file.name}\"\r\n")
                out.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                FileInputStream(file).use { input -> input.copyTo(out) }
                out.writeBytes("\r\n--$boundary--\r\n")
            }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299 || !response.contains("\"ok\":true")) {
                throw IllegalStateException("Telegram HTTP $code: ${response.take(250)}")
            }
        } finally {
            connection.disconnect()
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
