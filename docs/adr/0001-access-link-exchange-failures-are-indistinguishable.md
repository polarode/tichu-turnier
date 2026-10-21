# Access-link exchange failures are indistinguishable

Redeeming an Access Link (`POST /api/team/auth/exchange`) fails with one generic
rejection for all causes — unknown token, revoked link, expired link, rate limiting.
The backend deliberately does not tell the client *why* redemption failed, and the
team UI must not distinguish either: one localized error screen, no hints, no retry.

Why: a failed redemption means the presenter of the link either holds a dead token
or is guessing. Any difference in response between "this token never existed" and
"this token existed but was revoked" would let an attacker enumerate valid links and
probe revocation state. Rate-limited attempts are also folded into the same rejection
so probing cannot distinguish "too many guesses" from "dead link" either.

## Consequences

- Legitimate recovery paths are organizer-side: Rotate a link (or reveal a fresh one)
  and hand it out again. The error screen points to the organizer, not to self-service.
- The UI must never branch on exchange-failure details, including HTTP status.
- Adding a "why did my link fail?" diagnostic later would require deliberately
  weakening this property — treat such requests with suspicion.
