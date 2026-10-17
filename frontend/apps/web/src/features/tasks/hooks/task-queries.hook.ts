import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { QueryKeys } from '@/shared/constants/query-keys.ts';
import type { CreateTaskRequestDto, UpdateTaskRequestDto } from '@/task-api/dtos/task.dto.ts';
import { TaskRepository } from '@/task-api/task.repository.ts';

/**
 * TanStack Query hooks. Components call these; they never touch the repository
 * directly. Mutations invalidate the task list so the UI stays in sync.
 */
export function useTasks() {
    return useQuery({
        queryKey: [QueryKeys.TASKS],
        queryFn: TaskRepository.list,
    });
}

export function useCreateTask() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (request: CreateTaskRequestDto) => TaskRepository.create(request),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: [QueryKeys.TASKS] }),
    });
}

export function useUpdateTask() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ id, request }: { id: string; request: UpdateTaskRequestDto }) =>
            TaskRepository.update(id, request),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: [QueryKeys.TASKS] }),
    });
}

export function useDeleteTask() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (id: string) => TaskRepository.remove(id),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: [QueryKeys.TASKS] }),
    });
}
