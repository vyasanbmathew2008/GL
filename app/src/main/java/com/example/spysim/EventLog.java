package com.example.spysim;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class EventLog {
    public static final class TestEvent {
        public final String timestamp;
        public final String type;
        public final String message;

        public TestEvent(String timestamp, String type, String message) {
            this.timestamp = timestamp;
            this.type = type;
            this.message = message;
        }
    }

    private static final SimpleDateFormat FORMATTER;
    private static final List<TestEvent> EVENTS = new ArrayList<>();

    static {
        FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        FORMATTER.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private EventLog() { }

    public static synchronized TestEvent add(String type, String message) {
        TestEvent event = new TestEvent(FORMATTER.format(new Date()), type, message);
        EVENTS.add(event);
        return event;
    }

    public static synchronized List<TestEvent> snapshot() {
        return new ArrayList<>(EVENTS);
    }

    public static synchronized void clear() {
        EVENTS.clear();
    }
}
