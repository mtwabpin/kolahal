# Release notes

One file per version, named after it: `0.3.1.md`. When the tag `v0.3.1` is pushed, the Release
workflow (`.github/workflows/release.yml`) publishes it as the GitHub release's notes and adds a
last line naming the commit the builds are made from. While the APKs are not attached to the
release (`PUBLISH_APKS` is off in the workflow), that line also says to install from Google Play.
The workflow stops if the file is missing.

What a file holds, as `0.3.0.md` does (that release was published by hand, so it still ends with
the commit line the workflow now adds):

- the package and the Android version it needs, and what it installs over;
- the two APKs (the full build and the Play build) and what each has;
- **What's new**, in plain words;
- **Fixed**, when there is something;
- a line saying the mapping zip is only for reading crash reports.

Google Play asks for its own short "What's new" (500 characters at most). Keep it beside the notes
as `0.3.1-play.txt`: the workflow sends it to Play with the bundle and posts it to the Discord
server. When the workflow is not set up to upload to Play, paste it in Play Console.

## The release that moves off the debug key

The first release signed with the release key (see `../signing.org`) can't install over 0.3.0 or
earlier. Its notes say so before anything else, for example:

> **Before you install:** this version is signed with Kolahal's new release key, so it can't
> install over 0.3.0 or earlier. Move once, keeping everything: Settings → Backup → Export
> everything, keep the file, uninstall Kolahal, install this version, then Settings → Backup →
> Import a backup. Later versions install over this one as usual.
