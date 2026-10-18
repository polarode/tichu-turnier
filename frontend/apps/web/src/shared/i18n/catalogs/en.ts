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

    'common.loading': 'Loading…',
    'common.retry': 'Try again',

    'validation.email.required': 'Email is required.',
    'validation.email.invalid': 'Enter a valid email address.',
    'validation.password.required': 'Password is required.',
    'validation.password.min': 'The password must be at least 8 characters long.',

    'error.unknown': 'Something went wrong. Please try again.',
    'error.notFound': 'Not found.',
    'error.invalidInput': 'Please check your input.',
    'error.emailAlreadyRegistered': 'An account with this email already exists.',
    'error.invalidCredentials': 'Email or password is incorrect.',
    'error.unauthenticated': 'Please sign in to continue.',
    'error.forbidden': 'You do not have permission to do that.',
    'error.tournamentNotEditable': 'This tournament can no longer be edited.',
    'error.duplicateTeamName': 'A team with this name already exists.',
    'error.duplicatePlayerName': 'A player with this name already plays in this tournament.',
    'error.duplicateTeamPlayers': 'The two players must be different.',
    'error.teamLimitReached': 'This tournament already has the maximum number of teams.',
    'error.maxTeamsBelowTeamCount': 'The maximum cannot be lower than the current number of teams.',

    'auth.email': 'Email',
    'auth.password': 'Password',
    'auth.signedInAs': 'Signed in as',
    'auth.logout': 'Log out',
    'auth.logout.pending': 'Signing out…',

    'login.title': 'Login',
    'login.description': 'Sign in as an organizer or open your team link.',
    'login.submit': 'Sign in',
    'login.pending': 'Signing in…',
    'login.noAccount': 'No account yet?',
    'login.registerLink': 'Create one',

    'register.title': 'Create account',
    'register.description': 'Create an organizer account to run tournaments.',
    'register.submit': 'Create account',
    'register.pending': 'Creating account…',
    'register.haveAccount': 'Already have an account?',
    'register.loginLink': 'Sign in',

    'admin.title': 'Admin area',
    'admin.description': 'Create and manage tournaments, teams and rounds.',

    'dashboard.title': 'My tournaments',
    'dashboard.description': 'The tournaments you organize.',
    'dashboard.create': 'New tournament',
    'dashboard.open': 'Open',
    'dashboard.teamCount': 'Teams',
    'dashboard.empty.title': 'No tournaments yet',
    'dashboard.empty.description': 'Create your first tournament to get started.',
    'dashboard.error': 'The tournaments could not be loaded.',

    'tournament.new.title': 'New tournament',
    'tournament.new.description': 'Set the name, description and rules.',
    'tournament.detail.title': 'Tournament',
    'tournament.detail.description': 'Manage teams and settings.',

    'tournament.status.setup': 'Setup',
    'tournament.status.active': 'Active',
    'tournament.status.completed': 'Completed',

    'team.title': 'Team area',
    'team.description': 'Follow your table and enter your game results.',

    'public.title': 'Public area',
    'public.description': 'Browse tournaments, standings and live statistics.',
} as const satisfies Record<string, string>;

/** Union of every user-facing string the app can render. */
export type TranslationKey = keyof typeof en;

/** A complete catalog: every translation key mapped to a string. */
export type Catalog = Record<TranslationKey, string>;
