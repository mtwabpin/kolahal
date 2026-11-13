package com.mtwabpin.kolahal.commands

/**
 * One built-in command.
 * - [usage]: its whole usage line. Completion and "Usage:" errors read it.
 * - [help]: what it does, in full. Suggestions and `help <command>` show it.
 * - [short]: what it does, in a few words, for the `help` list.
 * - [args]: its arguments and subcommands, split by " · ", for the muted line under [short]
 *   (`on · off · status`). Empty when it takes none.
 * - [uses]: each way to use it and what that does, for `help <command>`. When empty, that shows [usage].
 * - [aka]: its other names (`youtube` for `yt`). They run it, and every check treats them as it
 *   ([InstantRun.canonical]). Aliases and scripts can't take them.
 * - [sensitive]: it never runs without Enter, and a script can't send it ([InstantRun.isSensitive]).
 *   Commands are sensitive when they:
 *   - cost money, change state or are hard to undo (`call`, `clear`, `uninstall`, `config`);
 *   - run code or fetch without a script's internet switch (`lua`, `=`, `rss`);
 *   - leave the launcher, or open Android's settings and panels (`system`, `wifi`, `info`):
 *     typing `sy` or `wi` on the way to another word must not open them;
 *   - change how the launcher looks or what it shows first (`theme`, `pin`);
 *   - replace what can't be put back (`wallpaper`), or open another app's screen (`activity`).
 *
 * A name only in `CommandProcessor.execute` would pass these checks unseen: `DispatchNamesTest` checks
 * that every name it dispatches is a command here or one of its [aka].
 */
data class CommandSpec(
    val name: String,
    val usage: String,
    val help: String,
    val short: String,
    val args: String = "",
    val uses: List<Pair<String, String>> = emptyList(),
    val aka: List<String> = emptyList(),
    val sensitive: Boolean = false,
)
