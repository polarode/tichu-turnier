/**
 * The organizer's role on one tournament (GLOSSARY.md): the lead organizer created it,
 * co-organizers were added later. The wire sends `"LEAD"` or `"CO"`.
 */
export const OrganizerRole = {
    LEAD: 'LEAD',
    CO: 'CO',
} as const;

export type OrganizerRole = (typeof OrganizerRole)[keyof typeof OrganizerRole];

export const ORGANIZER_ROLES = [OrganizerRole.LEAD, OrganizerRole.CO] as const;
