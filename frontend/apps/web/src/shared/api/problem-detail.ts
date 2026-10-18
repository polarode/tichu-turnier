import { z } from 'zod';

/**
 * The RFC 7807 problem document the API returns for every failure. Only the members
 * the frontend acts on are modelled; the schema is lenient so unknown problem
 * responses still parse rather than collapsing into an opaque transport error.
 */
export const problemDetailSchema = z.object({
    status: z.number().optional(),
    title: z.string().optional(),
    detail: z.string().optional(),
    code: z.string().optional(),
    field: z.string().optional(),
});

export type ProblemDetail = z.infer<typeof problemDetailSchema>;

/** Parse an unknown response body as a problem document; null when it is not one. */
export function parseProblemDetail(data: unknown): ProblemDetail | null {
    const result = problemDetailSchema.safeParse(data);
    return result.success ? result.data : null;
}
