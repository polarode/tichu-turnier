import { Badge } from '@template/ui/badge.tsx';

import { TASK_STATUS_LABELS, TaskStatus } from '@/vocabulary/task-status.enum.ts';

const VARIANT_BY_STATUS = {
    [TaskStatus.TODO]: 'secondary',
    [TaskStatus.IN_PROGRESS]: 'warning',
    [TaskStatus.DONE]: 'success',
} as const;

export function TaskStatusBadge({ status }: { status: TaskStatus }) {
    return <Badge variant={VARIANT_BY_STATUS[status]}>{TASK_STATUS_LABELS[status]}</Badge>;
}
