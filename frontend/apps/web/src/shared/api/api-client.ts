import axios from 'axios';

import { ApiError, UNKNOWN_ERROR_CODE } from '@/shared/api/api-error.ts';
import { parseProblemDetail } from '@/shared/api/problem-detail.ts';

/**
 * Resolve a path against the configured API base (`VITE_API_URL`). EventSource and
 * other non-axios transports need this because a root-relative path would otherwise
 * resolve against the frontend dev server, not the API.
 */
export function resolveApiUrl(path: string): string {
    const base = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
    return new URL(path, base).toString();
}

/**
 * The single axios instance every API slice uses. `withCredentials` makes the browser
 * send the httpOnly session cookie; the response interceptor turns any failure into a
 * typed [ApiError] carrying the backend's stable `code`.
 */
export const apiClient = axios.create({
    baseURL: resolveApiUrl('/'),
    withCredentials: true,
    headers: {
        'Content-Type': 'application/json',
    },
});

apiClient.interceptors.response.use(
    (response) => response,
    (error: unknown) => Promise.reject(toApiError(error)),
);

/** Translate any transport failure into a typed [ApiError], preserving a problem `code`. */
export function toApiError(error: unknown): ApiError {
    if (error instanceof ApiError) return error;

    if (axios.isAxiosError(error)) {
        const problem = parseProblemDetail(error.response?.data);
        if (problem) {
            return ApiError.fromProblem(problem, error.response?.status ?? null);
        }
        return new ApiError({
            code: UNKNOWN_ERROR_CODE,
            status: error.response?.status ?? null,
            field: null,
            message: error.message,
        });
    }

    return new ApiError({
        code: UNKNOWN_ERROR_CODE,
        status: null,
        field: null,
        message: 'Unexpected error',
    });
}
