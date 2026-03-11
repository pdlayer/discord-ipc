package pd.discordipc;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class UnixConnection implements DiscordConnection {
    private final SocketChannel channel;

    public UnixConnection() throws IOException {
        String[] envVars = {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"};
        Map<String, String> env = System.getenv();
        
        Path socketPath = null;
        outer: for (String var : envVars) {
            String dir = env.get(var);
            if (dir == null) continue;
            
            for (int i = 0; i < 10; i++) {
                Path path = Path.of(dir, "discord-ipc-" + i);
                if (Files.exists(path)) {
                    socketPath = path;
                    break outer;
                }
            }
        }

        if (socketPath == null) {
            // Last resort: check /tmp directly
            for (int i = 0; i < 10; i++) {
                Path path = Path.of("/tmp", "discord-ipc-" + i);
                if (Files.exists(path)) {
                    socketPath = path;
                    break;
                }
            }
        }

        if (socketPath == null) {
            throw new IOException("Discord IPC socket not found (checked XDG_RUNTIME_DIR, TMPDIR, TMP, TEMP, and /tmp)");
        }

        channel = SocketChannel.open(StandardProtocolFamily.UNIX);
        channel.connect(UnixDomainSocketAddress.of(socketPath));
    }

    @Override
    public synchronized void write(Opcode opcode, String data) throws IOException {
        byte[] bytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(8 + bytes.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(opcode.ordinal());
        buffer.putInt(bytes.length);
        buffer.put(bytes);
        buffer.flip();
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    @Override
    public Response read() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(8);
        header.order(ByteOrder.LITTLE_ENDIAN);
        while (header.hasRemaining()) {
            int read = channel.read(header);
            if (read == -1) throw new IOException("Disconnected");
        }
        header.flip();
        int opInt = header.getInt();
        int length = header.getInt();
        
        ByteBuffer dataBuffer = ByteBuffer.allocate(length);
        while (dataBuffer.hasRemaining()) {
            int read = channel.read(dataBuffer);
            if (read == -1) throw new IOException("Disconnected");
        }
        return new Response(Opcode.fromInt(opInt), new String(dataBuffer.array(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
