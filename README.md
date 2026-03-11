# Discord IPC for Java

A lightweight Discord Rich Presence IPC client for Java.

## Features
- Windows & Unix (Linux/macOS) support
- No native dependencies (uses ProcessHandle and local sockets)
- Lightweight (uses Gson)

## Usage

```java
DiscordRPC rpc = new DiscordRPC("APPLICATION_ID");
rpc.start();

rpc.updatePresence(builder -> {
    builder.details("Exploring the world")
           .state("In a party")
           .large("logo", "Discord IPC");
});
```

## Installation (JitPack)

Add the JitPack repository to your build file:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
```

Add the dependency:

```gradle
dependencies {
    implementation 'com.github.USER:REPO:1.0.0'
}
```

## License
MIT
