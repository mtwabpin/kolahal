package com.mtwabpin.kolahal.documents

/**
 * Files in memory, standing in for the phone's. Each has its bytes and what its app would say
 * about it. A test can change a file behind the editor's back ([change]), take it out of reach
 * ([unreachable]) or make writes fail ([writeFails]).
 */
class FakeFiles : DocumentSource {
    /** [modified] null: the file's app gives no time. [sized] false: it gives no size either. */
    class File(var bytes: ByteArray, var name: String, var modified: Long?, var writable: Boolean = true, var sized: Boolean = true)

    val files = HashMap<String, File>()
    private var clock = 1_000L

    /** Why no file can be read or written now, or null. */
    var unreachable: String? = null

    /** Why writes fail now, or null. */
    var writeFails: String? = null

    /** How many times a file was read: a check that trusts the file's size and time doesn't read. */
    var reads = 0

    fun put(uri: String, text: String, timed: Boolean = true, writable: Boolean = true) =
        File(text.toByteArray(), TextDocument.nameIn(uri), if (timed) clock++ else null, writable).also { files[uri] = it }

    /** Another app writes [text] to the file. */
    fun change(uri: String, text: String) {
        val f = files.getValue(uri)
        f.bytes = text.toByteArray()
        if (f.modified != null) f.modified = clock++
    }

    fun text(uri: String) = String(files.getValue(uri).bytes)

    override fun info(uri: String): DocumentInfo {
        val f = files[uri]?.takeIf { unreachable == null } ?: return DocumentInfo(TextDocument.nameIn(uri))
        return DocumentInfo(f.name, f.bytes.size.toLong().takeIf { f.sized }, f.modified, f.writable)
    }

    override fun read(uri: String, max: Int): ByteArray {
        reads++
        unreachable?.let { throw DocumentUnavailable(it) }
        return files[uri]?.bytes ?: throw DocumentUnavailable("This file can't be opened to read: it was moved or deleted")
    }

    override fun write(uri: String, bytes: ByteArray) {
        unreachable?.let { throw DocumentUnavailable(it) }
        writeFails?.let { throw java.io.IOException(it) }
        val f = files.getOrPut(uri) { File(ByteArray(0), TextDocument.nameIn(uri), clock++) }
        f.bytes = bytes
        if (f.modified != null) f.modified = clock++
    }
}
