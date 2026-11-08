import { describe, expect, it } from 'vitest';

import { organizersDtoToDomain } from '@/tournament-api/mappers/organizers.mapper.ts';
import type { OrganizersDto } from '@/tournament-api/dtos/organizers.dto.ts';

const dto: OrganizersDto = {
    lead: { email: 'lead@example.com' },
    coOrganizers: [
        { email: 'second@example.com', addedAt: '2026-02-01T09:00:00.000Z' },
        { email: 'third@example.com', addedAt: '2026-02-03T17:45:30.000Z' },
    ],
};

describe('organizersDtoToDomain', () => {
    it('maps the lead organizer and the co-organizers, parsing addedAt into Date objects', () => {
        const organizers = organizersDtoToDomain(dto);

        expect(organizers.lead).toEqual({ email: 'lead@example.com' });
        expect(
            organizers.coOrganizers.every((organizer) => organizer.addedAt instanceof Date),
        ).toBe(true);
        expect(organizers.coOrganizers.map((organizer) => organizer.addedAt.toISOString())).toEqual(
            ['2026-02-01T09:00:00.000Z', '2026-02-03T17:45:30.000Z'],
        );
    });

    it('keeps the co-organizers in the order the server sent them', () => {
        const organizers = organizersDtoToDomain(dto);

        expect(organizers.coOrganizers.map((organizer) => organizer.email)).toEqual([
            'second@example.com',
            'third@example.com',
        ]);
    });

    it('maps an empty co-organizer list to an empty list', () => {
        expect(organizersDtoToDomain({ ...dto, coOrganizers: [] }).coOrganizers).toEqual([]);
    });
});
