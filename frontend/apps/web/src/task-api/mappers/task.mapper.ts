import type { Task } from '@/domain/task.domain.ts';
import type { TaskDto } from '@/task-api/dtos/task.dto.ts';

/** Pure mapping functions. Unit-tested in isolation (see `__tests__`). */
export function taskDtoToDomain(dto: TaskDto): Task {
    return {
        id: dto.id,
        title: dto.title,
        description: dto.description,
        status: dto.status,
        createdAt: new Date(dto.createdAt),
        updatedAt: new Date(dto.updatedAt),
    };
}
