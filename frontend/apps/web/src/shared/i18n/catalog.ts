import { de } from '@/shared/i18n/catalogs/de.ts';
import { en, type Catalog } from '@/shared/i18n/catalogs/en.ts';
import type { Locale } from '@/shared/i18n/locale.ts';

/** Every catalog the app can switch between, keyed by locale. */
export const CATALOGS: Record<Locale, Catalog> = { de, en };
