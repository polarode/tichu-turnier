import { useState } from 'react';
import { Button } from '@tichu/ui/button.tsx';

import type { Team } from '@/domain/team.domain.ts';
import type { Tournament } from '@/domain/tournament.domain.ts';
import { useStartTournament } from '@/features/tournaments/hooks/start-rounds-queries.hook.ts';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

interface StartTournamentButtonProps {
    tournament: Tournament;
    teams: readonly Team[];
}

/**
 * The organizer's start action. Guards are enforced where the client can know them —
 * the button locks with a hint while there are fewer than two teams or an odd team
 * count — and anything only the server can judge (status conflicts, pairing limits)
 * surfaces as the localized typed error. Starting is confirmed explicitly, mirroring
 * the other destructive-ish setup actions.
 */
export function StartTournamentButton({ tournament, teams }: StartTournamentButtonProps) {
    const { t } = useI18n();
    const startTournament = useStartTournament();
    const [confirming, setConfirming] = useState(false);
    const [actionError, setActionError] = useState<string | null>(null);

    if (tournament.status !== TournamentStatus.SETUP) {
        return null;
    }

    const oddTeamCount = teams.length % 2 !== 0;
    const blocked = teams.length < 2 || oddTeamCount;
    const hintKey = teams.length < 2
        ? 'tournament.start.hintInsufficientTeams'
        : 'tournament.start.hintOddTeamCount';

    const start = async () => {
        try {
            await startTournament.mutateAsync(tournament.id);
            setConfirming(false);
            setActionError(null);
        } catch (error) {
            setConfirming(false);
            setActionError(localizedErrorMessage(error, t));
        }
    };

    return (
        <div className="flex flex-col items-end gap-1">
            <div className="flex items-center gap-3">
                {blocked ? (
                    <span className="text-sm text-muted-foreground">{t(hintKey)}</span>
                ) : null}
                <Button size="sm" disabled={blocked} onClick={() => setConfirming(true)}>
                    {t('tournament.start.action')}
                </Button>
            </div>
            {confirming ? (
                <div className="flex items-center gap-2">
                    <span className="text-sm text-muted-foreground">
                        {t('tournament.start.confirmDescription')}
                    </span>
                    <Button size="sm" onClick={start} disabled={startTournament.isPending}>
                        {t('common.confirm')}
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => setConfirming(false)}>
                        {t('common.cancel')}
                    </Button>
                </div>
            ) : null}
            {actionError ? (
                <p role="alert" className="text-sm text-destructive">
                    {actionError}
                </p>
            ) : null}
        </div>
    );
}
