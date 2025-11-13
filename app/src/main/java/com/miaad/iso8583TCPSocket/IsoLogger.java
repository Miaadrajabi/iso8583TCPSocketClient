package com.miaad.iso8583TCPSocket;

import com.miaad.iso8583TCPSocket.logging.InMemoryLogSink;
import com.miaad.iso8583TCPSocket.logging.LogEntry;
import com.miaad.iso8583TCPSocket.logging.LogLevel;
import com.miaad.iso8583TCPSocket.logging.LogSink;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IsoLogger {
    private final LoggingConfig config;
    private final LogSink externalSink;
    private final InMemoryLogSink inMemorySink;
    private final ConnectionStateListener stateListener;

    public IsoLogger(LoggingConfig config, ConnectionStateListener listener) {
        this.config = config != null ? config : LoggingConfig.builder().enabled(false).build();
        this.externalSink = this.config.getExternalSink();
        this.inMemorySink = this.config.isCaptureInMemory()
                ? new InMemoryLogSink(this.config.getInMemoryCapacity())
                : null;
        this.stateListener = listener;
    }

    public static IsoLogger noOp() {
        return new IsoLogger(LoggingConfig.builder().enabled(false).build(), null);
    }

    public boolean isEnabled() { return config.isEnabled(); }

    public void log(LogLevel level, String cls, String method, String message, String details, byte[] payload, String direction) {
        if (!config.isEnabled()) return;
        if (level.ordinal() < config.getMinimumLevel().ordinal()) return;

        byte[] payloadOut = null;
        if (config.isIncludePayloads() && payload != null) {
            int max = Math.max(0, config.getMaxPayloadBytes());
            if (payload.length > max) {
                byte[] truncated = new byte[max];
                System.arraycopy(payload, 0, truncated, 0, max);
                payloadOut = truncated;
            } else {
                payloadOut = payload.clone();
            }
            if (config.isMaskSensitive()) {
                // naive redaction for ASCII content
                try {
                    String s = new String(payloadOut, StandardCharsets.ISO_8859_1);
                    s = s.replaceAll(config.getRedactionRegex(), "************");
                    payloadOut = s.getBytes(StandardCharsets.ISO_8859_1);
                } catch (Exception ignored) {
                }
            }
        }

        LogEntry entry = new LogEntry(
                System.currentTimeMillis(),
                level,
                Thread.currentThread().getName(),
                cls,
                method,
                message,
                details,
                payloadOut,
                direction
        );

        if (inMemorySink != null) {
            inMemorySink.accept(entry);
        }
        if (externalSink != null) {
            try { externalSink.accept(entry); } catch (Exception ignored) {}
        }
        if (stateListener != null && config.isLogState()) {
            try {
                stateListener.onLog(level.name(), cls + "." + method + ": " + message, details);
            } catch (Exception ignored) {
            }
        }
    }

    public void trace(String cls, String method, String msg) {
        log(LogLevel.TRACE, cls, method, msg, null, null, "INTERNAL");
    }
    public void debug(String cls, String method, String msg) {
        log(LogLevel.DEBUG, cls, method, msg, null, null, "INTERNAL");
    }
    public void debug(String cls, String method, String msg, String details, byte[] payload, String direction) {
        log(LogLevel.DEBUG, cls, method, msg, details, payload, direction);
    }
    public void info(String cls, String method, String msg) {
        log(LogLevel.INFO, cls, method, msg, null, null, "INTERNAL");
    }
    public void warn(String cls, String method, String msg) {
        log(LogLevel.WARN, cls, method, msg, null, null, "INTERNAL");
    }
    public void error(String cls, String method, String msg, Throwable t) {
        String details = t != null ? t.getClass().getSimpleName() + ": " + t.getMessage() : null;
        log(LogLevel.ERROR, cls, method, msg, details, null, "INTERNAL");
    }

    public List<LogEntry> getCapturedLogs() {
        if (inMemorySink == null) return Collections.emptyList();
        return new ArrayList<>(inMemorySink.snapshot());
    }

    public void clearCaptured() {
        if (inMemorySink != null) inMemorySink.clear();
    }
}


