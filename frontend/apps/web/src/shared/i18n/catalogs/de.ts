import type { Catalog } from './en.ts';

/** German catalog. Typed as `Catalog`, so a missing or unknown key is a compile error. */
export const de: Catalog = {
    'app.name': 'Tichu Turnier',
    'app.tagline': 'Tichu-Turniere Runde für Runde organisieren.',

    'nav.primary': 'Hauptnavigation',
    'nav.admin': 'Verwaltung',
    'nav.team': 'Team',
    'nav.public': 'Öffentlich',
    'nav.login': 'Anmelden',

    'language.label': 'Sprache',
    'language.de': 'Deutsch',
    'language.en': 'Englisch',

    'common.loading': 'Wird geladen…',
    'common.retry': 'Erneut versuchen',

    'validation.email.required': 'E-Mail ist erforderlich.',
    'validation.email.invalid': 'Bitte eine gültige E-Mail-Adresse eingeben.',
    'validation.password.required': 'Passwort ist erforderlich.',
    'validation.password.min': 'Das Passwort muss mindestens 8 Zeichen lang sein.',

    'error.unknown': 'Etwas ist schiefgelaufen. Bitte erneut versuchen.',
    'error.notFound': 'Nicht gefunden.',
    'error.invalidInput': 'Bitte die Eingaben prüfen.',
    'error.emailAlreadyRegistered': 'Es existiert bereits ein Konto mit dieser E-Mail-Adresse.',
    'error.invalidCredentials': 'E-Mail oder Passwort ist falsch.',
    'error.unauthenticated': 'Bitte anmelden, um fortzufahren.',
    'error.forbidden': 'Du hast keine Berechtigung für diese Aktion.',
    'error.tournamentNotEditable': 'Dieses Turnier kann nicht mehr bearbeitet werden.',
    'error.duplicateTeamName': 'Ein Team mit diesem Namen existiert bereits.',
    'error.duplicatePlayerName': 'Ein Spieler mit diesem Namen spielt bereits in diesem Turnier.',
    'error.duplicateTeamPlayers': 'Die beiden Spieler müssen unterschiedlich sein.',
    'error.teamLimitReached': 'Dieses Turnier hat bereits die maximale Teamanzahl erreicht.',
    'error.maxTeamsBelowTeamCount': 'Das Maximum darf nicht unter der aktuellen Teamanzahl liegen.',

    'auth.email': 'E-Mail',
    'auth.password': 'Passwort',
    'auth.signedInAs': 'Angemeldet als',
    'auth.logout': 'Abmelden',
    'auth.logout.pending': 'Abmeldung läuft…',

    'login.title': 'Anmelden',
    'login.description': 'Als Organisator anmelden oder Team-Link öffnen.',
    'login.submit': 'Anmelden',
    'login.pending': 'Anmeldung läuft…',
    'login.noAccount': 'Noch kein Konto?',
    'login.registerLink': 'Jetzt erstellen',

    'register.title': 'Konto erstellen',
    'register.description': 'Organisatorenkonto erstellen, um Turniere zu verwalten.',
    'register.submit': 'Konto erstellen',
    'register.pending': 'Konto wird erstellt…',
    'register.haveAccount': 'Bereits ein Konto?',
    'register.loginLink': 'Anmelden',

    'admin.title': 'Verwaltungsbereich',
    'admin.description': 'Turniere, Teams und Runden anlegen und verwalten.',

    'dashboard.title': 'Meine Turniere',
    'dashboard.description': 'Die Turniere, die du organisierst.',
    'dashboard.create': 'Neues Turnier',
    'dashboard.open': 'Öffnen',
    'dashboard.teamCount': 'Teams',
    'dashboard.empty.title': 'Noch keine Turniere',
    'dashboard.empty.description': 'Erstelle dein erstes Turnier, um zu starten.',
    'dashboard.error': 'Die Turniere konnten nicht geladen werden.',

    'tournament.new.title': 'Neues Turnier',
    'tournament.new.description': 'Name, Beschreibung und Regeln festlegen.',
    'tournament.detail.title': 'Turnier',
    'tournament.detail.description': 'Teams und Einstellungen verwalten.',

    'tournament.status.setup': 'Vorbereitung',
    'tournament.status.active': 'Läuft',
    'tournament.status.completed': 'Beendet',

    'team.title': 'Teambereich',
    'team.description': 'Verfolge deinen Tisch und trage deine Spielergebnisse ein.',

    'public.title': 'Öffentlicher Bereich',
    'public.description': 'Turniere, Ranglisten und Live-Statistiken ansehen.',
};
