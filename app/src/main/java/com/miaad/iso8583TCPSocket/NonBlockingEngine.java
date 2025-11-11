package com.miaad.iso8583TCPSocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Non-blocking NIO engine for ISO-8583 connections
 */
public class NonBlockingEngine implements ConnectionEngine {
    
    public IsoConfig config;
    private ConnectionStateListener stateListener;
    private SocketChannel channel;
    private Selector selector;
    private final int lengthHeaderSize;
    private final ByteOrder byteOrder;
    private volatile ConnectionState currentState = ConnectionState.DISCONNECTED;
    private final ReentrantLock operationLock = new ReentrantLock();
    private final AtomicBoolean transactionInProgress = new AtomicBoolean(false);
    private AtomicBoolean cancelled = new AtomicBoolean(false);
    private long connectionStartTime = 0;
    private long lastActivityTime = 0;
    private Exception lastError = null;
    private int reconnectAttempts = 0;

    public NonBlockingEngine(int lengthHeaderSize, ByteOrder byteOrder) {
        this.lengthHeaderSize = lengthHeaderSize;
        this.byteOrder = byteOrder;
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
            if (channel != null && channel.isConnected()) {
                changeState(ConnectionState.CONNECTED, "Already connected");
                return; // Already connected
            }

            RetryConfig retryConfig = config.getRetryConfig();
            Exception lastException = null;
            long operationStartTime = System.currentTimeMillis();
            int maxAttempts = retryConfig.getMaxRetries() + 1;

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
                        changeState(ConnectionState.CONNECTING, "Starting NIO connection");
                    }

                    if (stateListener != null) {
                        stateListener.onConnectionAttemptStarted(config.getHost(), config.getPort(), attempt + 1, maxAttempts);
                    }
                    System.out.println("Connecting to " + config.getHost() + ":" + config.getPort() + 
                                     " (attempt " + (attempt + 1) + "/" + maxAttempts + ") [NON-BLOCKING ENGINE]");

                    // Initialize NIO components
                    changeState(ConnectionState.RESOLVING_HOST, "Initializing NIO components");
                    if (stateListener != null) {
                        stateListener.onHostResolutionStarted(config.getHost());
                    }
                    long hostResolveStart = System.currentTimeMillis();
                    
                    selector = Selector.open();
                    channel = SocketChannel.open();
                    channel.configureBlocking(false);
                    
                    // Start TCP connection
                    changeState(ConnectionState.TCP_CONNECTING, "Establishing NIO TCP connection");
                    if (stateListener != null) {
                        stateListener.onTcpConnectionStarted(config.getHost(), config.getPort());
                    }
                    long tcpConnectStart = System.currentTimeMillis();
                    
                    InetSocketAddress address = new InetSocketAddress(config.getHost(), config.getPort());
                    boolean connected = channel.connect(address);
                    
                    if (!connected) {
                        // Register for connect events
                        SelectionKey key = channel.register(selector, SelectionKey.OP_CONNECT);
                        
                        // Wait for connection to complete
                        long connectTimeout = config.getConnectTimeoutMs();
                        long startTime = System.currentTimeMillis();
                        
                        while (!connected && (System.currentTimeMillis() - startTime) < connectTimeout) {
                            if (cancelled.get()) {
                                throw new IOException("Connection cancelled");
                            }
                            
                            int readyChannels = selector.select(1000); // 1 second timeout
                            
                            if (readyChannels > 0) {
                                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                                
                                while (keyIterator.hasNext()) {
                                    SelectionKey selectedKey = keyIterator.next();
                                    keyIterator.remove();
                                    
                                    if (selectedKey.isConnectable()) {
                                        SocketChannel sc = (SocketChannel) selectedKey.channel();
                                        if (sc.finishConnect()) {
                                            connected = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (!connected) {
                            throw new IOException("Connection timeout after " + connectTimeout + "ms");
                        }
                    }
                    
                    long tcpConnectTime = System.currentTimeMillis() - tcpConnectStart;
                    changeState(ConnectionState.TCP_CONNECTED, "NIO TCP connection established");
                    
                    if (stateListener != null) {
                        long hostResolveTime = tcpConnectStart - hostResolveStart;
                        stateListener.onHostResolutionCompleted(config.getHost(), 
                            address.getAddress().getHostAddress(), hostResolveTime);
                        
                        // Use socket() method for API 21+ compatibility
                        String localAddress = "N/A";
                        String remoteAddress = "N/A";
                        try {
                            if (channel.socket() != null) {
                                if (channel.socket().getLocalAddress() != null) {
                                    localAddress = channel.socket().getLocalAddress().toString();
                                }
                                if (channel.socket().getInetAddress() != null) {
                                    remoteAddress = channel.socket().getInetAddress().toString();
                                }
                            }
                        } catch (Exception e) {
                            // Ignore address resolution errors
                        }
                        
                        stateListener.onTcpConnectionCompleted(localAddress, remoteAddress, tcpConnectTime);
                    }
                    
                    System.out.println("NIO Connected successfully!");

                    // Note: TLS support would require SSLEngine for NIO
                    if (config.isUseTls()) {
                        changeState(ConnectionState.TLS_HANDSHAKING, "TLS not yet supported in NIO engine");
                        throw new UnsupportedOperationException("TLS not yet implemented for NIO engine");
                    }

                    changeState(ConnectionState.CONNECTED, "NIO connection established successfully");
                    
                    if (stateListener != null) {
                        stateListener.onMetric("nio_connection_time", System.currentTimeMillis() - operationStartTime, "ms");
                    }
                    return;

                } catch (Exception e) {
                    lastException = e;
                    changeState(ConnectionState.CONNECTION_FAILED, "NIO connection attempt failed: " + e.getMessage());
                    
                    if (stateListener != null) {
                        stateListener.onError(e, currentState, "NIO connection attempt " + (attempt + 1) + " failed");
                    }
                    
                    System.err.println("NIO Connection attempt " + (attempt + 1) + " failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());

                    // Cleanup
                    cleanupConnection();

                    // Check if we should retry
                    boolean willRetry = (attempt < retryConfig.getMaxRetries() && retryConfig.shouldRetry(e));

                    if (willRetry) {
                        System.out.println("Retrying NIO connection...");
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
            System.err.println("All NIO connection attempts failed");
            if (lastException instanceof IOException) {
                throw (IOException) lastException;
            } else {
                throw new IOException("NIO connection failed after " + maxAttempts + " attempts", lastException);
            }

        } finally {
            // Always reset transaction flag
            transactionInProgress.set(false);
            operationLock.unlock();
        }
    }

    @Override
    public IsoResponse sendAndReceive(byte[] message) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected");
        }

        changeState(ConnectionState.PREPARING_SEND, "Preparing to send NIO message");
        if (stateListener != null) {
            stateListener.onSendStarted(message.length, "ISO-8583 NIO");
        }
        
        long startTime = System.currentTimeMillis();
        
        // Create message with length header
        changeState(ConnectionState.CREATING_FRAME, "Creating NIO message frame");
        byte[] lengthHeader = createLengthHeader(message.length);
        ByteBuffer sendBuffer = ByteBuffer.allocate(lengthHeader.length + message.length);
        sendBuffer.put(lengthHeader);
        sendBuffer.put(message);
        sendBuffer.flip();
        
        if (stateListener != null) {
            stateListener.onFrameCreated("Length-Prefixed NIO", lengthHeader.length, message.length);
        }
        
        // Send data
        changeState(ConnectionState.SENDING_DATA, "Sending NIO data");
        if (stateListener != null) {
            stateListener.onDataTransmissionStarted(sendBuffer.remaining());
        }
        
        while (sendBuffer.hasRemaining()) {
            if (cancelled.get()) {
                throw new IOException("Send operation cancelled");
            }
            
            int written = channel.write(sendBuffer);
            if (written == 0) {
                // Channel is not ready, use selector
                SelectionKey key = channel.register(selector, SelectionKey.OP_WRITE);
                selector.select(config.getReadTimeoutMs());
                key.cancel();
            }
        }
        
        changeState(ConnectionState.DATA_SENT, "NIO data sent");
        if (stateListener != null) {
            stateListener.onDataTransmissionCompleted(lengthHeader.length + message.length, 
                System.currentTimeMillis() - startTime);
        }
        
        // Read response
        changeState(ConnectionState.WAITING_RESPONSE, "Waiting for NIO response");
        if (stateListener != null) {
            stateListener.onResponseWaitStarted(config.getReadTimeoutMs());
        }
        
        // Read length header
        changeState(ConnectionState.READING_HEADER, "Reading NIO response header");
        if (stateListener != null) {
            stateListener.onResponseHeaderReadStarted(lengthHeaderSize);
        }
        
        ByteBuffer headerBuffer = ByteBuffer.allocate(lengthHeaderSize);
        readFullBuffer(headerBuffer, config.getReadTimeoutMs());
        
        headerBuffer.flip();
        int responseLength = parseLength(headerBuffer.array());
        
        changeState(ConnectionState.HEADER_RECEIVED, "NIO header received");
        if (stateListener != null) {
            stateListener.onResponseHeaderReceived(headerBuffer.array(), responseLength, 
                System.currentTimeMillis() - startTime);
        }
        
        // Read response data
        changeState(ConnectionState.READING_DATA, "Reading NIO response data");
        if (stateListener != null) {
            stateListener.onResponseDataReadStarted(responseLength);
        }
        
        ByteBuffer dataBuffer = ByteBuffer.allocate(responseLength);
        readFullBuffer(dataBuffer, config.getReadTimeoutMs());
        
        changeState(ConnectionState.DATA_RECEIVED, "NIO data received");
        if (stateListener != null) {
            stateListener.onResponseDataReceived(dataBuffer.array(), responseLength,
                System.currentTimeMillis() - startTime);
        }
        
        // Process response
        changeState(ConnectionState.PROCESSING_RESPONSE, "Processing NIO response");
        if (stateListener != null) {
            stateListener.onResponseProcessingStarted(responseLength);
        }
        
        long responseTime = System.currentTimeMillis() - startTime;
        
        changeState(ConnectionState.TRANSACTION_COMPLETE, "NIO transaction complete");
        if (stateListener != null) {
            stateListener.onResponseProcessingCompleted(0, responseTime);
        }
        
        // Auto-close after transaction (configurable)
        if (config != null && config.isAutoCloseAfterResponse()) {
            close();
        }
        
        return new IsoResponse(dataBuffer.array(), responseTime);
    }

    @Override
    public IsoResponse sendAndReceive(byte[] message, FramingOptions framingOverride) throws IOException {
        // Legacy top-level NIO engine does not support per-call framing; delegate to default behavior.
        return sendAndReceive(message);
    }

    @Override
    public void close() {
        if (currentState == ConnectionState.DISCONNECTED) {
            return; // Already disconnected
        }
        
        changeState(ConnectionState.DISCONNECTING, "Starting NIO disconnection");
        if (stateListener != null) {
            stateListener.onDisconnectionStarted("Manual close");
        }
        
        operationLock.lock();
        try {
            cleanupConnection();
            changeState(ConnectionState.DISCONNECTED, "NIO connection closed");
            
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
        return channel != null && channel.isConnected();
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
        // Legacy top-level NIO engine does not support runtime framing options; no-op.
    }

    @Override
    public String getEngineType() {
        return "Non-blocking NIO Engine";
    }

    @Override
    public ConnectionStatus getConnectionStatus() {
        ConnectionStatus.Builder builder = new ConnectionStatus.Builder();
        
        // Basic connection states
        boolean channelConnected = channel != null && channel.isConnected();
        boolean channelClosed = channel != null && !channel.isOpen();
        boolean channelOpen = channel != null && channel.isOpen();
        
        builder.connected(channelConnected && channelOpen)
               .closed(channelClosed || currentState == ConnectionState.DISCONNECTED)
               .open(channelOpen)
               .connecting(currentState.isConnecting())
               .disconnecting(currentState.isDisconnecting())
               .transactionInProgress(transactionInProgress.get())
               .operationInProgress(transactionInProgress.get())
               .cancelled(cancelled.get())
               .retrying(currentState.isRetrying())
               .hasError(lastError != null || currentState.isError())
               .timeout(currentState == ConnectionState.TIMEOUT)
               .tlsEnabled(config != null && config.isUseTls())
               .tlsConnected(false) // TLS not yet supported in NIO
               .socketBound(channelConnected)
               .socketClosed(channelClosed)
               .currentState(currentState)
               .connectionMode(config != null ? config.getConnectionMode() : ConnectionMode.NON_BLOCKING)
               .engineType(getEngineType())
               .lastError(lastError)
               .connectionStartTime(connectionStartTime)
               .lastActivityTime(lastActivityTime)
               .reconnectAttempts(reconnectAttempts);

        // Channel-specific checks
        if (channel != null && channel.isOpen()) {
            try {
                builder.readable(true) // NIO channels are generally readable if open
                       .writable(true); // NIO channels are generally writable if open
                
                // Use socket() method for API 21+ compatibility
                if (channel.socket() != null) {
                    if (channel.socket().getLocalAddress() != null) {
                        builder.localAddress(channel.socket().getLocalAddress().toString());
                    }
                    if (channel.socket().getInetAddress() != null) {
                        builder.remoteAddress(channel.socket().getInetAddress().toString());
                    }
                }
            } catch (Exception e) {
                // Channel might be in invalid state
                builder.readable(false).writable(false);
            }
        }
        
        // Calculate connection duration
        if (connectionStartTime > 0) {
            long duration = System.currentTimeMillis() - connectionStartTime;
            builder.connectionDuration(duration);
        }
        
        // Status description
        String description = generateStatusDescription();
        builder.statusDescription(description);
        
        return builder.build();
    }

    private String generateStatusDescription() {
        if (currentState == ConnectionState.CONNECTED) {
            return "NIO Connected and ready";
        } else if (currentState.isConnecting()) {
            return "NIO Connection in progress";
        } else if (currentState.isDisconnecting()) {
            return "NIO Disconnection in progress";
        } else if (currentState.isError()) {
            return "NIO Error state: " + (lastError != null ? lastError.getMessage() : "Unknown error");
        } else if (currentState == ConnectionState.DISCONNECTED) {
            return "NIO Disconnected";
        } else if (currentState.isRetrying()) {
            return "NIO Retrying connection (attempt " + reconnectAttempts + ")";
        } else if (currentState.isTransacting()) {
            return "NIO Transaction in progress";
        } else {
            return "NIO " + currentState.getDescription();
        }
    }

    private void cleanupConnection() {
        changeState(ConnectionState.CLOSING_SOCKET, "Closing NIO resources");
        if (stateListener != null) {
            stateListener.onSocketClosing();
        }
        
        long closeStart = System.currentTimeMillis();
        
        try {
            if (channel != null) {
                channel.close();
                channel = null;
            }
            if (selector != null) {
                selector.close();
                selector = null;
            }
        } catch (IOException ignored) {
        }
        
        long closeTime = System.currentTimeMillis() - closeStart;
        
        if (stateListener != null) {
            stateListener.onSocketClosed(closeTime);
        }
    }

    private void readFullBuffer(ByteBuffer buffer, int timeoutMs) throws IOException {
        long startTime = System.currentTimeMillis();
        
        while (buffer.hasRemaining()) {
            if (cancelled.get()) {
                throw new IOException("Read operation cancelled");
            }
            
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                throw new IOException("Read timeout");
            }
            
            int read = channel.read(buffer);
            if (read == -1) {
                throw new IOException("Channel closed");
            }
            
            if (read == 0) {
                // Channel not ready, use selector
                SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
                int ready = selector.select(1000); // 1 second timeout
                key.cancel();
                
                if (ready == 0) {
                    continue; // Timeout, check cancellation and overall timeout
                }
            }
        }
    }

    private void changeState(ConnectionState newState, String details) {
        ConnectionState oldState = currentState;
        currentState = newState;
        if (stateListener != null) {
            stateListener.onStateChanged(oldState, newState, details);
        }
        if (stateListener != null) {
            stateListener.onLog("INFO", "NIO State changed: " + oldState + " -> " + newState, details);
        }
    }

    private byte[] createLengthHeader(int length) {
        ByteBuffer buffer = ByteBuffer.allocate(lengthHeaderSize);
        buffer.order(byteOrder);
        
        if (lengthHeaderSize == 2) {
            buffer.putShort((short) (length & 0xFFFF));
        } else {
            buffer.putInt(length);
        }
        
        return buffer.array();
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
