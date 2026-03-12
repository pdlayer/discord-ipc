import pd.discordipc.DiscordRPC;
import pd.discordipc.models.ActivityType;

public class Example {
    public static void main(String[] args) {
        DiscordRPC rpc = new DiscordRPC("123456789012345678");
        rpc.start();

        rpc.updatePresence(p -> p
            .type(ActivityType.PLAYING)
            .details("Playing Game")
            .state("In Menu")
            .timer("05:20")
            .large("large_icon", "Large Icon Text")
            .small("small_icon", "Small Icon Text")
            .party("party_id", 1, 5)
            .button("Website", "https://example.com")
            .button("Discord", "https://discord.gg/example")
        );

        Runtime.getRuntime().addShutdownHook(new Thread(rpc::stop));

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
