package com.pdlayer.discordipc.utils

object TimeUtils {
    private val HOURS_MINUTES_SECONDS = Regex("""(\d{1,4}):([0-5]\d):([0-5]\d)""")
    private val MINUTES_SECONDS = Regex("""(\d{1,4}):([0-5]\d)""")

    fun parseToSeconds(time: String?): Long {
        if (time == null) {
            return 0
        }

        val value = time.trim()

        HOURS_MINUTES_SECONDS.matchEntire(value)?.let { match ->
            val (hours, minutes, seconds) = match.destructured
            return hours.toLong() * 3600 + minutes.toLong() * 60 + seconds.toLong()
        }

        MINUTES_SECONDS.matchEntire(value)?.let { match ->
            val (minutes, seconds) = match.destructured
            return minutes.toLong() * 60 + seconds.toLong()
        }

        throw IllegalArgumentException(
            "Time must be mm:ss or hh:mm:ss with minutes and seconds in 0..59, got \"$time\""
        )
    }
}
