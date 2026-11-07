import { useRouterState } from '@tanstack/react-router';
import { LogIn } from 'lucide-react';
import { useEffect, useId, useRef, useState, type FocusEvent, type KeyboardEvent } from 'react';

import { LoginForm } from '@/features/auth/components/login-form.component.tsx';
import { useSession } from '@/features/auth/hooks/auth-queries.hook.ts';
import { useDropdownDismiss } from '@/shared/hooks/use-dropdown-dismiss.hook.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/** Pages that already show their own credentials form; a second one would only compete with it. */
const AUTH_PAGES: ReadonlySet<string> = new Set(['/login', '/register']);

/**
 * Sign-in icon in the top-right corner, the avatar's counterpart while no session
 * exists. It opens the login form in place, so a visitor signs in without leaving
 * the page; the resulting session swaps it for the avatar.
 */
export function SignInPopover() {
    const { data: admin, isError } = useSession();
    const pathname = useRouterState({ select: (state) => state.location.pathname });

    // Shown once the session is known to be absent, or could not be checked at all (then
    // this is the only way in). While it loads, the corner stays empty. Hiding unmounts
    // the popover, so it always comes back closed.
    const signedOut = !admin && (admin === null || isError);
    if (!signedOut || AUTH_PAGES.has(pathname)) return null;
    return <SignInPopoverEntry />;
}

function SignInPopoverEntry() {
    const { t } = useI18n();
    const [open, setOpen] = useState(false);
    const rootRef = useRef<HTMLDivElement>(null);
    const triggerRef = useRef<HTMLButtonElement>(null);
    const dialogRef = useRef<HTMLDivElement>(null);
    const titleId = useId();
    // Outside clicks close quietly; Escape is handled on the dialog so focus can return.
    useDropdownDismiss(open, rootRef, () => setOpen(false));

    // Opening hands focus to the first field, so the visitor can type straight away.
    useEffect(() => {
        if (open) dialogRef.current?.querySelector('input')?.focus();
    }, [open]);

    const onDialogKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        if (event.key !== 'Escape') return;
        event.stopPropagation();
        setOpen(false);
        triggerRef.current?.focus();
    };

    // Tabbing out of the form closes it; a null target (a click on bare page) is left to the dismiss hook.
    const onRootBlur = (event: FocusEvent<HTMLDivElement>) => {
        const next = event.relatedTarget;
        if (next !== null && !event.currentTarget.contains(next)) setOpen(false);
    };

    return (
        <div ref={rootRef} onBlur={onRootBlur} className="relative">
            <button
                ref={triggerRef}
                type="button"
                aria-label={t('auth.signIn')}
                aria-haspopup="dialog"
                aria-expanded={open}
                onClick={() => setOpen((wasOpen) => !wasOpen)}
                className="flex size-10 items-center justify-center rounded-md text-header-muted transition-colors outline-none hover:text-header-foreground focus-visible:ring-2 focus-visible:ring-header-foreground"
            >
                <LogIn aria-hidden="true" className="size-5" />
            </button>

            {open && (
                <div
                    ref={dialogRef}
                    role="dialog"
                    onKeyDown={onDialogKeyDown}
                    aria-labelledby={titleId}
                    className="absolute right-0 z-10 mt-2 w-80 max-w-[calc(100vw-2rem)] rounded-md border border-border bg-popover p-4 text-popover-foreground shadow-lg"
                >
                    <h2 id={titleId} className="mb-3 text-base font-semibold">
                        {t('login.title')}
                    </h2>
                    <LoginForm />
                </div>
            )}
        </div>
    );
}
