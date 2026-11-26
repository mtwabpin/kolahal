---
title: Assistant
description: A page beside home where you chat with Claude, Gemini or ChatGPT, with your own API key. Answers come as cards and lists, or as plain text.
---

The Assistant is a page beside the terminal where you ask Claude, Gemini or ChatGPT a question and read the answer.
It uses your own API key from the service you choose, and it's in the full build only.

## Turning it on

1. Open **Settings → Assistant** and turn on **Show the Assistant page**, or run `config set assistant.page on`.
2. Swipe sideways on the home screen past the widgets pages. The Assistant is the outermost page.
   To put it elsewhere, use [Page order](/guides/widgets/#page-order).
3. Enter an API key for the service you want. The page asks for one when the service has none.

You get an API key from the service's own developer console:

- **Claude**: an Anthropic API key, from the Claude Console
- **Gemini**: a Google API key, from Google AI Studio
- **ChatGPT**: an OpenAI API key, from the OpenAI Platform

Each service bills its own account for what you ask. Kolahal doesn't charge or count anything.

## Asking

Type in the **Ask** field and tap **Ask**. Your question and the answer appear one under the other, as a thread.
Ask a follow-up and the whole conversation goes with it, so you can say "make it shorter" or "and for Saturday?".

At the top of the page:

- The row of services switches between Claude, Gemini and ChatGPT in one tap. A conversation can go on with
  another service.
- The row under it chooses the service's model. The first one is the default: fast and inexpensive.
- **New conversation** (+) starts an empty thread.
- **Earlier conversations** (the clock) lists the conversations kept on the phone.
- **Assistant settings** (the gear) opens Settings → Assistant.

## Rich answers and plain text

With **Rich answers** on (the default), the model answers with A2UI, a format that describes a screen, and the page
draws it: titles, cards, rows of figures, lists, icons. For example, ask "compare the train and the car for a day at
the coast" and the answer can come back as two cards side by side.

If you tap a button in an answer, the tap goes back to the model, and its reply changes that answer where it is.
A follow-up that changes an earlier answer changes it there too.

With **Rich answers** off, the model writes an ordinary answer. It shows as text you can select and copy, with
**bold**, *italics*, lists and headings.

If an answer can't be drawn, the page says so and shows the answer as it came. With the next question, the model
is told what went wrong.

What an answer can do is limited:

- It can't run a command, open a link or an app, or reach anything on the phone.
- Pictures, video and sound in an answer are never loaded: they show as a line of text.
- A link shows as text.

## History

Conversations are kept on the phone, the newest 50. Tap **Earlier conversations** to open one: it continues
with the service and model it used. Tap the bin beside one to delete it.

To keep nothing, turn off **Keep history** in Settings → Assistant. New conversations aren't kept then, and the
list is hidden. **Delete all conversations** removes the ones already kept.

Conversations stay on this phone. They aren't in Kolahal's backup file or in Android's cloud backup.

## Settings

**Settings → Assistant** has:

- **Show the Assistant page**
- **Service**, and **Model**: the service's models, or another of its models typed by name. A model that isn't in
  the list yet works this way without an update.
- **API keys**: one row for each service. It says whether a key is saved, and where it is sent. Tap a row to
  add or replace the key, or the bin to remove it. A key you saved is never shown again.
- **Rich answers**
- **Custom instructions**: a text added to every conversation, for example "answer in Spanish" or "use metric units".
- **Keep history**, and **Delete all conversations**

`config` changes the same settings: see [Configuration keys](/reference/config/#assistant).

## What is sent

Your questions, the conversation so far and your custom instructions go to the service you chose, with your own
API key. Nothing else from the phone is sent: not the log, notifications, apps, files or anything a script wrote.

Each key is kept sealed on the phone, the same way as scripts' secrets, and is sent only to its own service.
It is never in a backup. See [Privacy & permissions](/reference/privacy/#the-assistant).
