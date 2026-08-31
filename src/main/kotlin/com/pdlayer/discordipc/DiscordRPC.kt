package com.pdlayer.discordipc

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.pdlayer.discordipc.models.ActivityType
import com.pdlayer.discordipc.models.RichPresence
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DiscordRPC @JvmOverloads constructor(
    private val clientId: String,
    private val onError: (code: Int, message: String, command: String?) -> Unit = { _, _, _ -> },
) {
    private val lock = Any()

    private var scheduler: ScheduledExecutorService? = null

    private var readerExecutor: ExecutorService? = null

    private var writerExecutor: ExecutorService? = null

    private val nonce = AtomicInteger(0)

    private val pendingCommands = ConcurrentHashMap<String, String>()

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
        val reconnect: ScheduledExecutorService
        val reader: ExecutorService
        val writer: ExecutorService

        synchronized(lock) {
            if (running) {
                return
            }

            reconnect = Executors.newSingleThreadScheduledExecutor {
                Thread(it, "DiscordRPC-Reconnect").apply { isDaemon = true }
            }
            reader = Executors.newSingleThreadExecutor {
                Thread(it, "DiscordRPC-Reader").apply { isDaemon = true }
            }
            writer = Executors.newSingleThreadExecutor {
                Thread(it, "DiscordRPC-Writer").apply { isDaemon = true }
            }

            running = true
            scheduler = reconnect
            readerExecutor = reader
            writerExecutor = writer
        }

        try {
            reconnect.scheduleAtFixedRate(::checkConnection, 0, RECONNECT_INTERVAL_SECONDS, TimeUnit.SECONDS)
        } catch (_: Exception) {
            suppress { reconnect.shutdownNow() }
            suppress { reader.shutdownNow() }
            suppress { writer.shutdownNow() }
            synchronized(lock) {
                if (scheduler === reconnect) scheduler = null
                if (readerExecutor === reader) readerExecutor = null
                if (writerExecutor === writer) writerExecutor = null
                running = false
            }
        }
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
        val nextConnection = openConnection()

        try {
            val handshake = JsonObject().apply {
                addProperty("v", RPC_VERSION)
                addProperty("client_id", clientId)
            }

            nextConnection.write(Opcode.HANDSHAKE, GSON.toJson(handshake))
        } catch (e: IOException) {
            closeQuietly(nextConnection)
            throw e
        }

        var reader: ExecutorService? = null

        synchronized(lock) {
            if (running) {
                reader = readerExecutor
                connection = nextConnection
                connected = true
            }
        }

        val target = reader ?: run {
            closeQuietly(nextConnection)
            return
        }

        try {
            target.submit { readLoop(nextConnection) }
        } catch (_: Exception) {
            handleDisconnect(nextConnection)
        }
    }

    @Throws(IOException::class)
    private fun openConnection(): DiscordConnection =
        if (System.getProperty("os.name").lowercase().contains("win")) {
            WindowsConnection()
        } else {
            UnixConnection()
        }

    private fun readLoop(conn: DiscordConnection) {
        try {
            while (isCurrent(conn)) {
                val response = conn.read()

                when (response.opcode) {
                    Opcode.CLOSE -> break

                    Opcode.PING -> {
                        val sent = try {
                            conn.write(Opcode.PONG, response.data)
                            true
                        } catch (_: IOException) {
                            false
                        }

                        if (!sent) break
                    }

                    Opcode.FRAME -> handleFrame(response.data)

                    Opcode.HANDSHAKE, Opcode.PONG -> Unit
                }
            }
        } catch (_: IOException) {
        } catch (e: Throwable) {
            reportError(ERROR_CODE_INTERNAL, e.message ?: "Unhandled error in reader loop", null)
        } finally {
            handleDisconnect(conn)
        }
    }

    private fun isCurrent(conn: DiscordConnection): Boolean =
        synchronized(lock) { running && connection === conn }

    private fun handleFrame(data: String) {
        val json = try {
            GSON.fromJson(data, JsonObject::class.java)
        } catch (_: Exception) {
            null
        } ?: return

        val cmd = json.stringOrNull("cmd")
        val evt = json.stringOrNull("evt")
        val responseNonce = json.stringOrNull("nonce")

        when (evt) {
            EVT_ERROR -> {
                val error = json.getAsJsonObject("data")
                val code = error?.intOrNull("code") ?: 0
                val message = error?.stringOrNull("message") ?: "Unknown RPC error"
                val failedCommand = responseNonce?.let { pendingCommands.remove(it) }

                reportError(code, message, failedCommand)
            }

            EVT_READY -> {
                if (cmd == CMD_DISPATCH) {
                    ready = true
                    lastPresence?.let(::setPresence)
                }
            }

            else -> Unit
        }

        responseNonce?.let { pendingCommands.remove(it) }
    }

    private fun reportError(code: Int, message: String, command: String?) {
        suppress { onError(code, message, command) }
    }

    private fun handleDisconnect(conn: DiscordConnection?) {
        var toClose: DiscordConnection? = conn

        synchronized(lock) {
            if (conn == null) {
                toClose = connection
                connection = null
                connected = false
                ready = false
                pendingCommands.clear()
            } else if (connection === conn) {
                connection = null
                connected = false
                ready = false
                pendingCommands.clear()
            }
        }

        closeQuietly(toClose)
    }

    fun setPresence(presence: RichPresence?) {
        if (presence != null && !presence.type.supportedBySetActivity) {
            val allowed = ActivityType.entries.filter { it.supportedBySetActivity }.joinToString { it.name }

            throw IllegalArgumentException(
                "SET_ACTIVITY supports activity types $allowed only, got ${presence.type.name}"
            )
        }

        lastPresence = presence

        val currentConnection = synchronized(lock) {
            if (connected && ready) connection else null
        } ?: return

        val nonceValue = nonce.incrementAndGet().toString()
        if (pendingCommands.size >= MAX_PENDING_COMMANDS) {
            pendingCommands.clear()
        }
        pendingCommands[nonceValue] = CMD_SET_ACTIVITY

        val payload = JsonObject().apply {
            addProperty("cmd", CMD_SET_ACTIVITY)
            add("args", JsonObject().apply {
                addProperty("pid", ProcessHandle.current().pid().toInt())
                add("activity", presence?.toActivityJson() ?: JsonNull.INSTANCE)
            })
            addProperty("nonce", nonceValue)
        }

        val writer = synchronized(lock) { writerExecutor }

        if (writer != null) {
            try {
                writer.submit { send(currentConnection, payload, nonceValue) }
                return
            } catch (_: Exception) {
            }
        }

        send(currentConnection, payload, nonceValue)
    }

    private fun send(conn: DiscordConnection, payload: JsonObject, nonceValue: String) {
        try {
            conn.write(Opcode.FRAME, GSON.toJson(payload))
        } catch (_: IOException) {
            pendingCommands.remove(nonceValue)
            handleDisconnect(conn)
        }
    }

    fun clearPresence() {
        setPresence(null)
    }

    fun stop() {
        var reconnect: ScheduledExecutorService? = null
        var reader: ExecutorService? = null
        var writer: ExecutorService? = null

        synchronized(lock) {
            running = false
            reconnect = scheduler
            reader = readerExecutor
            writer = writerExecutor
            scheduler = null
            readerExecutor = null
            writerExecutor = null
        }

        handleDisconnect(null)

        suppress { reconnect?.shutdownNow() }
        suppress { reader?.shutdownNow() }
        suppress { writer?.shutdownNow() }
    }

    private fun RichPresence.toActivityJson(): JsonObject {
        val presence = this

        return JsonObject().apply {
            addProperty("type", presence.type.id)
            addProperty("application_id", clientId)
            presence.url?.let { addProperty("url", it) }
            presence.details?.let { addProperty("details", it) }
            presence.detailsUrl?.let { addProperty("details_url", it) }
            presence.state?.let { addProperty("state", it) }
            presence.stateUrl?.let { addProperty("state_url", it) }

            if (presence.instance) {
                addProperty("instance", true)
            }

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
        largeImageUrl?.let { addProperty("large_url", it) }
        smallImageUrl?.let { addProperty("small_url", it) }
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

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive) return null

        return try {
            element.asString
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive) return null

        return try {
            element.asInt
        } catch (_: Exception) {
            null
        }
    }

    private fun closeQuietly(conn: DiscordConnection?) {
        suppress { conn?.close() }
    }

    private inline fun suppress(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
        }
    }

    private companion object {
        val GSON = Gson()

        const val RPC_VERSION = 1
        const val RECONNECT_INTERVAL_SECONDS = 5L
        const val MAX_PENDING_COMMANDS = 64

        const val CMD_SET_ACTIVITY = "SET_ACTIVITY"
        const val CMD_DISPATCH = "DISPATCH"

        const val EVT_READY = "READY"
        const val EVT_ERROR = "ERROR"

        const val ERROR_CODE_INTERNAL = 0
    }
}
