# Changelog

All notable changes to the iso8583TCPSocket library will be documented in this file.

## [1.3.1] - 2025-11-11

### Added
- LoggingConfig and IsoLogger: structured logging with in-memory capture and external sink hook
- Engine instrumentation for send/receive/header/error events
- Debug logging overload for payload + direction
- Documentation: config modes guide (docs/CONFIG_MODES.md) and README link; logging quick start

### Changed
- Cleanup: remove legacy duplicate classes outside engine/

## [1.3.0] - 2025-11-11

### Added
- FramingOptions.lengthIncludesHeader: optionally include header size in length value (send/receive)
- Sample: “Send (No Header)” example in MainActivity

## [1.2.0] - 2025-11-11

### ✨ Added
- FramingOptions for advanced framing control:
  - Optional send length header (2/4 bytes) and optional expect response header
  - Auto-detect fallback if header is missing/invalid on receive
  - Headerless receive modes: fixed length, delimiter-terminated, idle-gap based
  - Per-call override via `IsoClient.sendAndReceive(bytes, FramingOptions)` and runtime update via `IsoClient.updateFraming(...)`
- BlockingEngine and NonBlockingEngine updated to support new framing behaviors
- Advanced documentation added to README: “Advanced Socket Connection & Framing (New)”

### 🛡️ Compatibility
- Default behavior remains unchanged (2-byte BIG_ENDIAN length header for send/receive)

## [1.0.0] - 2025-08-28

### 🎉 Initial Release

#### ✨ Added
- **Complete ISO-8583 TCP Client Library** for Android
- **Dual Engine Support**: Blocking I/O and Non-blocking NIO engines
- **Comprehensive Status Checking System** with 40+ status methods
- **Connection State Management** with detailed state monitoring
- **Retry Mechanism** with configurable retry policies
- **TLS/SSL Support** with certificate pinning
- **Thread-Safe Implementation** with concurrent operation protection
- **Android API 21+ Support** (Android 5.0 and above)

#### 🔧 Core Features
- `IsoClient` - Main client class with dual engine support
- `IsoConfig` - Configuration builder with all connection options
- `ConnectionEngine` - Interface for different I/O engines
- `BlockingEngine` - Traditional blocking socket implementation
- `NonBlockingEngine` - NIO-based non-blocking implementation
- `ConnectionStatus` - Comprehensive status information class

#### 📊 Status Checking Methods
- `isConnected()`, `isOpen()`, `isClosed()`
- `isConnecting()`, `isDisconnecting()`
- `isTransactionInProgress()`, `isOperationInProgress()`
- `hasError()`, `isTimeout()`, `isCancelled()`
- `isTlsEnabled()`, `isTlsConnected()`
- `isReadable()`, `isWritable()`, `isSocketBound()`
- `canConnect()`, `canSend()`, `canDisconnect()`
- `isHealthy()`, `needsReconnection()`

#### 🔄 Retry & Error Handling
- `RetryConfig` - Configurable retry policies
- `RetryCallback` - Retry attempt monitoring
- Exponential backoff with jitter
- Configurable retry conditions (timeout, connection failure, etc.)

#### 📡 Connection State Management
- `ConnectionState` - 20+ connection states
- `ConnectionStateListener` - Detailed state monitoring
- Real-time state change notifications
- Comprehensive error reporting

#### 🛠️ Development Tools
- **Sample Android App** with comprehensive testing UI
- **Python Test Server** with configurable response delays
- **Quick Server Test Script** for easy server management
- **Complete documentation** and usage examples

#### 📦 Package Structure
```
com.miaad.iso8583TCPSocket/
├── IsoClient.java              # Main client class
├── IsoConfig.java              # Configuration builder
├── IsoResponse.java            # Response wrapper
├── ConnectionStatus.java       # Status information
├── ConnectionState.java        # State enumeration
├── ConnectionStateListener.java # State monitoring
├── RetryConfig.java            # Retry configuration
├── RetryCallback.java          # Retry monitoring
├── ConnectionMode.java         # Engine mode selection
└── engine/
    ├── ConnectionEngine.java   # Engine interface
    ├── BlockingEngine.java     # Blocking I/O engine
    └── NonBlockingEngine.java  # Non-blocking NIO engine
```

#### 🚀 Installation

**Gradle:**
```gradle
dependencies {
    implementation 'com.github.Miaadrajabi:iso8583TCPSocket:1.0.0'
}
```

**Maven:**
```xml
<dependency>
    <groupId>com.github.Miaadrajabi</groupId>
    <artifactId>iso8583TCPSocket</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### 📋 Requirements
- **Minimum SDK**: API 21 (Android 5.0)
- **Target SDK**: API 33+
- **Java**: 8 or higher
- **Gradle**: 6.7.1+
- **Android Gradle Plugin**: 4.1+

#### 🎯 Key Benefits
- **Lightweight**: Minimal dependencies and memory footprint
- **Thread-Safe**: Designed for concurrent usage
- **Flexible**: Support for both blocking and non-blocking modes
- **Robust**: Comprehensive error handling and retry mechanisms
- **Observable**: Detailed state monitoring and logging
- **Production-Ready**: Tested with real-world scenarios

---

## [Unreleased]

### 🔮 Planned Features
- Enhanced TLS configuration options
- Connection pooling support
- Performance metrics and monitoring
- Additional framing protocols
- WebSocket support
- Kotlin coroutines integration
