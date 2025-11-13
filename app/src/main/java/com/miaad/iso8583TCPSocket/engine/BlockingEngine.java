package com.miaad.iso8583TCPSocket.engine;

import com.miaad.iso8583TCPSocket.ConnectionState;
import com.miaad.iso8583TCPSocket.ConnectionStateListener;
import com.miaad.iso8583TCPSocket.ConnectionStatus;
import com.miaad.iso8583TCPSocket.ConnectionMode;
import com.miaad.iso8583TCPSocket.IsoConfig;
import com.miaad.iso8583TCPSocket.IsoLogger;
import com.miaad.iso8583TCPSocket.IsoResponse;
import com.miaad.iso8583TCPSocket.RetryConfig;
import com.miaad.iso8583TCPSocket.FramingOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.net.SocketTimeoutException;
import java.io.ByteArrayOutputStream;
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
    private IsoLogger logger = IsoLogger.noOp();
    private final int defaultLengthHeaderSize;
    private final ByteOrder defaultByteOrder;
    private final byte[] lengthHeaderBuffer;
    private final byte[] headerReadBuffer;
    private volatile FramingOptions framingOptions;
    private volatile ConnectionState currentState = ConnectionState.DISCONNECTED;
    private final ReentrantLock operationLock = new ReentrantLock();
    private final AtomicBoolean transactionInProgress = new AtomicBoolean(false);
    private AtomicBoolean cancelled = new AtomicBoolean(false);
    private long connectionStartTime = 0;
    private long lastActivityTime = 0;
    private Exception lastError = null;
    private int reconnectAttempts = 0;

    public BlockingEngine(int lengthHeaderSize, ByteOrder byteOrder) {
        this.defaultLengthHeaderSize = lengthHeaderSize;
        this.defaultByteOrder = byteOrder;
        this.lengthHeaderBuffer = new byte[4]; // support 2 or 4
        this.headerReadBuffer = new byte[4];   // support 2 or 4
        this.framingOptions = FramingOptions.defaults(lengthHeaderSize, byteOrder);
    }

    @Override
    public void initialize(IsoConfig config, ConnectionStateListener stateListener) {
        this.config = config;
        this.stateListener = stateListener;
        if (config != null && config.getLoggingConfig() != null && config.getLoggingConfig().isEnabled()) {
            this.logger = new IsoLogger(config.getLoggingConfig(), stateListener);
        } else {
            this.logger = IsoLogger.noOp();
        }
    }

    @Override
    public void setFramingOptions(FramingOptions options) {
        if (options == null) {
            this.framingOptions = FramingOptions.defaults(defaultLengthHeaderSize, defaultByteOrder);
        } else {
            this.framingOptions = options;
        }
    }

    @Override
    public void setLogger(IsoLogger logger) {
        this.logger = (logger != null) ? logger : IsoLogger.noOp();
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
        return sendAndReceive(message, null);
    }

    @Override
    public IsoResponse sendAndReceive(byte[] message, FramingOptions framingOverride) throws IOException {
        changeState(ConnectionState.PREPARING_SEND, "Preparing to send message");
        if (stateListener != null) {
            stateListener.onSendStarted(message.length, "ISO-8583");
        }
        if (config != null && config.getLoggingConfig() != null && config.getLoggingConfig().isLogSends()) {
            logger.info(getClass().getSimpleName(), "sendAndReceive", "Preparing to send " + message.length + " bytes");
        }
        
        long startTime = System.currentTimeMillis();

        FramingOptions eff = (framingOverride != null) ? framingOverride : this.framingOptions;

        // Send message
        changeState(ConnectionState.CREATING_FRAME, "Creating message frame");
        byte[] lengthHeader = null;
        if (eff.isSendLengthHeader()) {
            lengthHeader = createLengthHeader(message.length, eff);
            if (stateListener != null) {
                stateListener.onFrameCreated("Length-Prefixed", eff.getLengthHeaderSize(), message.length);
            }
            if (config.getLoggingConfig() != null && config.getLoggingConfig().isLogHeaders()) {
                logger.debug(getClass().getSimpleName(), "sendAndReceive", "Created header size=" + eff.getLengthHeaderSize());
            }
        } else {
            if (stateListener != null) {
                stateListener.onFrameCreated("Raw", 0, message.length);
            }
            logger.debug(getClass().getSimpleName(), "sendAndReceive", "Sending raw message without header");
        }
        
        changeState(ConnectionState.SENDING_DATA, "Sending data");
        if (stateListener != null) {
            int total = (lengthHeader != null ? lengthHeader.length : 0) + message.length;
            stateListener.onDataTransmissionStarted(total);
        }
        
        OutputStream out = socket.getOutputStream();
        if (lengthHeader != null) {
            out.write(lengthHeader, 0, eff.getLengthHeaderSize());
        }
        out.write(message);
        out.flush();
        
        changeState(ConnectionState.DATA_SENT, "Data sent");
        if (stateListener != null) {
            int total = (lengthHeader != null ? eff.getLengthHeaderSize() : 0) + message.length;
            stateListener.onDataTransmissionCompleted(total, 
                System.currentTimeMillis() - startTime);
        }
        if (config.getLoggingConfig() != null && config.getLoggingConfig().isLogSends()) {
            logger.debug(getClass().getSimpleName(), "sendAndReceive", "Data sent",
                    null, config.getLoggingConfig().isIncludePayloads() ? message : null, "SEND");
        }
        
        // Wait for response
        changeState(ConnectionState.WAITING_RESPONSE, "Waiting for response");
        if (stateListener != null) {
            stateListener.onResponseWaitStarted(config.getReadTimeoutMs());
        }
        
        InputStream in = socket.getInputStream();
        byte[] responseData = readResponse(in, eff, startTime);
        
        changeState(ConnectionState.DATA_RECEIVED, "Data received");
        if (stateListener != null) {
            stateListener.onResponseDataReceived(responseData, responseData.length,
                System.currentTimeMillis() - startTime);
        }
        if (config.getLoggingConfig() != null && config.getLoggingConfig().isLogReceives()) {
            logger.debug(getClass().getSimpleName(), "sendAndReceive", "Data received (" + responseData.length + " bytes)",
                    null, responseData, "RECV");
        }
        
        // Process response
        changeState(ConnectionState.PROCESSING_RESPONSE, "Processing response");
        if (stateListener != null) {
            stateListener.onResponseProcessingStarted(responseData.length);
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

    private byte[] readResponse(InputStream in, FramingOptions eff, long startTime) throws IOException {
        if (eff.isExpectResponseHeader()) {
            // Try read header first
            changeState(ConnectionState.READING_HEADER, "Reading response header");
            if (stateListener != null) {
                stateListener.onResponseHeaderReadStarted(eff.getLengthHeaderSize());
            }
            int needed = eff.getLengthHeaderSize();
            int headerRead = 0;
            while (headerRead < needed) {
                int n = in.read(headerReadBuffer, headerRead, needed - headerRead);
                if (n < 0) {
                    if (eff.isAutoDetect()) {
                        // fallback to raw receive
                        logger.warn(getClass().getSimpleName(), "readResponse", "EOF while reading header, auto-detect fallback");
                        return readWithoutHeader(in, eff, startTime);
                    }
                    IOException ex = new IOException("Connection closed while reading header");
                    logger.error(getClass().getSimpleName(), "readResponse", "Header read failed", ex);
                    throw ex;
                }
                headerRead += n;
            }
            int headerValue = parseLength(headerReadBuffer, eff);
            int responseLength = eff.isLengthIncludesHeader()
                ? (headerValue - eff.getLengthHeaderSize())
                : headerValue;
            if (responseLength < 0) {
                IOException ex = new IOException("Invalid response length parsed from header");
                logger.error(getClass().getSimpleName(), "readResponse", "Invalid length", ex);
                throw ex;
            }
            if (config.getLoggingConfig() != null && config.getLoggingConfig().isLogHeaders()) {
                logger.debug(getClass().getSimpleName(), "readResponse", "Parsed header value=" + headerValue + " -> payloadLen=" + responseLength, null, null, "RECV");
            }
            changeState(ConnectionState.HEADER_RECEIVED, "Header received");
            if (stateListener != null) {
                byte[] hdr = new byte[needed];
                System.arraycopy(headerReadBuffer, 0, hdr, 0, needed);
                stateListener.onResponseHeaderReceived(hdr, responseLength, 
                    System.currentTimeMillis() - startTime);
            }
            // Read fixed amount
            changeState(ConnectionState.READING_DATA, "Reading response data");
            if (stateListener != null) {
                stateListener.onResponseDataReadStarted(responseLength);
            }
            return readFixedBytes(in, responseLength);
        } else {
            return readWithoutHeader(in, eff, startTime);
        }
    }

    private byte[] readWithoutHeader(InputStream in, FramingOptions eff, long startTime) throws IOException {
        // Fixed length
        if (eff.getFixedResponseLength() > 0) {
            changeState(ConnectionState.READING_DATA, "Reading fixed-length response");
            if (stateListener != null) {
                stateListener.onResponseDataReadStarted(eff.getFixedResponseLength());
            }
            return readFixedBytes(in, eff.getFixedResponseLength());
        }
        // Delimiter-terminated
        if (eff.getResponseTerminator() != null && eff.getResponseTerminator().length > 0) {
            changeState(ConnectionState.READING_DATA, "Reading response until terminator");
            return readUntilTerminator(in, eff.getResponseTerminator(), eff.getMaxMessageSizeBytes());
        }
        // Raw-until-idle
        changeState(ConnectionState.READING_DATA, "Reading response until idle");
        return readUntilIdle(in, eff.getIdleGapMs(), eff.getMaxMessageSizeBytes());
    }

    private byte[] readFixedBytes(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(data, read, length - read);
            if (n < 0) {
                break;
            }
            read += n;
        }
        if (read < length) {
            byte[] partial = new byte[read];
            System.arraycopy(data, 0, partial, 0, read);
            return partial;
        }
        return data;
    }

    private byte[] readUntilTerminator(InputStream in, byte[] terminator, int maxSize) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (true) {
            int n = in.read(buf);
            if (n < 0) {
                break;
            }
            baos.write(buf, 0, n);
            if (maxSize > 0 && baos.size() >= maxSize) {
                break;
            }
            if (endsWith(baos.toByteArray(), terminator)) {
                break;
            }
        }
        return baos.toByteArray();
    }

    private boolean endsWith(byte[] data, byte[] suffix) {
        if (suffix.length == 0) return false;
        if (data.length < suffix.length) return false;
        for (int i = 0; i < suffix.length; i++) {
            if (data[data.length - suffix.length + i] != suffix[i]) return false;
        }
        return true;
    }

    private byte[] readUntilIdle(InputStream in, int idleGapMs, int maxSize) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int originalTimeout = 0;
        boolean timeoutChanged = false;
        try {
            if (socket != null) {
                originalTimeout = socket.getSoTimeout();
                socket.setSoTimeout(Math.max(1, idleGapMs));
                timeoutChanged = true;
            }
        } catch (Exception ignored) {
        }
        byte[] buf = new byte[2048];
        while (true) {
            try {
                int n = in.read(buf);
                if (n < 0) {
                    break;
                }
                if (n > 0) {
                    baos.write(buf, 0, n);
                    if (maxSize > 0 && baos.size() >= maxSize) {
                        break;
                    }
                }
            } catch (SocketTimeoutException e) {
                // idle gap reached
                break;
            }
        }
        if (timeoutChanged) {
            try { socket.setSoTimeout(originalTimeout); } catch (Exception ignored) {}
        }
        return baos.toByteArray();
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

    private byte[] createLengthHeader(int length, FramingOptions eff) {
        ByteBuffer buffer = ByteBuffer.wrap(lengthHeaderBuffer);
        buffer.order(eff.getByteOrder());
        buffer.clear();
        int value = length + (eff.isLengthIncludesHeader() ? eff.getLengthHeaderSize() : 0);
        if (eff.getLengthHeaderSize() == 2) {
            buffer.putShort((short) (value & 0xFFFF));
        } else {
            buffer.putInt(value);
        }
        byte[] out = new byte[eff.getLengthHeaderSize()];
        System.arraycopy(lengthHeaderBuffer, 0, out, 0, eff.getLengthHeaderSize());
        return out;
    }

    private int parseLength(byte[] header, FramingOptions eff) {
        ByteBuffer buffer = ByteBuffer.wrap(header);
        buffer.order(eff.getByteOrder());
        if (eff.getLengthHeaderSize() == 2) {
            return buffer.getShort() & 0xFFFF;
        } else {
            return buffer.getInt();
        }
    }
}
