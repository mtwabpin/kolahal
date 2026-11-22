package com.mtwabpin.kolahal

import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptSources
import com.mtwabpin.kolahal.script.ShaderStore
import com.mtwabpin.kolahal.script.Shaders
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.rules.ExternalResource
import java.io.File
import java.security.MessageDigest

/** A shader in a shared list, with the code the list's checksum is for. */
data class ListedShader(
    val name: String,
    val title: String,
    val desc: String,
    val author: String,
    val version: String,
    val code: String,
    val tags: List<String> = emptyList(),
    val animated: Boolean = false,
    val requires: String? = null,
    val tilt: Boolean = false,
) {
    val sha: String = MessageDigest.getInstance("SHA-256").digest(code.toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun json() = JSONObject().put("name", name).put("path", "shaders/$name/$name.agsl")
        .put("sha256", sha).put("title", title).put("desc", desc).put("author", author)
        .put("version", version).put("license", "MIT").put("tags", JSONArray(tags))
        .put("animated", animated).apply { requires?.let { put("requires", it) } }
        // Optional: a list written before it doesn't have it.
        .apply { if (tilt) put("tilt", true) }
}

/**
 * The shared lists with shaders in them, kept as if downloaded a moment ago, so nothing goes online.
 * After the test, kolahal-scripts' list is as it was, the added source is gone, and so are the shaders
 * and records of the names the test used.
 */
class SharedShaderLists : ExternalResource() {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cache = File(context.filesDir, "script-catalog.json")
    private val prefs = Stores.SCRIPT_CATALOG.open(context)
    private var hadCache: ByteArray? = null
    private var hadFetched: Long? = null
    private val names = HashSet<String>()

    val catalog = ScriptCatalog(context)
    val store = ShaderStore(context, catalog)

    /** A source of one's own, as `owner/name`. */
    val other = (ScriptSources.parse("slowner/shaders") as ScriptSources.Parsed.Ok).source

    override fun before() {
        hadCache = cache.takeIf { it.exists() }?.readBytes()
        hadFetched = prefs.getLong("fetched", -1).takeIf { it >= 0 }
    }

    /** Puts [shaders] in kolahal-scripts' list, or in [source]'s, which is then added. */
    fun listed(vararg shaders: ListedShader, source: ScriptSources.Source = ScriptSources.DEFAULT) {
        names += shaders.map { it.name }
        catalog.keep(
            source,
            JSONObject().put("format", 1).put("scripts", JSONArray())
                .put("shaders", JSONArray(shaders.map { it.json() })).toString()
        )
        if (!source.builtIn) catalog.add(source)
        assertEquals(
            shaders.map { it.name },
            store.cached().filter { it.source == source.name }.map { it.name })
        // The scripts of a list with shaders are read as ever.
        assertEquals(emptyList<ScriptCatalog.Entry>(), catalog.cached(source))
    }

    /** Installs [shader] from kolahal-scripts' list, which has it now. */
    fun installed(shader: ListedShader) {
        listed(shader)
        store.install(store.cached().first { it.name == shader.name }, shader.code)
    }

    override fun after() {
        catalog.add(other)
        catalog.remove(other.name)
        names.forEach {
            Shaders.remove(context, it)
            store.forget(it)
        }
        hadCache?.let { cache.writeBytes(it) } ?: cache.delete()
        prefs.edit { hadFetched?.let { putLong("fetched", it) } ?: remove("fetched") }
    }
}
