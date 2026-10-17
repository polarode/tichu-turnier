import { describe, expect, it } from 'vitest';

import type { TaskDto } from '@/task-api/dtos/task.dto.ts';
import { taskDtoToDomain } from '@/task-api/mappers/task.mapper.ts';
import { TaskStatus } from '@/vocabulary/task-status.enum.ts';

describe('taskDtoToDomain', () => {
    it('maps a DTO to a domain Task and parses timestamps into Date objects', () => {
        const dto: TaskDto = {
            id: 'abc',
            title: 'Write docs',
            description: 'for the template',
            status: TaskStatus.IN_PROGRESS,
            createdAt: '2026-01-01T12:00:00.000Z',
            updatedAt: '2026-01-02T08:30:00.000Z',
        };

        const task = taskDtoToDomain(dto);

        expect(task.id).toBe('abc');
        expect(task.title).toBe('Write docs');
        expect(task.status).toBe(TaskStatus.IN_PROGRESS);
        expect(task.createdAt).toBeInstanceOf(Date);
        expect(task.createdAt.toISOString()).toBe('2026-01-01T12:00:00.000Z');
        expect(task.updatedAt.toISOString()).toBe('2026-01-02T08:30:00.000Z');
    });
});
