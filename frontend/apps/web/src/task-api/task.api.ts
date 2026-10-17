import axios from 'axios';

/**
 * The single axios instance for the Task API. Base URL is configurable via
 * `VITE_API_URL` (defaults to the local backend). Interceptors are the place to
 * attach auth headers or normalize errors across every request.
 */
export const taskApi = axios.create({
    baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
    headers: {
        'Content-Type': 'application/json',
    },
});

taskApi.interceptors.response.use(
    (response) => response,
    (error: unknown) => {
        // Central place to translate transport errors into domain errors.
        // Kept intentionally thin in the template.
        return Promise.reject(error);
    },
);
