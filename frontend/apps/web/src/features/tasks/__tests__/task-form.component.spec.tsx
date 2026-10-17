import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { TaskForm } from '@/features/tasks/task-form.component.tsx';
import { renderWithProviders } from '@/test-helpers/render-with-providers.tsx';
import { TaskStatus } from '@/vocabulary/task-status.enum.ts';

describe('TaskForm', () => {
    it('submits trimmed values when the title is valid', async () => {
        const onSubmit = vi.fn();
        renderWithProviders(<TaskForm onSubmit={onSubmit} submitLabel="Add task" />);

        await userEvent.type(screen.getByLabelText('Title'), 'Write docs');
        await userEvent.click(screen.getByRole('button', { name: 'Add task' }));

        await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
        expect(onSubmit.mock.calls[0]![0]).toMatchObject({
            title: 'Write docs',
            status: TaskStatus.TODO,
        });
    });

    it('shows a validation error and does not submit when the title is empty', async () => {
        const onSubmit = vi.fn();
        renderWithProviders(<TaskForm onSubmit={onSubmit} submitLabel="Add task" />);

        await userEvent.click(screen.getByRole('button', { name: 'Add task' }));

        expect(await screen.findByText('Title is required')).toBeInTheDocument();
        expect(onSubmit).not.toHaveBeenCalled();
    });
});
