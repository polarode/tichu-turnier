import { createFileRoute } from '@tanstack/react-router';

import { TasksPage } from '@/features/tasks/tasks-page.component.tsx';

export const Route = createFileRoute('/')({
    component: TasksPage,
});
