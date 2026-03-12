[![JitPack](https://jitpack.io/v/pdlayer/discord-ipc.svg?style=flat-square)](https://jitpack.io/#pdlayer/discord-ipc)
![Java](https://img.shields.io/badge/Java-16%2B-informational?style=flat-square&logo=openjdk&color=4194c6)
![License](https://img.shields.io/github/license/pdlayer/discord-ipc?style=flat-square&color=orange)
![Stars](https://img.shields.io/github/stars/pdlayer/discord-ipc?style=flat-square&color=yellow)
![Issues](https://img.shields.io/github/issues/pdlayer/discord-ipc?style=flat-square&color=red)
![Repo Size](https://img.shields.io/github/repo-size/pdlayer/discord-ipc?style=flat-square)
![Last Commit](https://img.shields.io/github/last-commit/pdlayer/discord-ipc?style=flat-square&color=green)
# Discord IPC 

IPC дискорд джава нет жни и там ищо ну легковесная крч понял меня

## Подключение

### Gradle
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.pdlayer:discord-ipc:1.0.0'
}
```

### Maven
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>[https://jitpack.io](https://jitpack.io)</url>
    </repository>
</repositories>

<dependencies>
<dependency>
    <groupId>com.github.pdlayer</groupId>
    <artifactId>discord-ipc</artifactId>
    <version>1.0.0</version>
</dependency>
</dependencies>
```

### Gradle Kotlin
```kotlin
repositories {
    maven("[https://jitpack.io](https://jitpack.io)")
}

dependencies {
    implementation("com.github.pdlayer:discord-ipc:1.0.0")
}
```

## Использование

```java
DiscordRPC rpc = new DiscordRPC("CLIENT_ID"); // https://discord.dev
rpc.start();

rpc.updatePresence(p -> p
    .type(ActivityType.PLAYING)
    .details("Играет на сервере")
    .state("В лобби")
    .timer("05:20")
    .large("larg icon", "Текст")
    .small("smol_icon", "Игрок")
    .party("party_id", 1, 5)
    .button("Telegram", "https://t.me/...")
    .button("Discord", "https://discord.gg/...")
);

rpc.stop();
```

![Moe Counter](https://count.getloli.com/get/@pdlayer-discord-ipc?theme=moebooru)