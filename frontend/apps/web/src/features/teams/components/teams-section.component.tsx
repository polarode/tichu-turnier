import { useState } from 'react';
import { Button } from '@tichu/ui/button.tsx';

import type { Team, TeamWriteInput } from '@/domain/team.domain.ts';
import type { TeamFormValues } from '@/features/teams/team-schemas.ts';
import { useCreateTeam, useDeleteTeam, useTeams, useUpdateTeam } from '@/features/teams/hooks/team-queries.hook.ts';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { TeamFormDialog } from '@/features/teams/components/team-form-dialog.component.tsx';
import { AccessLinkDialog } from '@/features/teams/components/access-link-dialog.component.tsx';

interface TeamsSectionProps {
    tournamentId: string;
    /** Team capacity from the tournament; the add button locks when it is reached. */
    maxTeams: number;
    /** Team management only exists during the setup phase. */
    editable: boolean;
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
export function TeamsSection({ tournamentId, maxTeams, editable }: TeamsSectionProps) {
    const { t } = useI18n();
    const teamsQuery = useTeams(tournamentId);
    const createTeam = useCreateTeam(tournamentId);
    const updateTeam = useUpdateTeam(tournamentId);
    const deleteTeam = useDeleteTeam(tournamentId);

    const [dialog, setDialog] = useState<DialogState | null>(null);
    const [confirmDeleteTeam, setConfirmDeleteTeam] = useState<Team | null>(null);
    const [accessLinkTeam, setAccessLinkTeam] = useState<Team | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);

    const teams = teamsQuery.data ?? [];
    const capacityReached = teams.length >= maxTeams;

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

            {teamsQuery.isPending ? (
                <p className="text-muted-foreground">{t('common.loading')}</p>
            ) : teamsQuery.isError ? (
                <p role="alert" className="text-sm text-destructive">
                    {localizedErrorMessage(teamsQuery.error, t)}
                </p>
            ) : teams.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('teams.section.empty')}</p>
            ) : (
                <ul className="flex flex-col gap-2">
                    {teams.map((team) => (
                        <li
                            key={team.id}
                            className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-border p-3"
                        >
                            <div className="flex flex-col gap-0.5">
                                <span className="font-medium">{team.name}</span>
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
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
                    <div
                        role="dialog"
                        aria-modal="true"
                        aria-label={t('teams.delete.title')}
                        className="w-full max-w-sm rounded-md border border-border bg-background p-6 shadow-lg"
                    >
                        <h2 className="mb-2 text-lg font-semibold">{t('teams.delete.title')}</h2>
                        <p className="mb-4 text-sm text-muted-foreground">
                            {t('teams.delete.description')}{' '}
                            <strong className="font-medium text-foreground">
                                {confirmDeleteTeam.name}
                            </strong>
                            ?
                        </p>
                        <div className="flex justify-end gap-2">
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setConfirmDeleteTeam(null)}
                            >
                                {t('common.cancel')}
                            </Button>
                            <Button
                                type="button"
                                variant="destructive"
                                disabled={deleteTeam.isPending}
                                onClick={() => remove(confirmDeleteTeam)}
                            >
                                {t('teams.delete.confirm')}
                            </Button>
                        </div>
                    </div>
                </div>
            ) : null}

            {accessLinkTeam ? (
                <AccessLinkDialog
                    tournamentId={tournamentId}
                    team={accessLinkTeam}
                    onClose={() => setAccessLinkTeam(null)}
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
