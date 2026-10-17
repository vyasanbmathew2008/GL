package com.example.spysim

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val selectedImages = mutableListOf<Uri>()
    private lateinit var tokenInput: EditText
    private lateinit var chatIdInput: EditText
    private lateinit var statusView: TextView
    private lateinit var selectionView: TextView
    private lateinit var sendButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showConsentWarning()
    }

    private fun showConsentWarning() {
        AlertDialog.Builder(this)
            .setTitle("Image sharing — permission required")
            .setMessage(
                "When you press Start, you agree to use this app to send images you explicitly " +
                "select from this device to the Telegram chat ID you enter. Selected images will " +
                "leave this device and be delivered to Telegram. Do not select images you do not " +
                "have permission to share. The app does not scan or upload your whole gallery. " +
                "Your bot token is entered at runtime and is not saved by this app or committed to GitHub."
            )
            .setCancelable(false)
            .setPositiveButton("Start") { _, _ -> showDashboard() }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .show()
    }

    private fun showDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        root.addView(TextView(this).apply {
            text = "Send selected images to Telegram"
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = "Enter a test bot token and destination chat ID. Credentials remain in memory only."
            textSize = 14f
            setPadding(0, 10, 0, 14)
        })
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
        root.addView(button("Choose images") { chooseImages() })
        selectionView = TextView(this).apply {
            text = "No images selected"
            setPadding(0, 12, 0, 12)
        }
        root.addView(selectionView)
        sendButton = button("Send selected images") { sendSelectedImages() }
        sendButton.isEnabled = false
        root.addView(sendButton)
        statusView = TextView(this).apply {
            text = "Status: READY"
            textSize = 15f
            setPadding(0, 16, 0, 8)
        }
        root.addView(statusView)
        root.addView(button("Clear selection") {
            selectedImages.clear()
            selectionView.text = "No images selected"
            sendButton.isEnabled = false
            statusView.text = "Status: READY"
        })
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun button(label: String, action: () -> Unit) =
        Button(this).apply { text = label; setOnClickListener { action() } }

    private fun chooseImages() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, PICK_IMAGES)
    }

    @Deprecated("Uses the platform document picker for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_IMAGES || resultCode != RESULT_OK || data == null) return
        selectedImages.clear()
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) selectedImages.add(clip.getItemAt(i).uri)
        } ?: data.data?.let { selectedImages.add(it) }
        selectionView.text = if (selectedImages.isEmpty()) "No images selected"
            else "${selectedImages.size} image(s) selected"
        sendButton.isEnabled = selectedImages.isNotEmpty()
        statusView.text = "Status: SELECTION READY"
    }

    private fun sendSelectedImages() {
        val token = tokenInput.text.toString().trim()
        val chatId = chatIdInput.text.toString().trim()
        if (token.isBlank() || chatId.isBlank()) {
            statusView.text = "Status: Enter bot token and chat ID first"
            return
        }
        if (selectedImages.isEmpty()) {
            statusView.text = "Status: Choose at least one image"
            return
        }
        val images = selectedImages.toList()
        sendButton.isEnabled = false
        statusView.text = "Status: Sending ${images.size} selected image(s)…"
        executor.execute {
            var sent = 0
            val errors = mutableListOf<String>()
            images.forEachIndexed { index, uri ->
                try {
                    sendPhoto(token, chatId, uri, index + 1)
                    sent++
                } catch (e: Exception) {
                    errors.add("Image ${index + 1}: ${e.message ?: e.javaClass.simpleName}")
                }
            }
            runOnUiThread {
                statusView.text = if (errors.isEmpty()) {
                    "Status: Sent $sent/${images.size} image(s) successfully"
                } else {
                    "Status: Sent $sent/${images.size}; failed: ${errors.joinToString("; ").take(500)}"
                }
                sendButton.isEnabled = selectedImages.isNotEmpty()
            }
        }
    }

    private fun sendPhoto(token: String, chatId: String, uri: Uri, index: Int) {
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
                out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"selected_$index.jpg\"\r\n")
                out.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to open selected image" }
                    input.copyTo(out)
                }
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

    companion object {
        private const val PICK_IMAGES = 1001
    }
}
