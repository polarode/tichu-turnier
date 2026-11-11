import { zodResolver } from '@hookform/resolvers/zod';
import { type CSSProperties, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { CircleHelp, Minus, Plus, X } from 'lucide-react';
import { Button } from '@tichu/ui/button.tsx';
import { Popover, PopoverContent, PopoverTrigger } from '@tichu/ui/popover.tsx';
import { cn } from '@tichu/ui/lib/utils.ts';

import {
    type GameWriteInput,
    isFrozen,
    type Matchup,
    type RecordedGame,
    seatOf,
    type TeamTable,
    type TichuCall,
} from '@/domain/game.domain.ts';
import {
    type BonusSource,
    doubleWinTeam,
    gameWinner,
    emptyFormState,
    fromRecordedGame,
    nextFinishPosition,
    PLAYER_SLOTS,
    teamOfSlot,
    teamPoints,
    toWriteInput,
    victoryPoints,
    withSlot,
    type PlayerSlot,
} from '@/features/scoring/game-form.model.ts';
import { gameFormSchema, type GameFormValues } from '@/features/scoring/scoring-schemas.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { seatTone } from '@/shared/constants/seat-tones.ts';
import { FormAlert } from '@/shared/components/form-alert.component.tsx';
import { applyServerError } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { fillPlaceholders } from '@/shared/i18n/fill-placeholders.ts';

/** Card points per team range from −25 to 125 in steps of 5. */
const BASE_SCORE_MIN = -25;
const BASE_SCORE_MAX = 125;
const BASE_SCORE_STEP = 5;

/** A points figure with a typographic minus when negative: `50`, `−25`. */
function formatPoints(points: number): string {
    return points < 0 ? `\u2212${Math.abs(points)}` : `${points}`;
}

/** A signed points figure for the pill and the breakdown: `+100`, `−200`, and `+0` for zero. */
function formatSigned(points: number): string {
    return points < 0 ? formatPoints(points) : `+${points}`;
}

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
    /** The selected game's two playing teams, in seat order: slots 0–1 and 2–3. */
    matchup: Matchup;
    gameNumber: number;
    /**
     * Writes the game and resolves with the stored version: the team's create or
     * update, or an organizer's correction (ADR 0010). Rejections surface as ApiError.
     */
    save: (input: GameWriteInput) => Promise<RecordedGame>;
    /**
     * The stored game for this slot as the server last returned it (polled);
     * absent while nobody entered it. The form keeps the version it loaded, so a
     * newer one arriving mid-edit never overwrites unsaved input.
     */
    existingGame?: RecordedGame;
    /**
     * Reloads this game from the server and resolves with the stored version (if
     * any). Offered after a GAME_ALREADY_EXISTS collision and after a
     * GAME_CONCURRENT_MODIFICATION (the game changed underneath us).
     */
    onReload?: () => Promise<RecordedGame | undefined>;
    /** Invoked after the server accepted the write. */
    onSaved?: () => void;
    /** Invoked when a write is rejected because the table froze, so the table state can reload. */
    onFrozen?: () => void;
    /**
     * The tournament is no longer ACTIVE (S7c, #116): every write is rejected
     * server-side, so the form renders read-only from the start.
     */
    tournamentLocked?: boolean;
    /**
     * An organizer's correction of a completed table (ADR 0010). The table's freeze
     * does not apply: the correction is the one write a completed table accepts.
     */
    correcting?: boolean;
}

/**
 * The v1-style game entry grid (S5c, #101) for one game's [matchup]: a per-player
 * finish-cycling button, tri-state Tichu toggle, bomb counter, one base-score
 * slider for team 1 with the complement derived, and a Beschiss toggle. Each team
 * keeps its seat colour. Built on react-hook-form + a zod resolver; the client
 * math is display-only and the form submits raw facts, with the server returning
 * the authoritative outcome (ADR 0004).
 *
 * Read-only has three sources: the table being frozen (someone confirmed, ADR
 * 0006; the page explains who), the server rejecting a write with
 * `GAME_CONFIRMED` because the table froze mid-edit, or the tournament no longer
 * being ACTIVE (`tournamentLocked`, S7c). Error UX is per code (#101):
 * `GAME_VALIDATION_INVALID` → localized form message, `GAME_CONCURRENT_MODIFICATION`
 * → reload prompt, `GAME_CONFIRMED` → the form flips to read-only.
 *
 * The form edits the game version it [loaded] and saves against it. When a newer
 * version arrives (the games poll, #106), an untouched form takes it over quietly;
 * a form with unsaved input keeps it and offers the other team's version instead.
 */
export function GameEntryForm({
    table,
    matchup,
    gameNumber,
    save,
    existingGame,
    onReload,
    onSaved,
    onFrozen,
    tournamentLocked = false,
    correcting = false,
}: GameEntryFormProps) {
    const { t } = useI18n();

    // The stored version the form shows and saves against (null-version create when absent).
    const [loaded, setLoaded] = useState<RecordedGame | undefined>(existingGame);
    const valuesOf = (game: RecordedGame | undefined) =>
        game ? fromRecordedGame(game, matchup) : emptyFormState();

    const form = useForm<GameFormValues>({
        resolver: zodResolver(gameFormSchema(t)),
        defaultValues: valuesOf(existingGame),
    });

    const state: GameFormValues = form.watch();
    // Compared by value, not `formState.isDirty`: the grid writes through setValue,
    // which leaves react-hook-form's dirty tracking untouched.
    const dirty = JSON.stringify(state) !== JSON.stringify(valuesOf(loaded));
    // A stored version newer than the loaded one. Strictly newer: right after a save
    // the cached games still hold the older version until their refetch lands.
    const newer =
        existingGame && existingGame.version > (loaded?.version ?? 0) ? existingGame : undefined;

    function load(game: RecordedGame) {
        form.reset(fromRecordedGame(game, matchup));
        setLoaded(game);
    }

    async function reload() {
        const latest = await onReload?.();
        if (latest) load(latest);
    }

    // A newer stored version arrived (the games poll): an untouched form takes it over.
    useEffect(() => {
        if (newer && !dirty) load(newer);
        // Only a version change re-decides; `dirty` alone must not reload mid-edit.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [existingGame?.version]);
    const dw = doubleWinTeam(state.positions);
    const points = teamPoints(state, table);
    const totals: [number, number] = [points[0].total, points[1].total];
    const winner = gameWinner(state, totals);
    const vp = victoryPoints(state, totals);

    // A frozen table is read-only on arrival; a GAME_CONFIRMED rejection latches
    // the same read-only mode when the table freezes mid-edit.
    const frozen = !correcting && isFrozen(table.confirmation);
    const confirmed = form.formState.errors.root?.type === 'confirmed';
    const readOnly = frozen || confirmed || tournamentLocked;

    const players = PLAYER_SLOTS.map((slot) => {
        const team = matchup[teamOfSlot(slot)];
        const player = team.players[slot % 2 === 0 ? 0 : 1];
        return { slot, player, teamName: team.teamName };
    });

    function nameOf(slot: PlayerSlot): string {
        return players.find((entry) => entry.slot === slot)?.player.name ?? '';
    }

    /** One line of the bonus breakdown, e.g. "Tichu Alice: succeeded" or "Double win". */
    function describeBonus(source: BonusSource): string {
        if (source.kind === 'DOUBLE_WIN') return t('scoring.bonus.doubleWin');
        const label = fillPlaceholders(
            t(
                source.kind === 'SMALL_TICHU'
                    ? 'scoring.bonus.smallTichu'
                    : 'scoring.bonus.grandTichu',
            ),
            { name: nameOf(source.slot) },
        );
        return `${label}: ${t(source.success ? 'scoring.bonus.succeeded' : 'scoring.bonus.failed')}`;
    }

    function bonusLabel(team: 0 | 1): string {
        return fillPlaceholders(t('scoring.bonus.explain'), { team: matchup[team].teamName });
    }

    function assignFinish(slot: PlayerSlot) {
        const pos = nextFinishPosition(state.positions);
        if (pos === null) return;
        form.setValue('positions', withSlot(state.positions, slot, pos), { shouldValidate: true });
    }

    function resetFinish(slot: PlayerSlot) {
        form.setValue('positions', withSlot(state.positions, slot, null), { shouldValidate: true });
    }

    function cycleCall(slot: PlayerSlot) {
        const order: TichuCall[] = ['NONE', 'SMALL', 'GRAND'];
        const current = order.indexOf(state.calls[slot]);
        const nextCall = order[(current + 1) % order.length] ?? 'NONE';
        form.setValue('calls', withSlot(state.calls, slot, nextCall));
    }

    // The slider is moot on a double win (base fixed at 0 : 0) and in read-only mode.
    const baseScoreLocked = readOnly || dw !== null;

    function stepBaseScore(delta: number) {
        const next = Math.min(
            BASE_SCORE_MAX,
            Math.max(BASE_SCORE_MIN, state.baseScoreTeam0 + delta),
        );
        form.setValue('baseScoreTeam0', next);
    }

    function cycleBombs(slot: PlayerSlot) {
        form.setValue('bombs', withSlot(state.bombs, slot, (state.bombs[slot] + 1) % 4));
    }

    const submit = form.handleSubmit(async (values) => {
        const input = toWriteInput(values, matchup, gameNumber, loaded?.version ?? null);
        try {
            const recorded = await save(input);
            // The saved game is the new baseline, so its own refetch is no change from elsewhere.
            load(recorded);
            onSaved?.();
        } catch (error) {
            if (error instanceof ApiError && error.code === 'GAME_CONFIRMED') {
                // The table froze mid-edit: latch read-only and reload the table
                // state so the page can say who confirmed.
                form.setError('root', { type: 'confirmed', message: t('error.gameConfirmed') });
                onFrozen?.();
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

    // Each team keeps its seat colour across games (A Jade, B Star, C Pagoda).
    const tones = [
        seatTone(seatOf(table, matchup[0].teamId)),
        seatTone(seatOf(table, matchup[1].teamId)),
    ] as const;

    const rootError = form.formState.errors.root;
    const positionsError = form.formState.errors.positions?.message;
    const pending = form.formState.isSubmitting;

    return (
        <form
            onSubmit={submit}
            noValidate
            className="flex flex-col gap-4"
            aria-label={t('scoring.form.title')}
        >
            {newer && !readOnly ? (
                <div className="flex flex-col gap-2 rounded-md bg-muted px-3 py-2 text-sm sm:flex-row sm:items-center sm:justify-between">
                    {/* At a completed table the other writer can only be another organizer. */}
                    <p>
                        {t(correcting ? 'correction.changedElsewhere' : 'scoring.changedElsewhere')}
                    </p>
                    <Button type="button" variant="outline" size="sm" onClick={() => load(newer)}>
                        {t(
                            correcting
                                ? 'correction.changedElsewhere.load'
                                : 'scoring.changedElsewhere.load',
                        )}
                    </Button>
                </div>
            ) : null}

            {/* One group per team. Phones stack each player's name above three equal
                44px controls; from `sm` up name and controls share one row. The team's
                card points sit in a pill on the box's top border, opposite its name. When
                the team has bonus sources, the pill adds their signed sum in muted type and
                a (?) button that opens the breakdown of each source. Once the result is
                complete, the pill ends with the team's victory points (`· 2 SP`, with an
                sr-only "Siegpunkte"). */}
            {([0, 1] as const).map((team) => (
                <fieldset
                    key={team}
                    className="relative flex flex-col gap-3 rounded-md border border-border bg-card p-3"
                >
                    <legend
                        className={cn(
                            // Leaves room for the pill opposite on the border; wider with the VP suffix.
                            vp === null ? 'max-w-[calc(100%-10rem)]' : 'max-w-[calc(100%-13rem)]',
                            'truncate rounded-full px-2.5 py-0.5 text-sm font-semibold',
                            tones[team].chip,
                        )}
                    >
                        {matchup[team].teamName}
                    </legend>
                    {/* The padding box starts below the 24px legend: -top-6 lines the
                        pill up with it, and py-px plus the border keeps the same height. */}
                    <p className="absolute -top-6 right-3 rounded-full border border-border bg-card px-2.5 py-px text-sm font-semibold">
                        {winner === team ? (
                            <>
                                <span aria-hidden="true">🏆 </span>
                                <span className="sr-only">{`${t('scoring.winner')}: `}</span>
                            </>
                        ) : null}
                        {formatPoints(points[team].base)}
                        {points[team].bonuses.length > 0 ? (
                            <>
                                <span className="text-muted-foreground">
                                    {` ${formatSigned(points[team].bonus)}`}
                                </span>
                                <Popover>
                                    <PopoverTrigger
                                        aria-label={bonusLabel(team)}
                                        className="relative -my-1 ml-0.5 inline-flex size-6 items-center justify-center rounded-full align-middle text-muted-foreground hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none after:absolute after:-inset-2.5 after:content-['']"
                                    >
                                        <CircleHelp aria-hidden="true" className="size-3.5" />
                                    </PopoverTrigger>
                                    <PopoverContent
                                        aria-label={bonusLabel(team)}
                                        align="end"
                                        className="w-auto max-w-64 p-3 text-sm"
                                    >
                                        <ul className="flex flex-col gap-1">
                                            {points[team].bonuses.map((source, index) => (
                                                <li
                                                    key={index}
                                                    className="flex justify-between gap-4"
                                                >
                                                    <span>{describeBonus(source)}</span>
                                                    <span className="font-semibold tabular-nums">
                                                        {formatSigned(source.points)}
                                                    </span>
                                                </li>
                                            ))}
                                        </ul>
                                    </PopoverContent>
                                </Popover>
                            </>
                        ) : null}
                        {vp !== null ? (
                            <>
                                <span aria-hidden="true" className="text-muted-foreground">
                                    {' · '}
                                </span>
                                <span aria-hidden="true">
                                    <span className="font-semibold">{vp[team]}</span>{' '}
                                    <span className="text-muted-foreground">
                                        {t('standings.victoryPoints')}
                                    </span>
                                </span>
                                <span className="sr-only">
                                    {`${vp[team]} ${t('standings.victoryPoints.long')}`}
                                </span>
                            </>
                        ) : null}
                    </p>
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
                                    <span className="truncate text-sm font-medium">
                                        {player.name}
                                    </span>
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
                                                <X
                                                    aria-hidden="true"
                                                    className="size-4 text-muted-foreground"
                                                />
                                            </Button>
                                        )}
                                        <Button
                                            type="button"
                                            size="touch"
                                            variant={
                                                state.calls[slot] === 'NONE'
                                                    ? 'outline'
                                                    : 'secondary'
                                            }
                                            className={cn(
                                                'px-2',
                                                state.calls[slot] === 'NONE' &&
                                                    'text-muted-foreground',
                                            )}
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
                                            <span
                                                aria-hidden="true"
                                                className={bombs === 0 ? 'opacity-50' : undefined}
                                            >
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

            <div className="flex flex-col gap-1">
                {/* A scoreboard right above the slider: each team's card points in its seat
                    colour on the side of the track that is theirs, the label centred between
                    them. The slider's aria-valuetext announces the same split, so the team
                    cells are hidden from assistive tech. */}
                <div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-3">
                    {([0, 1] as const).map((team) => (
                        <div
                            key={team}
                            aria-hidden="true"
                            className={cn(
                                'flex min-w-0 items-center gap-2',
                                team === 1 && 'order-last flex-row-reverse',
                            )}
                        >
                            <span
                                className={cn(
                                    'truncate rounded-full px-2.5 py-0.5 text-sm font-semibold',
                                    tones[team].chip,
                                )}
                            >
                                {matchup[team].teamName}
                            </span>
                            <span
                                className={cn(
                                    'shrink-0 text-2xl font-bold tabular-nums',
                                    tones[team].text,
                                )}
                            >
                                {formatPoints(points[team].base)}
                            </span>
                        </div>
                    ))}
                    <label
                        htmlFor="base-score"
                        className="text-xs font-medium tracking-wide text-muted-foreground uppercase"
                    >
                        {t('scoring.baseScore.label')}
                    </label>
                </div>
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
                        aria-valuetext={fillPlaceholders(t('scoring.baseScore.valueText'), {
                            team0: matchup[0].teamName,
                            points0: formatPoints(points[0].base),
                            team1: matchup[1].teamName,
                            points1: formatPoints(points[1].base),
                        })}
                        className="score-split h-11 min-w-0 flex-1"
                        style={
                            {
                                '--split': `${((state.baseScoreTeam0 - BASE_SCORE_MIN) / (BASE_SCORE_MAX - BASE_SCORE_MIN)) * 100}%`,
                                '--split-left': tones[0].fill,
                                '--split-right': tones[1].fill,
                            } as CSSProperties
                        }
                        onChange={(e) => form.setValue('baseScoreTeam0', Number(e.target.value))}
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
            </div>

            {/* Once the page explains the freeze, the latched rejection needs no message of its own. */}
            <FormAlert
                message={rootError?.type === 'confirmed' && frozen ? undefined : rootError?.message}
            />
            {rootError?.type === 'exists' && onReload ? (
                <Button type="button" variant="outline" onClick={() => void reload()}>
                    {t('scoring.alreadyExists.load')}
                </Button>
            ) : null}
            {rootError?.type === 'concurrent' && onReload ? (
                <Button type="button" variant="outline" onClick={() => void reload()}>
                    {t('scoring.concurrent.reload')}
                </Button>
            ) : null}

            {/* Phones: one row, the primary save taking the wider right-hand cell under the thumb. */}
            <div className="grid grid-cols-[auto_minmax(0,1fr)] gap-2 sm:flex sm:items-center sm:justify-between">
                <Button
                    type="button"
                    size="touch"
                    variant={state.beschiss ? 'destructive' : 'outline'}
                    disabled={readOnly}
                    aria-pressed={state.beschiss}
                    onClick={() => form.setValue('beschiss', !state.beschiss)}
                >
                    {state.beschiss
                        ? `${t('scoring.beschiss.label')} ✓`
                        : t('scoring.beschiss.label')}
                </Button>
                <Button
                    type="submit"
                    size="touch"
                    disabled={readOnly || pending}
                    aria-busy={pending}
                >
                    {pending
                        ? t('scoring.save.saving')
                        : loaded
                          ? t('scoring.save.update')
                          : t('scoring.save.create')}
                </Button>
            </div>
        </form>
    );
}
