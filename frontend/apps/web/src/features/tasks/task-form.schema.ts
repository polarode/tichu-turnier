import { z } from 'zod';

import { TaskStatus } from '@/vocabulary/task-status.enum.ts';

/** The react-hook-form schema for creating/editing a task. */
export const taskFormSchema = z.object({
    title: z.string().min(1, 'Title is required').max(120),
    description: z.string().max(1000),
    status: z.enum([TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.DONE]),
});

export type TaskFormValues = z.infer<typeof taskFormSchema>;
