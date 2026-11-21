/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.documents

import java.io.IOException

/**
 * Where the editor's files are. A file is named by its address: a `content:` URI, as text.
 * - On the phone this is Android's content resolver ([ContentDocuments]).
 * - Tests use plain files.
 *
 * [read] and [write] throw [DocumentUnavailable] with a reason for the user when the file can't be
 * reached: its permission was taken back, it was deleted, or the app that holds it is gone.
 */
interface DocumentSource {
    /** What is known about the file without reading it. It never throws: what can't be known is left out. */
    fun info(uri: String): DocumentInfo

    /** The file's bytes. Reading may stop once it has more than [max] of them: more than [max] means the file is too big. */
    fun read(uri: String, max: Int): ByteArray

    /** Replaces the file's content with [bytes]. */
    fun write(uri: String, bytes: ByteArray)
}

/**
 * - [name]: the file's name as its app gives it, or the last part of its address.
 * - [size], [modified]: its length in bytes and when it last changed (ms), or null when its app doesn't say.
 * - [writable]: whether Kolahal may write it, as far as Android says. Only a write shows it for sure.
 */
data class DocumentInfo(
    val name: String,
    val size: Long? = null,
    val modified: Long? = null,
    val writable: Boolean = true
)

/** The file can't be read or written. The message is a sentence for the user, without a full stop. */
class DocumentUnavailable(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Tries each way to open a file in [routes], in order, and returns what the first one opens. A
 * later way is tried only when the ones before it threw, so nothing was written yet. When none
 * opens, it throws what the first one threw: that is the file's own answer.
 */
internal fun <T> firstOpen(routes: Sequence<() -> T>): T {
    var first: Exception? = null
    for (open in routes) {
        try {
            return open()
        } catch (e: Exception) {
            if (first == null) first = e
        }
    }
    throw first ?: IllegalArgumentException("no way to open the file")
}
