import { Badge } from '@template/ui/badge.tsx';
import { Button } from '@template/ui/button.tsx';
import { Trash2 } from 'lucide-react';
import { useMemo } from 'react';

import { TaskForm } from '@/features/tasks/task-form.component.tsx';
import type { TaskFormValues } from '@/features/tasks/task-form.schema.ts';
import { TaskStatusBadge } from '@/features/tasks/task-status-badge.component.tsx';
import {
    useCreateTask,
    useDeleteTask,
    useTasks,
    useUpdateTask,
} from '@/features/tasks/hooks/task-queries.hook.ts';
import { useTaskFilterStore } from '@/shared/stores/task-filter.store.ts';
import { TASK_STATUS_LABELS, TASK_STATUSES } from '@/vocabulary/task-status.enum.ts';

export function TasksPage() {
    const { data: tasks = [], isPending, isError } = useTasks();
    const createTask = useCreateTask();
    const updateTask = useUpdateTask();
    const deleteTask = useDeleteTask();

    const { statusFilter, setStatusFilter } = useTaskFilterStore();

    const visibleTasks = useMemo(
        () =>
            statusFilter === 'ALL'
                ? tasks
                : tasks.filter((task) => task.status === statusFilter),
        [tasks, statusFilter],
    );

    const handleCreate = (values: TaskFormValues) => createTask.mutate(values);

    return (
        <div className="mx-auto flex max-w-3xl flex-col gap-8 p-8">
            <header>
                <h1 className="text-2xl font-semibold">Task Tracker</h1>
                <p className="text-sm text-muted-foreground">
                    A minimal end-to-end example of the template architecture.
                </p>
            </header>

            <section className="rounded-lg border border-border p-4">
                <h2 className="mb-3 text-lg font-medium">New task</h2>
                <TaskForm
                    submitLabel="Add task"
                    pending={createTask.isPending}
                    onSubmit={handleCreate}
                />
            </section>

            <section className="flex flex-col gap-3">
                <div className="flex items-center gap-2">
                    <span className="text-sm font-medium">Filter:</span>
                    <button type="button" onClick={() => setStatusFilter('ALL')}>
                        <Badge variant={statusFilter === 'ALL' ? 'default' : 'outline'}>All</Badge>
                    </button>
                    {TASK_STATUSES.map((status) => (
                        <button key={status} type="button" onClick={() => setStatusFilter(status)}>
                            <Badge variant={statusFilter === status ? 'default' : 'outline'}>
                                {TASK_STATUS_LABELS[status]}
                            </Badge>
                        </button>
                    ))}
                </div>

                {isPending && <p className="text-sm text-muted-foreground">Loading…</p>}
                {isError && <p className="text-sm text-destructive">Failed to load tasks.</p>}

                <ul className="flex flex-col gap-2">
                    {visibleTasks.map((task) => (
                        <li
                            key={task.id}
                            className="flex items-center justify-between gap-3 rounded-md border border-border p-3"
                        >
                            <div className="min-w-0">
                                <p className="truncate font-medium">{task.title}</p>
                                {task.description && (
                                    <p className="truncate text-sm text-muted-foreground">
                                        {task.description}
                                    </p>
                                )}
                            </div>
                            <div className="flex items-center gap-2">
                                <TaskStatusBadge status={task.status} />
                                <Button
                                    size="sm"
                                    variant="outline"
                                    onClick={() =>
                                        updateTask.mutate({
                                            id: task.id,
                                            request: {
                                                title: task.title,
                                                description: task.description,
                                                status: nextStatus(task.status),
                                            },
                                        })
                                    }
                                >
                                    Advance
                                </Button>
                                <Button
                                    size="icon"
                                    variant="ghost"
                                    aria-label={`Delete ${task.title}`}
                                    onClick={() => deleteTask.mutate(task.id)}
                                >
                                    <Trash2 />
                                </Button>
                            </div>
                        </li>
                    ))}
                    {!isPending && visibleTasks.length === 0 && (
                        <li className="text-sm text-muted-foreground">No tasks yet.</li>
                    )}
                </ul>
            </section>
        </div>
    );
}

function nextStatus(status: (typeof TASK_STATUSES)[number]): (typeof TASK_STATUSES)[number] {
    const order = TASK_STATUSES;
    const index = order.indexOf(status);
    return order[Math.min(index + 1, order.length - 1)]!;
}
