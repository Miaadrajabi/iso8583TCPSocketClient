package com.miaad.iso8583TCPSocket.engine;

import com.miaad.iso8583TCPSocket.ConnectionState;
import com.miaad.iso8583TCPSocket.ConnectionStateListener;
import com.miaad.iso8583TCPSocket.ConnectionStatus;
import com.miaad.iso8583TCPSocket.IsoConfig;
import com.miaad.iso8583TCPSocket.IsoLogger;
import com.miaad.iso8583TCPSocket.IsoResponse;
import com.miaad.iso8583TCPSocket.FramingOptions;

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

    /**
     * Set structured logger
     */
    void setLogger(IsoLogger logger);
}
