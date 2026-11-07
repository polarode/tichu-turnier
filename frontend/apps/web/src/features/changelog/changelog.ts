import type { ChangelogEntry } from '@/domain/changelog.domain.ts';
import { parseChangelog } from '@/features/changelog/changelog-schema.ts';

import document from './changelog.json';

/** The shipped changelog. Invalid document data fails at module load, not on render. */
export const CHANGELOG: ChangelogEntry[] = parseChangelog(document);
