import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { TaskRepository } from '@/task-api/task.repository.ts';
import { taskApi } from '@/task-api/task.api.ts';
import { TaskStatus } from '@/vocabulary/task-status.enum.ts';

describe('TaskRepository', () => {
    let mock: MockAdapter;

    beforeEach(() => {
        mock = new MockAdapter(taskApi);
    });

    afterEach(() => {
        mock.restore();
    });

    const dto = {
        id: '1',
        title: 'A',
        description: 'd',
        status: TaskStatus.TODO,
        createdAt: '2026-01-01T00:00:00.000Z',
        updatedAt: '2026-01-01T00:00:00.000Z',
    };

    it('lists tasks and maps them to domain', async () => {
        mock.onGet('/api/tasks').reply(200, [dto]);

        const tasks = await TaskRepository.list();

        expect(tasks).toHaveLength(1);
        expect(tasks[0]!.title).toBe('A');
        expect(tasks[0]!.createdAt).toBeInstanceOf(Date);
    });

    it('creates a task', async () => {
        mock.onPost('/api/tasks').reply(201, dto);

        const task = await TaskRepository.create({ title: 'A', description: 'd' });

        expect(task.id).toBe('1');
        expect(JSON.parse(mock.history.post[0]!.data)).toEqual({ title: 'A', description: 'd' });
    });

    it('throws when the response violates the schema', async () => {
        mock.onGet('/api/tasks/1').reply(200, { id: 1, title: 'A' });

        await expect(TaskRepository.get('1')).rejects.toThrow();
    });

    it('deletes a task', async () => {
        mock.onDelete('/api/tasks/1').reply(204);

        await expect(TaskRepository.remove('1')).resolves.toBeUndefined();
    });
});
