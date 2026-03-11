package pd.discordipc;

import java.io.IOException;

public enum Opcode {
    HANDSHAKE,
    FRAME,
    CLOSE,
    PING,
    PONG;

    public static Opcode fromInt(int val) {
        return values()[val];
    }
}
