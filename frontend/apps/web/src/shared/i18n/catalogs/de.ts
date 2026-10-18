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

    'admin.title': 'Verwaltungsbereich',
    'admin.description': 'Turniere, Teams und Runden anlegen und verwalten.',

    'team.title': 'Teambereich',
    'team.description': 'Verfolge deinen Tisch und trage deine Spielergebnisse ein.',

    'public.title': 'Öffentlicher Bereich',
    'public.description': 'Turniere, Ranglisten und Live-Statistiken ansehen.',

    'login.title': 'Anmelden',
    'login.description': 'Als Organisator anmelden oder Team-Link öffnen.',
};
