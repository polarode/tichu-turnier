import { create } from 'zustand';
import { createJSONStorage, persist, type StateStorage } from 'zustand/middleware';

import {
    STATISTICS_COLUMN_IDS,
    type StatisticsColumn,
} from '@/shared/constants/statistics-columns.ts';

/** localStorage key for the viewer's column choice on the team table. */
export const STATISTICS_COLUMNS_STORAGE_KEY = 'tichu.statisticsColumns';

interface StatisticsColumnsState {
    /**
     * The columns the viewer chose, in table order. Null until they choose, which lets the
     * device default apply.
     */
    visible: StatisticsColumn[] | null;
    /** Show or hide one column, given the columns shown now; stores the full explicit list. */
    toggle: (column: StatisticsColumn, current: readonly StatisticsColumn[]) => void;
}

/** localStorage that never throws: blocked or full storage just stops remembering the choice. */
const bestEffortLocalStorage: StateStorage = {
    getItem: (name) => {
        try {
            return window.localStorage.getItem(name);
        } catch {
            return null;
        }
    },
    setItem: (name, value) => {
        try {
            window.localStorage.setItem(name, value);
        } catch {
            // Remembering the choice is best-effort; never break the table over it.
        }
    },
    removeItem: (name) => {
        try {
            window.localStorage.removeItem(name);
        } catch {
            // Nothing to forget if storage is unavailable.
        }
    },
};

/**
 * Keeps only the column ids this build knows, in table order. A stored list that names
 * only unknown ids is treated as no choice at all, so stale storage falls back to the default.
 */
function sanitizeVisible(stored: unknown): StatisticsColumn[] | null {
    if (!Array.isArray(stored)) return null;
    const known = STATISTICS_COLUMN_IDS.filter((id) => stored.includes(id));
    if (known.length === 0 && stored.length > 0) return null;
    return [...known];
}

export const useStatisticsColumnsStore = create<StatisticsColumnsState>()(
    persist(
        (set) => ({
            visible: null,
            toggle: (column, current) => {
                const next = current.includes(column)
                    ? current.filter((id) => id !== column)
                    : [...current, column];
                set({ visible: STATISTICS_COLUMN_IDS.filter((id) => next.includes(id)) });
            },
        }),
        {
            name: STATISTICS_COLUMNS_STORAGE_KEY,
            storage: createJSONStorage(() => bestEffortLocalStorage),
            partialize: (state) => ({ visible: state.visible }),
            merge: (persisted, current) => ({
                ...current,
                visible: sanitizeVisible((persisted as { visible?: unknown } | null)?.visible),
            }),
        },
    ),
);
