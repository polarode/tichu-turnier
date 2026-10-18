# Tichu Turnier

A tournament manager for the card game Tichu: a platform owner provisions tournament organizers (admins), organizers run tournaments and their teams, and teams follow their matches through secret links.

## Language

### Accounts & access

**Admin**:
A person who creates and manages tournaments on behalf of the platform owner. Has an email/password account with a lifecycle status (`PENDING`, `ACTIVE`, `DISABLED`) and a role (`OWNER`, `ADMIN`).
_Avoid_: user, organizer (except in user stories)

**Owner**:
The admin with the `OWNER` role who provisions other admins by approving registration requests or issuing invites. Exactly one exists, created from configuration — never through an API.
_Avoid_: superadmin, root

**Admin invite**:
An owner-issued, single-use token that lets one named email register as an `ACTIVE` admin directly, skipping approval.
_Avoid_: magic link (reserved for teams)

### Teams & links

**Team**:
Two players competing together in a tournament, registered by an admin.
_Avoid_: participant, pair

**Team access link**:
The one secret link per team that grants the team access to their own matches. Multi-use: both team members share it as a bearer secret. Valid until rotated, revoked, or the tournament completes.
_Avoid_: token (when speaking of the whole link), login link

**Team session**:
The browser session a team obtains by exchanging their access link — an httpOnly cookie whose principal is the team itself, authorized only for that team's own reads and confirmations. Carries no per-player identity.
_Avoid_: user session
