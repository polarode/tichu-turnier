import type { ProblemDetail } from '@/shared/api/problem-detail.ts';
import { isErrorCode, type ErrorCode } from '@/vocabulary/error-code.enum.ts';

/** Fallback code for failures that did not come from a recognizable backend problem. */
export const UNKNOWN_ERROR_CODE = 'UNKNOWN';
export type ApiErrorCode = ErrorCode | typeof UNKNOWN_ERROR_CODE;

interface ApiErrorInit {
    code: ApiErrorCode;
    status: number | null;
    field: string | null;
    message: string;
}

/**
 * A typed failure raised by the shared axios client. It carries the backend's stable
 * `code` (so UI can localize and branch) and, when the failure is field-anchored, the
 * offending `field` so forms can attach the message to the right input.
 */
export class ApiError extends Error {
    readonly code: ApiErrorCode;
    readonly status: number | null;
    readonly field: string | null;

    constructor({ code, status, field, message }: ApiErrorInit) {
        super(message);
        this.name = 'ApiError';
        this.code = code;
        this.status = status;
        this.field = field;
    }

    static fromProblem(problem: ProblemDetail, status: number | null): ApiError {
        return new ApiError({
            code: isErrorCode(problem.code) ? problem.code : UNKNOWN_ERROR_CODE,
            status: status ?? problem.status ?? null,
            field: problem.field ?? null,
            message: problem.detail ?? problem.title ?? 'Request failed',
        });
    }
}
