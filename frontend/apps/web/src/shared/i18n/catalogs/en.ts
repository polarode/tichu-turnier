/**
 * English catalog. This file is the source of truth for translation keys:
 * `TranslationKey` is derived from it, and every other catalog must cover the
 * exact same keys. `catalog.spec.ts` fails CI if a key is missing from any
 * catalog, or maps to an empty string.
 */
export const en = {
    'app.name': 'Tichu Tournament',
    'app.tagline': 'Run Tichu tournaments round by round.',

    'nav.primary': 'Main navigation',
    'nav.admin': 'Admin',
    'nav.team': 'Team',
    'nav.public': 'Public',
    'nav.login': 'Login',

    'language.label': 'Language',
    'language.de': 'German',
    'language.en': 'English',

    'admin.title': 'Admin area',
    'admin.description': 'Create and manage tournaments, teams and rounds.',

    'team.title': 'Team area',
    'team.description': 'Follow your table and enter your game results.',

    'public.title': 'Public area',
    'public.description': 'Browse tournaments, standings and live statistics.',

    'login.title': 'Login',
    'login.description': 'Sign in as an organizer or open your team link.',
} as const satisfies Record<string, string>;

/** Union of every user-facing string the app can render. */
export type TranslationKey = keyof typeof en;

/** A complete catalog: every translation key mapped to a string. */
export type Catalog = Record<TranslationKey, string>;
