import { createFileRoute, useNavigate } from '@tanstack/react-router';

import { TournamentForm } from '@/features/tournaments/components/tournament-form.component.tsx';
import { useCreateTournament } from '@/features/tournaments/hooks/tournament-queries.hook.ts';
import { tournamentFormToWriteInput } from '@/tournament-api/mappers/tournament.mapper.ts';
import { BackLink } from '@/shared/components/back-link.component.tsx';
import { PageHeader } from '@/shared/components/page-header.component.tsx';

/** The create form; a successful submission lands on the new tournament's detail page. */
function TournamentCreateComponent() {
    const navigate = useNavigate();
    const createTournament = useCreateTournament();

    return (
        <div className="flex flex-col gap-4">
            <BackLink to="/admin" labelKey="dashboard.backLink" />
            <section className="flex flex-col gap-6">
                <PageHeader titleKey="tournament.new.title" descriptionKey="tournament.new.description" />
                <TournamentForm
                    submitLabelKey="tournament.new.submit"
                    pendingLabelKey="tournament.new.pending"
                    onSubmit={async (values) => {
                        const tournament = await createTournament.mutateAsync(tournamentFormToWriteInput(values));
                        await navigate({
                            to: '/admin/tournaments/$tournamentId',
                            params: { tournamentId: tournament.id },
                        });
                    }}
                />
            </section>
        </div>
    );
}

export const Route = createFileRoute('/admin/tournaments/new')({
    component: TournamentCreateComponent,
});
