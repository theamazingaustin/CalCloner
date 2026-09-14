package com.stripedlens.calcloner.domain.routing

import com.stripedlens.calcloner.SyncPair

/**
 * Directed Acyclic Graph (DAG) cycle detection to prevent circular sync loops.
 *
 * Checks if adding or updating a calendar sync pair from [proposedFromId] to [proposedToId]
 * would introduce a circular dependency among [existingPairs].
 */
object CycleDetector {

    /**
     * Returns true if adding or updating the sync route creates a directed cycle.
     *
     * @param existingPairs Currently configured sync pairs.
     * @param proposedFromId Source calendar ID for the route.
     * @param proposedToId Target calendar ID for the route.
     * @param currentPairId ID of the pair being edited, if any (excluded from cycle check to allow updating self).
     * @return `true` if a cycle is detected, `false` if the proposed route forms a valid DAG.
     */
    fun hasCycle(
        existingPairs: List<SyncPair>,
        proposedFromId: Long,
        proposedToId: Long,
        currentPairId: String? = null
    ): Boolean {
        // Direct self-sync (from calendar X directly to calendar X) is always invalid
        if (proposedFromId == proposedToId) return true

        // Build adjacency list: fromCalendarId -> list of toCalendarIds
        val graph = mutableMapOf<Long, MutableList<Long>>()
        for (pair in existingPairs) {
            if (pair.id == currentPairId) continue
            graph.getOrPut(pair.fromCalendarId) { mutableListOf() }.add(pair.toCalendarId)
        }
        graph.getOrPut(proposedFromId) { mutableListOf() }.add(proposedToId)

        val visited = mutableSetOf<Long>()
        val inStack = mutableSetOf<Long>()

        fun dfs(node: Long): Boolean {
            visited.add(node)
            inStack.add(node)
            for (neighbor in graph[node] ?: emptyList()) {
                if (neighbor !in visited) {
                    if (dfs(neighbor)) return true
                } else if (neighbor in inStack) {
                    return true
                }
            }
            inStack.remove(node)
            return false
        }

        for (node in graph.keys) {
            if (node !in visited) {
                if (dfs(node)) return true
            }
        }
        return false
    }
}
