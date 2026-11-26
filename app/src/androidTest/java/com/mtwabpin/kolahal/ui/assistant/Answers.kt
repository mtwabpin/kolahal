package com.mtwabpin.kolahal.ui.assistant

import com.mtwabpin.kolahal.assistant.Asker
import com.mtwabpin.kolahal.assistant.Service
import com.mtwabpin.kolahal.assistant.Turn

/** A service that answers from a list, and keeps what it was asked: no network. */
internal class FakeAsker(vararg answers: () -> String) : Asker {
    class Asked(val service: Service, val model: String, val system: String, val turns: List<Turn>, val json: Boolean)

    private val queue = ArrayDeque(answers.toList())
    val asked = mutableListOf<Asked>()

    /** How many questions have had their answer (or error) handed back. */
    @Volatile
    var answered = 0

    fun add(answer: () -> String) = queue.addLast(answer)

    override fun ask(service: Service, model: String, system: String, turns: List<Turn>, json: Boolean): String {
        asked += Asked(service, model, system, turns, json)
        try {
            return (queue.removeFirstOrNull() ?: { "[]" })()
        } finally {
            answered++
        }
    }
}

/** Hand-written answers, as a model would send them. Invented examples. */
internal object Answers {
    private val CATALOG = androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1.CatalogId

    private fun surface(id: String, components: String) = """
[
{"version":"v0.9","createSurface":{"surfaceId":"$id","catalogId":"$CATALOG","sendDataModel":true}},
{"version":"v0.9","updateComponents":{"surfaceId":"$id","components":[$components]}}
]"""

    /** A dashboard: a title, three stat cards in a row, a card of rows. */
    val DASHBOARD = surface(
        "s1", """
{"id":"root","component":"Column","children":["title","stats","week"]},
{"id":"title","component":"Text","text":"Home energy this week","variant":"h2"},
{"id":"stats","component":"Row","children":["c1","c2","c3"]},
{"id":"c1","component":"Card","child":"c1c","weight":1},
{"id":"c1c","component":"Column","children":["c1v","c1l"]},
{"id":"c1v","component":"Text","text":"86 kWh","variant":"h3"},
{"id":"c1l","component":"Text","text":"Used","variant":"caption"},
{"id":"c2","component":"Card","child":"c2c","weight":1},
{"id":"c2c","component":"Column","children":["c2v","c2l"]},
{"id":"c2v","component":"Text","text":"31 kWh","variant":"h3"},
{"id":"c2l","component":"Text","text":"Solar","variant":"caption"},
{"id":"c3","component":"Card","child":"c3c","weight":1},
{"id":"c3c","component":"Column","children":["c3v","c3l"]},
{"id":"c3v","component":"Text","text":"−12%","variant":"h3"},
{"id":"c3l","component":"Text","text":"vs last week","variant":"caption"},
{"id":"week","component":"Card","child":"weekc"},
{"id":"weekc","component":"Column","children":["wh","r1","d1","r2","d2","r3"]},
{"id":"wh","component":"Text","text":"Biggest users","variant":"h4"},
{"id":"r1","component":"Row","children":["r1i","r1t","r1v"],"align":"center"},
{"id":"r1i","component":"Icon","name":"home"},
{"id":"r1t","component":"Text","text":"Heat pump","weight":1},
{"id":"r1v","component":"Text","text":"41 kWh","variant":"caption"},
{"id":"d1","component":"Divider"},
{"id":"r2","component":"Row","children":["r2i","r2t","r2v"],"align":"center"},
{"id":"r2i","component":"Icon","name":"refresh"},
{"id":"r2t","component":"Text","text":"Washing machine","weight":1},
{"id":"r2v","component":"Text","text":"12 kWh","variant":"caption"},
{"id":"d2","component":"Divider"},
{"id":"r3","component":"Row","children":["r3i","r3t","r3v"],"align":"center"},
{"id":"r3i","component":"Icon","name":"star"},
{"id":"r3t","component":"Text","text":"**Oven**, mostly on Sunday","weight":1},
{"id":"r3v","component":"Text","text":"9 kWh","variant":"caption"}
"""
    )

    /** A list in cards: the catalog's List of cards, a list inside it too. */
    val LIST = surface(
        "s2", """
{"id":"root","component":"Column","children":["title","list"]},
{"id":"title","component":"Text","text":"Three easy dinners","variant":"h2"},
{"id":"list","component":"List","children":["a","b","c"],"direction":"vertical"},
{"id":"a","component":"Card","child":"ac"},
{"id":"ac","component":"Column","children":["at","ad","al"]},
{"id":"at","component":"Text","text":"Lemon pasta","variant":"h4"},
{"id":"ad","component":"Text","text":"20 min · pasta, lemon, parmesan, butter"},
{"id":"al","component":"List","children":["al1","al2"],"direction":"vertical"},
{"id":"al1","component":"Text","text":"Zest the lemon first","variant":"caption"},
{"id":"al2","component":"Text","text":"Keep a cup of pasta water","variant":"caption"},
{"id":"b","component":"Card","child":"bc"},
{"id":"bc","component":"Column","children":["bt","bd"]},
{"id":"bt","component":"Text","text":"Chickpea curry","variant":"h4"},
{"id":"bd","component":"Text","text":"30 min · chickpeas, tomatoes, coconut milk, spinach"},
{"id":"c","component":"Card","child":"cc"},
{"id":"cc","component":"Column","children":["ct","cd"]},
{"id":"ct","component":"Text","text":"Egg fried rice","variant":"h4"},
{"id":"cd","component":"Text","text":"15 min · rice from yesterday, eggs, peas, soy sauce"}
"""
    )

    /** A comparison side by side: two cards in a row, the same rows in each. */
    val COMPARISON = surface(
        "s3", """
{"id":"root","component":"Column","children":["title","row","note"]},
{"id":"title","component":"Text","text":"Train or car to the coast","variant":"h2"},
{"id":"row","component":"Row","children":["t","c"]},
{"id":"t","component":"Card","child":"tc","weight":1},
{"id":"tc","component":"Column","children":["th","tt","tl","tp","tpl"]},
{"id":"th","component":"Text","text":"Train","variant":"h4"},
{"id":"tt","component":"Text","text":"2 h 10 m","variant":"h3"},
{"id":"tl","component":"Text","text":"Door to door","variant":"caption"},
{"id":"tp","component":"Text","text":"€38","variant":"h3"},
{"id":"tpl","component":"Text","text":"Return ticket","variant":"caption"},
{"id":"c","component":"Card","child":"cc","weight":1},
{"id":"cc","component":"Column","children":["ch","ct","cl","cp","cpl"]},
{"id":"ch","component":"Text","text":"Car","variant":"h4"},
{"id":"ct","component":"Text","text":"1 h 45 m","variant":"h3"},
{"id":"cl","component":"Text","text":"Without traffic","variant":"caption"},
{"id":"cp","component":"Text","text":"€52","variant":"h3"},
{"id":"cpl","component":"Text","text":"Fuel and parking","variant":"caption"},
{"id":"note","component":"Text","text":"The train wins on a weekend: no parking to find, and you can read on the way."}
"""
    )

    /** A change to the dashboard's title only. */
    val DASHBOARD_TITLE = """
[{"version":"v0.9","updateComponents":{"surfaceId":"s1","components":[{"id":"title","component":"Text","text":"Home energy, updated","variant":"h2"}]}}]
"""

    /** A component the catalog doesn't have: the renderer rejects it. */
    val UNKNOWN_COMPONENT = surface("s9", """{"id":"root","component":"Hologram","text":"?"}""")
}
