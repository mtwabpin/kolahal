/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

/**
 * What a shader file (AGSL) says about itself, read from its text without compiling it:
 * - `// @name` and `// @description`, and `// @animated` for one that moves by itself;
 * - for one that is shared, `// @author`, `// @version`, `// @license` and `// @requires` (the least
 *   Kolahal version);
 * - `// @param`, for each thing that people can change ([ShaderParam]);
 * - `// @once 6`: a one-off shader, which runs for so many seconds and goes ([ShaderPlay]);
 * - `// @suits`: what it works well over ([ShaderSuits]);
 * - `// @text`: it works on text, and shows nothing without any;
 * - `// @fps 10`: how many frames a second it needs while nothing touches it, and `// @settles 4`: how many
 *   seconds a touch changes it ([ShaderClock.Pace]). A line that can't be read is left out ([warnings]);
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
    /** What people can change, in the header's order. */
    val params: List<ShaderParam>,
    /** How long a one-off shader runs, in seconds; null for one that stays on. */
    val once: Float?,
    val suits: Map<ShaderSurface, ShaderFit>,
    /** It works on text: as a wallpaper it would be nothing. */
    val text: Boolean,
    /** Words that find it. */
    val tags: List<String>,
    /** How many frames a second it needs while nothing touches it; null without a line that can be read: [ShaderClock.FPS]. */
    val fps: Int?,
    /** How many seconds after a touch the touch still changes it; null without a line that can be read: [ShaderClock.IDLE_MS]. */
    val settles: Float?,
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

    /**
     * On home, the app selector is drawn under it with the rest of home: true for one that works on the
     * text itself ([text]), as a CRT's curve and lines cover the whole screen. Any other is a picture
     * behind the text. It takes a pixel close to the page's color for the page and one a little off it
     * for faint text, so it would wash out an app icon. The selector is drawn over its picture instead.
     */
    val coversSelector: Boolean get() = text

    /** The uniforms it declares that nothing sets: each stays 0. */
    fun unset(): List<String> =
        uniforms.map { it.name }.filter { name -> name != INPUT && name !in KNOWN && params.none { it.name == name } }

    companion object {
        /** The shader's input: the surface it is drawn over. */
        const val INPUT = "content"

        /**
         * The alpha of the page a shader reads as [INPUT]: a step under opaque. Text and pictures drawn on
         * it are opaque, so a pixel at full alpha is covered and the bare page is the only thing below it:
         * a scene stays behind an icon, a photo or an emoji even where its color is the page's. Every
         * surface that hands its page to a shader draws it so, and only while a shader reads it.
         */
        const val PAGE_ALPHA = 254f / 255f

        /** The uniform that is 1 while a finger is down on the surface, else 0. */
        const val PRESSED = "pressed"

        /** The uniform that says how full the battery is, 0 to 1. */
        const val BATTERY = "battery"

        /** The uniform that is 1 while the phone is plugged in, else 0. */
        const val CHARGING = "charging"

        /** The uniform that is 1 in the live wallpaper, where no text is, else 0. */
        const val WALLPAPER = "wallpaper"

        /** The uniform that goes from 0 to 1 as a one-off shader runs. */
        const val PROGRESS = "progress"

        /**
         * The uniform that holds the radius of the screen's rounded corners, in pixels of the surface: 0 where
         * they are square, below Android 12, or on a surface that isn't the whole screen (a preview).
         */
        const val CORNER = "corner"

        /**
         * The uniform that holds the rectangle of the camera's cutout at the top of the screen, in pixels of the
         * surface: left, top, right, bottom. All 0 where there is none (as in landscape, where the camera is at a
         * side), or on a surface that isn't the whole screen (a preview).
         */
        const val CUTOUT = "cutout"

        /** The header's words for the frames a second ([fps]) and the seconds a touch lasts ([settles]). */
        const val FPS = "fps"
        const val SETTLES = "settles"

        /** The fewest seconds `@settles` can say; the most is the time without a touch after which a shader stops ([ShaderClock.IDLE_MS]). */
        const val MIN_SETTLES = 0.5f
        const val MAX_SETTLES = ShaderClock.IDLE_MS / 1000f

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
            PROGRESS to listOf(1),
            WALLPAPER to listOf(1),
            CORNER to listOf(1),
            CUTOUT to listOf(4),
        ) + COLORS.associateWith { listOf(4) }

        private val COMMENTS = Regex("""//[^\n]*|/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        private val UNIFORM =
            Regex("""(layout\s*\(\s*color\s*\)\s*)?\buniform\s+([A-Za-z_]\w*)\s+([^;]+);""")
        private val DIRECTIVE = Regex("""^\s*//\s*@([A-Za-z]+)\b[ \t]*(.*)$""")
        private val ERROR = Regex("""error:\s*(\d+):\s*([^\n]*)""")

        fun parse(source: String): ShaderMeta {
            val tags = directives(source)
            fun said(vararg names: String) =
                names.firstNotNullOfOrNull { tags[it] }?.takeIf { it.isNotEmpty() }
            val once = said("once")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(ShaderPlay.MIN_SECONDS, ShaderPlay.MAX_SECONDS)
            // The scripts' words for the same (`@title`, `@desc`) are read too.
            return ShaderMeta(
                title = said("name", "title"),
                description = said("description", "desc"),
                // A one-off shader moves, or it would be nothing.
                animated = "animated" in tags || once != null,
                uniforms = uniforms(source),
                author = said("author"),
                version = said("version"),
                license = said("license"),
                requires = said("requires"),
                params = paramLines(source).mapNotNull(ShaderParam::read).distinctBy { it.name }.take(ShaderParam.MAX),
                once = once,
                suits = said(ShaderSuits.DIRECTIVE)?.let(ShaderSuits::read).orEmpty(),
                text = "text" in tags,
                tags = said("tags")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                fps = tags[FPS]?.let(::fps),
                settles = tags[SETTLES]?.let(::settles),
            )
        }

        /** Each directive in [source] and what follows it, by its name in lower case: the first of two wins. */
        private fun directives(source: String): Map<String, String> {
            val tags = LinkedHashMap<String, String>()
            for (line in source.lineSequence()) {
                val m = DIRECTIVE.find(line) ?: continue
                tags.putIfAbsent(m.groupValues[1].lowercase(), m.groupValues[2].trim())
            }
            return tags
        }

        private fun fps(said: String): Int? = said.toIntOrNull()?.takeIf { it in 1..ShaderClock.FPS }

        private fun settles(said: String): Float? = said.toFloatOrNull()?.takeIf { it in MIN_SETTLES..MAX_SETTLES }

        /**
         * The header's lines that can't be read, each with what to write instead. They are left out, and the
         * shader works without them: unlike [problems], they keep nothing from being used.
         */
        fun warnings(source: String): List<String> {
            val tags = directives(source)
            fun quoted(name: String, said: String) = if (said.isEmpty()) "'@$name'" else "'@$name $said'"
            return listOfNotNull(
                tags[FPS]?.takeIf { fps(it) == null }
                    ?.let { "${quoted(FPS, it)}: write a whole number from 1 to ${ShaderClock.FPS}" },
                tags[SETTLES]?.takeIf { settles(it) == null }
                    ?.let { "${quoted(SETTLES, it)}: write the seconds, from ${number(MIN_SETTLES)} to ${number(MAX_SETTLES)}" },
            )
        }

        /** [x] as people write it: 60, not 60.0. */
        private fun number(x: Float): String = if (x == x.toInt().toFloat()) x.toInt().toString() else x.toString()

        /** What follows each `@param` in the header. */
        private fun paramLines(source: String): List<String> = source.lineSequence()
            .mapNotNull { DIRECTIVE.find(it) }
            .filter { it.groupValues[1].lowercase() == ShaderParam.DIRECTIVE }
            .map { it.groupValues[2].trim() }
            .toList()

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
            val lines = paramLines(source)
            if (lines.size > ShaderParam.MAX) add("it has ${lines.size} parameters, and can have ${ShaderParam.MAX}")
            val seen = HashSet<String>()
            for (line in lines) {
                val param = ShaderParam.read(line)
                if (param == null) {
                    add("'@param $line' can't be read: write '@param amount 0.0..2.0 = 1.0 Title' or '@param shape a|b = a Title'")
                    continue
                }
                val name = param.name
                when {
                    name in KNOWN || name == INPUT -> add("'$name' can't be a parameter: Kolahal sets it")
                    !seen.add(name) -> add("the parameter '$name' is there twice")
                    all.none { it.name == name && it.floats == 1 } -> add("the parameter '$name' needs 'uniform float $name;'")
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
