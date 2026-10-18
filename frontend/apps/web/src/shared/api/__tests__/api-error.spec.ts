import { describe, expect, it } from 'vitest';

import { ApiError, UNKNOWN_ERROR_CODE } from '@/shared/api/api-error.ts';

describe('ApiError.fromProblem', () => {
    it('carries the stable code, status, field and message', () => {
        const error = ApiError.fromProblem(
            {
                status: 409,
                code: 'DUPLICATE_TEAM_NAME',
                detail: 'team name already taken',
                field: 'name',
            },
            409,
        );

        expect(error).toBeInstanceOf(ApiError);
        expect(error.code).toBe('DUPLICATE_TEAM_NAME');
        expect(error.status).toBe(409);
        expect(error.field).toBe('name');
        expect(error.message).toBe('team name already taken');
    });

    it('falls back to UNKNOWN for an unrecognized code while keeping the status', () => {
        const error = ApiError.fromProblem({ status: 500, code: 'SOMETHING_NEW' }, 500);

        expect(error.code).toBe(UNKNOWN_ERROR_CODE);
        expect(error.status).toBe(500);
        expect(error.field).toBeNull();
    });

    it('prefers the transport status over the problem status', () => {
        const error = ApiError.fromProblem({ status: 418, code: 'NOT_FOUND' }, 404);

        expect(error.status).toBe(404);
    });

    it('leaves field null when the problem is not field-anchored', () => {
        const error = ApiError.fromProblem({ code: 'NOT_FOUND', detail: 'gone' }, 404);

        expect(error.field).toBeNull();
    });
});
