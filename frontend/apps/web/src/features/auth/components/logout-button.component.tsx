import { useNavigate } from '@tanstack/react-router';
import { Button } from '@tichu/ui/button.tsx';

import { useLogoutAdmin } from '@/features/auth/hooks/auth-queries.hook.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/** Ends the session and returns to the login screen. */
export function LogoutButton() {
    const { t } = useI18n();
    const navigate = useNavigate();
    const logout = useLogoutAdmin();

    const onClick = async () => {
        await logout.mutateAsync();
        await navigate({ to: '/login' });
    };

    return (
        <Button type="button" variant="outline" size="sm" onClick={onClick} disabled={logout.isPending}>
            {logout.isPending ? t('auth.logout.pending') : t('auth.logout')}
        </Button>
    );
}
