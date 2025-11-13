# Config Modes and Framing Guide

This guide shows how to configure sending/receiving frames, header behavior, and logging.

## Quick Reference

- Length-prefixed (default): 2 or 4 bytes, BIG_ENDIAN by default
- Headerless modes: delimiter, fixed-length, raw-until-idle
- Mixed modes per direction:
  - Send with header, receive without header
  - Send without header, receive with header
- Auto-detect: fallback to headerless when header cannot be read
- lengthIncludesHeader: header value = payloadLen (+) headerSize
- Per-call overrides and runtime updates supported

## Length Header Modes

Default client:
```java
IsoClient client = new IsoClient(config, 2, ByteOrder.BIG_ENDIAN);
```

4-byte header (LE):
```java
FramingOptions fo = FramingOptions.builder()
    .sendLengthHeader(true)
    .expectResponseHeader(true)
    .lengthHeaderSize(4)
    .byteOrder(ByteOrder.LITTLE_ENDIAN)
    .build();
IsoClient client = new IsoClient(config, fo);
```

Include header size in length value:
```java
FramingOptions fo = FramingOptions.builder()
    .sendLengthHeader(true)
    .expectResponseHeader(true)
    .lengthHeaderSize(2)
    .lengthIncludesHeader(true) // value = payload + headerSize
    .build();
```

## Headerless Modes

Raw until idle (1:1 request/response):
```java
FramingOptions noHeader = FramingOptions.builder()
    .sendLengthHeader(false)
    .expectResponseHeader(false)
    .idleGapMs(150)
    .build();
IsoResponse r = client.sendAndReceive(data, noHeader);
```

Fixed-length response:
```java
FramingOptions fixed = FramingOptions.builder()
    .sendLengthHeader(false)
    .expectResponseHeader(false)
    .fixedResponseLength(512)
    .build();
```

Delimiter-terminated response (e.g., LF):
```java
FramingOptions line = FramingOptions.builder()
    .sendLengthHeader(false)
    .expectResponseHeader(false)
    .responseTerminator(new byte[]{'\n'})
    .build();
```

## Mixed Direction Modes

Send with header; receive without:
```java
FramingOptions fo = FramingOptions.builder()
    .sendLengthHeader(true)
    .expectResponseHeader(false)
    .idleGapMs(150)
    .build();
```

Send without header; receive with:
```java
FramingOptions fo = FramingOptions.builder()
    .sendLengthHeader(false)
    .expectResponseHeader(true)
    .build();
```

## Auto-Detect Fallback

Try header first; fallback to headerless on EOF while reading header:
```java
FramingOptions auto = FramingOptions.builder()
    .sendLengthHeader(true)
    .expectResponseHeader(true)
    .autoDetect(true)
    .idleGapMs(150)
    .build();
client.updateFraming(auto);
```

## Per-Call Override and Runtime Update

Per-call:
```java
IsoResponse r = client.sendAndReceive(data, FramingOptions.builder()
    .sendLengthHeader(false).expectResponseHeader(false).idleGapMs(150).build());
```

Runtime update:
```java
client.updateFraming(FramingOptions.builder()
    .sendLengthHeader(true).expectResponseHeader(true).lengthHeaderSize(4).build());
```

## Advanced Logging

Enable logging:
```java
LoggingConfig logs = LoggingConfig.builder()
    .enabled(true)
    .minimumLevel(com.miaad.iso8583TCPSocket.logging.LogLevel.DEBUG)
    .includePayloads(true)
    .maxPayloadBytes(2048)
    .maskSensitive(true)
    .logSends(true)
    .logReceives(true)
    .logHeaders(true)
    .logErrors(true)
    .logState(true)
    .captureInMemory(true)
    .inMemoryCapacity(1000)
    .build();

IsoConfig config = new IsoConfig.Builder(host, port)
    .connectTimeout(30000)
    .readTimeout(30000)
    .loggingConfig(logs)
    .build();
```

Update logging at runtime:
```java
client.updateLogging(logs);
```

Fetch captured logs:
```java
List<com.miaad.iso8583TCPSocket.logging.LogEntry> entries = client.getCapturedLogs();
```


