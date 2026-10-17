package com.example.spysim

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    private var serverAddress = "http://10.0.2.2:8000"
    private var imageCount = 3
    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No network access or image generation before explicit confirmation.
        showMandatoryWarning()
    }

    private fun showMandatoryWarning() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ SECURITY TEST SIMULATION")
            .setMessage(
                "This application simulates spyware-like behavior for Android antivirus research. " +
                "It demonstrates simulated image-access attempts and Telegram Bot API communication patterns.\n\n" +
                "Only synthetic test images and a controlled local server are used. " +
                "No real personal photos or credentials will be collected or transmitted.\n\n" +
                "Use only in an authorized, isolated testing environment."
            )
            .setCancelable(false)
            .setPositiveButton("Start Simulation") { _, _ -> showDashboard(); startSimulation() }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .show()
    }

    private fun showDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        root.addView(TextView(this).apply { text = "Android Security Test Simulator"; textSize = 22f })
        statusView = TextView(this).apply { text = "Status: READY"; textSize = 16f; setPadding(0, 16, 0, 16) }
        root.addView(statusView)
        root.addView(button("Test Configuration") { showConfiguration() })
        startButton = button("Start Simulation") { startSimulation() }
        root.addView(startButton)
        stopButton = button("Stop Simulation") { stopSimulation() }
        stopButton.isEnabled = false
        root.addView(stopButton)
        root.addView(button("Clear Event Log") {
            EventLog.clear()
            EventLog.add("LOG", "Displayed log cleared")
            refreshLog()
        })
        root.addView(TextView(this).apply { text = "Network Event Log (UTC)"; textSize = 18f; setPadding(0, 18, 0, 8) })
        logView = TextView(this).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            textIsSelectable = true
        }
        val scroll = ScrollView(this).apply { addView(logView) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        EventLog.add("APP", "Dashboard opened after user consent")
        refreshLog()
    }

    private fun button(label: String, action: () -> Unit) =
        Button(this).apply { text = label; setOnClickListener { action() } }

    private fun showConfiguration() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        fun field(label: String, value: String, enabled: Boolean = true): EditText {
            layout.addView(TextView(this).apply { text = label })
            return EditText(this).apply { setText(value); isSingleLine = true; isEnabled = enabled }.also { layout.addView(it) }
        }
        val url = field("Local mock server URL", serverAddress)
        field("Placeholder bot token (fixed)", "TEST_BOT_TOKEN", false)
        field("Placeholder chat ID (fixed)", "TEST_CHAT_ID", false)
        val count = field("Synthetic image count (1–20)", imageCount.toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this).setTitle("Test Configuration").setView(layout)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                try {
                    val n = count.text.toString().toInt()
                    require(n in 1..20) { "Image count must be between 1 and 20" }
                    MockApiClient.validateAddress(url.text.toString())
                    serverAddress = url.text.toString().trim()
                    imageCount = n
                    EventLog.add("CONFIG", "Configuration saved count=$imageCount destination=$serverAddress")
                    statusView.text = "Status: CONFIGURATION SAVED"
                    refreshLog()
                } catch (e: Exception) {
                    statusView.text = "Status: CONFIGURATION ERROR — ${e.message}"
                }
            }.show()
    }

    private fun startSimulation() {
        if (!running.compareAndSet(false, true)) return
        startButton.isEnabled = false
        stopButton.isEnabled = true
        statusView.text = "Status: RUNNING"
        EventLog.add("SIMULATION_START", "count=$imageCount")
        refreshLog()
        val count = imageCount
        val address = serverAddress
        worker = Thread({
            try {
                for (index in 1..count) {
                    if (!running.get() || Thread.currentThread().isInterrupted) break
                    val file = SyntheticImageFactory.create(applicationContext, index)
                    EventLog.add("SYNTHETIC_IMAGE_CREATED", "id=$index filename=${file.name} bytes=${file.length()}")
                    try {
                        val result = MockApiClient.upload(address, file, index)
                        mainHandler.post {
                            statusView.text = "Status: ${result.message}"
                            refreshLog()
                        }
                    } finally { file.delete() }
                }
            } catch (e: Exception) {
                EventLog.add("SIMULATION_ERROR", "${e.javaClass.simpleName}: ${e.message}")
            } finally {
                running.set(false)
                mainHandler.post {
                    startButton.isEnabled = true
                    stopButton.isEnabled = false
                    statusView.text = "Status: STOPPED"
                    EventLog.add("SIMULATION_STOP", "Worker finished")
                    refreshLog()
                }
            }
        }, "SyntheticUploadWorker").also { it.start() }
    }

    private fun stopSimulation() {
        if (!running.getAndSet(false)) return
        worker?.interrupt()
        EventLog.add("SIMULATION_STOP_REQUESTED", "User pressed Stop")
        statusView.text = "Status: STOP REQUESTED"
        refreshLog()
    }

    private fun refreshLog() {
        if (!::logView.isInitialized) return
        logView.text = EventLog.snapshot().joinToString("\n") { "${it.timestamp} [${it.type}] ${it.message}" }
    }

    override fun onDestroy() {
        running.set(false)
        worker?.interrupt()
        super.onDestroy()
    }
}
