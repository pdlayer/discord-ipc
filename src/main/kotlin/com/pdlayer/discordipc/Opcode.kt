package com.pdlayer.discordipc

enum class Opcode {
    HANDSHAKE,
    FRAME,
    CLOSE,
    PING,
    PONG;

    companion object {
        fun fromInt(value: Int): Opcode = entries[value]
    }
}
