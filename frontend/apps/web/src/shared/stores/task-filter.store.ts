import { create } from 'zustand';

import type { TaskStatus } from '@/vocabulary/task-status.enum.ts';

/**
 * Client/UI state lives in Zustand (server state lives in TanStack Query).
 * This store holds the current status filter for the task list.
 */
interface TaskFilterState {
    statusFilter: TaskStatus | 'ALL';
    setStatusFilter: (status: TaskStatus | 'ALL') => void;
}

export const useTaskFilterStore = create<TaskFilterState>((set) => ({
    statusFilter: 'ALL',
    setStatusFilter: (status) => set({ statusFilter: status }),
}));
