package com.miaad.iso8583TCPSocket;

import com.miaad.iso8583TCPSocket.logging.LogLevel;
import com.miaad.iso8583TCPSocket.logging.LogSink;

public class LoggingConfig {
    private final boolean enabled;
    private final LogLevel minimumLevel;
    private final boolean includePayloads;
    private final boolean includeHexDump;
    private final int maxPayloadBytes;
    private final boolean maskSensitive;
    private final String redactionRegex;
    private final boolean logSends;
    private final boolean logReceives;
    private final boolean logHeaders;
    private final boolean logErrors;
    private final boolean logState;
    private final boolean captureInMemory;
    private final int inMemoryCapacity;
    private final LogSink externalSink;

    private LoggingConfig(Builder b) {
        this.enabled = b.enabled;
        this.minimumLevel = b.minimumLevel;
        this.includePayloads = b.includePayloads;
        this.includeHexDump = b.includeHexDump;
        this.maxPayloadBytes = b.maxPayloadBytes;
        this.maskSensitive = b.maskSensitive;
        this.redactionRegex = b.redactionRegex;
        this.logSends = b.logSends;
        this.logReceives = b.logReceives;
        this.logHeaders = b.logHeaders;
        this.logErrors = b.logErrors;
        this.logState = b.logState;
        this.captureInMemory = b.captureInMemory;
        this.inMemoryCapacity = b.inMemoryCapacity;
        this.externalSink = b.externalSink;
    }

    public boolean isEnabled() { return enabled; }
    public LogLevel getMinimumLevel() { return minimumLevel; }
    public boolean isIncludePayloads() { return includePayloads; }
    public boolean isIncludeHexDump() { return includeHexDump; }
    public int getMaxPayloadBytes() { return maxPayloadBytes; }
    public boolean isMaskSensitive() { return maskSensitive; }
    public String getRedactionRegex() { return redactionRegex; }
    public boolean isLogSends() { return logSends; }
    public boolean isLogReceives() { return logReceives; }
    public boolean isLogHeaders() { return logHeaders; }
    public boolean isLogErrors() { return logErrors; }
    public boolean isLogState() { return logState; }
    public boolean isCaptureInMemory() { return captureInMemory; }
    public int getInMemoryCapacity() { return inMemoryCapacity; }
    public LogSink getExternalSink() { return externalSink; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean enabled = false;
        private LogLevel minimumLevel = LogLevel.INFO;
        private boolean includePayloads = true;
        private boolean includeHexDump = true;
        private int maxPayloadBytes = 2048;
        private boolean maskSensitive = true;
        private String redactionRegex = "(?<!\\d)\\d{12,19}(?!\\d)"; // naive PAN mask
        private boolean logSends = true;
        private boolean logReceives = true;
        private boolean logHeaders = true;
        private boolean logErrors = true;
        private boolean logState = true;
        private boolean captureInMemory = true;
        private int inMemoryCapacity = 500;
        private LogSink externalSink = null;

        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder minimumLevel(LogLevel v) { this.minimumLevel = v; return this; }
        public Builder includePayloads(boolean v) { this.includePayloads = v; return this; }
        public Builder includeHexDump(boolean v) { this.includeHexDump = v; return this; }
        public Builder maxPayloadBytes(int v) { this.maxPayloadBytes = v; return this; }
        public Builder maskSensitive(boolean v) { this.maskSensitive = v; return this; }
        public Builder redactionRegex(String v) { this.redactionRegex = v; return this; }
        public Builder logSends(boolean v) { this.logSends = v; return this; }
        public Builder logReceives(boolean v) { this.logReceives = v; return this; }
        public Builder logHeaders(boolean v) { this.logHeaders = v; return this; }
        public Builder logErrors(boolean v) { this.logErrors = v; return this; }
        public Builder logState(boolean v) { this.logState = v; return this; }
        public Builder captureInMemory(boolean v) { this.captureInMemory = v; return this; }
        public Builder inMemoryCapacity(int v) { this.inMemoryCapacity = v; return this; }
        public Builder externalSink(LogSink v) { this.externalSink = v; return this; }

        public LoggingConfig build() { return new LoggingConfig(this); }
    }
}


