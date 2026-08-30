# UCTALE frontend design foundation

## Direction: Charcoal Folio

UCTALE uses a **genre-neutral interactive storybook** direction. The generated scene image is the strongest visual element; the interface acts as a quiet folio around it.

The design deliberately avoids fantasy parchment/copper theming, futuristic HUD chrome, neon AI gradients, glassmorphism, chat bubbles, and decorative AI sparkle. User-created worlds can be modern, historical, horror, SF, fantasy, or everyday life, so genre is carried by the generated scene rather than by the application chrome.

## Visual hierarchy

Gameplay keeps one editorial reading direction on mobile and desktop:

1. compact UCTALE identity / scene title
2. 16:9 generated scene
3. story body
4. available choices
5. secondary actions

The scene is never intentionally cropped, filtered, tinted, inverted, or displayed above 768 CSS px. Story and choices use a narrower reading measure (`42rem`) so long Korean prose remains readable.

A desktop two-column composition is intentionally deferred. At the current content shape it separates the image/story reading order and encourages cropping or oversized prose columns without solving an actual interaction problem.

## Color contract

Theme values are semantic custom properties. Components should not repeat raw theme colors.

| Role | Light | Dark |
| --- | --- | --- |
| background | `#F3EFE7` | `#111210` |
| surface | `#FAF8F3` | `#191A18` |
| text | `#211F1B` | `#F0EDE6` |
| muted text | `#655F56` | `#B6AFA4` |
| action | `#315F8C` | `#9EC7EF` |
| action text | `#FFFFFF` | `#102238` |

The primary text/background and muted text/background pairs exceed WCAG 2.2 AA normal-text contrast. The action/action-text pairs also exceed 4.5:1. Strong control borders are kept separate from subtle decorative rules so interactive boundaries can maintain at least 3:1 contrast.

The ink-blue action color is reserved for interaction, selected state, links, and focus. It is not a decorative brand wash.

## Theme behavior

Supported modes are:

- `system`
- `light`
- `dark`

The explicit mode is stored under `uctale.theme-mode`. `system` listens to `prefers-color-scheme`; explicit light/dark modes do not get overwritten by later OS changes.

A small script in `index.html` applies the resolved theme before React mounts to avoid a visible opposite-theme flash. React then owns subsequent selection and system-preference changes through the same `data-theme` contract.

Storage errors or invalid stored values fall back to `system` and must never block game startup.

## Typography

The production family is **SUIT Variable 2.0.5**, installed from the official `@sun-typeface/suit` npm package and bundled by Vite rather than loaded from a runtime font CDN.

- UI and story share one family.
- Hierarchy comes from size, weight, spacing, and reading measure rather than loading a second serif family.
- Story line-height is approximately `1.82`.
- A system sans-serif fallback remains available while the webfont is loading.

SUIT is distributed under the SIL Open Font License 1.1. The repository keeps a third-party notice in `frontend/THIRD_PARTY_NOTICES.md`; the package's own license remains available in the installed dependency.

Pretendard remains a reasonable alternative, but using both families would add payload and reopen a decision that does not currently solve an observed readability problem.

## Component boundary

`App` remains application orchestration: auth/game state and request handlers.

Screen presentation is separated into:

- `AccessScreen`
- `GameSetupScreen`
- `GamePlayScreen`
- `ThemeSelector`
- `BrandHeader`

`GameImage` continues to own authenticated image fetching/object URL lifecycle. `TypewriterText` continues to own typing behavior. Their visual styles live in CSS rather than JSX.

Generic `Button`, `Field`, `Surface`, or atomic-design wrappers are not introduced until repeated APIs/variants justify them.

## Dependency policy

This foundation deliberately does **not** add Tailwind, shadcn/ui, Radix UI, Lucide, CSS-in-JS, or a full design-system package.

Native controls and plain CSS are sufficient for the current screens. A headless primitive can be reconsidered when a real complex interaction such as Dialog/Popover/Select appears (for example during later Save/Resume work).

## Scope boundary with #48

This foundation owns visual identity, typography, theme, semantic tokens, base responsive composition, screen-level presentation boundaries, and visual focus/contrast/zoom baseline.

Issue #48 remains responsible for behavior-level polish such as inline API error/retry, consistent loading/disabled states, typewriter skip/reduced motion, image failure recovery, `aria-live`/`aria-busy`, focus movement, and end-to-end keyboard flow.
