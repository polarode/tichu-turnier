import { create } from 'zustand';

import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';

/** A one-shot message shown once on the next screen, e.g. after a redirect. */
export interface Notice {
    key: TranslationKey;
    /** Fills `{name}` placeholders in the catalog text, e.g. the tournament name. */
    values?: Record<string, string>;
}

interface NoticeState {
    /** At most one notice; a new one replaces the pending one. */
    notice: Notice | null;
    show: (notice: Notice) => void;
    /** Shows [notice] only when none is pending, so a more specific one keeps its place. */
    showUnlessPending: (notice: Notice) => void;
    dismiss: () => void;
}

export const useNoticeStore = create<NoticeState>()((set) => ({
    notice: null,
    show: (notice) => set({ notice }),
    showUnlessPending: (notice) => set((state) => (state.notice ? state : { notice })),
    dismiss: () => set({ notice: null }),
}));
