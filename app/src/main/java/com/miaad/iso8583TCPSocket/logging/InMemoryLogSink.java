package com.miaad.iso8583TCPSocket.logging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryLogSink implements LogSink {
    private final int capacity;
    private final List<LogEntry> buffer;

    public InMemoryLogSink(int capacity) {
        this.capacity = Math.max(10, capacity);
        this.buffer = new ArrayList<>(this.capacity);
    }

    @Override
    public synchronized void accept(LogEntry entry) {
        if (buffer.size() >= capacity) {
            buffer.remove(0);
        }
        buffer.add(entry);
    }

    public synchronized List<LogEntry> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(buffer));
    }

    public synchronized void clear() {
        buffer.clear();
    }
}


