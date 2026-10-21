import { useState } from 'react';
import { createFileRoute, useNavigate } from '@tanstack/react-router';

import { TournamentForm } from '@/features/tournaments/components/tournament-form.component.tsx';
import { useTournament, useUpdateTournament } from '@/features/tournaments/hooks/tournament-queries.hook.ts';
import { tournamentFormToWriteInput } from '@/tournament-api/mappers/tournament.mapper.ts';
import { PageHeader } from '@/shared/components/page-header.component.tsx';
import { ApiError } from '@/shared/api/api-error.ts';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

/**
 * The edit form. Editing is only allowed while the tournament is in setup: the route
 * renders the localized conflict message instead of the form otherwise, so a stale
 * link can never open an editable view of a running or finished tournament.
 */
function TournamentEditComponent() {
    const { t } = useI18n();
    const navigate = useNavigate();
    const { tournamentId } = Route.useParams();
    const tournamentQuery = useTournament(tournamentId);
    const updateTournament = useUpdateTournament();
    // Set when the server itself rejects the update with TOURNAMENT_NOT_EDITABLE
    // (the status flipped between load and submit); demotes the form to the same
    // route-level conflict block the stale-link path uses.
    const [blocked, setBlocked] = useState(false);

    if (tournamentQuery.isPending) {
        return (
            <p className="text-muted-foreground">{t('common.loading')}</p>
        );
    }
    if (tournamentQuery.isError) {
        return (
            <p role="alert" className="text-sm text-destructive">
                {localizedErrorMessage(tournamentQuery.error, t)}
            </p>
        );
    }

    const tournament = tournamentQuery.data;
    const notEditable = blocked || tournament.status !== TournamentStatus.SETUP;

    return (
        <section className="flex flex-col gap-6">
            <PageHeader
                titleKey="tournament.edit.title"
                descriptionKey="tournament.edit.description"
            />
            {notEditable ? (
                <p role="alert" className="text-sm text-destructive">
                    {t('error.tournamentNotEditable')}
                </p>
            ) : (
                <TournamentForm
                    defaultValues={{
                        name: tournament.name,
                        description: tournament.description,
                        maxTeams: tournament.maxTeams,
                        allowGrandTichu: tournament.settings.allowGrandTichu,
                    }}
                    submitLabelKey="tournament.edit.submit"
                    pendingLabelKey="tournament.edit.pending"
                    onSubmit={async (values) => {
                        try {
                            await updateTournament.mutateAsync({
                                id: tournament.id,
                                input: tournamentFormToWriteInput(values),
                            });
                        } catch (error) {
                            if (error instanceof ApiError && error.code === 'TOURNAMENT_NOT_EDITABLE') {
                                setBlocked(true);
                                return;
                            }
                            throw error;
                        }
                        await navigate({
                            to: '/admin/tournaments/$tournamentId',
                            params: { tournamentId: tournament.id },
                        });
                    }}
                />
            )}
        </section>
    );
}

export const Route = createFileRoute('/admin/tournaments/$tournamentId_/edit')({
    component: TournamentEditComponent,
});
