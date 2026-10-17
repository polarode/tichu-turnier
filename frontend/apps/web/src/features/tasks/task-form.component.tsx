import { zodResolver } from '@hookform/resolvers/zod';
import { Badge } from '@template/ui/badge.tsx';
import { Button } from '@template/ui/button.tsx';
import { Input } from '@template/ui/input.tsx';
import { Textarea } from '@template/ui/textarea.tsx';
import { useForm } from 'react-hook-form';

import { type TaskFormValues, taskFormSchema } from '@/features/tasks/task-form.schema.ts';
import { TASK_STATUSES, TASK_STATUS_LABELS, TaskStatus } from '@/vocabulary/task-status.enum.ts';

interface TaskFormProps {
    defaultValues?: TaskFormValues;
    submitLabel?: string;
    pending?: boolean;
    onSubmit: (values: TaskFormValues) => void;
}

/**
 * A form driven by react-hook-form with a zod resolver. Validation errors come
 * straight from the schema, so the UI and the parse logic never drift apart.
 */
export function TaskForm({ defaultValues, submitLabel = 'Save', pending, onSubmit }: TaskFormProps) {
    const {
        register,
        handleSubmit,
        watch,
        setValue,
        formState: { errors },
    } = useForm<TaskFormValues>({
        resolver: zodResolver(taskFormSchema),
        defaultValues: defaultValues ?? {
            title: '',
            description: '',
            status: TaskStatus.TODO,
        },
    });

    const status = watch('status');

    return (
        <form className="flex flex-col gap-3" onSubmit={handleSubmit(onSubmit)}>
            <div className="flex flex-col gap-1">
                <label className="text-sm font-medium" htmlFor="title">
                    Title
                </label>
                <Input id="title" placeholder="What needs doing?" {...register('title')} />
                {errors.title && (
                    <span className="text-xs text-destructive">{errors.title.message}</span>
                )}
            </div>

            <div className="flex flex-col gap-1">
                <label className="text-sm font-medium" htmlFor="description">
                    Description
                </label>
                <Textarea id="description" rows={3} {...register('description')} />
            </div>

            <div className="flex flex-col gap-1">
                <span className="text-sm font-medium">Status</span>
                <div className="flex gap-2">
                    {TASK_STATUSES.map((option) => (
                        <button
                            key={option}
                            type="button"
                            onClick={() => setValue('status', option)}
                            aria-pressed={status === option}
                        >
                            <Badge variant={status === option ? 'default' : 'outline'}>
                                {TASK_STATUS_LABELS[option]}
                            </Badge>
                        </button>
                    ))}
                </div>
            </div>

            <Button type="submit" disabled={pending}>
                {submitLabel}
            </Button>
        </form>
    );
}
