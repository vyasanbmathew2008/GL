package com.example.spysim

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    private var imageCount = 3
    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showMandatoryWarning()
    }

    private fun showMandatoryWarning() {
        AlertDialog.Builder(this)
            .setTitle("SECURITY TEST SIMULATION")
            .setMessage(
                "This app is an offline Android security test simulator. It creates synthetic JPEGs " +
                "and records simulated upload events for analysis. No gallery access, network requests, " +
                "Telegram messages, or real photos are used.\n\nRun only in an authorized test environment."
            )
            .setCancelable(false)
            .setPositiveButton("Start Simulation") { _, _ -> showDashboard() }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .show()
    }

    private fun showDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        root.addView(TextView(this).apply { text = "Android Security Test Simulator (Offline)"; textSize = 22f })
        statusView = TextView(this).apply {
            text = "Status: READY"
            textSize = 16f
            setPadding(0, 16, 0, 16)
        }
        root.addView(statusView)
        root.addView(button("Set Synthetic Image Count") { showConfiguration() })
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
        root.addView(TextView(this).apply {
            text = "Simulation Event Log (UTC)"
            textSize = 18f
            setPadding(0, 18, 0, 8)
        })
        logView = TextView(this).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            textIsSelectable = true
        }
        val scroll = ScrollView(this).apply { addView(logView) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        EventLog.add("APP", "Offline dashboard opened after user consent")
        refreshLog()
    }

    private fun button(label: String, action: () -> Unit) =
        Button(this).apply { text = label; setOnClickListener { action() } }

    private fun showConfiguration() {
        val input = android.widget.EditText(this).apply {
            setText(imageCount.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this).setTitle("Synthetic image count (1–20)").setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                try {
                    val n = input.text.toString().toInt()
                    require(n in 1..20) { "Choose a count from 1 to 20" }
                    imageCount = n
                    EventLog.add("CONFIG", "Synthetic image count set to $imageCount")
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
        statusView.text = "Status: RUNNING (OFFLINE)"
        val count = imageCount
        EventLog.add("SIMULATION_START", "offline synthetic-only run count=$count; network disabled")
        refreshLog()
        worker = Thread({
            try {
                for (index in 1..count) {
                    if (!running.get() || Thread.currentThread().isInterrupted) break
                    val file = SyntheticImageFactory.create(applicationContext, index)
                    try {
                        EventLog.add("SYNTHETIC_IMAGE_CREATED", "id=$index filename=${file.name} bytes=${file.length()}")
                        EventLog.add("SIMULATED_UPLOAD", "id=$index result=NOT_SENT reason=offline_mode")
                        mainHandler.post {
                            statusView.text = "Status: SIMULATED EVENT $index OF $count (NOT SENT)"
                            refreshLog()
                        }
                    } finally {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                EventLog.add("SIMULATION_ERROR", "${e.javaClass.simpleName}: ${e.message}")
            } finally {
                running.set(false)
                mainHandler.post {
                    startButton.isEnabled = true
                    stopButton.isEnabled = false
                    statusView.text = "Status: STOPPED (OFFLINE)"
                    EventLog.add("SIMULATION_STOP", "Offline worker finished")
                    refreshLog()
                }
            }
        }, "OfflineSyntheticWorker").also { it.start() }
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
        logView.text = EventLog.snapshot().joinToString("\n") {
            "${it.timestamp} [${it.type}] ${it.message}"
        }
    }

    override fun onDestroy() {
        running.set(false)
        worker?.interrupt()
        super.onDestroy()
    }
}
