import { z } from 'zod';

import { TaskStatus } from '@/vocabulary/task-status.enum.ts';

/**
 * zod schemas describing the wire format. Every response is parsed through these
 * at the repository boundary, so a backend change that breaks the contract fails
 * loudly and early instead of propagating `any` through the app.
 */
export const taskDtoSchema = z.object({
    id: z.string(),
    title: z.string(),
    description: z.string(),
    status: z.enum([TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.DONE]),
    createdAt: z.string(),
    updatedAt: z.string(),
});

export const taskListDtoSchema = z.array(taskDtoSchema);

export type TaskDto = z.infer<typeof taskDtoSchema>;

export interface CreateTaskRequestDto {
    title: string;
    description: string;
}

export interface UpdateTaskRequestDto {
    title: string;
    description: string;
    status: TaskStatus;
}
