from PIL import Image, ImageDraw, ImageFont
import os

OUT = os.path.join(os.path.dirname(__file__), 'icons')
os.makedirs(OUT, exist_ok=True)

ORANGE = (230, 74, 25)       # #E64A19
WHITE = (255, 255, 255)
ORANGE_LIGHT = (240, 100, 55)  # subtle grid line color

FONT_BOLD = 'C:/Windows/Fonts/arialbd.ttf'
FONT_REGULAR = 'C:/Windows/Fonts/segoeui.ttf'
FONT_SEMI = 'C:/Windows/Fonts/segoeuib.ttf'


def draw_m_letter(draw, cx, cy, size, fill=WHITE):
    """Draw a bold geometric M centered at (cx, cy) with given size."""
    half = size / 2
    x0 = cx - half
    y0 = cy - half
    w = size
    h = size
    sw = w * 0.155  # stroke width

    polygon = [
        (x0, y0 + h),
        (x0, y0),
        (x0 + w / 2, y0 + h * 0.58),
        (x0 + w, y0),
        (x0 + w, y0 + h),
        (x0 + w - sw, y0 + h),
        (x0 + w - sw, y0 + h * 0.25),
        (x0 + w / 2, y0 + h * 0.72),
        (x0 + sw, y0 + h * 0.25),
        (x0 + sw, y0 + h),
    ]
    draw.polygon(polygon, fill=fill)


# ═══════════════════════════════════════════
# 1. FEATURE GRAPHIC — 1024 x 500
# ═══════════════════════════════════════════
def generate_feature_graphic():
    W, H = 1024, 500
    img = Image.new('RGB', (W, H), ORANGE)
    draw = ImageDraw.Draw(img)

    # Subtle grid pattern
    grid_spacing = 40
    for x in range(0, W, grid_spacing):
        draw.line([(x, 0), (x, H)], fill=ORANGE_LIGHT, width=1)
    for y in range(0, H, grid_spacing):
        draw.line([(0, y), (W, y)], fill=ORANGE_LIGHT, width=1)

    # Slight vignette-like darker edges using rectangles with alpha
    # (simple approach: draw semi-transparent border strips)
    overlay = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ov_draw = ImageDraw.Draw(overlay)
    for i in range(20):
        alpha = int(15 * (1 - i / 20))
        ov_draw.rectangle([(i, i), (W - 1 - i, H - 1 - i)], outline=(0, 0, 0, alpha))
    img_rgba = img.convert('RGBA')
    img_rgba = Image.alpha_composite(img_rgba, overlay)
    img = img_rgba.convert('RGB')
    draw = ImageDraw.Draw(img)

    # White M icon — centered horizontally, upper area
    m_size = 120
    m_cx = W // 2
    m_cy = 140
    # Circle background behind M (slightly lighter)
    circle_r = 80
    draw.ellipse(
        [(m_cx - circle_r, m_cy - circle_r), (m_cx + circle_r, m_cy + circle_r)],
        fill=(255, 255, 255, 30),
        outline=None
    )
    # Actually draw a subtle white circle with low opacity
    # Pillow RGB doesn't support alpha in fill, so use a light orange instead
    draw.ellipse(
        [(m_cx - circle_r, m_cy - circle_r), (m_cx + circle_r, m_cy + circle_r)],
        fill=(235, 90, 40)
    )
    draw_m_letter(draw, m_cx, m_cy, m_size, fill=WHITE)

    # Main text: "Stop retyping."
    font_main = ImageFont.truetype(FONT_BOLD, 58)
    text_main = "Stop retyping."
    bbox = draw.textbbox((0, 0), text_main, font=font_main)
    tw = bbox[2] - bbox[0]
    draw.text(((W - tw) // 2, 250), text_main, fill=WHITE, font=font_main)

    # Subtitle: "Save once. Paste anywhere. One tap."
    font_sub = ImageFont.truetype(FONT_SEMI, 28)
    text_sub = "Save once. Paste anywhere. One tap."
    bbox2 = draw.textbbox((0, 0), text_sub, font=font_sub)
    tw2 = bbox2[2] - bbox2[0]
    # Slightly transparent white — use light color
    sub_color = (255, 255, 255)
    draw.text(((W - tw2) // 2, 330), text_sub, fill=sub_color, font=font_sub)

    # Thin separator line between title and subtitle
    line_y = 318
    line_w = 80
    draw.line(
        [(W // 2 - line_w, line_y), (W // 2 + line_w, line_y)],
        fill=(255, 255, 255), width=2
    )

    # Bottom tagline - app name
    font_tag = ImageFont.truetype(FONT_REGULAR, 20)
    tag = "MacroKey — Your Floating Text Assistant"
    bbox3 = draw.textbbox((0, 0), tag, font=font_tag)
    tw3 = bbox3[2] - bbox3[0]
    draw.text(((W - tw3) // 2, 430), tag, fill=(255, 220, 200), font=font_tag)

    path = os.path.join(OUT, 'feature_graphic_1024x500.png')
    img.save(path, 'PNG')
    print(f'Feature graphic saved: {path}')


# ═══════════════════════════════════════════
# 2. APP ICON — 512 x 512
# ═══════════════════════════════════════════
def generate_app_icon():
    S = 512
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Rounded square background
    radius = int(S * 0.22)
    draw.rounded_rectangle([(0, 0), (S - 1, S - 1)], radius=radius, fill=ORANGE)

    # Draw bold M letter centered
    m_size = int(S * 0.55)
    draw_m_letter(draw, S // 2, S // 2, m_size, fill=WHITE)

    path = os.path.join(OUT, 'app_icon_512x512.png')
    img.save(path, 'PNG')
    print(f'App icon saved: {path}')


if __name__ == '__main__':
    generate_feature_graphic()
    generate_app_icon()
    print('Done!')
