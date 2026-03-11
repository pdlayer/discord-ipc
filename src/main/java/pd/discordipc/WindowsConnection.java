package pd.discordipc;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WindowsConnection implements DiscordConnection {
    private final RandomAccessFile pipe;

    public WindowsConnection() throws IOException {
        RandomAccessFile p = null;
        for (int i = 0; i < 10; i++) {
            try {
                p = new RandomAccessFile("\\\\?\\pipe\\discord-ipc-" + i, "rw");
                break;
            } catch (IOException e) {
                // Try next
            }
        }
        if (p == null) {
            throw new IOException("Discord IPC pipe not found");
        }
        this.pipe = p;
    }

    @Override
    public synchronized void write(Opcode opcode, String data) throws IOException {
        byte[] bytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] header = new byte[8];
        ByteBuffer buffer = ByteBuffer.wrap(header);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(opcode.ordinal());
        buffer.putInt(bytes.length);
        
        pipe.write(header);
        pipe.write(bytes);
    }

    @Override
    public Response read() throws IOException {
        byte[] header = new byte[8];
        pipe.readFully(header);
        ByteBuffer buffer = ByteBuffer.wrap(header);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int opInt = buffer.getInt();
        int length = buffer.getInt();
        
        byte[] data = new byte[length];
        pipe.readFully(data);
        return new Response(Opcode.fromInt(opInt), new String(data, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public void close() throws IOException {
        pipe.close();
    }
}
