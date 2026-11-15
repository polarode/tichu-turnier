import { useState } from 'react';
import { Button } from '@tichu/ui/button.tsx';

import type { Team, TeamWriteInput } from '@/domain/team.domain.ts';
import type { TeamFormValues } from '@/features/teams/team-schemas.ts';
import {
    useCreateTeam,
    useDeleteTeam,
    useUndoWithdrawal,
    useUpdateTeam,
} from '@/features/teams/hooks/team-queries.hook.ts';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { TeamFormDialog } from '@/features/teams/components/team-form-dialog.component.tsx';
import { AccessLinkDialog } from '@/features/teams/components/access-link-dialog.component.tsx';
import { WithdrawTeamDialog } from '@/features/teams/components/withdraw-team-dialog.component.tsx';
import { ConfirmDialog } from '@/shared/components/confirm-dialog.component.tsx';
import { WithdrawalBadge } from '@/shared/components/withdrawal-badge.component.tsx';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

interface TeamsSectionProps {
    tournamentId: string;
    /** Team capacity from the tournament; the add button locks when it is reached. */
    maxTeams: number;
    /** Team management only exists during the setup phase. */
    editable: boolean;
    /** Withdrawals are possible once the setup phase is over; undo stops at publishing. */
    tournamentStatus: TournamentStatus;
    /** The tournament's teams, owned by the parent's `useTeams` query (one observer). */
    teams: readonly Team[];
}

interface DialogState {
    mode: 'add' | 'edit';
    team?: Team;
}

/**
 * The team management section of the tournament detail screen: the team list, an add
 * button that locks with a hint once team capacity is reached, and edit/delete row
 * actions during the setup phase. Server problems are localized and anchored to the
 * offending form field by the dialog.
 */
export function TeamsSection({
    tournamentId,
    maxTeams,
    editable,
    tournamentStatus,
    teams,
}: TeamsSectionProps) {
    const { t } = useI18n();
    const createTeam = useCreateTeam(tournamentId);
    const updateTeam = useUpdateTeam(tournamentId);
    const deleteTeam = useDeleteTeam(tournamentId);
    const undoWithdrawal = useUndoWithdrawal(tournamentId);

    const [dialog, setDialog] = useState<DialogState | null>(null);
    const [confirmDeleteTeam, setConfirmDeleteTeam] = useState<Team | null>(null);
    const [accessLinkTeam, setAccessLinkTeam] = useState<Team | null>(null);
    const [withdrawTeam, setWithdrawTeam] = useState<Team | null>(null);
    const [confirmUndoTeam, setConfirmUndoTeam] = useState<Team | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);

    const capacityReached = teams.length >= maxTeams;
    const started = tournamentStatus !== TournamentStatus.SETUP;
    const published = tournamentStatus === TournamentStatus.PUBLISHED;

    const submit = async (values: TeamFormValues) => {
        const input: TeamWriteInput = {
            name: values.name,
            playerOneName: values.playerOneName,
            playerTwoName: values.playerTwoName,
        };
        if (dialog?.mode === 'edit' && dialog.team) {
            await updateTeam.mutateAsync({ teamId: dialog.team.id, input });
        } else {
            await createTeam.mutateAsync(input);
        }
        setDialog(null);
    };

    const remove = async (team: Team) => {
        try {
            await deleteTeam.mutateAsync(team.id);
            setConfirmDeleteTeam(null);
            setActionError(null);
        } catch (error) {
            setActionError(localizedErrorMessage(error, t));
        }
    };

    const undo = async (team: Team) => {
        try {
            await undoWithdrawal.mutateAsync(team.id);
            setActionError(null);
        } catch (error) {
            setActionError(localizedErrorMessage(error, t));
        }
        // The confirmation closes either way; a refusal is reported in the list above it.
        setConfirmUndoTeam(null);
    };

    return (
        <section aria-labelledby="teams-heading" className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <h2 id="teams-heading" className="text-lg font-semibold">
                    {t('teams.section.title')}
                </h2>
                {editable ? (
                    <div className="flex items-center gap-3">
                        {capacityReached ? (
                            <span className="text-sm text-muted-foreground">
                                {t('teams.section.capacityReached')}
                            </span>
                        ) : null}
                        <Button
                            size="sm"
                            disabled={capacityReached}
                            onClick={() => setDialog({ mode: 'add' })}
                        >
                            {t('teams.section.add')}
                        </Button>
                    </div>
                ) : null}
            </div>

            {actionError ? (
                <p role="alert" className="text-sm text-destructive">
                    {actionError}
                </p>
            ) : null}

            {teams.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('teams.section.empty')}</p>
            ) : (
                <ul className="flex flex-col gap-2">
                    {teams.map((team) => (
                        <li
                            key={team.id}
                            className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-border bg-card p-3"
                        >
                            <div className="flex flex-col gap-0.5">
                                <div className="flex flex-wrap items-center gap-2">
                                    <span className="font-medium">{team.name}</span>
                                    {team.withdrawal ? (
                                        <WithdrawalBadge reason={team.withdrawal.reason} />
                                    ) : null}
                                </div>
                                <span className="text-sm text-muted-foreground">
                                    {team.players.map((player) => player.name).join(' & ')}
                                </span>
                            </div>
                            <Button
                                size="sm"
                                variant="outline"
                                onClick={() => setAccessLinkTeam(team)}
                            >
                                {t('teams.accessLink.action')}
                            </Button>
                            {started && !team.withdrawal ? (
                                <Button
                                    size="sm"
                                    variant="outline"
                                    onClick={() => setWithdrawTeam(team)}
                                >
                                    {t('teams.withdraw.action')}
                                </Button>
                            ) : null}
                            {team.withdrawal && !published ? (
                                <Button
                                    size="sm"
                                    variant="outline"
                                    onClick={() => setConfirmUndoTeam(team)}
                                >
                                    {t('teams.withdrawal.undoAction')}
                                </Button>
                            ) : null}
                            {editable ? (
                                <div className="flex items-center gap-2">
                                    <Button
                                        size="sm"
                                        variant="outline"
                                        onClick={() => setDialog({ mode: 'edit', team })}
                                    >
                                        {t('teams.section.edit')}
                                    </Button>
                                    <Button
                                        size="sm"
                                        variant="outline"
                                        onClick={() => setConfirmDeleteTeam(team)}
                                    >
                                        {t('teams.section.delete')}
                                    </Button>
                                </div>
                            ) : null}
                        </li>
                    ))}
                </ul>
            )}

            {confirmDeleteTeam ? (
                <ConfirmDialog
                    titleKey="teams.delete.title"
                    description={
                        <>
                            {t('teams.delete.description')}{' '}
                            <strong className="font-medium text-foreground">
                                {confirmDeleteTeam.name}
                            </strong>
                            ?
                        </>
                    }
                    confirmLabelKey="teams.delete.confirm"
                    destructive
                    disabled={deleteTeam.isPending}
                    onConfirm={() => remove(confirmDeleteTeam)}
                    onCancel={() => setConfirmDeleteTeam(null)}
                />
            ) : null}

            {accessLinkTeam ? (
                <AccessLinkDialog
                    tournamentId={tournamentId}
                    team={accessLinkTeam}
                    onClose={() => setAccessLinkTeam(null)}
                />
            ) : null}

            {withdrawTeam ? (
                <WithdrawTeamDialog
                    tournamentId={tournamentId}
                    team={withdrawTeam}
                    onClose={() => setWithdrawTeam(null)}
                />
            ) : null}

            {confirmUndoTeam ? (
                <ConfirmDialog
                    titleKey="teams.withdrawal.undoTitle"
                    descriptionKey="teams.withdrawal.undoDescription"
                    confirmLabelKey="teams.withdrawal.undoConfirm"
                    destructive={false}
                    disabled={undoWithdrawal.isPending}
                    onConfirm={() => undo(confirmUndoTeam)}
                    onCancel={() => setConfirmUndoTeam(null)}
                />
            ) : null}

            {dialog ? (
                <TeamFormDialog
                    open
                    team={dialog.team}
                    onSubmit={submit}
                    onClose={() => setDialog(null)}
                />
            ) : null}
        </section>
    );
}
