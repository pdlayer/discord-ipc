package com.pdlayer.discordipc.utils

object TimeUtils {
    fun parseToSeconds(time: String?): Long {
        if (time == null || !time.contains(":")) {
            return 0
        }

        val parts = time.split(":")

        return try {
            when (parts.size) {
                2 -> parts[0].toLong() * 60 + parts[1].toLong()
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                else -> 0
            }
        } catch (_: NumberFormatException) {
            0
        }
    }
}
