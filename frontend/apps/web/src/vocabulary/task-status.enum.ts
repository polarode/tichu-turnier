/**
 * Shared value type. Mirrors the backend `TaskStatus` enum. Kept in `vocabulary`
 * so both the domain model and the DTO schema can reference one source of truth.
 */
export const TaskStatus = {
    TODO: 'TODO',
    IN_PROGRESS: 'IN_PROGRESS',
    DONE: 'DONE',
} as const;

export type TaskStatus = (typeof TaskStatus)[keyof typeof TaskStatus];

export const TASK_STATUSES: TaskStatus[] = [
    TaskStatus.TODO,
    TaskStatus.IN_PROGRESS,
    TaskStatus.DONE,
];

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
    [TaskStatus.TODO]: 'To do',
    [TaskStatus.IN_PROGRESS]: 'In progress',
    [TaskStatus.DONE]: 'Done',
};
