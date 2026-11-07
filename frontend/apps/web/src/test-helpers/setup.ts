import '@testing-library/jest-dom/vitest';
import { beforeEach } from 'vitest';

import { FakeEventSource } from '@/test-helpers/fake-event-source.ts';

// happy-dom has no EventSource; every spec gets the fake so live pages can render.
globalThis.EventSource = FakeEventSource as unknown as typeof EventSource;

beforeEach(() => {
    FakeEventSource.reset();
});
