import type { TaskStatus } from '@/vocabulary/task-status.enum.ts';

/**
 * The domain model used throughout the UI. It is deliberately decoupled from the
 * wire format (DTO): `createdAt`/`updatedAt` are real `Date` objects here, whereas
 * the DTO carries ISO strings. Mappers bridge the two.
 */
export interface Task {
    id: string;
    title: string;
    description: string;
    status: TaskStatus;
    createdAt: Date;
    updatedAt: Date;
}
