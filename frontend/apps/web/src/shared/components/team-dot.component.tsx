import { cn } from '@tichu/ui/lib/utils.ts';

/** Small colour marker tying a team to its seat colour. */
export function TeamDot({ tone }: { tone: string }) {
    return (
        <span
            aria-hidden="true"
            className={cn('mr-1 inline-block size-2 rounded-full align-middle', tone)}
        />
    );
}
