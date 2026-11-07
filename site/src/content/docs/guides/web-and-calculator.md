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

Each command opens in the app that handles it. It also adds a line to the log, which you can tap later to open it again.
If you use `maps` or `play` with no argument, it opens the app with that name instead.

### Search engine

```sh
config set search.engine brave
```

The choices are `duckduckgo` (default), `google`, `bing`, `brave`, `startpage`, `kagi` and `custom`.
For `custom`, set a URL with `%s` where the query goes:

```sh
config set search.engine custom
config set search.url https://search.example.org/?q=%s
```

## Calculator

Type a calculation after `=`. Kolahal calculates it on the phone right away, and the answer appears above the prompt while you type.

- Tap the answer to put it in the prompt and keep calculating from it.
- Press Enter to add it to the log.

```sh
= 2^10            # 1024
= 17 * 3 + 1      # 52
= 15% of 80       # 12
= 80 + 15%        # 92: a percent adds a share of what comes before it
= sqrt(2)         # 1.41421356237
= 10 mod 3        # 1 (10 % 3 too)
```

The calculator supports:

- operators: `+ - * /` (also `×`, `x` as in `3x4`, and `÷`), `^`, `mod`, `n!` and parentheses
- constants: `pi` (`π`) and `e`
- functions: `sqrt`, `cbrt`, `abs`, `round`, `floor`, `ceil`, `ln`, `log` (base 10), `log2`, `exp`, `min` and `max`
- `sin`, `cos`, `tan` and their inverses (`a…`). These use radians. `sin 30°` uses degrees.
- multiplication without a sign: `2pi` and `3(4+5)`

**The `%` sign:**

- Before a number, `%` is the remainder: `10 % 3` is 1.
- Before a minus or plus, `%` is a percent, as on a phone's calculator. So `10% - 3`, `10%-3` and `10 % - 3` are all -2.9.
- There is one exception: a space before the `%`, and a negative number with no space (`-3`). Then it's the remainder.
  `10 % -3` is -2, the same as `10 mod -3`.

**Commas:**

- Commas can group a number's digits, in Western or Indian style: `1,500`, `1,000,000`, `1,00,000`, `12,34,567`.
- Any other single comma is a decimal comma: `0,500` is 0.5 and `1,5` is 1.5.
- Amounts in a unit conversion work the same way.
- Inside a function's parentheses, a comma separates values. So `max(1,500)` is 500.

If the text after `=` is anything else, Kolahal evaluates it as a **Lua expression**:

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

- Long names and plurals work too (`kilometers`, `pounds`, `celsius`, …).
- `in` is both a unit (inches) and a connecting word: `= 5 in to cm`, `= 12 cm in in`.
- Long-press a result to copy the number.

Answers up to 15 digits are written in full, with commas and at most four decimals.
For example, `= 1 gb in b` gives `1 GB = 1,000,000,000 B`.
Answers smaller than 0.0001, or bigger than 15 digits, are written like `1.000e+15`.

## Currency

Three-letter codes that aren't units are read as currencies:

```sh
= 100 usd in eur
= 2500 inr to gbp
```

Rates come from [open.er-api.com](https://open.er-api.com). Kolahal caches them on the device for six
hours per base currency. This is the only network request the calculator makes. For a
more complete converter, see the [`exchange` script](/scripting/library/#exchange).
