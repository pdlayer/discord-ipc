package com.pdlayer.discordipc

enum class Opcode(val wire: Int) {
    HANDSHAKE(0),
    FRAME(1),
    CLOSE(2),
    PING(3),
    PONG(4);

    companion object {
        @JvmStatic
        fun fromWire(value: Int): Opcode? = entries.firstOrNull { it.wire == value }
    }
}
