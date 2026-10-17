import type { Task } from '@/domain/task.domain.ts';
import {
    type CreateTaskRequestDto,
    taskDtoSchema,
    taskListDtoSchema,
    type UpdateTaskRequestDto,
} from '@/task-api/dtos/task.dto.ts';
import { taskDtoToDomain } from '@/task-api/mappers/task.mapper.ts';
import { taskApi } from '@/task-api/task.api.ts';

/**
 * The only layer that knows about DTOs and HTTP. Each function:
 *   1. calls the api client,
 *   2. validates the response with a zod schema,
 *   3. maps the DTO to a domain model.
 * Everything above this returns/consumes domain `Task` objects.
 */
async function list(): Promise<Task[]> {
    const { data } = await taskApi.get<unknown>('/api/tasks');
    return taskListDtoSchema.parse(data).map(taskDtoToDomain);
}

async function get(id: string): Promise<Task> {
    const { data } = await taskApi.get<unknown>(`/api/tasks/${id}`);
    return taskDtoToDomain(taskDtoSchema.parse(data));
}

async function create(request: CreateTaskRequestDto): Promise<Task> {
    const { data } = await taskApi.post<unknown>('/api/tasks', request);
    return taskDtoToDomain(taskDtoSchema.parse(data));
}

async function update(id: string, request: UpdateTaskRequestDto): Promise<Task> {
    const { data } = await taskApi.put<unknown>(`/api/tasks/${id}`, request);
    return taskDtoToDomain(taskDtoSchema.parse(data));
}

async function remove(id: string): Promise<void> {
    await taskApi.delete(`/api/tasks/${id}`);
}

export const TaskRepository = {
    list,
    get,
    create,
    update,
    remove,
};
