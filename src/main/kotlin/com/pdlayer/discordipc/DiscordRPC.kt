package com.pdlayer.discordipc

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.pdlayer.discordipc.models.RichPresence
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DiscordRPC(
    private val clientId: String,
) {
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "DiscordRPC-Reconnect").apply { isDaemon = true }
    }
    private val readerExecutor: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "DiscordRPC-Reader").apply { isDaemon = true }
    }
    private val nonce = AtomicInteger(0)

    private var connection: DiscordConnection? = null

    @Volatile
    private var connected = false

    @Volatile
    private var ready = false

    @Volatile
    private var running = false

    @Volatile
    private var lastPresence: RichPresence? = null

    fun start() {
        running = true
        scheduler.scheduleAtFixedRate(::checkConnection, 0, 5, TimeUnit.SECONDS)
    }

    private fun checkConnection() {
        if (connected || !running) {
            return
        }

        try {
            connectInternal()
        } catch (_: Exception) {
        }
    }

    @Throws(IOException::class)
    private fun connectInternal() {
        val nextConnection: DiscordConnection =
            if (System.getProperty("os.name").lowercase().contains("win")) {
                WindowsConnection()
            } else {
                UnixConnection()
            }

        val handshake = JsonObject().apply {
            addProperty("v", 1)
            addProperty("client_id", clientId)
        }

        nextConnection.write(Opcode.HANDSHAKE, GSON.toJson(handshake))
        connection = nextConnection
        connected = true

        readerExecutor.submit {
            try {
                while (connected && running) {
                    val response = nextConnection.read()

                    if (response.opcode == Opcode.CLOSE) {
                        break
                    }

                    if (response.opcode == Opcode.PING) {
                        try {
                            nextConnection.write(Opcode.PONG, response.data)
                        } catch (_: IOException) {
                        }
                    }

                    if (response.opcode == Opcode.FRAME) {
                        handleFrame(response.data)
                    }
                }
            } catch (_: IOException) {
            } finally {
                handleDisconnect()
            }
        }
    }

    private fun handleFrame(data: String) {
        try {
            val json = GSON.fromJson(data, JsonObject::class.java)
            val cmd = json.takeIf { it.has("cmd") }?.get("cmd")?.asString
            val event = json.takeIf { it.has("evt") }?.get("evt")?.asString

            if (cmd == "DISPATCH" && event == "READY") {
                ready = true
                lastPresence?.let(::setPresence)
            }
        } catch (_: Exception) {
        }
    }

    private fun handleDisconnect() {
        connected = false
        ready = false
        connection?.let {
            try {
                it.close()
            } catch (_: IOException) {
            }
        }
        connection = null
    }

    fun setPresence(presence: RichPresence?) {
        lastPresence = presence

        val currentConnection = connection
        if (!connected || !ready || currentConnection == null) {
            return
        }

        val payload = JsonObject().apply {
            addProperty("cmd", "SET_ACTIVITY")
            add("args", JsonObject().apply {
                addProperty("pid", ProcessHandle.current().pid().toInt())
                add("activity", presence?.toActivityJson() ?: JsonNull.INSTANCE)
            })
            addProperty("nonce", nonce.incrementAndGet().toString())
        }

        try {
            currentConnection.write(Opcode.FRAME, GSON.toJson(payload))
        } catch (_: IOException) {
            handleDisconnect()
        }
    }

    fun clearPresence() {
        setPresence(null)
    }

    fun stop() {
        running = false
        handleDisconnect()
        scheduler.shutdownNow()
        readerExecutor.shutdownNow()
    }

    private fun RichPresence.toActivityJson(): JsonObject {
        val presence = this

        return JsonObject().apply {
            addProperty("type", presence.type.id)
            addProperty("application_id", clientId)
            presence.url?.let { addProperty("url", it) }
            presence.details?.let { addProperty("details", it) }
            presence.state?.let { addProperty("state", it) }

            presence.timestamps?.toJson()?.takeIf { it.size() > 0 }?.let {
                add("timestamps", it)
            }

            presence.assets?.toJson()?.takeIf { it.size() > 0 }?.let {
                add("assets", it)
            }

            presence.party?.let {
                add("party", it.toJson())
            }

            presence.secrets?.toJson()?.takeIf { it.size() > 0 }?.let {
                add("secrets", it)
            }

            presence.buttons?.takeIf { it.isNotEmpty() }?.let {
                add("buttons", it.toJson())
            }
        }
    }

    private fun RichPresence.Timestamps.toJson(): JsonObject = JsonObject().apply {
        start?.let { addProperty("start", it) }
        end?.let { addProperty("end", it) }
    }

    private fun RichPresence.Assets.toJson(): JsonObject = JsonObject().apply {
        largeImageKey?.let { addProperty("large_image", it) }
        largeImageText?.let { addProperty("large_text", it) }
        smallImageKey?.let { addProperty("small_image", it) }
        smallImageText?.let { addProperty("small_text", it) }
    }

    private fun RichPresence.Party.toJson(): JsonObject = JsonObject().apply {
        addProperty("id", id)
        add("size", JsonArray().apply {
            add(size)
            add(max)
        })
    }

    private fun RichPresence.Secrets.toJson(): JsonObject = JsonObject().apply {
        match?.let { addProperty("match", it) }
        join?.let { addProperty("join", it) }
        spectate?.let { addProperty("spectate", it) }
    }

    private fun List<RichPresence.Button>.toJson(): JsonArray = JsonArray().also { buttons ->
        forEach { button ->
            buttons.add(JsonObject().apply {
                addProperty("label", button.label)
                addProperty("url", button.url)
            })
        }
    }

    private companion object {
        val GSON = Gson()
    }
}
