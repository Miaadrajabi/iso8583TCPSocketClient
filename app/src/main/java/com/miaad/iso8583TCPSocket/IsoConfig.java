package com.miaad.iso8583TCPSocket;

/**
 * Simple configuration for ISO-8583 client
 */
public class IsoConfig {
    private final String host;
    private final int port;
        private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final boolean useTls;
    private final RetryConfig retryConfig;
    private final ConnectionMode connectionMode;
    private final boolean autoCloseAfterResponse;
    // Performance options (all optional; defaults keep prior behavior)
    private final boolean tcpNoDelay;
    private final boolean keepAlive;
    private final int sendBufferSize;
    private final int receiveBufferSize;
    private final boolean reuseBuffers;
    private final int maxMessageSizeBytes;
    private final int nioSelectIntervalMs;
    private final boolean enableHotPathLogs;
    private final LoggingConfig loggingConfig;

    private IsoConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.connectTimeoutMs = builder.connectTimeoutMs;
        this.readTimeoutMs = builder.readTimeoutMs;
        this.useTls = builder.useTls;
        this.retryConfig = builder.retryConfig;
        this.connectionMode = builder.connectionMode;
        this.autoCloseAfterResponse = builder.autoCloseAfterResponse;
        this.tcpNoDelay = builder.tcpNoDelay;
        this.keepAlive = builder.keepAlive;
        this.sendBufferSize = builder.sendBufferSize;
        this.receiveBufferSize = builder.receiveBufferSize;
        this.reuseBuffers = builder.reuseBuffers;
        this.maxMessageSizeBytes = builder.maxMessageSizeBytes;
        this.nioSelectIntervalMs = builder.nioSelectIntervalMs;
        this.enableHotPathLogs = builder.enableHotPathLogs;
        this.loggingConfig = builder.loggingConfig;
    }
    
    public String getHost() { return host; }
    public int getPort() { return port; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public boolean isUseTls() { return useTls; }
    public RetryConfig getRetryConfig() { return retryConfig; }
    public ConnectionMode getConnectionMode() { return connectionMode; }
    public boolean isAutoCloseAfterResponse() { return autoCloseAfterResponse; }
    public boolean isTcpNoDelay() { return tcpNoDelay; }
    public boolean isKeepAlive() { return keepAlive; }
    public int getSendBufferSize() { return sendBufferSize; }
    public int getReceiveBufferSize() { return receiveBufferSize; }
    public boolean isReuseBuffers() { return reuseBuffers; }
    public int getMaxMessageSizeBytes() { return maxMessageSizeBytes; }
    public int getNioSelectIntervalMs() { return nioSelectIntervalMs; }
    public boolean isEnableHotPathLogs() { return enableHotPathLogs; }
    public LoggingConfig getLoggingConfig() { return loggingConfig; }
    
    public static class Builder {
        private String host;
        private int port;
        private int connectTimeoutMs = 30000; // Default 30 seconds
        private int readTimeoutMs = 30000;    // Default 30 seconds
        private boolean useTls = false;
        private RetryConfig retryConfig = RetryConfig.defaultConfig();
        private ConnectionMode connectionMode = ConnectionMode.BLOCKING; // Default to blocking
        private boolean autoCloseAfterResponse = true; // Default: close after response
        // Performance defaults (disabled by default to preserve behavior)
        private boolean tcpNoDelay = false;
        private boolean keepAlive = false;
        private int sendBufferSize = 0; // 0 or negative => leave default
        private int receiveBufferSize = 0; // 0 or negative => leave default
        private boolean reuseBuffers = false;
        private int maxMessageSizeBytes = 0; // 0 => disabled/not used
        private int nioSelectIntervalMs = 1000; // default existing behavior
        private boolean enableHotPathLogs = true; // keep printing by default
        private LoggingConfig loggingConfig = null;
        
        public Builder(String host, int port) {
            this.host = host;
            this.port = port;
        }
        
        public Builder connectTimeout(int ms) {
            this.connectTimeoutMs = ms;
            return this;
        }
        
        public Builder readTimeout(int ms) {
            this.readTimeoutMs = ms;
            return this;
        }
        
                public Builder useTls(boolean useTls) {
            this.useTls = useTls;
            return this;
        }

        public Builder retryConfig(RetryConfig retryConfig) {
            this.retryConfig = retryConfig;
            return this;
        }

        public Builder connectionMode(ConnectionMode connectionMode) {
            this.connectionMode = connectionMode;
            return this;
        }

        /**
         * Configure whether to automatically close the connection after receiving a response.
         * Default is true for backward compatibility.
         */
        public Builder autoCloseAfterResponse(boolean autoClose) {
            this.autoCloseAfterResponse = autoClose;
            return this;
        }

        public Builder tcpNoDelay(boolean tcpNoDelay) {
            this.tcpNoDelay = tcpNoDelay;
            return this;
        }

        public Builder keepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        public Builder sendBufferSize(int bytes) {
            this.sendBufferSize = bytes;
            return this;
        }

        public Builder receiveBufferSize(int bytes) {
            this.receiveBufferSize = bytes;
            return this;
        }

        public Builder reuseBuffers(boolean reuseBuffers) {
            this.reuseBuffers = reuseBuffers;
            return this;
        }

        public Builder maxMessageSizeBytes(int bytes) {
            this.maxMessageSizeBytes = bytes;
            return this;
        }

        public Builder nioSelectIntervalMs(int ms) {
            this.nioSelectIntervalMs = ms;
            return this;
        }

        public Builder enableHotPathLogs(boolean enable) {
            this.enableHotPathLogs = enable;
            return this;
        }

        public Builder loggingConfig(LoggingConfig loggingConfig) {
            this.loggingConfig = loggingConfig;
            return this;
        }

        /**
         * Enable a recommended low-latency configuration without removing callbacks or features.
         */
        public Builder performanceMode() {
            this.tcpNoDelay = true;
            this.keepAlive = true;
            this.reuseBuffers = true;
            if (this.nioSelectIntervalMs > 10) this.nioSelectIntervalMs = 10;
            this.enableHotPathLogs = false; // callbacks remain active
            return this;
        }

        public IsoConfig build() {
            return new IsoConfig(this);
        }
    }
}
