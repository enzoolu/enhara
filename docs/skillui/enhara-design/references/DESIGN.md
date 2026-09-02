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
