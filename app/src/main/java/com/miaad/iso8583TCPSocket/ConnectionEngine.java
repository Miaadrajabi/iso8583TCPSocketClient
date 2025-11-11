package com.miaad.iso8583TCPSocket;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interface for connection engines
 */
public interface ConnectionEngine {
    
    /**
     * Initialize the engine with configuration
     */
    void initialize(IsoConfig config, ConnectionStateListener stateListener);
    
    /**
     * Connect to the server
     * @throws IOException if connection fails
     */
    void connect() throws IOException;
    
    /**
     * Send message and receive response
     * @param message Message to send
     * @return Response from server
     * @throws IOException if send/receive fails
     */
    IsoResponse sendAndReceive(byte[] message) throws IOException;
    
    /**
     * Send message and receive response with per-call framing override.
     */
    IsoResponse sendAndReceive(byte[] message, FramingOptions framingOverride) throws IOException;
    
    /**
     * Close the connection
     */
    void close();
    
    /**
     * Cancel any ongoing operations
     */
    void cancel();
    
    /**
     * Check if connected
     */
    boolean isConnected();
    
    /**
     * Get current connection state
     */
    ConnectionState getCurrentState();
    
    /**
     * Check if operation is in progress
     */
    boolean isOperationInProgress();
    
    /**
     * Set cancellation flag
     */
    void setCancelled(AtomicBoolean cancelled);
    
    /**
     * Get engine type description
     */
    String getEngineType();
    
    /**
     * Get comprehensive connection status
     */
    ConnectionStatus getConnectionStatus();
    
    /**
     * Update framing options at runtime
     */
    void setFramingOptions(FramingOptions options);
}
