package com.mtwabpin.kolahal.data

import java.security.MessageDigest
import java.util.Locale

/**
 * The SHA-256 of [bytes] as 64 lowercase hex digits, the form checksums are written in: a shared
 * script's in the script list, a library file's in its history, an APK's signer.
 */
fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(Locale.ROOT, it) }
