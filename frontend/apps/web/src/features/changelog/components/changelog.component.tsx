import { Badge, type BadgeProps } from '@tichu/ui/badge.tsx';

import type { ChangelogEntry, ChangeType } from '@/domain/changelog.domain.ts';
import { PageHeader } from '@/shared/components/page-header.component.tsx';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/** The changelog is organizer content in English, so its type badges are not localized. */
const CHANGE_TYPE_LABELS: Record<ChangeType, string> = {
    feature: 'Feature',
    improvement: 'Improvement',
    fix: 'Fix',
};

const CHANGE_TYPE_BADGE_VARIANTS: Record<ChangeType, NonNullable<BadgeProps['variant']>> = {
    feature: 'success',
    improvement: 'secondary',
    fix: 'destructive',
};

/**
 * The admin changelog: a page header and the dated entries, newest first. Each change is
 * a list item led by a badge naming its type. Dates are UTC calendar days, so they are
 * formatted in UTC to keep the day the document names.
 */
export function Changelog({ entries }: { entries: ChangelogEntry[] }) {
    const { t, locale } = useI18n();

    return (
        <section className="flex flex-col gap-8">
            <PageHeader titleKey="changelog.title" descriptionKey="changelog.description" />

            {entries.length === 0 ? (
                <p className="text-muted-foreground">{t('changelog.empty')}</p>
            ) : (
                entries.map((entry, entryIndex) => (
                    <article key={entryIndex} className="flex flex-col gap-2">
                        <h2 className="text-lg font-semibold">
                            {entry.date.toLocaleDateString(locale, { timeZone: 'UTC' })}
                        </h2>
                        <ul className="flex flex-col gap-2">
                            {entry.changes.map((change, changeIndex) => (
                                <li key={changeIndex} className="flex items-start gap-3">
                                    <Badge
                                        variant={CHANGE_TYPE_BADGE_VARIANTS[change.type]}
                                        className="mt-0.5 shrink-0"
                                    >
                                        {CHANGE_TYPE_LABELS[change.type]}
                                    </Badge>
                                    <span>{change.text}</span>
                                </li>
                            ))}
                        </ul>
                    </article>
                ))
            )}
        </section>
    );
}
