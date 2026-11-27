package com.mtwabpin.kolahal.assistant

import org.json.JSONArray
import org.json.JSONObject

/**
 * Which component holds which, on each surface of a conversation, read from the A2UI messages before
 * the renderer gets them. Drawing a component draws its children in turn, on the main thread: an
 * answer whose components hold each other in a circle would recurse without end, and one nested very
 * deep nearly so. Such an answer is refused ([take] throws), and shows as one that can't be drawn.
 *
 * A component names its children by id: "child", "children" (ids, or a template's "componentId"), a
 * tab's "child", a modal's "trigger" and "content". An id no component has yet is no edge: it may come
 * in a later message, and is checked then.
 */
class SurfaceShapes {
    /** Each surface's components, by id: the ids each one names as its children. */
    private var surfaces: Map<String, Map<String, List<String>>> = emptyMap()

    /** Forgets every surface: the renderer was made new. */
    fun clear() {
        surfaces = emptyMap()
    }

    /**
     * Takes in [messages] (each one's JSON text), all or none. Throws [IllegalArgumentException], in
     * words the model and the page can read, when a surface would then have a circle or be nested more
     * than [MAX_DEPTH] deep.
     */
    fun take(messages: List<String>) {
        val next = HashMap<String, MutableMap<String, List<String>>>()
        surfaces.forEach { (id, components) -> next[id] = HashMap(components) }
        val changed = mutableSetOf<String>()
        for (m in messages) {
            val o = runCatching { JSONObject(m) }.getOrNull() ?: continue
            o.optJSONObject("createSurface")?.optString("surfaceId")?.let { next[it] = HashMap() }
            o.optJSONObject("deleteSurface")?.optString("surfaceId")?.let {
                next.remove(it)
                changed.remove(it)
            }
            val update = o.optJSONObject("updateComponents") ?: continue
            val surfaceId = update.optString("surfaceId")
            val components = update.optJSONArray("components") ?: continue
            val graph = next.getOrPut(surfaceId) { HashMap() }
            for (i in 0 until components.length()) {
                val c = components.optJSONObject(i) ?: continue
                val id = c.optString("id")
                if (id.isNotEmpty()) graph[id] = children(c)
            }
            changed += surfaceId
        }
        for (id in changed) next[id]?.let { checkShape(it) }
        surfaces = next
    }

    companion object {
        /** The deepest an answer's components may be nested: a dense screen needs fewer than ten. */
        const val MAX_DEPTH = 32

        private val CHILD_KEYS = setOf("child", "children", "componentId", "trigger", "content", "contentChild", "entryPointChild")

        /** An action's context is the model's own data, not children. */
        private val NOT_CHILDREN = setOf("action", "context")

        /** The ids [component] names as its children. */
        internal fun children(component: JSONObject): List<String> {
            val out = mutableListOf<String>()
            // By hand, not by recursion: the walk itself mustn't go as deep as the answer does.
            val stack = ArrayDeque<Pair<String?, Any?>>()
            stack.addLast(null to component)
            while (stack.isNotEmpty()) {
                val (key, value) = stack.removeLast()
                val names = key in CHILD_KEYS
                when (value) {
                    is String -> if (names) out += value
                    is JSONObject -> for (k in value.keys()) {
                        if (k != "id" && k !in NOT_CHILDREN) stack.addLast(k to value.opt(k))
                    }
                    is JSONArray -> for (i in 0 until value.length()) stack.addLast(key to value.opt(i))
                }
            }
            return out
        }

        /** Throws when [graph] has a circle, or a path longer than [MAX_DEPTH]. */
        private fun checkShape(graph: Map<String, List<String>>) {
            val done = HashMap<String, Int>() // Each component's height: itself and what it holds, at most.
            val onPath = HashSet<String>()
            for (start in graph.keys) {
                if (start in done) continue
                val stack = ArrayDeque<Pair<String, Iterator<String>>>()
                stack.addLast(start to graph.getValue(start).iterator())
                onPath += start
                while (stack.isNotEmpty()) {
                    val (node, rest) = stack.last()
                    if (rest.hasNext()) {
                        val child = rest.next()
                        if (child !in graph || child in done) continue
                        require(child !in onPath) { "The component \"$child\" holds itself, through \"$node\"" }
                        onPath += child
                        stack.addLast(child to graph.getValue(child).iterator())
                    } else {
                        stack.removeLast()
                        onPath -= node
                        val height = 1 + (graph.getValue(node).mapNotNull { done[it] }.maxOrNull() ?: 0)
                        require(height <= MAX_DEPTH) { "The components are nested more than $MAX_DEPTH deep" }
                        done[node] = height
                    }
                }
            }
        }
    }
}
