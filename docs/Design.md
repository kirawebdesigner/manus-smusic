# Design – Design System & UI/UX Guidelines for Smusic

## 1. Visual Language
| Style | Description |
|---|---|
| **Neo‑Brutalist** | Heavy use of stark contrast, blocky containers, and thick borders to convey robustness. |
| **Glassmorphism** | Subtle frosted‑glass overlays on dialogs and the settings panel with a backdrop‑blur of 12‑16dp. |
| **Minimalist** | Sparse UI, generous whitespace, and focus on content (media thumbnail + controls). |

## 2. Color Palettes
### Dark Mode (Primary)
- `#0D0D0D` – Canvas (background)
- `#1F1F1F` – Panel surface
- `#2C2C2C` – Card background
- `#BB86FC` – Accent / Primary (purple‑ish)
- `#03DAC5` – Secondary accent (teal)
- `#FFFFFF` – Text (primary)
- `#AAAAAA` – Text (secondary)

### Light Mode (Optional)
- `#FFFFFF` – Canvas
- `#F2F2F2` – Panel surface
- `#E0E0E0` – Card background
- `#6200EE` – Accent (deep violet)
- `#018786` – Secondary accent (teal)
- `#000000` – Text primary
- `#555555` – Text secondary

## 3. Typography & Font Pairings
- **Primary Font:** `Inter` – variable weight 100‑900, used for headings, buttons, and body copy.
- **Secondary Font (optional):** `Roboto Slab` – for decorative section titles or release notes.
- **Sizing Scale (sp):**
  - `Display`: 34sp
  - `Headline`: 24sp
  - `Title`: 20sp
  - `Body`: 16sp
  - `Caption`: 12sp
- **Line Height:** 1.25 × font size.

## 4. Spacing & Layout Rules
| Token | Value (dp) |
|---|---|
| **Gutter** | 8dp (base) |
| **Grid** | 4‑column responsive grid (2 columns on phones, 4 on tablets) |
| **Component Padding** | 16dp surrounding cards, 12dp inside list items |
| **Touch Target** | Minimum 48dp × 48dp per Material guidelines |

## 5. Component Library (Compose)
- **AppBar** – Transparent background, dark‑mode icons, optional back button.
- **Card** – Rounded‑corner (12dp), elevation 4dp, glass‑blur overlay for progress overlay.
- **Button** – Filled primary (`#BB86FC`) with ripple, disabled opacity 0.38.
- **Progress Indicator** – Linear progress bar with animated gradient matching accent color.
- **Bottom Navigation** – 4 tabs with icons (Home, Queue, Library, Settings), active tint accent, inactive muted.
- **Dialog** – Semi‑transparent dark background with glass effect, rounded corners.

## 6. Motion & Micro‑animations
- **Hero Transition** – When tapping a library tile, image expands to fullscreen player using shared element transition.
- **Progress Fill** – Linear progress animates with a pulsing gradient every 2s.
- **State Change** – Fade‑in/out of success/error snackbars (300ms).
- **Loading Skeleton** – Shimmer placeholders on list items while data loads.

---

## 7. Accessibility & Contrast
- Minimum AA contrast for text vs. background (4.5:1 dark, 3:1 light).
- All actionable elements have content‑description for TalkBack.
- Dynamic font scaling respects user system settings (up to 1.4x).

---

## 8. Asset Guidelines
- Icons: Material‑Icons‑Extended, weight 400, monochrome for dark mode, tinted for light.
- Images: Prefer WebP lossless for album art, 256 × 256px minimum, rounded corners.
- Logo: Simple monogram “S” with gradient from `#BB86FC` to `#03DAC5` on dark canvas.

**All UI components are built with Jetpack Compose, no XML layouts.**
