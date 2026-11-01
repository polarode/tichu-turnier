import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Minus, Plus, X } from 'lucide-react';
import { Button } from '@tichu/ui/button.tsx';
import { cn } from '@tichu/ui/lib/utils.ts';

import type { RecordedGame, TeamTable, TichuCall } from '@/domain/game.domain.ts';
import {
    complementBaseScore,
    displayTotals,
    doubleWinTeam,
    emptyFormState,
    fromRecordedGame,
    nextFinishPosition,
    PLAYER_SLOTS,
    teamOfSlot,
    toWriteInput,
    withSlot,
    type PlayerSlot,
} from '@/features/scoring/game-form.model.ts';
import { gameFormSchema, type GameFormValues } from '@/features/scoring/scoring-schemas.ts';
import { useRecordGame } from '@/features/scoring/hooks/scoring-queries.hook.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { FormAlert } from '@/shared/components/form-alert.component.tsx';
import { applyServerError } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/** Card points per team range from −25 to 125 in steps of 5. */
const BASE_SCORE_MIN = -25;
const BASE_SCORE_MAX = 125;
const BASE_SCORE_STEP = 5;

/**
 * `GAME_VALIDATION_INVALID` is field-anchored server-side
 * (`baseScore`/`position`/`call`/`bombs`/`players`/`teams`), but the grid has no
 * single input per anchor, so `applyServerError`'s default routes it to the
 * form-level `root` message. `GAME_CONFIRMED` and `GAME_CONCURRENT_MODIFICATION`
 * are handled explicitly in the submit handler — they change the form's mode
 * (read-only / reload) rather than annotate a field.
 */
interface GameEntryFormProps {
    table: TeamTable;
    gameNumber: number;
    /** The already-entered game for this slot, when editing; absent in create mode. */
    existingGame?: RecordedGame;
    /** Offered after a GAME_ALREADY_EXISTS create collision. */
    onLoadExisting?: () => void;
    /** Invoked when the server reports the game changed underneath us (reload prompt). */
    onReloadRequested?: () => void;
}

/**
 * The v1-style game entry grid (S5c, #101): a per-player finish-cycling button,
 * tri-state Tichu toggle, bomb counter, one base-score slider for team 1 with the
 * complement derived, and a Beschiss toggle. Built on react-hook-form + a zod
 * resolver; the client math is display-only and the form submits raw facts, with
 * the server returning the authoritative outcome (ADR 0004).
 *
 * Read-only has two sources: the round being locked on arrival (`table.locked`),
 * or the server rejecting a write with `GAME_CONFIRMED` because the round locked
 * mid-edit. Error UX is per code (#101): `GAME_VALIDATION_INVALID` → localized
 * form message, `GAME_CONCURRENT_MODIFICATION` → reload prompt, `GAME_CONFIRMED`
 * → the form flips to read-only with an explanation.
 */
export function GameEntryForm({
    table,
    gameNumber,
    existingGame,
    onLoadExisting,
    onReloadRequested,
}: GameEntryFormProps) {
    const { t } = useI18n();
    const recordGame = useRecordGame(table);

    const form = useForm<GameFormValues>({
        resolver: zodResolver(gameFormSchema(t)),
        defaultValues: existingGame ? fromRecordedGame(existingGame, table) : emptyFormState(),
    });

    const state: GameFormValues = form.watch();
    const dw = doubleWinTeam(state.positions);
    const totals = displayTotals(state);
    const team1Base = complementBaseScore(state.baseScoreTeam0, dw !== null);

    // A locked round is read-only on arrival; a GAME_CONFIRMED rejection latches
    // the same read-only mode when the round locks mid-edit.
    const confirmed = form.formState.errors.root?.type === 'confirmed';
    const readOnly = table.locked || confirmed;

    const players = PLAYER_SLOTS.map((slot) => {
        const team = table.teams[teamOfSlot(slot)];
        const player = team.players[slot % 2 === 0 ? 0 : 1];
        return { slot, player, teamName: team.teamName };
    });

    function assignFinish(slot: PlayerSlot) {
        const pos = nextFinishPosition(state.positions);
        if (pos === null) return;
        form.setValue('positions', withSlot(state.positions, slot, pos), { shouldValidate: true });
    }

    function resetFinish(slot: PlayerSlot) {
        form.setValue('positions', withSlot(state.positions, slot, null), { shouldValidate: true });
    }

    function cycleCall(slot: PlayerSlot) {
        const order: TichuCall[] = table.allowGrandTichu
            ? ['NONE', 'SMALL', 'GRAND']
            : ['NONE', 'SMALL'];
        const current = order.indexOf(state.calls[slot]);
        const nextCall = order[(current + 1) % order.length] ?? 'NONE';
        form.setValue('calls', withSlot(state.calls, slot, nextCall));
    }

    // The slider is moot on a double win (fixed 200 : 0) and in read-only mode.
    const baseScoreLocked = readOnly || dw !== null;

    function stepBaseScore(delta: number) {
        const next = Math.min(BASE_SCORE_MAX, Math.max(BASE_SCORE_MIN, state.baseScoreTeam0 + delta));
        form.setValue('baseScoreTeam0', next);
    }

    function cycleBombs(slot: PlayerSlot) {
        form.setValue('bombs', withSlot(state.bombs, slot, (state.bombs[slot] + 1) % 4));
    }

    const submit = form.handleSubmit(async (values) => {
        const input = toWriteInput(values, table, gameNumber, existingGame?.version ?? null);
        try {
            await recordGame.mutateAsync(input);
        } catch (error) {
            if (error instanceof ApiError && error.code === 'GAME_CONFIRMED') {
                // The round locked mid-edit: latch read-only. The read-only banner
                // at the top carries the explanation, so the root error needs no
                // message of its own (avoids a duplicate).
                form.setError('root', { type: 'confirmed' });
                return;
            }
            if (error instanceof ApiError && error.code === 'GAME_CONCURRENT_MODIFICATION') {
                form.setError('root', {
                    type: 'concurrent',
                    message: t('scoring.concurrent.reloadPrompt'),
                });
                return;
            }
            if (error instanceof ApiError && error.code === 'GAME_ALREADY_EXISTS') {
                form.setError('root', { type: 'exists', message: t('error.gameAlreadyExists') });
                return;
            }
            applyServerError(form.setError, error, t);
        }
    });

    const callLabel: Record<TichuCall, string> = {
        NONE: t('scoring.call.none'),
        SMALL: t('scoring.call.small'),
        GRAND: t('scoring.call.grand'),
    };

    const rootError = form.formState.errors.root;
    const positionsError = form.formState.errors.positions?.message;
    const pending = form.formState.isSubmitting || recordGame.isPending;

    return (
        <form onSubmit={submit} noValidate className="flex flex-col gap-4" aria-label={t('scoring.form.title')}>
            {readOnly ? <FormAlert message={t('scoring.locked.explanation')} /> : null}

            {/* One group per team. Phones stack each player's name above three equal
                44px controls; from `sm` up name and controls share one row. */}
            {([0, 1] as const).map((team) => (
                <fieldset key={team} className="flex flex-col gap-3 rounded-md border border-border p-3">
                    <legend className="px-1 text-sm font-semibold">{table.teams[team].teamName}</legend>
                    {players
                        .filter(({ slot }) => teamOfSlot(slot) === team)
                        .map(({ slot, player }) => {
                            const position = state.positions[slot];
                            const bombs = state.bombs[slot];
                            return (
                                <div
                                    key={slot}
                                    className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_repeat(3,6.5rem)] sm:items-center"
                                >
                                    <span className="truncate text-sm font-medium">{player.name}</span>
                                    <div className="grid grid-cols-3 gap-2 sm:contents">
                                        {position === null ? (
                                            <Button
                                                type="button"
                                                size="touch"
                                                variant="outline"
                                                className="px-2"
                                                disabled={readOnly}
                                                aria-label={`${t('scoring.finish.assign')} – ${player.name}`}
                                                onClick={() => assignFinish(slot)}
                                            >
                                                {t('scoring.finish.label')}
                                            </Button>
                                        ) : (
                                            <Button
                                                type="button"
                                                size="touch"
                                                variant="secondary"
                                                className="px-2"
                                                disabled={readOnly}
                                                aria-label={`${t('scoring.finish.reset')} – ${player.name}: ${position}.`}
                                                onClick={() => resetFinish(slot)}
                                            >
                                                <span className="font-semibold">{position}.</span>
                                                <X aria-hidden="true" className="size-4 text-muted-foreground" />
                                            </Button>
                                        )}
                                        <Button
                                            type="button"
                                            size="touch"
                                            variant={state.calls[slot] === 'NONE' ? 'outline' : 'secondary'}
                                            className={cn('px-2', state.calls[slot] === 'NONE' && 'text-muted-foreground')}
                                            disabled={readOnly}
                                            aria-label={`${t('scoring.call.label')} – ${player.name}: ${callLabel[state.calls[slot]]}`}
                                            onClick={() => cycleCall(slot)}
                                        >
                                            {state.calls[slot] === 'NONE'
                                                ? t('scoring.call.prompt')
                                                : callLabel[state.calls[slot]]}
                                        </Button>
                                        <Button
                                            type="button"
                                            size="touch"
                                            variant={bombs === 0 ? 'outline' : 'secondary'}
                                            className="px-2"
                                            disabled={readOnly}
                                            aria-label={`${t('scoring.bombs.label')} – ${player.name}: ${bombs}`}
                                            onClick={() => cycleBombs(slot)}
                                        >
                                            <span aria-hidden="true" className={bombs === 0 ? 'opacity-50' : undefined}>
                                                💣
                                            </span>
                                            <span>{bombs}</span>
                                        </Button>
                                    </div>
                                </div>
                            );
                        })}
                </fieldset>
            ))}

            {positionsError ? (
                <p role="alert" className="text-sm text-destructive">
                    {positionsError}
                </p>
            ) : null}

            <div className="grid grid-cols-2 gap-4 text-center text-sm">
                <div>
                    <p className="text-muted-foreground">{table.teams[0].teamName}</p>
                    <p className="text-lg font-semibold">
                        {dw === null ? state.baseScoreTeam0 : 0}
                        <span className="text-muted-foreground"> → {totals[0]}</span>
                    </p>
                </div>
                <div>
                    <p className="text-muted-foreground">{table.teams[1].teamName}</p>
                    <p className="text-lg font-semibold">
                        {team1Base}
                        <span className="text-muted-foreground"> → {totals[1]}</span>
                    </p>
                </div>
            </div>

            <div className="flex flex-col gap-1">
                <label htmlFor="base-score" className="text-sm font-medium">
                    {t('scoring.baseScore.label')} – {table.teams[0].teamName}
                </label>
                {/* ±5 steppers: dragging a 30-step slider precisely is fiddly on a phone. */}
                <div className="flex items-center gap-2">
                    <Button
                        type="button"
                        size="touch"
                        variant="outline"
                        className="w-11 px-0"
                        disabled={baseScoreLocked || state.baseScoreTeam0 <= BASE_SCORE_MIN}
                        aria-label={t('scoring.baseScore.decrease')}
                        onClick={() => stepBaseScore(-BASE_SCORE_STEP)}
                    >
                        <Minus aria-hidden="true" className="size-4" />
                    </Button>
                    <input
                        id="base-score"
                        type="range"
                        min={BASE_SCORE_MIN}
                        max={BASE_SCORE_MAX}
                        step={BASE_SCORE_STEP}
                        value={state.baseScoreTeam0}
                        disabled={baseScoreLocked}
                        className="h-11 min-w-0 flex-1 accent-primary"
                        onChange={(e) =>
                            form.setValue('baseScoreTeam0', Number(e.target.value))
                        }
                    />
                    <Button
                        type="button"
                        size="touch"
                        variant="outline"
                        className="w-11 px-0"
                        disabled={baseScoreLocked || state.baseScoreTeam0 >= BASE_SCORE_MAX}
                        aria-label={t('scoring.baseScore.increase')}
                        onClick={() => stepBaseScore(BASE_SCORE_STEP)}
                    >
                        <Plus aria-hidden="true" className="size-4" />
                    </Button>
                </div>
                <output htmlFor="base-score" className="text-center text-sm">
                    {state.baseScoreTeam0}
                </output>
            </div>

            <FormAlert message={rootError?.message} />
            {rootError?.type === 'exists' && onLoadExisting ? (
                <Button type="button" variant="outline" onClick={onLoadExisting}>
                    {t('scoring.alreadyExists.load')}
                </Button>
            ) : null}
            {rootError?.type === 'concurrent' && onReloadRequested ? (
                <Button type="button" variant="outline" onClick={onReloadRequested}>
                    {t('scoring.concurrent.reload')}
                </Button>
            ) : null}

            {/* Phones: full-width buttons, primary save on top within thumb reach of the grid. */}
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-between">
                <Button
                    type="button"
                    size="touch"
                    variant={state.beschiss ? 'destructive' : 'outline'}
                    disabled={readOnly}
                    aria-pressed={state.beschiss}
                    onClick={() => form.setValue('beschiss', !state.beschiss)}
                >
                    {state.beschiss ? `${t('scoring.beschiss.label')} ✓` : t('scoring.beschiss.label')}
                </Button>
                <Button type="submit" size="touch" disabled={readOnly || pending} aria-busy={pending}>
                    {pending
                        ? t('scoring.save.saving')
                        : existingGame
                          ? t('scoring.save.update')
                          : t('scoring.save.create')}
                </Button>
            </div>
        </form>
    );
}
