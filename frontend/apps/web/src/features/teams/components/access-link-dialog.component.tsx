import { useEffect, useRef, useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { Button } from '@tichu/ui/button.tsx';
import { CopyButton } from '@tichu/ui/copy-button.tsx';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from '@tichu/ui/dialog.tsx';

import type { RevealedAccessLink } from '@/domain/access-link.domain.ts';
import type { Team } from '@/domain/team.domain.ts';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import {
    useRevokeAccessLink,
    useRevealAccessLink,
    useRotateAccessLink,
} from '@/features/teams/hooks/team-queries.hook.ts';
import { ConfirmDialog } from '@/shared/components/confirm-dialog.component.tsx';

type ConfirmAction = 'rotate' | 'revoke';

interface AccessLinkDialogProps {
    tournamentId: string;
    team: Team;
    /** Closes the dialog; the revoke flow closes it after success. */
    onClose: () => void;
}

/**
 * The one-time view of a team's access link (see glossary: "Access Link"). Opening
 * the dialog reveals the raw link — the only response that ever carries token
 * material — together with its QR code. Rotation mints new material and immediately
 * shows it here; revocation disables the link and closes the dialog. If the link was
 * already revealed (or revoked), the backend rejects the reveal with
 * `LINK_ALREADY_REVEALED` and rotation is offered as the recovery path.
 */
export function AccessLinkDialog({ tournamentId, team, onClose }: AccessLinkDialogProps) {
    const { t } = useI18n();
    const reveal = useRevealAccessLink(tournamentId);
    const rotate = useRotateAccessLink(tournamentId);
    const revoke = useRevokeAccessLink(tournamentId);

    const [revealed, setRevealed] = useState<RevealedAccessLink | null>(null);
    const [error, setError] = useState<unknown>(null);
    const [pendingConfirm, setPendingConfirm] = useState<ConfirmAction | null>(null);
    const [revokeError, setRevokeError] = useState<unknown>(null);
    const requested = useRef(false);

    useEffect(() => {
        if (requested.current) return;
        requested.current = true;
        reveal.mutateAsync(team.id).then(setRevealed).catch(setError);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const doRotate = async () => {
        setPendingConfirm(null);
        setError(null);
        await rotate.mutateAsync(team.id);
        const next = await reveal.mutateAsync(team.id).catch(() => undefined);
        if (next) setRevealed(next);
    };

    const doRevoke = async () => {
        setPendingConfirm(null);
        setRevokeError(null);
        try {
            await revoke.mutateAsync(team.id);
            onClose();
        } catch (e) {
            // Revoke failed — the link still works, so stay open and report it.
            setRevokeError(e);
        }
    };

    const pending = pendingConfirm !== null && (rotate.isPending || revoke.isPending);

    return (
        <>
            <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>{t('teams.accessLink.title')}</DialogTitle>
                        <DialogDescription>{team.name}</DialogDescription>
                    </DialogHeader>

                    {error !== null ? (
                        <>
                            <p role="alert" className="text-sm text-destructive">
                                {localizedErrorMessage(error, t)}
                            </p>
                            <DialogFooter>
                                <Button variant="outline" onClick={onClose}>
                                    {t('common.cancel')}
                                </Button>
                                {/* Recovery rotate goes through the same confirmation as the
                                normal path — there is no safe link left to lose. */}
                                <Button
                                    disabled={rotate.isPending}
                                    onClick={() => setPendingConfirm('rotate')}
                                >
                                    {t('teams.accessLink.rotate')}
                                </Button>
                            </DialogFooter>
                        </>
                    ) : revealed ? (
                        <>
                            <p className="text-sm text-muted-foreground">
                                {t('teams.accessLink.oneTime')}
                            </p>
                            <div className="flex flex-col gap-2">
                                <span className="text-sm font-medium">
                                    {t('teams.accessLink.linkLabel')}
                                </span>
                                <code className="block break-all rounded-md border border-border bg-muted p-2 text-xs">
                                    {revealed.link}
                                </code>
                            </div>
                            <div className="flex items-center gap-4">
                                <QRCodeSVG
                                    value={revealed.link}
                                    // The 4-module quiet zone keeps the code scannable on
                                    // a dark background.
                                    size={160}
                                    marginSize={4}
                                    className="rounded-md"
                                    role="img"
                                    aria-label={t('teams.accessLink.qr')}
                                />
                                {/* Keyed by link so a rotated token resets the copied state. */}
                                <CopyButton
                                    key={revealed.link}
                                    value={revealed.link}
                                    copyLabel={t('teams.accessLink.copy')}
                                    copiedLabel={t('teams.accessLink.copied')}
                                />
                            </div>
                            {revokeError !== null ? (
                                <p role="alert" className="text-sm text-destructive">
                                    {localizedErrorMessage(revokeError, t)}
                                </p>
                            ) : null}
                            <DialogFooter>
                                <Button variant="outline" onClick={onClose}>
                                    {t('common.cancel')}
                                </Button>
                                <Button
                                    variant="outline"
                                    disabled={rotate.isPending}
                                    onClick={() => setPendingConfirm('rotate')}
                                >
                                    {t('teams.accessLink.rotate')}
                                </Button>
                                <Button
                                    variant="destructive"
                                    disabled={revoke.isPending}
                                    onClick={() => setPendingConfirm('revoke')}
                                >
                                    {t('teams.accessLink.revoke')}
                                </Button>
                            </DialogFooter>
                        </>
                    ) : null}
                </DialogContent>
            </Dialog>

            {pendingConfirm === 'rotate' ? (
                <ConfirmDialog
                    titleKey="teams.accessLink.rotateTitle"
                    descriptionKey="teams.accessLink.rotateDescription"
                    confirmLabelKey={
                        rotate.isPending
                            ? 'teams.accessLink.rotatePending'
                            : 'teams.accessLink.rotateConfirm'
                    }
                    destructive={false}
                    disabled={pending}
                    onConfirm={doRotate}
                    onCancel={() => setPendingConfirm(null)}
                />
            ) : null}

            {pendingConfirm === 'revoke' ? (
                <ConfirmDialog
                    titleKey="teams.accessLink.revokeTitle"
                    descriptionKey="teams.accessLink.revokeDescription"
                    confirmLabelKey={
                        revoke.isPending
                            ? 'teams.accessLink.revokePending'
                            : 'teams.accessLink.revokeConfirm'
                    }
                    destructive
                    disabled={pending}
                    onConfirm={doRevoke}
                    onCancel={() => setPendingConfirm(null)}
                />
            ) : null}
        </>
    );
}
