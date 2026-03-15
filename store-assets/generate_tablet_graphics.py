from PIL import Image, ImageDraw, ImageFont
import os

OUT = os.path.join(os.path.dirname(__file__), 'icons')
os.makedirs(OUT, exist_ok=True)

ORANGE = (230, 74, 25)
ORANGE_DARK = (190, 55, 15)
ORANGE_LIGHT = (240, 100, 55)
WHITE = (255, 255, 255)
NEAR_WHITE = (255, 240, 230)
LIGHT_BG = (255, 243, 224)  # #FFF3E0
GRAY = (85, 85, 85)
GREEN = (76, 175, 80)
BLUE = (33, 150, 243)
PURPLE = (156, 39, 176)
RED = (244, 67, 54)

FONT_BOLD = 'C:/Windows/Fonts/arialbd.ttf'
FONT_REGULAR = 'C:/Windows/Fonts/segoeui.ttf'
FONT_SEMI = 'C:/Windows/Fonts/segoeuib.ttf'


def draw_m_letter(draw, cx, cy, size, fill=WHITE):
    half = size / 2
    x0 = cx - half
    y0 = cy - half
    w = size
    h = size
    sw = w * 0.155
    polygon = [
        (x0, y0 + h), (x0, y0),
        (x0 + w / 2, y0 + h * 0.58),
        (x0 + w, y0), (x0 + w, y0 + h),
        (x0 + w - sw, y0 + h),
        (x0 + w - sw, y0 + h * 0.25),
        (x0 + w / 2, y0 + h * 0.72),
        (x0 + sw, y0 + h * 0.25),
        (x0 + sw, y0 + h),
    ]
    draw.polygon(polygon, fill=fill)


def draw_rounded_rect(draw, xy, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=width)


def draw_color_block(draw, x, y, w, h, color, label, font):
    """Draw a colored block card like in the app."""
    draw.rounded_rectangle([(x, y), (x + w, y + h)], radius=8, fill=color)
    bbox = draw.textbbox((0, 0), label, font=font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    draw.text((x + (w - tw) // 2, y + (h - th) // 2), label, fill=WHITE, font=font)


def draw_fab_button(draw, cx, cy, size):
    """Draw the orange M floating button."""
    draw.ellipse(
        [(cx - size, cy - size), (cx + size, cy + size)],
        fill=ORANGE
    )
    draw_m_letter(draw, cx, cy, int(size * 1.1), fill=WHITE)


def draw_phone_mockup(draw, x, y, w, h, content_func):
    """Draw a simplified phone outline and fill with content."""
    # Phone body
    radius = int(w * 0.08)
    draw.rounded_rectangle([(x, y), (x + w, y + h)], radius=radius, fill=(40, 40, 40))
    # Screen area
    bezel = int(w * 0.04)
    top_bezel = int(h * 0.06)
    bot_bezel = int(h * 0.05)
    sx, sy = x + bezel, y + top_bezel
    sw, sh = w - 2 * bezel, h - top_bezel - bot_bezel
    draw.rectangle([(sx, sy), (sx + sw, sy + sh)], fill=WHITE)
    # Top notch
    notch_w = int(w * 0.25)
    notch_h = int(h * 0.015)
    draw.rounded_rectangle(
        [(x + (w - notch_w) // 2, y + int(top_bezel * 0.35)),
         (x + (w + notch_w) // 2, y + int(top_bezel * 0.35) + notch_h)],
        radius=notch_h // 2, fill=(60, 60, 60)
    )
    content_func(draw, sx, sy, sw, sh)


def draw_app_screen(draw, sx, sy, sw, sh, block_font, small_font):
    """Draw a simplified MacroKey app screen inside a phone."""
    # Status/top bar
    bar_h = int(sh * 0.07)
    draw.rectangle([(sx, sy), (sx + sw, sy + bar_h)], fill=ORANGE)
    bar_font = block_font
    draw.text((sx + 10, sy + (bar_h - 14) // 2), "MacroKey", fill=WHITE, font=bar_font)

    # Content area - light bg
    draw.rectangle([(sx, sy + bar_h), (sx + sw, sy + sh)], fill=LIGHT_BG)

    # Block cards
    bw = int(sw * 0.42)
    bh = int(sh * 0.07)
    gap = int(sw * 0.04)
    start_x = sx + gap
    start_y = sy + bar_h + int(sh * 0.05)

    blocks = [
        (GREEN, "Bank Details"),
        (BLUE, "Address"),
        (PURPLE, "Email Sig"),
        (RED, "Phone"),
    ]

    for i, (color, label) in enumerate(blocks):
        row = i // 2
        col = i % 2
        bx = start_x + col * (bw + gap)
        by = start_y + row * (bh + gap)
        draw_color_block(draw, bx, by, bw, bh, color, label, small_font)


def generate_tablet_promo(width, height, label, filename):
    """Generate a tablet promotional screenshot."""
    img = Image.new('RGB', (width, height), LIGHT_BG)
    draw = ImageDraw.Draw(img)

    # Scale factors based on width
    scale = width / 1200.0

    # Fonts
    title_size = max(14, int(52 * scale))
    sub_size = max(10, int(28 * scale))
    block_size = max(8, int(16 * scale))
    small_size = max(7, int(11 * scale))
    tag_size = max(8, int(18 * scale))

    title_font = ImageFont.truetype(FONT_BOLD, title_size)
    sub_font = ImageFont.truetype(FONT_SEMI, sub_size)
    block_font = ImageFont.truetype(FONT_BOLD, block_size)
    small_font = ImageFont.truetype(FONT_BOLD, small_size)
    tag_font = ImageFont.truetype(FONT_REGULAR, tag_size)

    # ── SLIDE 1: Hero / Overview ──
    # Left side: text content
    left_margin = int(80 * scale)
    center_y = height // 2

    # App icon
    icon_size = int(70 * scale)
    icon_x = left_margin
    icon_y = center_y - int(160 * scale)
    draw.rounded_rectangle(
        [(icon_x, icon_y), (icon_x + icon_size, icon_y + icon_size)],
        radius=int(14 * scale), fill=ORANGE
    )
    draw_m_letter(draw, icon_x + icon_size // 2, icon_y + icon_size // 2,
                  int(icon_size * 0.55), fill=WHITE)

    # Title
    ty = icon_y + icon_size + int(20 * scale)
    draw.text((left_margin, ty), "MacroKey", fill=ORANGE, font=title_font)

    # Subtitle
    ty2 = ty + title_size + int(12 * scale)
    draw.text((left_margin, ty2), "Stop retyping.", fill=GRAY, font=sub_font)

    # Feature bullets
    bullet_font = ImageFont.truetype(FONT_REGULAR, max(8, int(20 * scale)))
    features = [
        "Save text blocks once",
        "Paste anywhere with one tap",
        "Floating button over any app",
        "Works with WhatsApp, Email, SMS...",
    ]
    fy = ty2 + sub_size + int(30 * scale)
    for feat in features:
        draw.text((left_margin + int(5 * scale), fy), f"•  {feat}",
                  fill=(100, 100, 100), font=bullet_font)
        fy += int(32 * scale)

    # Right side: phone mockup
    phone_w = int(220 * scale)
    phone_h = int(420 * scale)
    phone_x = width - phone_w - int(120 * scale)
    phone_y = (height - phone_h) // 2

    def content1(d, sx, sy, sw, sh):
        draw_app_screen(d, sx, sy, sw, sh, block_font, small_font)
        # FAB
        fab_size = int(16 * scale)
        draw_fab_button(d, sx + sw - int(25 * scale), sy + sh - int(25 * scale), fab_size)

    draw_phone_mockup(draw, phone_x, phone_y, phone_w, phone_h, content1)

    # Second phone slightly behind showing the floating panel
    phone2_x = phone_x + int(160 * scale)
    phone2_y = phone_y + int(40 * scale)
    phone2_w = int(200 * scale)
    phone2_h = int(380 * scale)

    def content2(d, sx, sy, sw, sh):
        # Simulate a chat app background
        d.rectangle([(sx, sy), (sx + sw, sy + sh)], fill=(237, 247, 237))
        # Top bar
        bar_h = int(sh * 0.07)
        d.rectangle([(sx, sy), (sx + sw, sy + bar_h)], fill=(37, 175, 80))
        d.text((sx + 8, sy + (bar_h - 10) // 2), "WhatsApp", fill=WHITE, font=small_font)

        # Chat bubbles
        bub_y = sy + bar_h + int(sh * 0.08)
        # Received
        d.rounded_rectangle(
            [(sx + 6, bub_y), (sx + sw * 0.7, bub_y + int(sh * 0.06))],
            radius=6, fill=WHITE
        )
        d.text((sx + 12, bub_y + 4), "Hey, send me your details", fill=GRAY, font=small_font)
        bub_y += int(sh * 0.1)

        # Sent (green)
        d.rounded_rectangle(
            [(sx + sw * 0.2, bub_y), (sx + sw - 6, bub_y + int(sh * 0.12))],
            radius=6, fill=(220, 248, 198)
        )
        d.text((sx + sw * 0.2 + 8, bub_y + 4), "Daniel Peretz\n052-768-6686\nBank Leumi 10-123456",
               fill=GRAY, font=small_font)

        # Floating panel overlay
        panel_w = int(sw * 0.75)
        panel_h = int(sh * 0.35)
        px = sx + (sw - panel_w) // 2
        py = sy + sh - panel_h - int(sh * 0.12)
        d.rounded_rectangle(
            [(px, py), (px + panel_w, py + panel_h)],
            radius=10, fill=(250, 250, 250), outline=(224, 224, 224), width=1
        )
        # Panel title
        d.text((px + 6, py + 4), "MacroKey", fill=ORANGE, font=small_font)
        # Mini blocks
        mbw = (panel_w - 16) // 2
        mbh = int(panel_h * 0.25)
        mini_blocks = [(GREEN, "Bank"), (BLUE, "Address"), (PURPLE, "Email"), (RED, "Phone")]
        for i, (c, l) in enumerate(mini_blocks):
            r = i // 2
            col = i % 2
            mx = px + 4 + col * (mbw + 4)
            my = py + int(panel_h * 0.22) + r * (mbh + 3)
            d.rounded_rectangle([(mx, my), (mx + mbw, my + mbh)], radius=4, fill=c)
            d.text((mx + 4, my + 2), l, fill=WHITE, font=small_font)

        # FAB
        fab_size = int(14 * scale)
        draw_fab_button(d, sx + sw - int(20 * scale), sy + sh - int(20 * scale), fab_size)

    draw_phone_mockup(draw, phone2_x, phone2_y, phone2_w, phone2_h, content2)

    # Bottom badge
    badge_text = f"MacroKey — {label}"
    bbox = draw.textbbox((0, 0), badge_text, font=tag_font)
    tw = bbox[2] - bbox[0]
    draw.text(((width - tw) // 2, height - int(40 * scale)), badge_text,
              fill=(180, 140, 120), font=tag_font)

    path = os.path.join(OUT, filename)
    img.save(path, 'PNG')
    print(f'{label} saved: {path} ({width}x{height})')


if __name__ == '__main__':
    # 7-inch tablet: 1024x600
    generate_tablet_promo(1024, 600, "7\" Tablet", "tablet_7inch_1024x600.png")
    # 10-inch tablet: 1920x1200
    generate_tablet_promo(1920, 1200, "10\" Tablet", "tablet_10inch_1920x1200.png")
    print('Done!')
