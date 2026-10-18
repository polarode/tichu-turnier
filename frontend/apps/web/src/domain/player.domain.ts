/**
 * A named participant of a team, as returned by the team endpoints. Players are
 * tournament-scoped on the backend, but the wire view (and therefore this model) only
 * carries the team they belong to.
 */
export interface Player {
    id: string;
    teamId: string;
    name: string;
}
