package com.artkashin.aiwalkingguide.tour

import kotlin.math.*
import java.net.URLEncoder

data class Position(val lat: Double, val lon: Double, val accuracy: Float = 10f) {
    init { require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) }
    fun distance(other: Position): Double {
        val a = sin(Math.toRadians(other.lat - lat) / 2).pow(2) + cos(Math.toRadians(lat)) *
            cos(Math.toRadians(other.lat)) * sin(Math.toRadians(other.lon - lon) / 2).pow(2)
        return 6371000 * 2 * atan2(sqrt(a.coerceIn(0.0, 1.0)), sqrt((1-a).coerceIn(0.0, 1.0)))
    }
}

data class Poi(val id: String, val title: String, val position: Position, val extract: String,
    val source: String, val aliases: Set<String> = emptySet())

enum class NarrationState { RESERVED, PLAYING, PAUSED, COMPLETED, SKIPPED }

/** Navigation is intentionally absent from eligibility. */
object GuidePolicy {
    fun candidate(position: Position, pois: List<Poi>, excluded: Set<String>, radius: Double = 160.0): Poi? {
        if (!position.accuracy.isFinite() || position.accuracy > 60 || position.accuracy <= 0) return null
        return pois.filter { it.id !in excluded && it.aliases.none(excluded::contains) &&
            it.extract.length >= 100 && position.distance(it.position) <= radius }
            .minByOrNull { position.distance(it.position) }
    }
    fun route(position: Position, goals: List<Poi>, finished: Set<String>): List<Poi> {
        val remaining = goals.filter { it.id !in finished }.toMutableList()
        val result = mutableListOf<Poi>(); var cursor = position
        while (remaining.isNotEmpty()) {
            val next = remaining.minBy { cursor.distance(it.position) }
            remaining.remove(next); result.add(next); cursor = next.position
        }
        return result
    }
    fun mapsUrl(goal: Poi): String = "https://www.google.com/maps/dir/?api=1&destination=" +
        URLEncoder.encode("${goal.position.lat},${goal.position.lon}", "UTF-8") + "&travelmode=walking&dir_action=navigate"
}

object SpeechChunks {
    fun split(text: String, limit: Int = 1800): List<String> {
        require(limit > 0)
        val result = mutableListOf<String>(); var remaining = text.trim()
        while (remaining.length > limit) {
            val prefix = remaining.take(limit)
            val boundary = maxOf(prefix.lastIndexOf(". "), prefix.lastIndexOf("! "), prefix.lastIndexOf("? "))
            val end = if (boundary >= limit / 3) boundary + 1 else prefix.lastIndexOf(' ').takeIf { it > 0 } ?: limit
            result.add(remaining.take(end).trim()); remaining = remaining.drop(end).trimStart()
        }
        if (remaining.isNotEmpty()) result.add(remaining)
        return result
    }
}
