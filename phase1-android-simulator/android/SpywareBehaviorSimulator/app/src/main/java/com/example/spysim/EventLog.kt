package com.example.spysim

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class TestEvent(val timestamp: String, val type: String, val message: String)

object EventLog {
    private val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val events = mutableListOf<TestEvent>()

    @Synchronized fun add(type: String, message: String): TestEvent {
        val event = TestEvent(formatter.format(Date()), type, message)
        events.add(event)
        return event
    }
    @Synchronized fun snapshot(): List<TestEvent> = events.toList()
    @Synchronized fun clear() { events.clear() }
}
