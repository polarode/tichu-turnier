# Frontend — React + Vite + TanStack

A pnpm workspace. The app lives in `apps/web`; shared UI primitives in
`packages/ui`. See the root [`AGENTS.md`](../AGENTS.md) for conventions.

## Run

```bash
pnpm install
pnpm dev          # http://localhost:3000 (expects backend on :8080)
pnpm typecheck
pnpm lint
pnpm test
```

Point the app at a different backend with `VITE_API_URL`:

```bash
VITE_API_URL=http://localhost:9000 pnpm dev
```

## Structure (`apps/web/src`)

```
domain/              # framework-free domain models + write inputs
vocabulary/          # enums / shared value types (status, backend error codes)
auth-api/            # one folder per upstream API: repository + dtos/ + mappers/
admin-api/           #   (admin accounts + invites)
tournament-api/      #   (tournaments, rounds, standings)
team-api/
team-auth-api/       #   (access-link exchange, team session)
scoring-api/         #   (games, confirmations, final ranking)
features/            # feature folders: auth, tournaments, teams, scoring,
                     #   owner-console, changelog (components, hooks, queries)
shared/
  api/               #   shared axios client + typed ApiError (ProblemDetail mapping)
  components/        #   cross-cutting components (AppShell, PagePlaceholder, FormField, LiveBadge)
  constants/         #   centralized query keys
  forms/             #   react-hook-form helpers (server-error routing)
  hooks/             #   cross-cutting hooks (useLiveStream: SSE events -> query invalidation)
  i18n/              #   i18n provider, de/en catalogs, language switch, error messages
  sse/               #   reconnecting SSE client
  storage/           #   browser-storage helpers (e.g. theme preference)
  theme/             #   light/dark/system theme provider and toggle
routes/              # TanStack Router file-based routes (admin, team, public,
                     #   login, register, pending-approval)
router.ts            # QueryClient + router
main.tsx             # entry
test-helpers/        # shared test setup, render-with-providers, renderApp
```

The data layer follows the template's `api client → repository → zod DTO → mapper →
domain` shape. Each `*-api/` folder is the only place that knows its wire DTOs;
forms and hooks pass domain types (including the `*WriteInput` types).

## Areas and routes

The app shell (`shared/components/app-shell.component.tsx`) renders the header,
navigation, the language switch, the theme toggle and the account menu around every
screen. The areas that hang off it:

| Area    | Route       |
| ------- | ----------- |
| Admin   | `/admin`    |
| Team    | `/team`     |
| Public  | `/public`   |
| Login   | `/login`    |
| Register| `/register` |

`/` redirects to the public area, which is still a placeholder.

The **admin area** is guarded: unauthenticated visitors are redirected to `/login`, and
the session is refreshed from the httpOnly cookie on load so a returning admin stays
signed in. `/admin` is the tournament dashboard; `/admin/tournaments/new` and
`/admin/tournaments/$tournamentId` (with its `/edit` child) are the create and detail
entry points; the detail view is tabbed (rounds, standings, progress) and follows the
organizer's live stream while the tournament is active. `/admin/changelog`
shows the organizer changelog. Platform owners also get `/admin/console` (pending
requests, admin management and invite issuance); other admins see the localized
forbidden state there instead. A freshly registered, not-yet-approved admin lands on
`/pending-approval`.

The **team area** is reached through an access link rather than a password: `/team/login`
exchanges the link for a team session, `/team` is the team overview, and `/team/score`
is the scoring form where a team enters and confirms its games. Both team pages follow
the team's live stream until the results are published, so the other team's entries,
confirmations, new rounds and the publication show up without a reload.

Live updates go through `shared/hooks/use-live-stream.hook.ts`: each event invalidates
the queries it affects, every (re)connect invalidates all of them once, and a window
focus refetches as a fallback. There is no interval polling.

## i18n

All user-facing strings live in `shared/i18n/catalogs/`. `en.ts` is the source of
truth for the `TranslationKey` union; `de.ts` is typed as `Catalog`, so a missing
or unknown key is a compile error. Two checks keep catalogs honest:

- `shared/i18n/__tests__/catalog.spec.ts` fails if the catalogs diverge or a key
  is empty.
- The `no-restricted-syntax` rule in `eslint.config.mjs` fails on hardcoded JSX
  text in production sources; strings must come from the catalogs.

## Data flow

```
component → TanStack Query hook → repository → axios client → backend
                                     │
                                     └─ zod parse + DTO→domain mapping
```

- **Server state:** TanStack Query, keyed by `shared/constants/query-keys.ts`.
- **Client/UI state:** Zustand (`shared/stores`), added when the first store is needed.
- **Forms:** react-hook-form + zod resolver.
- **Styling:** Tailwind CSS v4, primitives from `@tichu/ui`.

The TanStack Router route tree (`src/routeTree.gen.ts`) is generated by the Vite
plugin on `dev`/`build`; a copy is committed so a fresh checkout typechecks.
