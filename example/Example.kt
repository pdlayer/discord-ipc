import com.pdlayer.discordipc.DiscordRPC
import com.pdlayer.discordipc.models.RichPresence

fun main() {
    val rpc = DiscordRPC("123456789012345678")
    rpc.start()

    val presence = RichPresence.playing(
        details = "Playing Game",
        state = "In Menu",
        timestamps = RichPresence.Timestamps.elapsed("05:20"),
        assets = RichPresence.Assets(
            largeImageKey = "large_icon",
            largeImageText = "Large Icon Text",
            smallImageKey = "small_icon",
            smallImageText = "Small Icon Text",
        ),
        party = RichPresence.Party("party_id", 1, 5),
        buttons = listOf(
            RichPresence.Button("Website", "https://example.com"),
            RichPresence.Button("Discord", "https://discord.gg/example"),
        ),
    )

    rpc.setPresence(presence)

    Runtime.getRuntime().addShutdownHook(Thread(rpc::stop))
    Thread.sleep(Long.MAX_VALUE)
}
