package com.mtwabpin.kolahal

import java.io.File

/**
 * [path] in the app's sources (`src/main/…`), from where the tests run: `app/` under Gradle,
 * the repository's root in some IDEs.
 */
fun sourceFile(path: String): File =
    listOf(File(path), File("app", path)).firstOrNull { it.exists() } ?: error("$path isn't here or under app/")
