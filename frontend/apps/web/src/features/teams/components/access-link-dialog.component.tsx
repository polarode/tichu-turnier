import { useEffect, useRef, useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { Button } from '@tichu/ui/button.tsx';
import { CopyButton } from '@tichu/ui/copy-button.tsx';
import { Dialog } from '@tichu/ui/dialog.tsx';

import type { RevealedAccessLink } from '@/domain/access-link.domain.ts';
import type { Team } from '@/domain/team.domain.ts';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import {
    useRevokeAccessLink,
    useRevealAccessLink,
    useRotateAccessLink,
} from '@/features/teams/hooks/team-queries.hook.ts';
import { ConfirmDialog } from '@/features/teams/components/confirm-dialog.component.tsx';

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
            <Dialog open label={t('teams.accessLink.title')} onClose={onClose}>
                <h2 className="mb-1 text-lg font-semibold">{t('teams.accessLink.title')}</h2>
                <p className="mb-4 text-sm text-muted-foreground">{team.name}</p>

                {error !== null ? (
                    <>
                        <p role="alert" className="mb-4 text-sm text-destructive">
                            {localizedErrorMessage(error, t)}
                        </p>
                        <div className="flex justify-end gap-2">
                            <Button variant="outline" onClick={onClose}>
                                {t('common.cancel')}
                            </Button>
                            {/* Recovery rotate goes through the same confirmation as the
                                normal path — there is no safe link left to lose. */}
                            <Button disabled={rotate.isPending} onClick={() => setPendingConfirm('rotate')}>
                                {t('teams.accessLink.rotate')}
                            </Button>
                        </div>
                    </>
                ) : revealed ? (
                    <>
                        <p className="mb-4 text-sm text-muted-foreground">
                            {t('teams.accessLink.oneTime')}
                        </p>
                        <div className="mb-4 flex flex-col gap-2">
                            <span className="text-sm font-medium">{t('teams.accessLink.linkLabel')}</span>
                            <code className="block break-all rounded-md border border-border p-2 text-xs">
                                {revealed.link}
                            </code>
                        </div>
                        <div className="mb-4 flex items-center gap-4">
                            <QRCodeSVG
                                value={revealed.link}
                                size={128}
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
                            <p role="alert" className="mb-4 text-sm text-destructive">
                                {localizedErrorMessage(revokeError, t)}
                            </p>
                        ) : null}
                        <div className="flex justify-end gap-2">
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
                        </div>
                    </>
                ) : null}
            </Dialog>

            {pendingConfirm === 'rotate' ? (
                <ConfirmDialog
                    titleKey="teams.accessLink.rotateTitle"
                    descriptionKey="teams.accessLink.rotateDescription"
                    confirmLabelKey={
                        rotate.isPending ? 'teams.accessLink.rotatePending' : 'teams.accessLink.rotateConfirm'
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
                        revoke.isPending ? 'teams.accessLink.revokePending' : 'teams.accessLink.revokeConfirm'
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
