**A shader**, on the phone:

1. `shader save template-tint` makes a shader of yours from a template, called `my-tint` (or `my-tint-2` when taken).
2. `shader edit my-tint`, replace all the code, tap **Save**: it says **Saved · compiles**, or the compiler's error with its line.
3. `shader my-tint` turns it on over home; `shader play my-tint` plays a one-off; `wallpaper shader my-tint` makes it the live wallpaper; `shader off` turns shaders off.

**A shader as a file**: save it as `<name>.agsl` (its name is the file name in lowercase; letters, digits, `-` and `_`; not a `shader` subcommand such as `ls` or `edit`), then **Shaders → Add shader → Import a file**, or put it in the script folder's `shaders` folder and type `shader reload`. Then `shader <name>`. Shaders need Android 13 or newer.
