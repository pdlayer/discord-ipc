# Discord IPC 

IPC дискорд джава нет жни

## Подключение (Gradle)
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.pdlayer:discord-ipc:1.0.0'
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
    .large("logo_key", "Текст")
    .small("verify_icon", "Игрок")
    .party("party_id", 1, 5)
    .button("Telegram", "https://t.me/...")
    .button("Discord", "https://discord.gg/...")
);

rpc.stop();
```
