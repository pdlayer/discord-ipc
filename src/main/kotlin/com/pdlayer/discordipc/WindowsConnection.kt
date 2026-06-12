package com.pdlayer.discordipc

import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class WindowsConnection @Throws(IOException::class) constructor() : DiscordConnection {
    private val pipe: RandomAccessFile = findPipe()

    @Synchronized
    @Throws(IOException::class)
    override fun write(opcode: Opcode, data: String) {
        val bytes = data.toByteArray(StandardCharsets.UTF_8)
        val header = ByteArray(8)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(opcode.ordinal)
        buffer.putInt(bytes.size)

        pipe.write(header)
        pipe.write(bytes)
    }

    @Throws(IOException::class)
    override fun read(): DiscordConnection.Response {
        val header = ByteArray(8)
        pipe.readFully(header)

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val opcode = Opcode.fromInt(buffer.getInt())
        val length = buffer.getInt()

        val data = ByteArray(length)
        pipe.readFully(data)

        return DiscordConnection.Response(opcode, data.toString(StandardCharsets.UTF_8))
    }

    @Throws(IOException::class)
    override fun close() {
        pipe.close()
    }

    companion object {
        @Throws(IOException::class)
        private fun findPipe(): RandomAccessFile {
            for (index in 0 until 10) {
                try {
                    return RandomAccessFile("\\\\?\\pipe\\discord-ipc-$index", "rw")
                } catch (_: IOException) {
                }
            }

            throw IOException("Discord IPC pipe not found")
        }
    }
}
