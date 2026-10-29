# 04 — Frontend Design System

## 4.1 Design Tokens

All tokens live in `frontend/src/styles/tokens.css`. No hard-coded values in component CSS.

### 4.1.1 Color Palette (Light + Dark)

```css
:root {
  /* Text */
  --color-text-primary: #111111;
  --color-text-secondary: #555555;
  --color-text-muted: #888888;
  --color-text-inverse: #ffffff;

  /* Surface */
  --color-surface: #ffffff;
  --color-surface-elevated: #f7f7f8;
  --color-border: #e5e5e5;
  --color-border-light: #eeeeee;

  /* Accent */
  --color-accent: #111111;
  --color-accent-subtle: rgba(0, 0, 0, 0.04);

  /* Status */
  --color-success: #1a6b4a;
  --color-success-bg: #e6f4ef;
  --color-warning: #8a6d00;
  --color-warning-bg: #fff8e1;
  --color-danger: #b91c1c;
  --color-danger-bg: #fef2f2;

  /* Chart */
  --color-chart-1: #111111;
  --color-chart-2: #555555;
  --color-chart-3: #999999;

  /* Focus */
  --color-focus: #2563eb;
}

[data-theme='dark'] {
  --color-text-primary: #f0f0f0;
  --color-text-secondary: #a0a0a0;
  --color-text-muted: #666666;
  --color-text-inverse: #111111;

  --color-surface: #1a1a1c;
  --color-surface-elevated: #222224;
  --color-border: #2e2e30;
  --color-border-light: #252527;

  --color-accent: #ffffff;
  --color-accent-subtle: rgba(255, 255, 255, 0.06);

  --color-success: #4ade80;
  --color-success-bg: rgba(74, 222, 128, 0.12);
  --color-warning: #facc15;
  --color-warning-bg: rgba(250, 204, 21, 0.12);
  --color-danger: #f87171;
  --color-danger-bg: rgba(248, 113, 113, 0.12);

  --color-chart-1: #f0f0f0;
  --color-chart-2: #a0a0a0;
  --color-chart-3: #555555;

  --color-focus: #60a5fa;
}
```

### 4.1.2 Type Scale

| Token | Size | Weight | Line Height | Use |
|-------|------|--------|-------------|-----|
| --text-xs | 0.75rem | 500 | 1.4 | Captions, labels, metadata |
| --text-sm | 0.875rem | 400 | 1.5 | Secondary text, nav items |
| --text-base | 1rem | 400 | 1.6 | Body, descriptions |
| --text-lg | 1.125rem | 400 | 1.5 | Card headings |
| --text-xl | 1.5rem | 300 | 1.2 | Page titles |
| --text-2xl | 2rem | 200 | 1.1 | Hero display (overview title) |
| --font-mono | 0.8125rem | 400 | 1.5 | Run IDs, metric values, code |

Tracking: headings use `-0.02em` to `-0.04em`. Body uses `0`.

### 4.1.3 Spacing Scale

| Token | Value | Use |
|-------|-------|-----|
| --space-1 | 4px | Micro gaps (icon-text) |
| --space-2 | 8px | Compact padding |
| --space-3 | 12px | Internal card padding |
| --space-4 | 16px | Form spacing |
| --space-5 | 24px | Card gaps |
| --space-6 | 32px | Section padding |
| --space-7 | 48px | Page margin |
| --space-8 | 64px | Major section breaks |

### 4.1.4 Radii and Shadows

| Token | Value |
|-------|-------|
| --radius-sm | 4px |
| --radius-md | 8px |
| --radius-lg | 12px |
| --shadow-sm | 0 1px 2px rgba(0,0,0,0.04) |
| --shadow-md | 0 4px 12px rgba(0,0,0,0.06) |
| --transition-fast | 150ms ease |

## 4.2 Component Specifications

### 4.2.1 StateCard

States: `loading`, `empty`, `unavailable`, `error`.

| State | Visual |
|-------|--------|
| loading | Skeleton shimmer with pulse animation |
| empty | Centered muted text "No runs yet." with optional icon |
| unavailable | Same as empty but amber-tinted border-left |
| error | Red-tinted card with `ProblemDetail.title` and retry button |

### 4.2.2 LatencyChart

- Library: `Chart.js` + `ng2-charts`.
- Datasets: always p50, p95, p99 when available. Missing percentile shown as dashed interpolated line with `(unavailable)` label.
- Axes: Y-axis labeled "Latency (ms)", X-axis labeled "Run" or "Percentile".
- Tooltip: shows exact value + unit on hover.
- Responsive: maintains aspect ratio, redraws on container resize.

### 4.2.3 PlanExplorer

Tree view with collapsible nodes.

| Node attribute | Display |
|----------------|---------|
| Node type | Uppercase label with monospace font and type-specific icon |
| Actual rows | Right-aligned tabular-nums |
| Plan rows | Right-aligned tabular-nums, slightly muted |
| Rows removed | `(plan - actual)` in muted text |
| Buffer reads | Shown under node with blue micro-indicator |
| Cost | Shown in small text under node |

Raw view toggle: switches to `<pre>` block with monospace font and copy button.

Unknown node types: rendered with `UNKNOWN` label and raw JSON in tooltip.

### 4.2.4 RunDetail

Layout:
- Header: run ID (mono, selectable), state badge, duration, startedAt.
- Metrics grid: 2×2 on desktop, 1 column on mobile.
- Artifacts list: download links with content-type badges.
- Events pane: SSE-driven log with auto-scroll and pause button.

## 4.3 Responsive Behavior

| Breakpoint | Layout |
|------------|--------|
| >= 1024px | Desktop: 2–3 column card grids, full nav |
| 768–1023px | Tablet: 2 column grids, nav wraps |
| < 768px | Mobile: single column, nav collapses to bottom bar or hamburger, charts reflow to full width |

## 4.4 Accessibility

- Skip link: `Skip to main content` as first focusable element.
- Focus: `:focus-visible` outline uses `--color-focus` with 2px offset.
- Color contrast: all text meets WCAG AA (4.5:1 for body, 3:1 for large text). Verified with `@axe-core/playwright`.
- Run state: never communicated by color alone. State badge includes text label.
- Keyboard: catalog launch, run detail navigation, compare form, and artifact download all keyboard-operable.
