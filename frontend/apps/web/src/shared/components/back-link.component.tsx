import { Link, type LinkProps } from '@tanstack/react-router';
import { ChevronLeft } from 'lucide-react';
import { buttonVariants } from '@tichu/ui/button.tsx';

import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface BackLinkProps {
    to: LinkProps['to'];
    params?: LinkProps['params'];
    labelKey: TranslationKey;
}

/**
 * "‹ Back to …" above a screen's title: a 44px touch target whose text lines up
 * with the content below (the negative margin offsets the button padding).
 */
export function BackLink({ to, params, labelKey }: BackLinkProps) {
    const { t } = useI18n();

    return (
        <Link to={to} params={params} className={buttonVariants({ variant: 'ghost', size: 'touch', className: '-ml-3 gap-1 self-start px-3' })}>
            <ChevronLeft aria-hidden="true" className="size-4" />
            {t(labelKey)}
        </Link>
    );
}
