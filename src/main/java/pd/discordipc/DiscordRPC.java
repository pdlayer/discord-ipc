package pd.discordipc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import pd.discordipc.models.RichPresence;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class DiscordRPC {
    private static final Gson GSON = new Gson();
    private final String clientId;
    private DiscordConnection connection;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "DiscordRPC-Reconnect");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DiscordRPC-Reader");
        t.setDaemon(true);
        return t;
    });
    
    private final AtomicInteger nonce = new AtomicInteger(0);
    private volatile boolean connected = false;
    private volatile boolean ready = false;
    private volatile boolean running = false;
    private RichPresence lastPresence;

    public DiscordRPC(String clientId) {
        this.clientId = clientId;
    }

    public void start() {
        running = true;
        scheduler.scheduleAtFixedRate(this::checkConnection, 0, 5, TimeUnit.SECONDS);
    }

    private void checkConnection() {
        if (connected || !running) return;
        try {
            connectInternal();
        } catch (Exception e) {
            // Silence connection errors during retry
        }
    }

    private void connectInternal() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            connection = new WindowsConnection();
        } else {
            connection = new UnixConnection();
        }

        JsonObject handshake = new JsonObject();
        handshake.addProperty("v", 1);
        handshake.addProperty("client_id", clientId);
        connection.write(Opcode.HANDSHAKE, GSON.toJson(handshake));
        
        connected = true;
        
        readerExecutor.submit(() -> {
            try {
                while (connected && running) {
                    DiscordConnection.Response res = connection.read();
                    if (res.opcode == Opcode.CLOSE) break;
                    if (res.opcode == Opcode.PING) {
                        try {
                            connection.write(Opcode.PONG, res.data);
                        } catch (IOException ignored) {}
                    }
                    if (res.opcode == Opcode.FRAME) {
                        handleFrame(res.data);
                    }
                }
            } catch (IOException e) {
                // Connection lost
            } finally {
                handleDisconnect();
            }
        });
    }

    private void handleFrame(String data) {
        try {
            JsonObject json = GSON.fromJson(data, JsonObject.class);
            String cmd = json.has("cmd") ? json.get("cmd").getAsString() : null;
            String evt = json.has("evt") ? json.get("evt").getAsString() : null;

            if ("DISPATCH".equals(cmd) && "READY".equals(evt)) {
                ready = true;
                if (lastPresence != null) {
                    setPresence(lastPresence);
                }
            }
        } catch (Exception e) {
            // Json error
        }
    }

    private void handleDisconnect() {
        connected = false;
        ready = false;
        if (connection != null) {
            try { 
                connection.close(); 
            } catch (IOException ignored) {}
            connection = null;
        }
    }

    public void setPresence(RichPresence rp) {
        this.lastPresence = rp;
        if (!connected || !ready) return;

        JsonObject payload = new JsonObject();
        payload.addProperty("cmd", "SET_ACTIVITY");
        JsonObject args = new JsonObject();
        args.addProperty("pid", (int) ProcessHandle.current().pid());
        
        if (rp != null) {
            JsonObject activity = new JsonObject();
            activity.addProperty("type", rp.getType().getId());
            activity.addProperty("application_id", clientId);
            if (rp.getUrl() != null) activity.addProperty("url", rp.getUrl());

            if (rp.getDetails() != null) activity.addProperty("details", rp.getDetails());
            if (rp.getState() != null) activity.addProperty("state", rp.getState());
            
            JsonObject timestamps = new JsonObject();
            if (rp.getStartTimestamp() != null) timestamps.addProperty("start", rp.getStartTimestamp());
            if (rp.getEndTimestamp() != null) timestamps.addProperty("end", rp.getEndTimestamp());
            if (timestamps.size() > 0) activity.add("timestamps", timestamps);
            
            JsonObject assets = new JsonObject();
            if (rp.getLargeImageKey() != null) assets.addProperty("large_image", rp.getLargeImageKey());
            if (rp.getLargeImageText() != null) assets.addProperty("large_text", rp.getLargeImageText());
            if (rp.getSmallImageKey() != null) assets.addProperty("small_image", rp.getSmallImageKey());
            if (rp.getSmallImageText() != null) assets.addProperty("small_text", rp.getSmallImageText());
            if (assets.size() > 0) activity.add("assets", assets);
            
            if (rp.getPartyId() != null) {
                JsonObject party = new JsonObject();
                party.addProperty("id", rp.getPartyId());
                JsonArray size = new JsonArray();
                size.add(rp.getPartySize());
                size.add(rp.getPartyMax());
                party.add("size", size);
                activity.add("party", party);
            }
            
            if (rp.getButtons() != null && !rp.getButtons().isEmpty()) {
                JsonArray buttons = new JsonArray();
                for (RichPresence.Button b : rp.getButtons()) {
                    JsonObject button = new JsonObject();
                    button.addProperty("label", b.getLabel());
                    button.addProperty("url", b.getUrl());
                    buttons.add(button);
                }
                activity.add("buttons", buttons);
            }
            args.add("activity", activity);
        } else {
            args.add("activity", null);
        }
        
        payload.add("args", args);
        payload.addProperty("nonce", String.valueOf(nonce.incrementAndGet()));

        try {
            connection.write(Opcode.FRAME, GSON.toJson(payload));
        } catch (IOException e) {
            handleDisconnect();
        }
    }

    public void updatePresence(Consumer<RichPresence.Builder> builderConsumer) {
        RichPresence.Builder builder = new RichPresence.Builder();
        builderConsumer.accept(builder);
        setPresence(builder.build());
    }

    public void stop() {
        running = false;
        handleDisconnect();
        scheduler.shutdownNow();
        readerExecutor.shutdownNow();
    }
}
