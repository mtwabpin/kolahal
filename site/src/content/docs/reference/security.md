---
title: Security
description: What keeps your phone and data safe, how to check a download, and how to report a problem privately.
---

Kolahal is young, and its code is still being reviewed. Treat it as beta software:

- Keep a [backup](/guides/backup/).
- Give scripts only what they need.
- Report anything that looks wrong.

## What keeps you safe

- **Your data stays on the phone.** There is no account and no server. Kolahal connects to the network only when you ask it to.
  [Privacy & permissions](/reference/privacy/) lists every request and every permission.
- **You control permissions.** Everything beyond the basics is optional. Kolahal asks for it the first time you use it.
  Before you turn on notification access, Kolahal explains what it reads. The Play build has no screen lock and no call log at all.
- **Scripts run separately.** They run in their own process, for at most one minute, with limited memory. Even a script stuck in
  a loop that the engine can't interrupt is stopped.
- **A script gets only what you allow.**
  - It can use your notifications, clipboard, apps, terminal commands, Do Not Disturb and work profile only if it has an
    `@uses` line and you approve it.
  - It can read your todos, notes, reminders, calendar and feeds only in the same way: an `@uses` line for each list,
    and your approval for each. The only thing it can change there is whether a todo is done.
  - It gets internet access only if its code uses the internet.
  - It gets a place at the top of home only when you pin it.
  - Each of these has a switch for each script ([switching access off](/scripting/overview/#switching-access-off)).
- **A script with internet access can reach your local network.** As with any Android app, internet access includes this phone
  and your local network. So don't give it to a script you don't trust. On Android 17 and newer, Android itself blocks access
  to your local network.
- **Imported scripts start switched off.** A script from a file, a folder or a backup asks for permission before it runs.
  If an update asks for more (including internet access), the new access starts off.
- **A script never holds your keys.** `secrets.get` gives the script a placeholder. The real value goes only to the
  sites that its `@secret` line names, over https. Values are encrypted with a key kept in Android's
  Keystore, and they are not included in backups ([API keys and other secrets](/scripting/overview/#api-keys-and-other-secrets)).
- **A script can't act for you.** When you tap its lines, they can't run a command or call a number. A script
  can't run commands that change things (`clear`, `uninstall`, `config` and similar).

## Checking a download

GitHub Actions builds each release from the commit that its tag points to. The release notes name that
commit. Both APKs on GitHub are signed with Kolahal's release key, and so is the version on Google Play.
The key's certificate has this SHA-256 digest:

```
21223ad4312a871e4a1dd16440efccac84f4660c56fa1e80467631062938e210
```

To check an APK before you install it, run `apksigner verify --print-certs kolahal-X.Y.Z.apk` (from
the Android SDK's build tools). Compare the digest it prints for the signer's certificate with the one above.

## Reporting a vulnerability

Please don't open a public issue for a security problem. Report it privately on GitHub:
[**Report a vulnerability**](https://github.com/mtwabpin/kolahal/security/advisories/new) (the
repository's **Security** tab). Include:

- the version and build (`fetch` shows them), the phone and its Android version
- what someone could do with the problem
- how to make it happen: the steps, or a small script that shows it

We reply there, fix the problem in a release and mention the fix in the release notes. Tell us if you'd like credit.
Only the latest release gets fixes.

You can use an AI tool to help write a report, if you have checked the problem yourself on a real build.
If a report is only a tool's output and nobody reproduced it, we close it without a reply.

### What's in scope

- A way around a script's limits or switches. For example, a script reaches data, the network or commands it wasn't
  allowed, or a secret goes somewhere its `@secret` line doesn't name.
- Anything that sends your data off the phone, or to another app, without you asking.
- Problems with the release builds or their signing.

Out of scope:

- a rooted phone
- someone holding your unlocked phone
- bugs in Android itself
- scripts doing what you allowed them to do
