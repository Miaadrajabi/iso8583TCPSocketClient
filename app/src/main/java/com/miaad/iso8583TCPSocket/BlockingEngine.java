package com.miaad.iso8583TCPSocket;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Blocking I/O engine for ISO-8583 connections
 */
public class BlockingEngine implements ConnectionEngine {
    
    public IsoConfig config;
    private ConnectionStateListener stateListener;
    private Socket socket;
    private final int lengthHeaderSize;
    private final ByteOrder byteOrder;
    private final byte[] lengthHeaderBuffer;
    private final byte[] headerReadBuffer;
    private volatile ConnectionState currentState = ConnectionState.DISCONNECTED;
    private final ReentrantLock operationLock = new ReentrantLock();
    private final AtomicBoolean transactionInProgress = new AtomicBoolean(false);
    private AtomicBoolean cancelled = new AtomicBoolean(false);
    private long connectionStartTime = 0;
    private long lastActivityTime = 0;
    private Exception lastError = null;
    private int reconnectAttempts = 0;

    public BlockingEngine(int lengthHeaderSize, ByteOrder byteOrder) {
        this.lengthHeaderSize = lengthHeaderSize;
        this.byteOrder = byteOrder;
        this.lengthHeaderBuffer = new byte[lengthHeaderSize];
        this.headerReadBuffer = new byte[lengthHeaderSize];
    }

    @Override
    public void initialize(IsoConfig config, ConnectionStateListener stateListener) {
        this.config = config;
        this.stateListener = stateListener;
    }

    @Override
    public void connect() throws IOException {
        // Check if any operation is in progress
        if (!transactionInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("Cannot connect while another operation is in progress");
        }

        operationLock.lock();
        try {
            if (socket != null && socket.isConnected()) {
                changeState(ConnectionState.CONNECTED, "Already connected");
                return; // Already connected
            }

            RetryConfig retryConfig = config.getRetryConfig();
            Exception lastException = null;
            long operationStartTime = System.currentTimeMillis();
            this.connectionStartTime = operationStartTime;
            this.lastActivityTime = operationStartTime;
            int maxAttempts = retryConfig.getMaxRetries() + 1;
            this.reconnectAttempts = 0;

            for (int attempt = 0; attempt <= retryConfig.getMaxRetries(); attempt++) {
                try {
                    if (attempt > 0) {
                        long delay = retryConfig.calculateDelay(attempt);
                        changeState(ConnectionState.RETRY_WAITING, "Waiting " + delay + "ms before retry attempt " + (attempt + 1));
                        
                        if (stateListener != null) {
                            stateListener.onRetryDelayStarted(attempt + 1, delay, "Connection failed");
                        }
                        
                        System.out.println("Retry attempt " + attempt + " after " + delay + "ms delay");
                        Thread.sleep(delay);
                        
                        if (stateListener != null) {
                            stateListener.onRetryDelayEnded(attempt + 1);
                        }
                        changeState(ConnectionState.RETRY_CONNECTING, "Starting retry attempt " + (attempt + 1));
                    } else {
                        changeState(ConnectionState.CONNECTING, "Starting connection");
                    }

                    if (stateListener != null) {
                        stateListener.onConnectionAttemptStarted(config.getHost(), config.getPort(), attempt + 1, maxAttempts);
                    }
                    if (config == null || config.isEnableHotPathLogs()) {
                        System.out.println("Connecting to " + config.getHost() + ":" + config.getPort() + 
                                         " (attempt " + (attempt + 1) + "/" + maxAttempts + ") [BLOCKING ENGINE]");
                    }

                    // Host resolution
                    changeState(ConnectionState.RESOLVING_HOST, "Resolving " + config.getHost());
                    if (stateListener != null) {
                        stateListener.onHostResolutionStarted(config.getHost());
                    }
                    long hostResolveStart = System.currentTimeMillis();
                    
                    // TCP Connection
                    changeState(ConnectionState.TCP_CONNECTING, "Establishing TCP connection");
                    if (stateListener != null) {
                        stateListener.onTcpConnectionStarted(config.getHost(), config.getPort());
                    }
                    long tcpConnectStart = System.currentTimeMillis();
                    
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(config.getHost(), config.getPort()), 
                                  config.getConnectTimeoutMs());
                    socket.setSoTimeout(config.getReadTimeoutMs());
                    
                    long tcpConnectTime = System.currentTimeMillis() - tcpConnectStart;
                    changeState(ConnectionState.TCP_CONNECTED, "TCP connection established");
                    
                    if (stateListener != null) {
                        long hostResolveTime = tcpConnectStart - hostResolveStart;
                        stateListener.onHostResolutionCompleted(config.getHost(), 
                            socket.getInetAddress().getHostAddress(), hostResolveTime);
                        stateListener.onTcpConnectionCompleted(
                            socket.getLocalSocketAddress().toString(),
                            socket.getRemoteSocketAddress().toString(), tcpConnectTime);
                    }
                    
                    if (config == null || config.isEnableHotPathLogs()) {
                        System.out.println("Connected successfully!");
                    }

                    // Apply socket options for performance
                    try {
                        if (config.isTcpNoDelay()) socket.setTcpNoDelay(true);
                        if (config.isKeepAlive()) socket.setKeepAlive(true);
                        if (config.getSendBufferSize() > 0) socket.setSendBufferSize(config.getSendBufferSize());
                        if (config.getReceiveBufferSize() > 0) socket.setReceiveBufferSize(config.getReceiveBufferSize());
                        socket.setPerformancePreferences(0, 1, 2);
                    } catch (Exception ignored) {
                    }

                    if (config.isUseTls()) {
                        changeState(ConnectionState.TLS_HANDSHAKING, "Performing TLS handshake");
                        if (stateListener != null) {
                            stateListener.onTlsHandshakeStarted();
                        }
                        
                        long tlsStart = System.currentTimeMillis();
                        if (config == null || config.isEnableHotPathLogs()) {
                            System.out.println("Starting TLS handshake...");
                        }
                        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                        socket = factory.createSocket(socket, config.getHost(), config.getPort(), true);
                        ((SSLSocket) socket).startHandshake();
                        
                        long tlsTime = System.currentTimeMillis() - tlsStart;
                        changeState(ConnectionState.TLS_CONNECTED, "TLS handshake completed");
                        
                        if (stateListener != null) {
                            SSLSocket sslSocket = (SSLSocket) socket;
                            stateListener.onTlsHandshakeCompleted(
                                sslSocket.getSession().getProtocol(),
                                sslSocket.getSession().getCipherSuite(), tlsTime);
                        }
                        if (config == null || config.isEnableHotPathLogs()) {
                            System.out.println("TLS handshake completed!");
                        }
                    }

                    changeState(ConnectionState.CONNECTED, "Connection established successfully");
                    
                    if (stateListener != null) {
                        stateListener.onMetric("connection_time", System.currentTimeMillis() - operationStartTime, "ms");
                    }
                    return;

                } catch (Exception e) {
                    lastException = e;
                    this.lastError = e;
                    this.reconnectAttempts++;
                    changeState(ConnectionState.CONNECTION_FAILED, "Connection attempt failed: " + e.getMessage());
                    
                    if (stateListener != null) {
                        stateListener.onError(e, currentState, "Connection attempt " + (attempt + 1) + " failed");
                    }
                    
                    if (config == null || config.isEnableHotPathLogs()) {
                        System.err.println("Connection attempt " + (attempt + 1) + " failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    }

                    if (socket != null) {
                        try { socket.close(); } catch (IOException ignored) {}
                        socket = null;
                    }

                    // Check if we should retry
                    boolean willRetry = (attempt < retryConfig.getMaxRetries() && retryConfig.shouldRetry(e));

                    if (willRetry) {
                        System.out.println("Retrying connection...");
                        continue;
                    } else {
                        // No more retries or not retryable
                        break;
                    }
                }
            }

            // All attempts failed
            if (stateListener != null) {
                stateListener.onRetryExhausted(maxAttempts, lastException);
            }
            if (config == null || config.isEnableHotPathLogs()) {
                System.err.println("All connection attempts failed");
            }
            if (lastException instanceof IOException) {
                throw (IOException) lastException;
            } else {
                throw new IOException("Connection failed after " + maxAttempts + " attempts", lastException);
            }

        } finally {
            // Always reset transaction flag
            transactionInProgress.set(false);
            operationLock.unlock();
        }
    }

    @Override
    public IsoResponse sendAndReceive(byte[] message) throws IOException {
        // Note: For blocking engine, we'll implement a simplified version here
        // Full implementation would include all the retry logic from the original
        
        changeState(ConnectionState.PREPARING_SEND, "Preparing to send message");
        if (stateListener != null) {
            stateListener.onSendStarted(message.length, "ISO-8583");
        }
        
        long startTime = System.currentTimeMillis();
        
        // Send message
        changeState(ConnectionState.CREATING_FRAME, "Creating message frame");
        byte[] lengthHeader = createLengthHeader(message.length);
        if (stateListener != null) {
            stateListener.onFrameCreated("Length-Prefixed", lengthHeader.length, message.length);
        }
        
        changeState(ConnectionState.SENDING_DATA, "Sending data");
        if (stateListener != null) {
            stateListener.onDataTransmissionStarted(lengthHeader.length + message.length);
        }
        
        OutputStream out = socket.getOutputStream();
        out.write(lengthHeader);
        out.write(message);
        out.flush();
        
        changeState(ConnectionState.DATA_SENT, "Data sent");
        if (stateListener != null) {
            stateListener.onDataTransmissionCompleted(lengthHeader.length + message.length, 
                System.currentTimeMillis() - startTime);
        }
        
        // Wait for response
        changeState(ConnectionState.WAITING_RESPONSE, "Waiting for response");
        if (stateListener != null) {
            stateListener.onResponseWaitStarted(config.getReadTimeoutMs());
        }
        
        // Read response header
        changeState(ConnectionState.READING_HEADER, "Reading response header");
        if (stateListener != null) {
            stateListener.onResponseHeaderReadStarted(lengthHeaderSize);
        }
        
        InputStream in = socket.getInputStream();
        int headerRead = 0;
        while (headerRead < lengthHeaderSize) {
            int n = in.read(headerReadBuffer, headerRead, lengthHeaderSize - headerRead);
            if (n < 0) throw new IOException("Connection closed while reading header");
            headerRead += n;
        }
        
        int responseLength = parseLength(headerReadBuffer);
        changeState(ConnectionState.HEADER_RECEIVED, "Header received");
        if (stateListener != null) {
            stateListener.onResponseHeaderReceived(headerReadBuffer, responseLength, 
                System.currentTimeMillis() - startTime);
        }
        
        // Read response data
        changeState(ConnectionState.READING_DATA, "Reading response data");
        if (stateListener != null) {
            stateListener.onResponseDataReadStarted(responseLength);
        }
        
        byte[] responseData = new byte[responseLength];
        int dataRead = 0;
        while (dataRead < responseLength) {
            int n = in.read(responseData, dataRead, responseLength - dataRead);
            if (n < 0) throw new IOException("Connection closed while reading data");
            dataRead += n;
        }
        
        changeState(ConnectionState.DATA_RECEIVED, "Data received");
        if (stateListener != null) {
            stateListener.onResponseDataReceived(responseData, responseLength,
                System.currentTimeMillis() - startTime);
        }
        
        // Process response
        changeState(ConnectionState.PROCESSING_RESPONSE, "Processing response");
        if (stateListener != null) {
            stateListener.onResponseProcessingStarted(responseLength);
        }
        
        long responseTime = System.currentTimeMillis() - startTime;
        
        changeState(ConnectionState.TRANSACTION_COMPLETE, "Transaction complete");
        if (stateListener != null) {
            stateListener.onResponseProcessingCompleted(0, responseTime);
        }
        
        // Auto-close after transaction (configurable)
        if (config != null && config.isAutoCloseAfterResponse()) {
            close();
        }
        
        return new IsoResponse(responseData, responseTime);
    }

    @Override
    public IsoResponse sendAndReceive(byte[] message, FramingOptions framingOverride) throws IOException {
        // Legacy engine does not support per-call options; fallback to default behavior.
        return sendAndReceive(message);
    }

    @Override
    public void close() {
        if (currentState == ConnectionState.DISCONNECTED) {
            return; // Already disconnected
        }
        
        changeState(ConnectionState.DISCONNECTING, "Starting disconnection");
        if (stateListener != null) {
            stateListener.onDisconnectionStarted("Manual close");
        }
        
        operationLock.lock();
        try {
            if (socket != null) {
                changeState(ConnectionState.CLOSING_SOCKET, "Closing socket");
                if (stateListener != null) {
                    stateListener.onSocketClosing();
                }
                
                long closeStart = System.currentTimeMillis();
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                long closeTime = System.currentTimeMillis() - closeStart;
                
                socket = null;
                
                if (stateListener != null) {
                    stateListener.onSocketClosed(closeTime);
                }
            }
            
            changeState(ConnectionState.DISCONNECTED, "Connection closed");
            
            // Reset transaction flag
            transactionInProgress.set(false);
        } finally {
            operationLock.unlock();
        }
    }

    @Override
    public void cancel() {
        cancelled.set(true);
        close();
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public ConnectionState getCurrentState() {
        return currentState;
    }

    @Override
    public boolean isOperationInProgress() {
        return transactionInProgress.get();
    }

    @Override
    public void setCancelled(AtomicBoolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public void setFramingOptions(FramingOptions options) {
        // Legacy engine does not support framing options; no-op to preserve behavior.
    }

    @Override
    public String getEngineType() {
        return "Blocking I/O Engine";
    }

    @Override
    public ConnectionStatus getConnectionStatus() {
        ConnectionStatus.Builder builder = new ConnectionStatus.Builder();
        
        // Basic connection states
        boolean socketConnected = socket != null && socket.isConnected();
        boolean socketClosed = socket != null && socket.isClosed();
        boolean socketBound = socket != null && socket.isBound();
        
        builder.connected(socketConnected && !socketClosed)
               .closed(socketClosed || currentState == ConnectionState.DISCONNECTED)
               .open(socketConnected && !socketClosed)
               .connecting(currentState.isConnecting())
               .disconnecting(currentState.isDisconnecting())
               .transactionInProgress(transactionInProgress.get())
               .operationInProgress(transactionInProgress.get())
               .cancelled(cancelled.get())
               .retrying(currentState.isRetrying())
               .hasError(lastError != null || currentState.isError())
               .timeout(currentState == ConnectionState.TIMEOUT)
               .tlsEnabled(config != null && config.isUseTls())
               .tlsConnected(config != null && config.isUseTls() && currentState == ConnectionState.TLS_CONNECTED)
               .socketBound(socketBound)
               .socketClosed(socketClosed)
               .currentState(currentState)
               .connectionMode(config != null ? config.getConnectionMode() : ConnectionMode.BLOCKING)
               .engineType(getEngineType())
               .lastError(lastError)
               .connectionStartTime(connectionStartTime)
               .lastActivityTime(lastActivityTime)
               .reconnectAttempts(reconnectAttempts);

        // Socket-specific checks
        if (socket != null) {
            try {
                builder.readable(!socket.isInputShutdown())
                       .writable(!socket.isOutputShutdown());
                
                if (socket.getLocalSocketAddress() != null) {
                    builder.localAddress(socket.getLocalSocketAddress().toString());
                }
                if (socket.getRemoteSocketAddress() != null) {
                    builder.remoteAddress(socket.getRemoteSocketAddress().toString());
                }
            } catch (Exception e) {
                // Socket might be in invalid state
                builder.readable(false).writable(false);
            }
        }
        
        // Calculate connection duration
        if (connectionStartTime > 0) {
            long duration = System.currentTimeMillis() - connectionStartTime;
            builder.connectionDuration(duration);
        }
        
        // Status description
        String description = generateStatusDescription(builder);
        builder.statusDescription(description);
        
        return builder.build();
    }

    private String generateStatusDescription(ConnectionStatus.Builder builder) {
        if (currentState == ConnectionState.CONNECTED) {
            return "Connected and ready";
        } else if (currentState.isConnecting()) {
            return "Connection in progress";
        } else if (currentState.isDisconnecting()) {
            return "Disconnection in progress";
        } else if (currentState.isError()) {
            return "Error state: " + (lastError != null ? lastError.getMessage() : "Unknown error");
        } else if (currentState == ConnectionState.DISCONNECTED) {
            return "Disconnected";
        } else if (currentState.isRetrying()) {
            return "Retrying connection (attempt " + reconnectAttempts + ")";
        } else if (currentState.isTransacting()) {
            return "Transaction in progress";
        } else {
            return currentState.getDescription();
        }
    }

    private void changeState(ConnectionState newState, String details) {
        ConnectionState oldState = currentState;
        currentState = newState;
        if (stateListener != null) {
            stateListener.onStateChanged(oldState, newState, details);
        }
        if (stateListener != null && (config == null || config.isEnableHotPathLogs())) {
            stateListener.onLog("INFO", "State changed: " + oldState + " -> " + newState, details);
        }
    }

    private byte[] createLengthHeader(int length) {
        ByteBuffer buffer = ByteBuffer.wrap(lengthHeaderBuffer);
        buffer.order(byteOrder);
        buffer.clear();
        if (lengthHeaderSize == 2) {
            buffer.putShort((short) (length & 0xFFFF));
        } else {
            buffer.putInt(length);
        }
        return lengthHeaderBuffer;
    }

    private int parseLength(byte[] header) {
        ByteBuffer buffer = ByteBuffer.wrap(header);
        buffer.order(byteOrder);
        
        if (lengthHeaderSize == 2) {
            return buffer.getShort() & 0xFFFF;
        } else {
            return buffer.getInt();
        }
    }
}
