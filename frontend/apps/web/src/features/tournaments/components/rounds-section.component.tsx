import type { Team } from '@/domain/team.domain.ts';
import { useRounds } from '@/features/tournaments/hooks/start-rounds-queries.hook.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface RoundsSectionProps {
    tournamentId: string;
    /** Team names for the id → name resolution of the table assignments. */
    teams: readonly Team[];
}

/**
 * The rounds view: one block per round with the table assignments rendered as
 * "Table N: Team A vs Team B". Team names resolve from the teams query; a team that
 * has since disappeared falls back to its raw id so the pairing stays legible.
 */
export function RoundsSection({ tournamentId, teams }: RoundsSectionProps) {
    const { t } = useI18n();
    const roundsQuery = useRounds(tournamentId);

    if (roundsQuery.isPending || roundsQuery.isError) {
        return null;
    }

    const rounds = roundsQuery.data;
    if (rounds.length === 0) {
        return null;
    }

    const teamName = (id: string) => teams.find((team) => team.id === id)?.name ?? id;

    return (
        <section aria-label={t('rounds.section.title')} className="flex flex-col gap-3">
            <h2 className="text-lg font-semibold">{t('rounds.section.title')}</h2>
            {rounds.map((round) => (
                <div
                    key={round.number}
                    className="flex flex-col gap-2 rounded-md border border-border p-4"
                >
                    <h3 className="font-medium">{`${t('rounds.round')} ${round.number}`}</h3>
                    <ul className="flex flex-col gap-1">
                        {round.tables.map((table) => (
                            <li key={table.tableNumber} className="text-sm">
                                <span className="font-medium">
                                    {`${t('rounds.table')} ${table.tableNumber}`}
                                </span>
                                {': '}
                                {teamName(table.homeTeamId)} {t('rounds.versus')}{' '}
                                {teamName(table.awayTeamId)}
                            </li>
                        ))}
                    </ul>
                </div>
            ))}
        </section>
    );
}
