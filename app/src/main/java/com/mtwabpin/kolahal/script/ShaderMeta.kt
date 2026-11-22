/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

/**
 * What a shader file (AGSL) says about itself, read from its text without compiling it:
 * - `// @name` and `// @description`, and `// @animated` for one that moves by itself;
 * - for one that is shared, `// @author`, `// @version`, `// @license` and `// @requires` (the least
 *   Kolahal version);
 * - the uniforms it declares. The host sets the ones it knows ([KNOWN]); it skips the rest.
 *
 * Pure: the compiler runs in [Shaders.compile], on Android 13 and newer.
 */
class ShaderMeta private constructor(
    /** Its name for people, when the file gives one. */
    val title: String?,
    val description: String?,
    /** It is redrawn many times a second: `time` moves. A shader without it costs nothing while the screen is still. */
    val animated: Boolean,
    val uniforms: List<Uniform>,
    val author: String?,
    val version: String?,
    val license: String?,
    /** The oldest Kolahal it works with. */
    val requires: String?,
) {
    /** One declared uniform. [color]: declared with `layout(color)`. */
    data class Uniform(val name: String, val type: String, val color: Boolean, val array: Boolean) {
        /** How many numbers it holds, or 0 for another kind (a shader, an integer, an array). */
        val floats: Int get() = if (array) 0 else FLOATS[type] ?: 0
    }

    /** A compiler error: the line it is on, counted from 1, when it names one, and what is wrong. */
    data class CompileError(val line: Int?, val text: String) {
        override fun toString() = if (line == null) text else "line $line: $text"
    }

    /** Whether it declares the uniform [name]. */
    fun uses(name: String): Boolean = uniforms.any { it.name == name }

    /** It follows how the phone leans ([ShaderTilt]), so the motion sensor is read while it shows. */
    val tilt: Boolean = uses(ShaderTilt.NAME)

    /** The uniforms it declares that nothing sets: each stays 0. */
    fun unset(): List<String> = uniforms.map { it.name }.filter { it != INPUT && it !in KNOWN }

    companion object {
        /** The shader's input: the surface it is drawn over. */
        const val INPUT = "content"

        /** The uniform that is 1 while a finger is down on the surface, else 0. */
        const val PRESSED = "pressed"

        /** The uniform that says how full the battery is, 0 to 1. */
        const val BATTERY = "battery"

        /** The uniform that is 1 while the phone is plugged in, else 0. */
        const val CHARGING = "charging"

        /** The most a file may have, in bytes: far more than any shader needs. */
        const val MAX_BYTES = 64 * 1024

        private val FLOATS = mapOf(
            "float" to 1, "half" to 1,
            "float2" to 2, "half2" to 2, "vec2" to 2,
            "float3" to 3, "half3" to 3, "vec3" to 3,
            "float4" to 4, "half4" to 4, "vec4" to 4,
        )

        /** The theme's colors, by the names of their roles. Each is four numbers, with its alpha. */
        val COLORS = listOf("bg", "surface", "fg", "dim", "accent", "error", "warn", "good", "info", "link")

        /** The uniforms the host sets, and how many numbers each may hold. */
        val KNOWN: Map<String, List<Int>> = mapOf(
            "resolution" to listOf(2),
            "cell" to listOf(2),
            "grid" to listOf(2),
            "origin" to listOf(2),
            "time" to listOf(1),
            "touch" to listOf(2),
            "touchAge" to listOf(1),
            "dark" to listOf(1),
            ShaderDate.NAME to listOf(ShaderDate.FLOATS),
            ShaderTilt.NAME to listOf(ShaderTilt.FLOATS),
            PRESSED to listOf(1),
            BATTERY to listOf(1),
            CHARGING to listOf(1),
        ) + COLORS.associateWith { listOf(4) }

        private val COMMENTS = Regex("""//[^\n]*|/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        private val UNIFORM =
            Regex("""(layout\s*\(\s*color\s*\)\s*)?\buniform\s+([A-Za-z_]\w*)\s+([^;]+);""")
        private val DIRECTIVE = Regex("""^\s*//\s*@([A-Za-z]+)\b[ \t]*(.*)$""")
        private val ERROR = Regex("""error:\s*(\d+):\s*([^\n]*)""")

        fun parse(source: String): ShaderMeta {
            val tags = LinkedHashMap<String, String>()
            for (line in source.lineSequence()) {
                val m = DIRECTIVE.find(line) ?: continue
                tags.putIfAbsent(m.groupValues[1].lowercase(), m.groupValues[2].trim())
            }
            fun said(vararg names: String) =
                names.firstNotNullOfOrNull { tags[it] }?.takeIf { it.isNotEmpty() }
            // The scripts' words for the same (`@title`, `@desc`) are read too.
            return ShaderMeta(
                title = said("name", "title"),
                description = said("description", "desc"),
                animated = "animated" in tags,
                uniforms = uniforms(source),
                author = said("author"),
                version = said("version"),
                license = said("license"),
                requires = said("requires"),
            )
        }

        /** The uniforms [source] declares, in order. One declaration can have several names. */
        fun uniforms(source: String): List<Uniform> =
            UNIFORM.findAll(COMMENTS.replace(source, " ")).flatMap { m ->
                val layout = m.groupValues[1]
                val type = m.groupValues[2]
                m.groupValues[3].split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { name ->
                    Uniform(
                        name = name.substringBefore('[').trim(),
                        type = type,
                        color = layout.isNotEmpty(),
                        array = '[' in name,
                    )
                }
            }.toList()

        /**
         * What keeps [source] from being used, checked before it is compiled: its size, its input
         * and the types of the uniforms the host sets. Empty when it can be tried.
         */
        fun problems(source: String): List<String> = buildList {
            if (source.toByteArray().size > MAX_BYTES) add("the file is larger than ${MAX_BYTES / 1024} KB")
            val all = uniforms(source)
            if (all.none { it.name == INPUT && it.type == "shader" && !it.array }) {
                add("it needs 'uniform shader $INPUT;', the surface it draws over")
            }
            for (u in all) {
                if (u.name == INPUT) continue
                val counts = KNOWN[u.name]
                when {
                    u.type == "shader" -> add("'${u.name}': the only shader input is $INPUT")
                    counts == null -> Unit
                    u.floats !in counts ->
                        add("'${u.name}' must be a ${typeNames(counts)}, not ${u.type}${if (u.array) "[]" else ""}")
                }
            }
        }

        private fun typeNames(counts: List<Int>): String =
            counts.joinToString(" or ") { if (it == 1) "float" else "float$it" }

        /** [message] as the compiler gives it: "error: 12: unknown identifier 'x'", with more lines after. */
        fun compileError(message: String): CompileError {
            val m = ERROR.find(message)
                ?: return CompileError(
                    null,
                    message.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "it doesn't compile",
                )
            return CompileError(m.groupValues[1].toIntOrNull(), m.groupValues[2].trim())
        }
    }
}
