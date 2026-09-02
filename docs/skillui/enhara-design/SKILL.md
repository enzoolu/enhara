---
name: Enhara-design
description: Design system skill for Enhara. Activate when building UI components, pages, or any visual elements. Provides exact color tokens, typography scale, spacing grid, component patterns, and craft rules. Read references/DESIGN.md before writing any CSS or JSX.
---

# Enhara Design System

You are building UI for **Enhara**. Light-themed, cool palette, sans-serif typography (Inter), compact density on a 4px grid.

## Design Philosophy

- **Layered depth** — use shadow tokens to create a sense of physical layering. Each elevation level has a specific shadow.
- **Gradient accents** — gradients are used thoughtfully for emphasis, not decoration.
- **Single typeface** — Inter carries all text. Hierarchy comes from size, weight, and color — never font mixing.
- **compact density** — 4px base grid. Every dimension is a multiple of 4.
- **cool palette** — the color temperature runs cool, matching the sans-serif typography.
- **Restrained accent** — `#c78cff` is the only pop of color. Used exclusively for CTAs, links, focus rings, and active states.
- **Subtle motion** — transitions smooth state changes. Keep durations under 300ms, use ease-out curves.

## Color System

### Core Palette

| Role | Token | Hex | Use |
|------|-------|-----|-----|
| Background | `--background` | `#ffffff` | Page/app background |
| Surface | `--surface` | `#f2f7f5` | Cards, panels, modals |
| Text Primary | `--text-primary` | `#07100e` | Headings, body text |
| Text Muted | `--text-muted` | `#8ea39d` | Captions, placeholders |
| Accent | `--accent` | `#c78cff` | CTAs, links, focus rings |

### Status Colors

| Status | Hex | Use |
|--------|-----|-----|
| Success | `#61e7a9` | Confirmations, positive trends |
| Warning | `#ffbd66` | Caution states, pending items |

### Extended Palette

- `#62766f`
- `#73847e`
- `#749187`
- `#82978f`
- `#adc0ba`
- `#d7e6e1`
- `#c7d8d2`
- `#b8cac4`

### CSS Variable Tokens

```css
--muted: #8ea39d;
```

## Typography

### Font Stack

- **Inter** — Heading 1, Heading 2, Heading 3, Body, Caption

### Font Sources

```css
@font-face {
  font-family: "Inter";
  src: url("fonts/Inter-SemiBold.ttf") format("truetype");
  font-weight: 600;
}
@font-face {
  font-family: "Inter";
  src: url("fonts/Inter-Bold.ttf") format("truetype");
  font-weight: 700;
}
@font-face {
  font-family: "Inter";
  src: url("fonts/Inter-Regular.ttf") format("truetype");
  font-weight: 400;
}
```

### Type Scale

| Role | Family | Size | Weight |
|------|--------|------|--------|
| Heading 1 | Inter | 48px / 3rem | 700 |
| Heading 2 | Inter | 32px / 2rem | 600 |
| Heading 3 | Inter | 24px / 1.5rem | 600 |
| Body | Inter | 16px / 1rem | 400 |
| Caption | Inter | 12px / 0.75rem | 400 |

### Typography Rules

- All text uses **Inter** — never add another font family
- Max 3-4 font sizes per screen
- Headings: weight 600-700, body: weight 400
- Use color and opacity for text hierarchy, not additional font sizes
- Line height: 1.5 for body, 1.2 for headings

## Spacing & Layout

### Base Grid: 4px

Every dimension (margin, padding, gap, width, height) must be a multiple of **4px**.

### Spacing Scale

`2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24` px

### Spacing as Meaning

| Spacing | Use |
|---------|-----|
| 4-8px | Tight: related items (icon + label, avatar + name) |
| 12-16px | Medium: between groups within a section |
| 24-32px | Wide: between distinct sections |
| 48px+ | Vast: major page section breaks |

### Border Radius

Scale: `5px, 6px, 7px, 7px 7px 3px 3px, 8px, 9px, 10px, 11px, 12px, 15px, 18px, 999px`
Default: `10px`

### Container

Max-width: `100%`, centered with auto margins.

## Component Patterns

### Card

```css
.card {
  background: #f2f7f5;
  border-radius: 10px;
  padding: 16px;
  box-shadow: 0 0 0 2px rgba(97,231,169,.2);
}
```

```html
<div class="card">
  <h3>Card Title</h3>
  <p>Card content goes here.</p>
</div>
```

### Button

```css
/* Primary */
.btn-primary {
  background: #c78cff;
  color: #07100e;
  border-radius: 10px;
  padding: 8px 16px;
  font-weight: 500;
  transition: opacity 150ms ease;
}
.btn-primary:hover { opacity: 0.9; }

/* Ghost */
.btn-ghost {
  background: transparent;
  border: 1px solid #cccccc;
  color: #07100e;
  border-radius: 10px;
  padding: 8px 16px;
}
```

```html
<button class="btn-primary">Get Started</button>
<button class="btn-ghost">Learn More</button>
```

### Input

```css
.input {
  background: #ffffff;
  border: 1px solid #cccccc;
  border-radius: 10px;
  padding: 8px 12px;
  color: #07100e;
  font-size: 14px;
}
.input:focus { border-color: #c78cff; outline: none; }
```

```html
<input class="input" type="text" placeholder="Search..." />
```

### Badge / Chip

```css
.badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 500;
  background: #f2f7f5;
  color: #8ea39d;
}
```

```html
<span class="badge">New</span>
<span class="badge">Beta</span>
```

### Modal / Dialog

```css
.modal-backdrop { background: rgba(0, 0, 0, 0.6); }
.modal {
  background: #f2f7f5;
  border-radius: 999px;
  padding: 24px;
  max-width: 480px;
  width: 90vw;
  box-shadow: 0 0 0 5px rgba(97,231,169,.08),0 0 15px rgba(97,231,169,.7);
}
```

```html
<div class="modal-backdrop">
  <div class="modal">
    <h2>Dialog Title</h2>
    <p>Dialog content.</p>
    <button class="btn-primary">Confirm</button>
    <button class="btn-ghost">Cancel</button>
  </div>
</div>
```

### Table

```css
.table { width: 100%; border-collapse: collapse; }
.table th {
  text-align: left;
  padding: 8px 12px;
  font-weight: 500;
  font-size: 12px;
  color: #8ea39d;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid #cccccc;
}
.table td {
  padding: 12px;
  border-bottom: 1px solid #cccccc;
}
```

```html
<table class="table">
  <thead><tr><th>Name</th><th>Status</th><th>Date</th></tr></thead>
  <tbody>
    <tr><td>Item One</td><td>Active</td><td>Jan 1</td></tr>
    <tr><td>Item Two</td><td>Pending</td><td>Jan 2</td></tr>
  </tbody>
</table>
```

### Navigation

```css
.nav {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
}
.nav-link {
  color: #8ea39d;
  padding: 8px 12px;
  border-radius: 10px;
  transition: color 150ms;
}
.nav-link:hover { color: #07100e; }
.nav-link.active { color: #c78cff; }
```

```html
<nav class="nav">
  <a href="/" class="nav-link active">Home</a>
  <a href="/about" class="nav-link">About</a>
  <a href="/pricing" class="nav-link">Pricing</a>
  <button class="btn-primary" style="margin-left: auto">Get Started</button>
</nav>
```

## Animation & Motion

This project uses **subtle motion**. Transitions smooth state changes without calling attention.

### CSS Animations

- `spin`

### Motion Guidelines

- **Duration:** 150-300ms for micro-interactions, 300-500ms for page transitions
- **Easing:** `ease-out` for enters, `ease-in` for exits
- **Direction:** Elements enter from bottom/right, exit to top/left
- **Reduced motion:** Always respect `prefers-reduced-motion` — disable animations when set

## Depth & Elevation

### Shadow Tokens

- Subtle: `0 0 0 2px rgba(97,231,169,.2)`
- Floating (dropdowns, popovers): `0 0 0 5px rgba(97,231,169,.08),0 0 15px rgba(97,231,169,.7)`
- Floating (dropdowns, popovers): `0 0 12px var(--metric-color)`
- Floating (dropdowns, popovers): `0 0 20px rgba(97,231,169,.14)`
- Overlay (modals, dialogs): `0 0 28px rgba(68,230,156,.2)`
- Overlay (modals, dialogs): `0 18px 50px rgba(0,0,0,.13)`

### Z-Index Scale

`1, 20, 30`

Use these exact values — never invent z-index values.

## Anti-Patterns (Never Do)

- **No blur effects** — no backdrop-blur, no filter: blur()
- **No zebra striping** — tables and lists use borders for separation
- **No invented colors** — every hex value must come from the palette above
- **No arbitrary spacing** — every dimension is a multiple of 4px
- **No extra fonts** — only Inter are allowed
- **No arbitrary border-radius** — use the scale: 5px, 6px, 7px, 8px, 9px, 10px, 11px, 12px, 15px, 18px
- **No opacity for disabled states** — use muted colors instead

## Workflow

1. **Read** `references/DESIGN.md` before writing any UI code
2. **Pick colors** from the Color System section — never invent new ones
3. **Set typography** — Inter only, using the type scale
4. **Build layout** on the 4px grid — check every margin, padding, gap
5. **Match components** to patterns above before creating new ones
6. **Apply elevation** — use shadow tokens
7. **Validate** — every value traces back to a design token. No magic numbers.

## Brand Spec

- **Brand color:** `#c78cff`
- **Brand typeface:** Inter

## Quick Reference

```
Background:     #ffffff
Surface:        #f2f7f5
Text:           #07100e / #8ea39d
Accent:         #c78cff
Border:         (not extracted)
Font:           Inter
Spacing:        4px grid
Radius:         10px
Frameworks:     React
Components:     0 detected
```

## When to Trigger

Activate this skill when:
- Creating new components, pages, or visual elements for Enhara
- Writing CSS, Tailwind classes, styled-components, or inline styles
- Building page layouts, templates, or responsive designs
- Reviewing UI code for design consistency
- The user mentions "Enhara" design, style, UI, or theme
- Generating mockups, wireframes, or visual prototypes

---

# Full Reference Files

> Every output file is embedded below. Claude has full design system context from /skills alone.

## Design System Tokens (DESIGN.md)

# Enhara DESIGN.md

> Auto-generated design system — reverse-engineered via static analysis by skillui.
> Frameworks: React 19.2.0
> Colors: 20 · Fonts: 1 · Components: 0
> Icon library: not detected · State: not detected
> Primary theme: light · Dark mode toggle: no · Motion: subtle

---

## 1. Visual Theme & Atmosphere

This is a **light-themed** interface with a cool, approachable feel. The light background emphasizes content clarity. Typography uses **Inter** throughout — a clean, modern choice that maintains consistency. Spacing follows a **4px base grid** (compact density), with scale: 2, 4, 6, 8, 10, 12, 14, 16px. The palette is predominantly monochromatic with **#c78cff** as the single accent color — used sparingly for interactive elements and emphasis. Motion is subtle — smooth transitions (150-300ms) ease state changes without drawing attention.

---

## 2. Color Palette & Roles

| Token | Hex | Role | Use |
|---|---|---|---|
| background | `#ffffff` | background | Page background, darkest surface |
| surface | `#f2f7f5` | surface | Card and panel backgrounds |
| text-primary | `#07100e` | text-primary | Headings and body text |
| muted | `#8ea39d` | text-muted | Captions, placeholders, secondary info |
| accent | `#c78cff` | accent | CTAs, links, focus rings, active states |
| success | `#61e7a9` | success | Success states, positive indicators |
| warning | `#ffbd66` | warning | Warning states, caution indicators |
| unknown | `#62766f` | unknown | Palette color |
| unknown | `#73847e` | unknown | Palette color |
| unknown | `#749187` | unknown | Palette color |
| unknown | `#82978f` | unknown | Palette color |
| unknown | `#adc0ba` | unknown | Palette color |
| unknown | `#d7e6e1` | unknown | Palette color |
| unknown | `#c7d8d2` | unknown | Palette color |
| unknown | `#b8cac4` | unknown | Palette color |
| unknown | `#2cc981` | unknown | Palette color |
| unknown | `#111f1b` | unknown | Palette color |
| unknown | `#9eb1ab` | unknown | Palette color |
| unknown | `#cffff0` | unknown | Palette color |
| unknown | `#29a16b` | unknown | Palette color |

### CSS Variable Tokens

```css
--muted: #8ea39d;
```


---

## 3. Typography Rules

**Font Stack:**
- **Inter** — Heading 1, Heading 2, Heading 3, Body, Caption

**Font Sources:**

```css
@font-face {
  font-family: "Inter";
  src: url("fonts/Inter-SemiBold.ttf") format("truetype");
  font-weight: 600;
}
@font-face {
  font-family: "Inter";
  src: url("fonts/Inter-Bold.ttf") format("truetype");
  font-weight: 700;
}
@font-face {
  font-family: "Inter";
  src: url("fonts/Inter-Regular.ttf") format("truetype");
  font-weight: 400;
}
```

| Role | Font | Size | Weight |
|---|---|---|---|
| Heading 1 | Inter | 48px / 3rem | 700 |
| Heading 2 | Inter | 32px / 2rem | 600 |
| Heading 3 | Inter | 24px / 1.5rem | 600 |
| Body | Inter | 16px / 1rem | 400 |
| Caption | Inter | 12px / 0.75rem | 400 |

**Typographic Rules:**
- Use **Inter** for all text — do not mix font families
- Maintain consistent hierarchy: no more than 3-4 font sizes per screen
- Headings use bold (600-700), body uses regular (400)
- Line height: 1.5 for body text, 1.2 for headings
- Use color and opacity for secondary hierarchy, not additional font sizes


---

## 4. Component Stylings

No components detected. Scan `src/components/` or `components/` to populate this section.

---

## 5. Layout Principles

- **Base spacing unit:** 4px
- **Spacing scale:** 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24
- **Border radius:** 5px, 6px, 7px, 7px 7px 3px 3px, 8px, 9px, 10px, 11px, 12px, 15px, 18px, 999px
- **Max content width:** 100%

**Spacing as Meaning:**
| Spacing | Use |
|---|---|
| 4-8px | Tight: related items within a group |
| 12-16px | Medium: between groups |
| 24-32px | Wide: between sections |
| 48px+ | Vast: major section breaks |


---

## 6. Depth & Elevation

### Flat — subtle depth hints

- `0 0 0 2px rgba(97,231,169,.2)`

### Floating — dropdowns, popovers, modals

- `0 0 0 5px rgba(97,231,169,.08),0 0 15px rgba(97,231,169,.7)`
- `0 0 12px var(--metric-color)`
- `0 0 20px rgba(97,231,169,.14)`

### Overlay — full-screen overlays, top-level dialogs

- `0 0 28px rgba(68,230,156,.2)`
- `0 18px 50px rgba(0,0,0,.13)`
- `0 0 35px color-mix(in srgb,var(--ring) 14%,transparent)`

### Z-Index Scale

`1, 20, 30`



---

## 7. Animation & Motion

This project uses **subtle motion**. Transitions smooth state changes without demanding attention.

### CSS Animations

- `@keyframes spin`

### Motion Guidelines

- Duration: 150-300ms for micro-interactions, 300-500ms for page transitions
- Easing: `ease-out` for enters, `ease-in` for exits
- Always respect `prefers-reduced-motion`


---

## 8. Do's and Don'ts

### Do's

- Use `#c78cff` for interactive elements (buttons, links, focus rings)
- Use `#ffffff` as the primary page background
- Use **Inter** for all UI text
- Follow the **4px** spacing grid for all margins, padding, and gaps
- Use the defined shadow tokens for elevation — see Section 6
- Use border-radius from the scale: 5px, 6px, 7px, 7px 7px 3px 3px, 8px

### Don'ts

- Don't introduce colors outside this palette — extend the design tokens first
- Don't mix font families — use Inter consistently
- Don't use arbitrary spacing values — stick to multiples of 4px
- Don't create custom box-shadow values outside the system tokens
- Don't use arbitrary border-radius values — pick from the defined scale
- Don't use backdrop-blur or blur effects

### Anti-Patterns (detected from codebase)

- No blur or backdrop-blur effects
- No zebra striping on tables/lists


---

## 9. Responsive Behavior

No breakpoints detected. Consider adding responsive breakpoints to the design system.

---

## 10. Agent Prompt Guide

Use these as starting points when building new UI:

### Build a Card

```
Background: #f2f7f5
Border: 1px solid var(--border)
Radius: 10px
Padding: 16px
Font: Inter
Use shadow tokens from Section 6.
```

### Build a Button

```
Primary: bg #c78cff, text white
Ghost: bg transparent, border var(--border)
Padding: 8px 16px
Radius: 10px
Hover: opacity 0.9 or lighter shade
Focus: ring with #c78cff
```

### Build a Page Layout

```
Background: #ffffff
Max-width: 100%, centered
Grid: 4px base
Responsive: mobile-first, breakpoints from Section 9
```

### Build a Stats Card

```
Surface: #f2f7f5
Label: #8ea39d (muted, 12px, uppercase)
Value: #07100e (primary, 24-32px, bold)
Status: use success/warning/danger from Section 2
```

### Build a Form

```
Input bg: #ffffff
Input border: 1px solid var(--border)
Focus: border-color #c78cff
Label: #8ea39d 12px
Spacing: 16px between fields
Radius: 10px
```

### General Component

```
1. Read DESIGN.md Sections 2-6 for tokens
2. Colors: only from palette
3. Font: Inter, type scale from Section 3
4. Spacing: 4px grid
5. Components: match patterns from Section 4
6. Elevation: shadow tokens
```

## Bundled Fonts (fonts/)

The following font files are bundled in the `fonts/` directory:

- `fonts/Inter-Black.ttf`
- `fonts/Inter-Bold.ttf`
- `fonts/Inter-ExtraBold.ttf`
- `fonts/Inter-ExtraLight.ttf`
- `fonts/Inter-Light.ttf`
- `fonts/Inter-Medium.ttf`
- `fonts/Inter-Regular.ttf`
- `fonts/Inter-SemiBold.ttf`
- `fonts/Inter-Thin.ttf`

Use these local font files in `@font-face` declarations instead of fetching from Google Fonts.

