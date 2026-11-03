# Design system

Calm, dense but readable, one accent colour. Colour carries meaning, never decoration. Built on
Tailwind CSS 4 and shadcn/ui (Radix primitives); tokens live in `src/styles/globals.css`.

## Tokens

Components use semantic tokens only, never palette colours such as `bg-blue-500`. Each token has a
light and a dark value (class strategy: `.dark` on `<html>`).

| Token | Use |
|---|---|
| `background`, `foreground` | Page |
| `card`, `popover` | Raised surfaces |
| `muted`, `muted-foreground` | Secondary surfaces and text |
| `accent` | Hover and selected rows |
| `border`, `input`, `ring` | Lines, field borders, focus ring |
| `primary` | The single accent (indigo): primary actions, links, active nav |
| `destructive` | Destructive actions |
| `success` · `warning` · `info` · `danger` · `neutral` (+ `-subtle`) | Status meaning; `-subtle` is the badge background |
| `sidebar*` | Navigation rail |
| `chart-1…5` | Chart series, in order |

## Typography

- Inter Variable (self-hosted via `@fontsource-variable`, no CDN). Body 14px, small/table 13px, headings 16/20/24px, `font-semibold`, slight negative tracking on titles.
- JetBrains Mono for request numbers, IDs, spec values and codes.
- `tabular-nums` for numbers in tables and counters.

## Spacing, radius, elevation

- 4px scale. Page gutter 16px (mobile) / 24–32px (desktop), content max width 1280px.
- Radius `--radius` = 8px: cards and dialogs `rounded-lg` (8px), controls `rounded-md` (6px), badges `rounded-full`.
- 1px borders separate surfaces; shadows only on overlays (menus, dialogs, toasts).

## Status vocabulary

A status is always icon + label, never colour alone.

| Request status | Tone | Icon |
|---|---|---|
| Draft | neutral | `FilePen` |
| Pending approval | warning | `Clock` |
| Approved | info | `CheckCircle` |
| Provisioning | info | `Loader` (spins unless reduced motion) |
| Provisioned | success | `CircleCheckBig` |
| Rejected | danger | `XCircle` |
| Provisioning failed | danger | `AlertTriangle` |
| Cancelled | neutral, muted | `Ban` |

Job statuses use the same tones: Queued neutral, Running / Awaiting provider info, Succeeded success,
Failed danger. Environments: Development and Staging neutral, **Production** warning outline, so it
stands out during review.

## Components

| Component | Location | Notes |
|---|---|---|
| shadcn primitives | `components/ui` | Vendored, lightly adapted (toaster uses our theme) |
| `PageHeader` | `components/shared` | Title (also sets `document.title`), description, actions |
| `FormField` | `components/shared` | Label + control + hint + error wired with `aria-describedby` / `aria-invalid` |
| `FormAlert` | `components/shared` | Form-level error or success |
| `EmptyState`, `ErrorState` | `components/feedback` | Explain the situation and offer the next action |
| `ConfirmDialog` | `components/feedback` | Stays open and disabled while the mutation runs (no double submit) |
| `PageSkeleton`, `TableSkeleton`, `FullPageLoader` | `components/feedback` | Loading states |
| `RouteErrorBoundary` | `components/feedback` | Render and chunk-load failures |

## Accessibility

- Radix handles focus traps, roving focus and ARIA for menus, dialogs, selects and tooltips.
- Visible focus rings (`ring` token); skip link to `#main`; landmarks `nav` and `main`; one `h1` per page.
- Every input has a label; errors are announced through `aria-describedby`, and form alerts use `role="alert"`.
- `prefers-reduced-motion` disables animation globally.
- Icons are `aria-hidden`; icon-only buttons carry `aria-label`.

## Layout

- Desktop: 240px sidebar, collapsible to a 56px icon rail (remembered per browser, labels move to tooltips).
- Below `md`: the sidebar becomes a sheet opened from the top bar.
- Top bar: navigation toggle, breadcrumbs, user menu (profile, theme, sign out).
