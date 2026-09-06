"""Rewrite the message catalog into the interface's small-capital style.

The style canon writes every fixed string in small capitals, drops the emoji that used to trail a
line, and never spells a separator with an em dash. This walks a HOCON catalog and applies those
three rewrites to the visible text only: MiniMessage tag names, {placeholders}, %papi% tokens and
/command literals are left exactly as they are, because a tag has to parse, a placeholder has to
match and a player has to be able to read a number and type a command.

Usage: python3 tools/style/smallcaps.py <catalog.conf> [--check]
"""

import re
import sys

ASCII = "abcdefghijklmnopqrstuvwxyz"
SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ"

# Tags whose quoted argument is text the player reads, so the argument is converted too. Every other
# tag argument is structural (a colour, a command, a key) and is left untouched.
VISIBLE_ARG_TAGS = ("hover:show_text:",)

# Tags that small-cap their own argument when they render, so the catalog keeps the argument in
# plain ASCII and this pass must not touch it.
RENDER_TIME_TAGS = ("h:", "tag:", "etag:")

EMOJI = re.compile(
    "[\U0001f000-\U0001faff←-⇿⌀-⏿☀-➿⬀-⯿️]"
)
KEEP_GLYPHS = set("▶◀←→◆≡✎•●▌")


def _small(text):
    out = []
    for ch in text:
        index = ASCII.find(ch.lower())
        if index >= 0 and ch.isascii() and ch.isalpha() and ch.lower() != "x":
            out.append(SMALL[index])
        else:
            out.append(ch)
    return "".join(out)


def _convert_plain(text):
    """Small-cap a run of plain text, stepping over placeholders, papi tokens and commands."""
    out = []
    i = 0
    skip = re.compile(r"\{[^}]*\}|%[A-Za-z0-9_:.<>=+*/-]*%|/[A-Za-z0-9_.-]+")
    while i < len(text):
        match = skip.search(text, i)
        if match is None:
            out.append(_small(text[i:]))
            break
        out.append(_small(text[i : match.start()]))
        out.append(match.group(0))
        i = match.end()
    return "".join(out)


def convert(value):
    """The catalog value rewritten: tags preserved, visible text small-capped."""
    out = []
    i = 0
    while i < len(value):
        start = value.find("<", i)
        if start < 0:
            out.append(_convert_plain(value[i:]))
            break
        out.append(_convert_plain(value[i:start]))
        end = _tag_end(value, start)
        if end < 0:
            out.append(_convert_plain(value[start:]))
            break
        out.append(_convert_tag(value[start : end + 1]))
        i = end + 1
    return "".join(out)


def _tag_end(value, start):
    """The index of the '>' closing the tag opened at start, ignoring one inside a quoted argument."""
    quote = None
    for i in range(start, len(value)):
        ch = value[i]
        if quote is not None:
            if ch == quote:
                quote = None
        elif ch in "'\"":
            quote = ch
        elif ch == ">":
            return i
    return -1


def _convert_tag(tag):
    body = tag[1:-1]
    if not body.startswith(VISIBLE_ARG_TAGS):
        return tag
    open_quote = body.find("'")
    if open_quote < 0 or not body.endswith("'"):
        return tag
    prefix = body[: open_quote + 1]
    inner = body[open_quote + 1 : -1]
    return "<" + prefix + convert(inner) + "'>"


def strip_decorations(value):
    """Drop the emoji and the trailing status glyph the canon no longer uses.

    The glyph takes the single space in front of it with it, so a sentence that ended in one closes
    on its full stop. Runs of spaces are otherwise left alone: a lore line's indentation is written
    with them and collapsing it would flatten the layout.
    """

    def drop(match):
        glyph = match.group(0).strip()
        return match.group(0) if glyph in KEEP_GLYPHS else ""

    value = re.sub(r" ?(?:" + EMOJI.pattern + r"|[✓✗✔✖])", drop, value)
    return value


def de_dash(value):
    """Replace the em dash: a separator becomes a dim bullet, prose takes a comma."""
    value = value.replace("<muted> (</muted>", "<dim>•</dim>").replace("<dim>) </dim>", "<dim>•</dim>")
    value = value.replace(" (", ", ").replace(") ", ",")
    return value


def rewrite(line):
    match = re.match(r'^(\s*"[^"]+"\s*=\s*")(.*)("\s*)$', line)
    if match is None:
        return line
    head, value, tail = match.groups()
    return head + convert(de_dash(strip_decorations(value))) + tail


def main():
    path = sys.argv[1]
    check = "--check" in sys.argv
    with open(path, encoding="utf-8") as handle:
        lines = handle.readlines()
    rewritten = [rewrite(line) for line in lines]
    changed = sum(1 for a, b in zip(lines, rewritten) if a != b)
    if check:
        print(f"{changed} lines would change")
        return
    with open(path, "w", encoding="utf-8") as handle:
        handle.writelines(rewritten)
    print(f"{changed} lines rewritten")


if __name__ == "__main__":
    main()
