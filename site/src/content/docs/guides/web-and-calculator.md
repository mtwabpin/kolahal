---
title: Web, calculator & units
description: Web search shortcuts, the = calculator, unit conversion and live currency rates.
---

## Web

```sh
search rust borrow checker   # web search (alias: g)
yt lofi                      # YouTube (alias: youtube)
wiki turing                  # Wikipedia
maps coffee near me          # your maps app (alias: map)
play termux                  # Play Store search (alias: store)
url example.com              # open a link; https:// is assumed (alias: web)
```

Each opens in whatever app handles it, and logs a tappable line you can reopen later. `maps` and
`play` with no argument launch the app of that name instead.

### Search engine

```sh
config set search.engine brave
```

Choices: `duckduckgo` (default), `google`, `bing`, `brave`, `startpage`, `kagi`, `custom`. For
`custom`, set a URL containing `%s` where the query goes:

```sh
config set search.engine custom
config set search.url https://search.example.org/?q=%s
```

## Calculator

Arithmetic after `=` is calculated on the phone at once, and the answer appears above the prompt while you type. Tap it to put it in the prompt and continue from it. Press Enter to add it to the log.

```sh
= 2^10            # 1024
= 17 * 3 + 1      # 52
= 15% of 80       # 12
= 80 + 15%        # 92: a percent adds a share of what comes before it
= sqrt(2)         # 1.41421356237
= 10 mod 3        # 1 (10 % 3 too)
```

It supports `+ - * /` (also `×`, `x`, as in `3x4`, `÷`), `^`, `mod`, `n!`, parentheses, `pi` (`π`) and `e`, and
`sqrt`, `cbrt`, `abs`, `round`, `floor`, `ceil`, `ln`, `log` (base 10), `log2`, `exp`, `sin`,
`cos`, `tan` and their `a…` inverses (radians; `sin 30°` takes degrees), `min` and `max`. `2pi` and `3(4+5)` work as multiplication.

`%` before a number is the remainder: `10 % 3` is 1. Before a minus or plus it is a percent, as on
a phone's calculator, so `10% - 3`, `10%-3` and `10 % - 3` are all -2.9. The exception is a negative number written without a space (`-3`), with a space before the `%`: `10 % -3` is the remainder,
-2, as `10 mod -3` is.

Commas group a number's digits, in Western or Indian style: `1,500`, `1,000,000`, `1,00,000`,
`12,34,567`. Any other single comma is a decimal comma: `0,500` is 0.5 and `1,5` is 1.5. Amounts in a conversion work the same way. In a function's parentheses a comma separates values, so `max(1,500)`
is 500.

Anything else after `=` is evaluated as a **Lua expression**:

```sh
= math.random(6)
= os.date("%A")
```

## Units

`= <amount> <unit> in|to|as|-> <unit>` converts between units of the same kind:

```sh
= 5 km in mi
= 72 f to c
= 3 cups in ml
= 1.5 gib to mb
= 90 kmh in mph
```

| Kind | Units |
| --- | --- |
| length | `mm` `cm` `m` `km` `in` `ft` `yd` `mi` `nmi` |
| mass | `mg` `g` `kg` `t` `oz` `lb` `st` |
| volume | `ml` `l` `tsp` `tbsp` `cup` `floz` `pt` `gal` |
| speed | `m/s` `km/h` (`kmh`, `kph`) `mph` `kn` |
| data | `b` `kb` `mb` `gb` `tb` `kib` `mib` `gib` |
| time | `ms` `s` `min` `h` `d` `wk` `yr` |
| area | `m2` `km2` `ft2` `acre` `ha` |
| temperature | `c` `f` `k` |

Long names and plurals work too (`kilometers`, `pounds`, `celsius`, …). `in` is both a unit and a
connecting word: `= 5 in to cm`, `= 12 cm in in`. Long-press a result to copy the number.

Answers up to 15 digits are written in full, with commas and at most four decimals: `= 1 gb in b`
gives `1 GB = 1,000,000,000 B`. Smaller than 0.0001, or bigger, they're written as `1.000e+15`.

## Currency

Three-letter codes that aren't units are treated as currencies:

```sh
= 100 usd in eur
= 2500 inr to gbp
```

Rates come from [open.er-api.com](https://open.er-api.com) and are cached on the device for six
hours per base currency. This is the only network request the calculator makes. For a
richer converter, see the [`exchange` script](/scripting/library/#exchange).
