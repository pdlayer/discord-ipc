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
        val frame = ByteArray(FrameFormat.HEADER_SIZE + bytes.size)
        val buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(opcode.wire)
        buffer.putInt(bytes.size)
        buffer.put(bytes)

        pipe.write(frame)
    }

    @Throws(IOException::class)
    override fun read(): DiscordConnection.Response {
        val header = ByteArray(FrameFormat.HEADER_SIZE)
        pipe.readFully(header)

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val opcode = FrameFormat.resolveOpcode(buffer.getInt())
        val length = buffer.getInt()
        FrameFormat.checkPayloadSize(length)

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
