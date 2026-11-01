---
name: ui-ux-expert
description: UI/UX review and design reference for the web frontend. Use when auditing a screen or component for accessibility (WCAG 2.1 AA), fixing responsive or mobile layout, designing or extending a `@tichu/ui` component, or improving loading, error, empty-state, form or navigation UX.
---

Adapted from the `ui-ux-expert` agent in jeremylongshore/tons-of-skills-marketplace (fullstack-starter-pack).

Review and build UI so it works for everyone: keyboard users, screen-reader users, phones at the table, and admins on a laptop. Every rule below is a checklist item. When reviewing, walk each section against the code and report findings with the rule, the file:line, and the fix. When building, satisfy every section before calling the component done.

For bad → good code pairs in this repo's stack (icon buttons, form fields, colour-only status, async states, mobile layout, disclosure, live regions, role-based tests), read [EXAMPLES.md](EXAMPLES.md) before writing or fixing markup.

## Project fit

- **Primitives first.** Reach for `@tichu/ui` (`Button`, `Input`, `Textarea`, `Dialog`, `Badge`, `CopyButton`) and the app's shared components (`FormField`, `FormAlert`, `LoadingIndicator` in `shared/components`) before writing raw elements. When a primitive is missing a variant, extend the primitive (via `cva` variants, `cn` from `lib/utils`) rather than restyling at the call site.
- **Styling is Tailwind.** Use the theme tokens already in `packages/ui/src/index.css` (`bg-background`, `text-muted-foreground`, `border-input`, …), never raw hex values.
- **Every visible or announced string goes through `t()`**, including `aria-label`, `alt`, `title` and placeholder text. Add the key to both de and en catalogs.
- **Tests query by role and accessible name** (`getByRole('button', { name: … })`, `getByLabelText`). A component that is hard to query that way is usually inaccessible; fix the markup, not the test.

## Accessibility (WCAG 2.1 AA)

- **Semantic elements.** `button` for actions, `a`/router `Link` for navigation, `header`/`nav`/`main`/`section`/`ul`/`table` for structure. A clickable `div` is a defect.
- **Accessible names.** Every interactive control has a name: visible label, `<label htmlFor>`, or `aria-label` when the visible text is only an icon or a symbol. Decorative icons get `aria-hidden="true"`; meaningful images get `alt`, decorative ones `alt=""`.
- **Keyboard.** Everything reachable and operable with Tab, Enter, Space and Escape, in a logical order. Custom widgets (tabs, comboboxes, menus) follow the WAI-ARIA authoring pattern for their role, including arrow-key handling.
- **Visible focus.** Keep `focus-visible:ring-*`; never remove an outline without a replacement.
- **Contrast.** 4.5:1 for normal text, 3:1 for large text (≥ 24px, or ≥ 18.66px bold) and for UI component boundaries and focus indicators.
- **Colour is never the only signal.** Pair status colour with text, an icon or a badge label.
- **Forms.** Each field has a label; required fields are marked (`aria-required`); errors set `aria-invalid` and link the message via `aria-describedby`; error messages say how to fix the input.
- **Live feedback.** Async results and status changes announce themselves: `role="status"`/`aria-live="polite"` for confirmations, `role="alert"` for errors. Loading buttons set `aria-busy` and are disabled.
- **Dialogs.** `role="dialog"`, `aria-modal`, `aria-labelledby` pointing at the title, focus moved in on open and returned on close, Escape closes. The `@tichu/ui` `Dialog` should provide this; verify rather than re-implement.
- **Zoom.** Text resizes to 200% without loss of content or function; no fixed heights that clip text.
- **No hidden time limits.** Anything that expires gives the user a way to extend it.

## Responsive design

- **Mobile-first.** Base classes target the phone; add `sm:`/`md:`/`lg:` overrides upward. Players enter scores on phones, so player-facing screens must work at 320px wide.
- **Touch targets** at least 44×44px for primary actions on player-facing screens.
- **Fluid layout.** Prefer flex/grid with wrapping over fixed widths; wide tables scroll horizontally inside their container instead of breaking the page.
- **Readable line length** around 50–75 characters (`max-w-prose` / `max-w-[65ch]`) for running text.

## UX patterns

- **Every async view has four states:** loading (skeleton or quiet placeholder), error (message plus retry), empty (explains why it is empty and what to do next), and success. Check all four exist for each TanStack Query consumer.
- **Forms** validate inline after the first blur or submit, keep the user's input on error, disable submit while pending, and confirm success.
- **Destructive actions** ask for confirmation or offer undo.
- **Progressive disclosure.** Show the common path; put rare or advanced options behind a toggle with `aria-expanded`.
- **Navigation** shows where the user is: active link state, `aria-current="page"`, breadcrumbs for deep hierarchies.
- **Visual hierarchy.** One clear primary action per view; consistent spacing on Tailwind's 4px scale; a heading order (`h1` → `h2` → `h3`) that mirrors the visual one.

## Component API consistency

- Shared components follow `Button`'s shape: `variant` and `size` from a `cva` definition, `className` merged via `cn`, and the remaining native props forwarded to the underlying element.
- Variants live in the component's `cva` definition, so call sites pick a variant instead of passing ad-hoc class strings.
- A new primitive gets a spec in `packages/ui/src/__tests__` covering its accessible role, name and keyboard behaviour.

## Reporting a review

Group findings by section above. For each: severity (**blocker** for a WCAG A/AA failure or broken mobile layout, **improvement** otherwise), file:line, the rule it breaks, and the concrete fix. End with what already works well, so it is kept.
