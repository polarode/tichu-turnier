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
    'common.yes': 'Yes',
    'common.no': 'No',
    'common.retry': 'Try again',

    'validation.email.required': 'Email is required.',
    'validation.email.invalid': 'Enter a valid email address.',
    'validation.password.required': 'Password is required.',
    'validation.password.min': 'The password must be at least 8 characters long.',

    'validation.tournamentName.required': 'The name is required.',
    'validation.tournamentName.max': 'The name may be at most 100 characters long.',
    'validation.tournamentDescription.max':
        'The description may be at most 500 characters long.',
    'validation.tournamentMaxTeams.required': 'The max team count is required.',
    'validation.tournamentMaxTeams.integer': 'The max team count must be a whole number.',
    'validation.tournamentMaxTeams.min': 'The tournament needs at least 2 teams.',
    'validation.tournamentMaxTeams.max': 'The max team count may be at most 64.',

    'error.unknown': 'Something went wrong. Please try again.',
    'error.notFound': 'Not found.',
    'error.invalidInput': 'Please check your input.',
    'error.emailAlreadyRegistered': 'An account with this email already exists.',
    'error.invalidCredentials': 'Email or password is incorrect.',
    'error.accountPending': 'This account is still awaiting approval.',
    'error.accountDisabled': 'This account has been disabled.',
    'error.unauthenticated': 'Please sign in to continue.',
    'error.forbidden': 'You do not have permission to do that.',
    'error.tournamentNotEditable': 'This tournament can no longer be edited.',
    'error.duplicateTeamName': 'A team with this name already exists.',
    'error.duplicatePlayerName': 'A player with this name already plays in this tournament.',
    'error.duplicateTeamPlayers': 'The two players must be different.',
    'error.teamLimitReached': 'This tournament already has the maximum number of teams.',
    'error.maxTeamsBelowTeamCount': 'The maximum cannot be lower than the current number of teams.',
    'error.adminStatusConflict': 'The account is not in a state that allows this action.',
    'error.inviteInvalid': 'This invite link is not valid. Please request a new one.',
    'error.inviteExpired': 'This invite link has expired. Please request a new one.',
    'error.inviteEmailMismatch': 'This invite was issued for a different email address.',
    'error.inviteStatusConflict': 'This invite can no longer be changed.',

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
    'register.inviteNotice':
        'You have been invited. Complete registration to activate your account immediately.',
    'register.signInFailed': 'Your account is active. Please sign in.',
    'register.submit': 'Create account',
    'register.pending': 'Creating account…',
    'register.haveAccount': 'Already have an account?',
    'register.loginLink': 'Sign in',

    'pendingApproval.title': 'Awaiting approval',
    'pendingApproval.description':
        'Your account has been created. An owner will review it shortly; you can sign in once it is approved.',
    'pendingApproval.loginLink': 'Back to sign in',

    'admin.title': 'Admin area',
    'admin.description': 'Create and manage tournaments, teams and rounds.',

    'ownerConsole.link': 'Owner console',
    'ownerConsole.title': 'Owner console',
    'ownerConsole.description': 'Approve organizers, manage admins and issue invites.',
    'ownerConsole.error': 'The owner console could not be loaded.',
    'ownerConsole.forbidden.title': 'Not available',
    'ownerConsole.forbidden.description': 'This area is only available to platform owners.',

    'ownerConsole.pending.title': 'Pending requests',
    'ownerConsole.pending.description': 'Accounts waiting for your approval.',
    'ownerConsole.pending.empty': 'No pending requests.',
    'ownerConsole.pending.approve': 'Approve',
    'ownerConsole.pending.reject': 'Reject',

    'ownerConsole.admins.title': 'Admins',
    'ownerConsole.admins.description': 'Disable or reactivate existing organizers.',
    'ownerConsole.admins.empty': 'No other admins yet.',
    'ownerConsole.admins.disable': 'Disable',
    'ownerConsole.admins.reactivate': 'Reactivate',

    'ownerConsole.invites.title': 'Invites',
    'ownerConsole.invites.description': 'Invite an organizer by email. The link is shown once.',
    'ownerConsole.invites.email': 'Email',
    'ownerConsole.invites.issue': 'Create invite',
    'ownerConsole.invites.issuing': 'Creating…',
    'ownerConsole.invites.issued.title': 'Invite link',
    'ownerConsole.invites.issued.description': 'Copy this link now — it is shown only once.',
    'ownerConsole.invites.copy': 'Copy link',
    'ownerConsole.invites.copied': 'Copied',
    'ownerConsole.invites.revoke': 'Revoke',
    'ownerConsole.invites.empty': 'No invites issued yet.',
    'ownerConsole.invites.expires': 'Expires',

    'ownerConsole.status.pending': 'Pending',
    'ownerConsole.status.active': 'Active',
    'ownerConsole.status.disabled': 'Disabled',
    'ownerConsole.role.owner': 'Owner',
    'ownerConsole.role.admin': 'Admin',
    'ownerConsole.inviteStatus.pending': 'Pending',
    'ownerConsole.inviteStatus.spent': 'Spent',
    'ownerConsole.inviteStatus.revoked': 'Revoked',

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
    'tournament.new.submit': 'Create tournament',
    'tournament.new.pending': 'Creating tournament…',
    'tournament.edit.title': 'Edit tournament',
    'tournament.edit.description': 'Adjust the name, description and rules.',
    'tournament.edit.submit': 'Save changes',
    'tournament.edit.pending': 'Saving changes…',
    'tournament.detail.title': 'Tournament',
    'tournament.detail.description': 'Manage teams and settings.',
    'tournament.detail.edit': 'Edit',
    'tournament.form.name': 'Name',
    'tournament.form.description': 'Description',
    'tournament.form.maxTeams': 'Max team count',
    'tournament.form.allowGrandTichu': 'Allow grand Tichu',

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
