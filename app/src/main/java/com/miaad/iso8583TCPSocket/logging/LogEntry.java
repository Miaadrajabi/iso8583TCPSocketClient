package com.miaad.iso8583TCPSocket.logging;

public class LogEntry {
    public final long timestampMs;
    public final LogLevel level;
    public final String threadName;
    public final String className;
    public final String methodName;
    public final String message;
    public final String details;
    public final byte[] payload; // optional (may be null)
    public final String direction; // SEND / RECV / INTERNAL

    public LogEntry(long timestampMs,
                    LogLevel level,
                    String threadName,
                    String className,
                    String methodName,
                    String message,
                    String details,
                    byte[] payload,
                    String direction) {
        this.timestampMs = timestampMs;
        this.level = level;
        this.threadName = threadName;
        this.className = className;
        this.methodName = methodName;
        this.message = message;
        this.details = details;
        this.payload = payload;
        this.direction = direction;
    }
}


