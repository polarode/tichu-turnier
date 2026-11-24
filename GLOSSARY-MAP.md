# Glossary Map

## Contexts

- [Tichu Rules](./backend/tichu-core/GLOSSARY.md): a single deal of Tichu and how it is scored; shared by both apps
- [Tichu Turnier](./GLOSSARY.md): running Tichu tournaments for teams the organizer registers (moves to `backend/turnier/` with the backend restructure)
- [Mittag](./backend/mittag/GLOSSARY.md): private circles of players recording their own games (working name)

## Relationships

- **Turnier → Tichu Rules**: a turnier Game is a Tichu Rules Game played at a table in a round; the tournament sets the Grand Tichu and Double Win bonuses.
- **Mittag → Tichu Rules**: a mittag Game is a Tichu Rules Game in a Circle, standalone or within a Match, with fixed 200 bonuses.
- **Turnier ↔ Mittag**: no shared data. Both have Players, Teams and an Owner, but they mean different things: a turnier Player belongs to one Team of one tournament, a mittag Player spans Circles; a turnier Team is registered, a mittag Team is just a pair.
