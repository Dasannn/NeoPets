#!/usr/bin/env python3
"""Render a NeoPets chest-menu layout (JSON) to a PNG that mimics the Minecraft client.

Used for design review of GUIs without a game client. Item textures are read from an
extracted Minecraft client jar (never committed):

    render.py layout.json out.png --textures <dir containing assets/minecraft/textures>

Layout format:
{
  "title": "<gold>Mis mascotas",
  "rows": 6,
  "slots": {"10": {"material": "WOLF_SPAWN_EGG", "name": "<green>Perro", "lore": ["<gray>..."],
                   "glint": true, "amount": 1}},
  "tooltips": [10, 49]          # slots whose tooltip is drawn beside the chest
}
Text supports MiniMessage-style colour tags (<gold>, <gray>, <#a0e0ff>, <bold>, ...).
"""
import argparse
import json
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SCALE = 4
SLOT = 18
COLORS = {
    "black": "#000000", "dark_blue": "#0000AA", "dark_green": "#00AA00", "dark_aqua": "#00AAAA",
    "dark_red": "#AA0000", "dark_purple": "#AA00AA", "gold": "#FFAA00", "gray": "#AAAAAA",
    "dark_gray": "#555555", "blue": "#5555FF", "green": "#55FF55", "aqua": "#55FFFF",
    "red": "#FF5555", "light_purple": "#FF55FF", "yellow": "#FFFF55", "white": "#FFFFFF",
}
TAG = re.compile(r"<(/?)([a-z_]+|#[0-9a-fA-F]{6})>")


def parse_rich(text, default="#FFFFFF"):
    """Split MiniMessage-like text into (segment, colour, bold) runs."""
    runs, color, bold, pos = [], default, False, 0
    for match in TAG.finditer(text):
        if match.start() > pos:
            runs.append((text[pos:match.start()], color, bold))
        closing, tag = match.group(1), match.group(2)
        if tag == "bold":
            bold = not closing
        elif tag.startswith("#"):
            color = default if closing else tag
        elif tag in COLORS:
            color = default if closing else COLORS[tag]
        elif tag == "reset":
            color, bold = default, False
        pos = match.end()
    if pos < len(text):
        runs.append((text[pos:], color, bold))
    return runs


def font(size):
    for candidate in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                      "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"):
        if Path(candidate).exists():
            return ImageFont.truetype(candidate, size)
    return ImageFont.load_default()


def font_bold(size):
    path = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    return ImageFont.truetype(path, size) if Path(path).exists() else font(size)


def item_texture(textures, material):
    name = material.lower()
    candidates = [f"item/{name}.png", f"block/{name}.png"]
    if name.endswith("_stained_glass_pane") or name == "glass_pane":
        candidates.insert(0, f"block/{name.removesuffix('_pane')}.png")
    for rel in candidates:
        path = textures / rel
        if path.exists():
            img = Image.open(path).convert("RGBA")
            return img.crop((0, 0, img.width, img.width))  # first animation frame
    return None


def draw_text_runs(draw, xy, text, size, default="#FFFFFF", shadow=True):
    x, y = xy
    for segment, color, bold in parse_rich(text, default):
        fnt = font_bold(size) if bold else font(size)
        if shadow:
            draw.text((x + 2, y + 2), segment, font=fnt, fill="#3F3F3F")
        draw.text((x, y), segment, font=fnt, fill=color)
        x += draw.textlength(segment, font=fnt)
    return x


def text_width(draw, text, size):
    return sum(draw.textlength(seg, font=font_bold(size) if bold else font(size))
               for seg, _, bold in parse_rich(text))


def tooltip_size(draw, item, size=22):
    lines = [item.get("name", item["material"])] + item.get("lore", [])
    pad, line_h = 10, size + 8
    width = max(text_width(draw, line, size) for line in lines) + pad * 2
    height = line_h * len(lines) + pad * 2 + (6 if len(lines) > 1 else 0)
    return int(width), int(height)


def render_tooltip(draw, origin, item, size=22):
    lines = [item.get("name", item["material"])] + item.get("lore", [])
    pad, line_h = 10, size + 8
    width, height = tooltip_size(draw, item, size)
    x, y = origin
    draw.rectangle((x, y, x + width, y + height), fill="#100010F0")
    draw.rectangle((x + 2, y + 2, x + width - 2, y + height - 2), outline="#5000FF", width=2)
    cy = y + pad
    for index, line in enumerate(lines):
        draw_text_runs(draw, (x + pad, cy), line, size, "#FFFFFF" if index == 0 else "#AAAAAA")
        cy += line_h + (6 if index == 0 else 0)
    return width, height


def render(layout, textures, out):
    rows = layout["rows"]
    base = Image.open(textures / "gui/container/generic_54.png").convert("RGBA")
    top_h = 17 + rows * SLOT
    chest = Image.new("RGBA", (176, top_h + 7))
    chest.paste(base.crop((0, 0, 176, top_h)), (0, 0))
    chest.paste(base.crop((0, 215, 176, 222)), (0, top_h))
    chest = chest.resize((176 * SCALE, chest.height * SCALE), Image.NEAREST)

    tooltips = layout.get("tooltips", [])
    measure = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    tooltip_sizes = [tooltip_size(measure, layout["slots"][str(slot)])
                     for slot in tooltips if str(slot) in layout["slots"]]
    canvas_w = chest.width + 40 + (max((w for w, _ in tooltip_sizes), default=0) + 40 if tooltip_sizes else 0)
    canvas_h = max(chest.height + 40, 20 + sum(h + 72 for _, h in tooltip_sizes))
    canvas = Image.new("RGBA", (canvas_w, canvas_h), "#2B2D31")
    canvas.paste(chest, (20, 20), chest)
    draw = ImageDraw.Draw(canvas)
    draw_text_runs(draw, (20 + 8 * SCALE, 20 + 5 * SCALE), layout.get("title", ""), 26,
                   "#404040", shadow=False)

    for key, item in layout["slots"].items():
        slot = int(key)
        col, row = slot % 9, slot // 9
        x = 20 + (8 + col * SLOT) * SCALE
        y = 20 + (18 + row * SLOT) * SCALE
        tex = item_texture(textures, item["material"])
        if tex is None:
            draw.rectangle((x, y, x + 16 * SCALE, y + 16 * SCALE), fill="#FF00FF")
            draw.text((x + 4, y + 4), item["material"][:10], font=font(12), fill="black")
            continue
        icon = tex.resize((16 * SCALE, 16 * SCALE), Image.NEAREST)
        if item.get("glint"):
            glint = Image.new("RGBA", icon.size, (170, 80, 255, 90))
            icon = Image.composite(Image.alpha_composite(icon, glint), icon, icon.split()[3])
        canvas.paste(icon, (x, y), icon)
        amount = item.get("amount", 1)
        if amount > 1:
            draw_text_runs(draw, (x + 40, y + 36), str(amount), 26)
        if slot in tooltips:
            draw.rectangle((x - 2, y - 2, x + 16 * SCALE + 2, y + 16 * SCALE + 2),
                           outline="#FFFFFF", width=3)

    ty = 20
    for slot in tooltips:
        item = layout["slots"].get(str(slot))
        if item:
            draw_text_runs(draw, (chest.width + 40, ty), f"<dark_gray>slot {slot}", 16, shadow=False)
            _, h = render_tooltip(draw, (chest.width + 40, ty + 22), item)
            ty += h + 50
    canvas.convert("RGB").save(out)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("layout")
    parser.add_argument("out")
    parser.add_argument("--textures", required=True,
                        help="directory containing assets/minecraft/textures (or that folder itself)")
    args = parser.parse_args()
    textures = Path(args.textures)
    if (textures / "assets/minecraft/textures").exists():
        textures = textures / "assets/minecraft/textures"
    render(json.loads(Path(args.layout).read_text(encoding="utf-8")), textures, args.out)


if __name__ == "__main__":
    main()
