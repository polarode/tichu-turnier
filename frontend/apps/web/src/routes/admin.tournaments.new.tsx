import { createFileRoute, useNavigate } from '@tanstack/react-router';

import { TournamentForm } from '@/features/tournaments/components/tournament-form.component.tsx';
import { useCreateTournament } from '@/features/tournaments/hooks/tournament-queries.hook.ts';
import { tournamentFormToWriteInput } from '@/tournament-api/mappers/tournament.mapper.ts';
import { PageHeader } from '@/shared/components/page-header.component.tsx';

/** The create form; a successful submission lands on the new tournament's detail page. */
function TournamentCreateComponent() {
        const navigate = useNavigate();
        const createTournament = useCreateTournament();

        return (
            <section className="flex flex-col gap-6">
                <PageHeader
                    titleKey="tournament.new.title"
                    descriptionKey="tournament.new.description"
                />
                <TournamentForm
                    submitLabelKey="tournament.new.submit"
                    pendingLabelKey="tournament.new.pending"
                    onSubmit={async (values) => {
                        const tournament = await createTournament.mutateAsync(
                            tournamentFormToWriteInput(values),
                        );
                        await navigate({
                            to: '/admin/tournaments/$tournamentId',
                            params: { tournamentId: tournament.id },
                        });
                    }}
                />
        </section>
    );
}

export const Route = createFileRoute('/admin/tournaments/new')({
    component: TournamentCreateComponent,
});
