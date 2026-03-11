package pd.discordipc;

import java.io.IOException;

public interface DiscordConnection extends AutoCloseable {
    void write(Opcode opcode, String data) throws IOException;
    Response read() throws IOException;
    
    @Override
    void close() throws IOException;
    
    class Response {
        public final Opcode opcode;
        public final String data;

        public Response(Opcode opcode, String data) {
            this.opcode = opcode;
            this.data = data;
        }
    }
}
