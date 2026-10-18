package com.windrm.app.domain

import java.text.Normalizer

/** How a list of routes is ordered: as it comes, or by distance or climb, smallest or largest first. */
enum class RouteSort { DEFAULT, DISTANCE_ASC, DISTANCE_DESC, ELEVATION_ASC, ELEVATION_DESC }

private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

/** Lower case, without accents: "Pietra di Bismantova" finds "pietra di bismàntova". */
private fun fold(text: String): String = DIACRITICS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()

/** True when every word of [query] appears in [name], in any order, ignoring case and accents; a blank query matches all. */
fun matchesQuery(name: String, query: String): Boolean {
    val words = fold(query).split(' ', '\t').filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val haystack = fold(name)
    return words.all { haystack.contains(it) }
}

/**
 * The items whose name matches [query], in the order of [sort]. A sort by distance or climb is stable: items equal on
 * that value stay in the order they came in. [DEFAULT] keeps the incoming order.
 */
fun <T> List<T>.searchAndSort(
    query: String,
    sort: RouteSort,
    name: (T) -> String,
    distanceM: (T) -> Double,
    climbM: (T) -> Double,
): List<T> {
    val found = if (query.isBlank()) this else filter { matchesQuery(name(it), query) }
    return when (sort) {
        RouteSort.DEFAULT -> found
        RouteSort.DISTANCE_ASC -> found.sortedBy(distanceM)
        RouteSort.DISTANCE_DESC -> found.sortedByDescending(distanceM)
        RouteSort.ELEVATION_ASC -> found.sortedBy(climbM)
        RouteSort.ELEVATION_DESC -> found.sortedByDescending(climbM)
    }
}
