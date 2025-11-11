package com.miaad.iso8583TCPSocket.sample;

import android.os.Bundle;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.miaad.iso8583TCPSocket.IsoClient;
import com.miaad.iso8583TCPSocket.IsoConfig;
import com.miaad.iso8583TCPSocket.IsoResponse;
import com.miaad.iso8583TCPSocket.RetryConfig;
import com.miaad.iso8583TCPSocket.RetryCallback;
import com.miaad.iso8583TCPSocket.ConnectionStateListener;
import com.miaad.iso8583TCPSocket.ConnectionState;
import com.miaad.iso8583TCPSocket.ConnectionMode;
import com.miaad.iso8583TCPSocket.FramingOptions;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simple ISO-8583 Test App
 */
public class MainActivity extends AppCompatActivity {

    private EditText hostInput;
    private EditText portInput;
    private EditText messageInput;
    private EditText timeoutInput;
    private TextView logView;
    private Button connectBtn;
    private Button sendBtn;
    private Button sendNoHeaderBtn;
    private Button disconnectBtn;
    private CheckBox hexModeCheck;
    private Spinner lengthSizeSpinner;
    private Spinner connectionModeSpinner;
    private ScrollView scrollView;

    private IsoClient client;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isOperationInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create UI programmatically
        scrollView = new ScrollView(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 20, 20, 20);

        // Host input
        hostInput = new EditText(this);
        hostInput.setHint("Host (e.g., 192.168.1.100)");
        hostInput.setText("172.20.10.3");
        mainLayout.addView(hostInput);

        // Port input
        portInput = new EditText(this);
        portInput.setHint("Port (e.g., 8583)");
        portInput.setText("8080");
        portInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        mainLayout.addView(portInput);

        // Timeout input
        timeoutInput = new EditText(this);
        timeoutInput.setHint("Timeout (ms)");
        timeoutInput.setText("5000");
        timeoutInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        mainLayout.addView(timeoutInput);

        // Length header size
        TextView lengthLabel = new TextView(this);
        lengthLabel.setText("Length Header Size:");
        mainLayout.addView(lengthLabel);

        lengthSizeSpinner = new Spinner(this);
        ArrayAdapter<String> lengthAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"2 bytes", "4 bytes"});
        lengthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        lengthSizeSpinner.setAdapter(lengthAdapter);
        mainLayout.addView(lengthSizeSpinner);

        // Connection Mode Spinner
        TextView modeLabel = new TextView(this);
        modeLabel.setText("Connection Mode:");
        mainLayout.addView(modeLabel);

        connectionModeSpinner = new Spinner(this);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Blocking I/O", "Non-blocking NIO"});
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        connectionModeSpinner.setAdapter(modeAdapter);
        mainLayout.addView(connectionModeSpinner);

        // Message input
        messageInput = new EditText(this);
        messageInput.setHint("Message (text or hex)");
        messageInput.setText("0800822000000000000004000000000000001234");
        mainLayout.addView(messageInput);

        // Hex mode checkbox
        hexModeCheck = new CheckBox(this);
        hexModeCheck.setText("Hex Mode");
        hexModeCheck.setChecked(true);
        mainLayout.addView(hexModeCheck);

        // Buttons layout
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);

        connectBtn = new Button(this);
        connectBtn.setText("Connect");
        connectBtn.setOnClickListener(v -> connect());
        buttonLayout.addView(connectBtn);

        sendBtn = new Button(this);
        sendBtn.setText("Send");
        sendBtn.setEnabled(false);
        sendBtn.setOnClickListener(v -> send());
        buttonLayout.addView(sendBtn);

        // Send without header (TMS-style)
        sendNoHeaderBtn = new Button(this);
        sendNoHeaderBtn.setText("Send (No Header)");
        sendNoHeaderBtn.setEnabled(false);
        sendNoHeaderBtn.setOnClickListener(v -> sendNoHeader());
        buttonLayout.addView(sendNoHeaderBtn);

        disconnectBtn = new Button(this);
        disconnectBtn.setText("Disconnect");
        disconnectBtn.setEnabled(false);
        disconnectBtn.setOnClickListener(v -> disconnect());
        buttonLayout.addView(disconnectBtn);

        // Test button for concurrent send
        Button testSendBtn = new Button(this);
        testSendBtn.setText("Test Send");
        testSendBtn.setOnClickListener(v -> testConcurrentSend());
        buttonLayout.addView(testSendBtn);

        // Test button for concurrent connect
        Button testConnectBtn = new Button(this);
        testConnectBtn.setText("Test Connect");
        testConnectBtn.setOnClickListener(v -> testConcurrentConnect());
        buttonLayout.addView(testConnectBtn);

        // Status button
        Button statusBtn = new Button(this);
        statusBtn.setText("Status");
        statusBtn.setOnClickListener(v -> showStatus());
        buttonLayout.addView(statusBtn);
        mainLayout.addView(buttonLayout);

        // Log view
        TextView logLabel = new TextView(this);
        logLabel.setText("\nLogs:");
        mainLayout.addView(logLabel);

        logView = new TextView(this);
        logView.setTextSize(12);
        logView.setPadding(10, 10, 10, 10);
        logView.setBackgroundColor(0xFFF0F0F0);
        mainLayout.addView(logView);

        scrollView.addView(mainLayout);
        setContentView(scrollView);
    }

    private void connect() {
        String host = hostInput.getText().toString();
        String portStr = portInput.getText().toString();
        String timeoutStr = timeoutInput.getText().toString();

        if (host.isEmpty() || portStr.isEmpty()) {
            log("Error: Host and port are required");
            return;
        }

        int port = Integer.parseInt(portStr);
        int timeout = Integer.parseInt(timeoutStr.isEmpty() ? "30000" : timeoutStr);

        // Determine length header size and connection mode
        int lengthSize = lengthSizeSpinner.getSelectedItemPosition() == 0 ? 2 : 4;
        ConnectionMode connectionMode = connectionModeSpinner.getSelectedItemPosition() == 0 ?
                ConnectionMode.BLOCKING : ConnectionMode.NON_BLOCKING;

        executor.execute(() -> {
            isOperationInProgress = true;
            try {
                runOnUiThread(() -> {
                    log("Connecting to " + host + ":" + port + "...");
                    log("Timeout: " + timeout + "ms");
                    log("Length header size: " + lengthSize + " bytes");
                    log("Connection mode: " + connectionMode.getDescription());
                    connectBtn.setEnabled(false);
                    disconnectBtn.setEnabled(true); // Allow cancel during connect
                });

                // Create retry config for demonstration
                RetryConfig retryConfig = new RetryConfig.Builder()
                        .maxRetries(3)
                        .baseDelay(1000)
                        .maxDelay(10000)
                        .backoffMultiplier(2.0)
                        .retryOnTimeout(true)
                        .retryOnConnectionFailure(true)
                        .retryOnIOException(false)
                        .build();

//                RetryConfig.defaultConfig();
//                RetryConfig.aggressiveConfig();
//                RetryConfig.noRetry();

                IsoConfig config = new IsoConfig.Builder(host, port)
                        .connectTimeout(timeout)
                        .readTimeout(timeout)
                        .performanceMode()
                        .autoCloseAfterResponse(false)
                        .retryConfig(retryConfig)
                        .connectionMode(connectionMode)
                        .build();

                client = new IsoClient(config, lengthSize, ByteOrder.BIG_ENDIAN);

                // Set retry callback to show progress in UI
                client.setRetryCallback(new RetryCallback() {
                    @Override
                    public void onRetryAttempt(String operation, int attempt, int maxAttempts, long delayMs) {
                        runOnUiThread(() -> log("🔄 Retry " + operation + " - Attempt " + attempt + "/" + maxAttempts + " after " + delayMs + "ms delay"));
                    }

                    @Override
                    public void onAttemptFailed(String operation, int attempt, int maxAttempts, Exception exception, boolean willRetry) {
                        runOnUiThread(() -> {
                            log("❌ " + operation + " attempt " + attempt + "/" + maxAttempts + " failed: " + exception.getClass().getSimpleName());
                            if (willRetry) {
                                log("🔄 Will retry...");
                            } else {
                                log("⛔ No more retries");
                            }
                        });
                    }

                    @Override
                    public void onSuccess(String operation, int attempt, long totalTimeMs) {
                        runOnUiThread(() -> log("✅ " + operation + " succeeded on attempt " + attempt + " (total time: " + totalTimeMs + "ms)"));
                    }

                    @Override
                    public void onAllAttemptsFailed(String operation, int totalAttempts, Exception lastException) {
                        runOnUiThread(() -> log("💥 All " + operation + " attempts failed after " + totalAttempts + " tries"));
                    }
                });

                // Set detailed state listener
                client.setConnectionStateListener(new ConnectionStateListener() {
                    @Override
                    public void onStateChanged(ConnectionState oldState, ConnectionState newState, String details) {
                        runOnUiThread(() -> log("🔄 STATE: " + newState.getDescription() + " (" + details + ")"));
                    }

                    @Override
                    public void onConnectionAttemptStarted(String host, int port, int attempt, int maxAttempts) {
                        runOnUiThread(() -> log("🌐 Starting connection to " + host + ":" + port + " (attempt " + attempt + "/" + maxAttempts + ")"));
                    }

                    @Override
                    public void onHostResolutionStarted(String hostname) {
                        runOnUiThread(() -> log("🔍 Resolving hostname: " + hostname));
                    }

                    @Override
                    public void onHostResolutionCompleted(String hostname, String resolvedIp, long timeMs) {
                        runOnUiThread(() -> log("✅ Resolved " + hostname + " to " + resolvedIp + " (" + timeMs + "ms)"));
                    }

                    @Override
                    public void onTcpConnectionStarted(String host, int port) {
                        runOnUiThread(() -> log("🔌 Establishing TCP connection to " + host + ":" + port));
                    }

                    @Override
                    public void onTcpConnectionCompleted(String localAddress, String remoteAddress, long timeMs) {
                        runOnUiThread(() -> log("✅ TCP connected: " + localAddress + " -> " + remoteAddress + " (" + timeMs + "ms)"));
                    }

                    @Override
                    public void onTlsHandshakeStarted() {
                        runOnUiThread(() -> log("🔒 Starting TLS handshake..."));
                    }

                    @Override
                    public void onTlsHandshakeCompleted(String protocol, String cipherSuite, long timeMs) {
                        runOnUiThread(() -> log("✅ TLS handshake completed: " + protocol + " (" + timeMs + "ms)"));
                    }

                    @Override
                    public void onMetric(String metricName, long value, String unit) {
                        runOnUiThread(() -> log("📊 " + metricName + ": " + value + " " + unit));
                    }

                    @Override
                    public void onLog(String level, String message, String details) {
                        runOnUiThread(() -> log("📝 [" + level + "] " + message + (details != null ? " - " + details : "")));
                    }

                    @Override
                    public void onError(Exception error, ConnectionState currentState, String details) {
                        runOnUiThread(() -> log("❌ ERROR in " + currentState + ": " + error.getClass().getSimpleName() + " - " + error.getMessage()));
                    }

                    // Implement other required methods with minimal logging
                    @Override
                    public void onSendStarted(int dataLength, String messageType) {
                    }

                    @Override
                    public void onFrameCreated(String frameType, int headerSize, int dataSize) {
                    }

                    @Override
                    public void onDataTransmissionStarted(int totalBytes) {
                    }

                    @Override
                    public void onDataTransmissionProgress(int bytesSent, int totalBytes, int percentComplete) {
                    }

                    @Override
                    public void onDataTransmissionCompleted(int totalBytes, long timeMs) {
                    }

                    @Override
                    public void onResponseWaitStarted(int timeoutMs) {
                    }

                    @Override
                    public void onResponseHeaderReadStarted(int expectedHeaderSize) {
                    }

                    @Override
                    public void onResponseHeaderReceived(byte[] headerBytes, int parsedLength, long timeMs) {
                    }

                    @Override
                    public void onResponseDataReadStarted(int expectedDataSize) {
                    }

                    @Override
                    public void onResponseDataReadProgress(int bytesRead, int totalBytes, int percentComplete) {
                    }

                    @Override
                    public void onResponseDataReceived(byte[] dataBytes, int totalBytes, long timeMs) {
                    }

                    @Override
                    public void onResponseProcessingStarted(int responseSize) {
                    }

                    @Override
                    public void onResponseProcessingCompleted(long processingTimeMs, long totalTransactionTimeMs) {
                    }

                    @Override
                    public void onDisconnectionStarted(String reason) {
                        runOnUiThread(() -> log("🔌 Disconnecting: " + reason));
                    }

                    @Override
                    public void onSocketClosing() {
                        runOnUiThread(() -> log("🔌 Closing socket..."));
                    }

                    @Override
                    public void onSocketClosed(long timeMs) {
                        runOnUiThread(() -> log("✅ Socket closed (" + timeMs + "ms)"));
                    }

                    @Override
                    public void onCancelled(ConnectionState currentState, String reason) {
                        runOnUiThread(() -> log("⏹️ Cancelled in " + currentState + ": " + reason));
                    }

                    @Override
                    public void onTimeout(String timeoutType, int timeoutMs, ConnectionState currentState) {
                        runOnUiThread(() -> log("⏰ Timeout: " + timeoutType + " (" + timeoutMs + "ms) in " + currentState));
                    }

                    @Override
                    public void onRetryDelayStarted(int attempt, long delayMs, String reason) {
                        runOnUiThread(() -> log("⏳ Retry delay started: " + delayMs + "ms (attempt " + attempt + ") - " + reason));
                    }

                    @Override
                    public void onRetryDelayEnded(int attempt) {
                        runOnUiThread(() -> log("⏳ Retry delay ended - starting attempt " + attempt));
                    }

                    @Override
                    public void onRetryExhausted(int totalAttempts, Exception lastError) {
                        runOnUiThread(() -> log("💥 All " + totalAttempts + " attempts failed"));
                    }
                });

                client.connect();

                runOnUiThread(() -> {
                    log("Connected successfully!");
                    log("Engine: " + client.getEngineType());
                    connectBtn.setEnabled(false);
                    sendBtn.setEnabled(true);
                    sendNoHeaderBtn.setEnabled(true);
                    disconnectBtn.setEnabled(true);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    log("Connection failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    if (e.getCause() != null) {
                        log("Cause: " + e.getCause().getMessage());
                    }
                    client = null;
                    connectBtn.setEnabled(true);
                    sendBtn.setEnabled(false);
                    sendNoHeaderBtn.setEnabled(false);
                    disconnectBtn.setEnabled(false);
                });
            } finally {
                isOperationInProgress = false;
            }
        });
    }

    private void send() {
        String message = messageInput.getText().toString();
        if (message.isEmpty()) {
            log("Error: Message is required");
            return;
        }

        if (client == null || !client.isConnected()) {
            log("Error: Not connected");
            return;
        }

        // Check if transaction is already in progress
        if (client.isTransactionInProgress()) {
            log("Error: Transaction already in progress. Please wait...");
            return;
        }

        executor.execute(() -> {
            isOperationInProgress = true;
            try {
                byte[] data;
                if (hexModeCheck.isChecked()) {
                    data = hexToBytes(message);
                } else {
                    data = message.getBytes(StandardCharsets.UTF_8);
                }

                runOnUiThread(() -> {
                    log("Sending " + data.length + " bytes...");
                    sendBtn.setEnabled(false);
                    sendNoHeaderBtn.setEnabled(false);
                    disconnectBtn.setText("Cancel");
                });

                IsoResponse response = client.sendAndReceive(data);

                runOnUiThread(() -> {
                    log("Response received in " + response.getResponseTimeMs() + "ms");
                    log("Response size: " + response.getData().length + " bytes");

                    if (hexModeCheck.isChecked()) {
                        log("Response (hex): " + bytesToHex(response.getData()));
                    } else {
                        log("Response (text): " + new String(response.getData(), StandardCharsets.UTF_8));
                    }

                    // Respect config: if connection is still open, keep client for reuse
                    if (client != null && client.isConnected()) {
                        log("Connection kept open (autoCloseAfterResponse = false)");
                        connectBtn.setEnabled(false);
                        sendBtn.setEnabled(true);
                        sendNoHeaderBtn.setEnabled(true);
                        disconnectBtn.setEnabled(true);
                        disconnectBtn.setText("Disconnect");
                    } else {
                        log("Connection closed after receiving response");
                        connectBtn.setEnabled(true);
                        sendBtn.setEnabled(false);
                        sendNoHeaderBtn.setEnabled(false);
                        disconnectBtn.setEnabled(false);
                        disconnectBtn.setText("Disconnect");
                        client = null;
                    }
                });

            } catch (IllegalStateException e) {
                // Handle concurrent transaction attempt
                runOnUiThread(() -> {
                    log("Transaction blocked: " + e.getMessage());
                    sendBtn.setEnabled(true);
                    sendNoHeaderBtn.setEnabled(true);
                    disconnectBtn.setText("Disconnect");
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    log("Send failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    sendBtn.setEnabled(true);
                    sendNoHeaderBtn.setEnabled(true);
                    disconnectBtn.setText("Disconnect");

                    // Reset connection state if client is not usable
                    if (client != null && !client.isConnected()) {
                        connectBtn.setEnabled(true);
                        sendBtn.setEnabled(false);
                        sendNoHeaderBtn.setEnabled(false);
                        disconnectBtn.setEnabled(false);
                        client = null;
                    }
                });
            } finally {
                isOperationInProgress = false;
            }
        });
    }

    /**
     * Send message without length header and read response without header (idle-gap based)
     */
    private void sendNoHeader() {
        String message = messageInput.getText().toString();
        if (message.isEmpty()) {
            log("Error: Message is required");
            return;
        }
        if (client == null || !client.isConnected()) {
            log("Error: Not connected");
            return;
        }
        if (client.isTransactionInProgress()) {
            log("Error: Transaction already in progress. Please wait...");
            return;
        }

        executor.execute(() -> {
            isOperationInProgress = true;
            try {
                byte[] data;
                if (hexModeCheck.isChecked()) {
                    data = hexToBytes(message);
                } else {
                    data = message.getBytes(StandardCharsets.UTF_8);
                }

                runOnUiThread(() -> {
                    log("Sending (no header) " + data.length + " bytes...");
                    sendBtn.setEnabled(false);
                    sendNoHeaderBtn.setEnabled(false);
                    disconnectBtn.setText("Cancel");
                });

                // Build no-header framing (TMS-style)
                FramingOptions noHeader = FramingOptions.builder()
                        .sendLengthHeader(false)
                        .expectResponseHeader(false)
                        .idleGapMs(150)
                        .build();

                IsoResponse response = client.sendAndReceive(data, noHeader);

                runOnUiThread(() -> {
                    log("Response (no header) received in " + response.getResponseTimeMs() + "ms");
                    log("Response size: " + response.getData().length + " bytes");
                    if (hexModeCheck.isChecked()) {
                        log("Response (hex): " + bytesToHex(response.getData()));
                    } else {
                        log("Response (text): " + new String(response.getData(), StandardCharsets.UTF_8));
                    }

                    if (client != null && client.isConnected()) {
                        log("Connection kept open (autoCloseAfterResponse = false)");
                        connectBtn.setEnabled(false);
                        sendBtn.setEnabled(true);
                        sendNoHeaderBtn.setEnabled(true);
                        disconnectBtn.setEnabled(true);
                        disconnectBtn.setText("Disconnect");
                    } else {
                        log("Connection closed after receiving response");
                        connectBtn.setEnabled(true);
                        sendBtn.setEnabled(false);
                        sendNoHeaderBtn.setEnabled(false);
                        disconnectBtn.setEnabled(false);
                        disconnectBtn.setText("Disconnect");
                        client = null;
                    }
                });

            } catch (IllegalStateException e) {
                runOnUiThread(() -> {
                    log("Transaction blocked: " + e.getMessage());
                    sendBtn.setEnabled(true);
                    sendNoHeaderBtn.setEnabled(true);
                    disconnectBtn.setText("Disconnect");
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    log("Send (no header) failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    sendBtn.setEnabled(true);
                    sendNoHeaderBtn.setEnabled(true);
                    disconnectBtn.setText("Disconnect");
                    if (client != null && !client.isConnected()) {
                        connectBtn.setEnabled(true);
                        sendBtn.setEnabled(false);
                        sendNoHeaderBtn.setEnabled(false);
                        disconnectBtn.setEnabled(false);
                        client = null;
                    }
                });
            } finally {
                isOperationInProgress = false;
            }
        });
    }

    private void disconnect() {
        if (isOperationInProgress && client != null) {
            // Cancel ongoing operation
            log("Cancelling operation...");
            client.cancel();
        }

        executor.execute(() -> {
            try {
                if (client != null) {
                    client.close();
                    client = null;
                }

                runOnUiThread(() -> {
                    log("Disconnected");
                    connectBtn.setEnabled(true);
                    sendBtn.setEnabled(false);
                    disconnectBtn.setEnabled(false);
                    disconnectBtn.setText("Disconnect");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    log("Disconnect error: " + e.getMessage());
                    connectBtn.setEnabled(true);
                    sendBtn.setEnabled(false);
                    disconnectBtn.setEnabled(false);
                    disconnectBtn.setText("Disconnect");
                });
            } finally {
                isOperationInProgress = false;
            }
        });
    }

    /**
     * Test concurrent operations to verify thread safety
     */
    private void testConcurrentSend() {
        if (client == null || !client.isConnected()) {
            log("Error: Please connect first before testing");
            return;
        }

        log("=== Starting Concurrent Send Test ===");
        log("INSTRUCTIONS: Click 'Send' button while this test is running!");

        executor.execute(() -> {
            try {
                runOnUiThread(() -> log("Test Thread: Starting long send operation..."));

                // Long send operation with test data
                byte[] testData = hexToBytes("0800822000000000000004000000000000009999");
                IsoResponse response = client.sendAndReceive(testData);

                runOnUiThread(() -> log("Test Thread: Send completed in " + response.getResponseTimeMs() + "ms"));

            } catch (IllegalStateException e) {
                runOnUiThread(() -> log("Test Thread: BLOCKED - " + e.getMessage()));
            } catch (Exception e) {
                runOnUiThread(() -> log("Test Thread Error: " + e.getMessage()));
            }
        });

        log("Test started! Now click 'Send' button to see thread safety in action!");
    }

    /**
     * Test concurrent connect operations to verify thread safety
     * This tries to create a new connection while another is active
     */
    private void testConcurrentConnect() {
        if (client == null || !client.isConnected()) {
            log("Error: Please connect first before testing");
            return;
        }

        log("=== Starting Concurrent Connect Test ===");
        log("INSTRUCTIONS: Click 'Connect' button while this test is running!");

        executor.execute(() -> {
            try {
                runOnUiThread(() -> log("Test Thread: Starting concurrent connect attempt..."));

                // Get connection parameters
                String host = hostInput.getText().toString();
                String portStr = portInput.getText().toString();
                String timeoutStr = timeoutInput.getText().toString();

                int port = Integer.parseInt(portStr);
                int timeout = Integer.parseInt(timeoutStr.isEmpty() ? "30000" : timeoutStr);
                int lengthSize = lengthSizeSpinner.getSelectedItemPosition() == 0 ? 2 : 4;

                // Try to connect with existing client (should be blocked or allowed)
                client.connect();

                runOnUiThread(() -> log("Test Thread: Connect attempt completed"));

            } catch (IllegalStateException e) {
                runOnUiThread(() -> log("Test Thread: BLOCKED - " + e.getMessage()));
            } catch (Exception e) {
                runOnUiThread(() -> log("Test Thread Error: " + e.getMessage()));
            }
        });

        log("Test started! Now click 'Connect' button to see thread safety in action!");
    }

    /**
     * Show detailed connection status
     */
    private void showStatus() {
        if (client == null) {
            log("=== STATUS ===");
            log("No client instance");
            log("Can create new client: ✅");
            log("===============");
            return;
        }

        log("=== CONNECTION STATUS ===");

        // Basic checks
        log("🔗 isConnected(): " + (client.isConnected() ? "✅" : "❌"));
        log("📂 isOpen(): " + (client.isOpen() ? "✅" : "❌"));
        log("🚪 isClosed(): " + (client.isClosed() ? "✅" : "❌"));
        log("🔄 isConnecting(): " + (client.isConnecting() ? "✅" : "❌"));
        log("🔌 isDisconnecting(): " + (client.isDisconnecting() ? "✅" : "❌"));

        // Operation status
        log("⚡ isOperationInProgress(): " + (client.isTransactionInProgress() ? "✅" : "❌"));
        log("📤 isTransactionInProgress(): " + (client.isTransactionInProgress() ? "✅" : "❌"));
        log("⏹️ isCancelled(): " + (client.isCancelled() ? "✅" : "❌"));
        log("🔄 isRetrying(): " + (client.isRetrying() ? "✅" : "❌"));

        // Error status
        log("❌ hasError(): " + (client.hasError() ? "✅" : "❌"));
        log("⏰ isTimeout(): " + (client.isTimeout() ? "✅" : "❌"));

        // Socket level
        log("📖 isReadable(): " + (client.isReadable() ? "✅" : "❌"));
        log("📝 isWritable(): " + (client.isWritable() ? "✅" : "❌"));
        log("🔗 isSocketBound(): " + (client.isSocketBound() ? "✅" : "❌"));
        log("🚪 isSocketClosed(): " + (client.isSocketClosed() ? "✅" : "❌"));

        // Security
        log("🔒 isTlsEnabled(): " + (client.isTlsEnabled() ? "✅" : "❌"));
        log("🔐 isTlsConnected(): " + (client.isTlsConnected() ? "✅" : "❌"));

        // State info
        log("📍 State: " + client.getCurrentState());
        log("🔧 Mode: " + client.getConnectionMode());
        log("⚙️ Engine: " + client.getEngineType());

        // Addresses
        String local = client.getLocalAddress();
        String remote = client.getRemoteAddress();
        log("🏠 Local: " + (local != null ? local : "N/A"));
        log("🌐 Remote: " + (remote != null ? remote : "N/A"));

        // Timing
        log("⏱️ Duration: " + client.getConnectionDuration() + "ms");
        log("🔄 Attempts: " + client.getReconnectAttempts());

        // Error info
        Exception error = client.getLastError();
        if (error != null) {
            log("⚠️ Last Error: " + error.getClass().getSimpleName() + " - " + error.getMessage());
        }

        // Utility checks
        log("--- UTILITY CHECKS ---");
        log("✅ canConnect(): " + (client.canConnect() ? "✅" : "❌"));
        log("📤 canSend(): " + (client.canSend() ? "✅" : "❌"));
        log("🔌 canDisconnect(): " + (client.canDisconnect() ? "✅" : "❌"));
        log("💚 isHealthy(): " + (client.isHealthy() ? "✅" : "❌"));
        log("🔄 needsReconnection(): " + (client.needsReconnection() ? "✅" : "❌"));

        // Status description
        log("📝 Status: " + client.getStatusDescription());

        log("========================");
    }

    private void log(String message) {
        String current = logView.getText().toString();
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        logView.setText(current + timestamp + " - " + message + "\n");
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private byte[] hexToBytes(String hex) {
        hex = hex.replaceAll("\\s", "");
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02X", b));
        }
        return result.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (client != null) {
            client.close();
        }
        executor.shutdown();
    }
}