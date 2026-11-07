import type { ChangelogEntry } from '@/domain/changelog.domain.ts';
import { PageHeader } from '@/shared/components/page-header.component.tsx';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * The admin changelog: a page header and the dated entries, newest first. Dates are
 * UTC calendar days, so they are formatted in UTC to keep the day the document names.
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
                        <ul className="flex list-disc flex-col gap-1 pl-5">
                            {entry.changes.map((change, changeIndex) => (
                                <li key={changeIndex}>{change}</li>
                            ))}
                        </ul>
                    </article>
                ))
            )}
        </section>
    );
}
