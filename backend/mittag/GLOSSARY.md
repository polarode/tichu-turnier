# Mittag

A private games app for circles of card players who know each other, such as a lunch
group at work: they record the games they play and follow their own history and stats.
"Mittag" is a working name. Tichu rules terms are in the
[Tichu Rules glossary](../tichu-core/GLOSSARY.md).

## Language

### Circles & people

**Circle**:
A group of people who play together and share one history: every Game and Match
belongs to exactly one Circle. A deployment hosts any number of Circles.
_Avoid_: group, club, tenant

**Circle Membership**:
A Player's place in one Circle, which makes them pickable there. Archiving a
membership hides the Player from that Circle's pickers but keeps their history; it
leaves their other Circles untouched.
_Avoid_: member, roster entry

**Player**:
A person who plays, with a name unique across all Circles. Takes part in any number of
Circles through Circle Memberships; renaming keeps their history.
_Avoid_: user, member, account

**Guest**:
A name typed into a seat for one Game, standing for someone who is not a Player. Has no
identity beyond that Game and never appears in any statistic.
_Avoid_: guest player, placeholder player, anonymous

**Account**:
A login, linked to one or more Players, e.g. one person and their family members. Only
the linked Account sees a Player's statistics across Circles.
_Avoid_: user, admin, player

**Owner**:
The single Account that exists from configuration and runs the deployment.
_Avoid_: superadmin, root

**Circle Admin**:
An Account that manages a Circle: its Shared Login, its Circle Memberships and merging
duplicate Players. Whoever creates a Circle becomes one, and any Circle Admin may make
another; a Circle always keeps at least one.
_Avoid_: owner (the deployment role), moderator

**Shared Login**:
A Circle's own address and shared password, for anyone at the table without an
Account: it lets them enter Games and see everything in that Circle, but not change
or delete past Games.
_Avoid_: guest access, guest login, table access

### Play

**Standalone Game**:
A Game that belongs to no Match: the default when people simply play a few deals in
whatever line-up is at the table.
_Avoid_: loose hand, single game

**Match**:
A series of Games between two Sides, played until a Side reaches the Target. Ends
**Won**, as a **Draw** (players chose to stop on a tie) or **Abandoned** (stopped
before the Target, with no winner).
_Avoid_: series, game (a Game is one deal)

**Side**:
One of a Match's two halves. Its line-up may change between Games when a Player or
Guest substitutes, so each Game records who played; a Match win credits everyone who
played for the winning Side.
_Avoid_: team (a Team is a pair of Players)

**Target**:
The total of Game Scores a Side must reach to win a Match, typically 500 or 1000.
When both Sides reach it in the same Game, the higher total wins; on a tie the players
choose a Draw or another Game.
_Avoid_: goal, limit, target points

**Team**:
Two Players who played together in a Game, regardless of when or in which Match. Not
created or named; it exists through the Games the pair played.
_Avoid_: pair, partnership, side

**Correction**:
A change to a recorded Game by someone with an Account, made in place and stamped with
who changed it and when.
_Avoid_: edit, override
