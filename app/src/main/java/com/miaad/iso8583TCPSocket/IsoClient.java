package com.miaad.iso8583TCPSocket;

import com.miaad.iso8583TCPSocket.engine.ConnectionEngine;
import com.miaad.iso8583TCPSocket.engine.BlockingEngine;
import com.miaad.iso8583TCPSocket.engine.NonBlockingEngine;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple ISO-8583 TCP Client
 * Supports both blocking and non-blocking modes via configurable engines
 */
public class IsoClient {
    private final ConnectionEngine engine;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private RetryCallback retryCallback;
    private ConnectionStateListener stateListener;
    private FramingOptions framingOptions;
    private IsoLogger logger = IsoLogger.noOp();
    
    /**
     * Create ISO-8583 client with 2-byte length header (default)
     */
    public IsoClient(IsoConfig config) {
        this(config, 2, ByteOrder.BIG_ENDIAN);
    }

    /**
     * Create ISO-8583 client with custom length header size and byte order
     */
    public IsoClient(IsoConfig config, int lengthHeaderSize, ByteOrder byteOrder) {
        if (lengthHeaderSize != 2 && lengthHeaderSize != 4) {
            throw new IllegalArgumentException("Length header size must be 2 or 4 bytes.");
        }
        this.framingOptions = FramingOptions.defaults(lengthHeaderSize, byteOrder);
        // Create appropriate engine based on connection mode
        switch (config.getConnectionMode()) {
            case BLOCKING:
                this.engine = new BlockingEngine(lengthHeaderSize, byteOrder);
                break;
            case NON_BLOCKING:
                this.engine = new NonBlockingEngine(lengthHeaderSize, byteOrder);
                break;
            default:
                throw new IllegalArgumentException("Unsupported connection mode: " + config.getConnectionMode());
        }
        
        // Initialize engine
        this.engine.initialize(config, null); // StateListener will be set later
        // Initialize logger from config
        if (config.getLoggingConfig() != null && config.getLoggingConfig().isEnabled()) {
            this.logger = new IsoLogger(config.getLoggingConfig(), null);
        } else {
            this.logger = IsoLogger.noOp();
        }
        this.engine.setLogger(this.logger);
        this.engine.setFramingOptions(this.framingOptions);
        this.engine.setCancelled(cancelled);
    }

    /**
     * Create ISO-8583 client with explicit framing options.
     * Defaults are preserved unless overridden in options.
     */
    public IsoClient(IsoConfig config, FramingOptions options) {
        this(config, options != null ? options.getLengthHeaderSize() : 2,
                options != null ? options.getByteOrder() : ByteOrder.BIG_ENDIAN);
        if (options != null) {
            updateFraming(options);
        }
    }

    /**
     * Connect to ISO server
     */
    public void connect() throws IOException {
        engine.connect();
    }

    /**
     * Send ISO message and receive response
     */
    public IsoResponse sendAndReceive(byte[] message) throws IOException {
        return engine.sendAndReceive(message);
    }

    /**
     * Send ISO message with per-call framing override
     */
    public IsoResponse sendAndReceive(byte[] message, FramingOptions override) throws IOException {
        return engine.sendAndReceive(message, override);
    }

    /**
     * Cancel any ongoing operations
     */
    public void cancel() {
        cancelled.set(true);
        engine.cancel();
    }

    /**
     * Close connection
     */
    public void close() {
        engine.close();
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return engine.isConnected();
    }

    /**
     * Check if transaction is currently in progress
     */
    public boolean isTransactionInProgress() {
        return engine.isOperationInProgress();
    }

    /**
     * Set retry callback for monitoring retry attempts
     */
    public void setRetryCallback(RetryCallback callback) {
        this.retryCallback = callback;
    }

    /**
     * Set connection state listener for detailed state monitoring
     */
    public void setConnectionStateListener(ConnectionStateListener listener) {
        this.stateListener = listener;
        // Re-initialize engine with the new listener
        if (engine instanceof BlockingEngine) {
            ((BlockingEngine) engine).initialize(((BlockingEngine) engine).config, listener);
            ((BlockingEngine) engine).setLogger(this.logger);
            ((BlockingEngine) engine).setFramingOptions(this.framingOptions);
        } else if (engine instanceof NonBlockingEngine) {
            ((NonBlockingEngine) engine).initialize(((NonBlockingEngine) engine).config, listener);
            ((NonBlockingEngine) engine).setLogger(this.logger);
            ((NonBlockingEngine) engine).setFramingOptions(this.framingOptions);
        }
    }

    /**
     * Update framing options at runtime (for singleton client usage)
     */
    public void updateFraming(FramingOptions options) {
        this.framingOptions = options != null ? options : FramingOptions.defaults(2, ByteOrder.BIG_ENDIAN);
        engine.setFramingOptions(this.framingOptions);
    }

    /**
     * Update logging configuration at runtime
     */
    public void updateLogging(LoggingConfig loggingConfig) {
        if (loggingConfig != null && loggingConfig.isEnabled()) {
            this.logger = new IsoLogger(loggingConfig, this.stateListener);
        } else {
            this.logger = IsoLogger.noOp();
        }
        engine.setLogger(this.logger);
    }

    /**
     * Retrieve captured logs (if in-memory capture is enabled)
     */
    public java.util.List<com.miaad.iso8583TCPSocket.logging.LogEntry> getCapturedLogs() {
        return this.logger.getCapturedLogs();
    }

    /**
     * Get current connection state
     */
    public ConnectionState getCurrentState() {
        return engine.getCurrentState();
    }

    /**
     * Get engine type description
     */
    public String getEngineType() {
        return engine.getEngineType();
    }

    // ========== STATUS CHECKING METHODS ==========

    /**
     * Get comprehensive connection status
     */
    public ConnectionStatus getConnectionStatus() {
        return engine.getConnectionStatus();
    }

    /**
     * Check if connection is closed
     */
    public boolean isClosed() {
        return getConnectionStatus().isClosed();
    }

    /**
     * Check if connection is open
     */
    public boolean isOpen() {
        return getConnectionStatus().isOpen();
    }

    /**
     * Check if currently connecting
     */
    public boolean isConnecting() {
        return getConnectionStatus().isConnecting();
    }

    /**
     * Check if currently disconnecting
     */
    public boolean isDisconnecting() {
        return getConnectionStatus().isDisconnecting();
    }

    /**
     * Check if operation was cancelled
     */
    public boolean isCancelled() {
        return getConnectionStatus().isCancelled();
    }

    /**
     * Check if currently retrying
     */
    public boolean isRetrying() {
        return getConnectionStatus().isRetrying();
    }

    /**
     * Check if there's an error
     */
    public boolean hasError() {
        return getConnectionStatus().hasError();
    }

    /**
     * Check if timed out
     */
    public boolean isTimeout() {
        return getConnectionStatus().isTimeout();
    }

    /**
     * Check if TLS is enabled
     */
    public boolean isTlsEnabled() {
        return getConnectionStatus().isTlsEnabled();
    }

    /**
     * Check if TLS is connected
     */
    public boolean isTlsConnected() {
        return getConnectionStatus().isTlsConnected();
    }

    /**
     * Check if socket is readable
     */
    public boolean isReadable() {
        return getConnectionStatus().isReadable();
    }

    /**
     * Check if socket is writable
     */
    public boolean isWritable() {
        return getConnectionStatus().isWritable();
    }

    /**
     * Check if socket is bound
     */
    public boolean isSocketBound() {
        return getConnectionStatus().isSocketBound();
    }

    /**
     * Check if socket is closed
     */
    public boolean isSocketClosed() {
        return getConnectionStatus().isSocketClosed();
    }

    /**
     * Get connection mode
     */
    public ConnectionMode getConnectionMode() {
        return getConnectionStatus().getConnectionMode();
    }

    /**
     * Get local address
     */
    public String getLocalAddress() {
        return getConnectionStatus().getLocalAddress();
    }

    /**
     * Get remote address
     */
    public String getRemoteAddress() {
        return getConnectionStatus().getRemoteAddress();
    }

    /**
     * Get last error
     */
    public Exception getLastError() {
        return getConnectionStatus().getLastError();
    }

    /**
     * Get connection start time
     */
    public long getConnectionStartTime() {
        return getConnectionStatus().getConnectionStartTime();
    }

    /**
     * Get last activity time
     */
    public long getLastActivityTime() {
        return getConnectionStatus().getLastActivityTime();
    }

    /**
     * Get connection duration in milliseconds
     */
    public long getConnectionDuration() {
        return getConnectionStatus().getConnectionDuration();
    }

    /**
     * Get number of reconnect attempts
     */
    public int getReconnectAttempts() {
        return getConnectionStatus().getReconnectAttempts();
    }

    /**
     * Get status description
     */
    public String getStatusDescription() {
        return getConnectionStatus().getStatusDescription();
    }

    // ========== UTILITY METHODS ==========

    /**
     * Check if can connect
     */
    public boolean canConnect() {
        return getConnectionStatus().canConnect();
    }

    /**
     * Check if can send
     */
    public boolean canSend() {
        return getConnectionStatus().canSend();
    }

    /**
     * Check if can disconnect
     */
    public boolean canDisconnect() {
        return getConnectionStatus().canDisconnect();
    }

    /**
     * Check if connection is healthy
     */
    public boolean isHealthy() {
        return getConnectionStatus().isHealthy();
    }

    /**
     * Check if needs reconnection
     */
    public boolean needsReconnection() {
        return getConnectionStatus().needsReconnection();
    }

    /**
     * Get detailed status string
     */
    public String getDetailedStatus() {
        return getConnectionStatus().toDetailedString();
    }
}