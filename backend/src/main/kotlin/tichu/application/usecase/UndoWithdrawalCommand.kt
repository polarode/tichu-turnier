package tichu.application.usecase

import tichu.application.api.UndoWithdrawal
import tichu.domain.AdminUserId
import tichu.domain.GameRepository
import tichu.domain.RoundRepository
import tichu.domain.TableConfirmationRepository
import tichu.domain.TableKey
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentEvents
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.TeamNotWithdrawnException
import tichu.vocabulary.WithdrawalFinalException
import org.springframework.transaction.annotation.Transactional

/**
 * Undoes a Withdrawal (ADR 0011). A plain class wired as a bean in [UseCaseConfiguration].
 *
 * One transaction: the tournament is row-locked first, then the organizer check runs
 * ([TournamentAccess]). The withdrawal is final once the results are published, once a
 * later round is drawn, or, in a completed tournament, when undoing would leave its table
 * unfinished: the withdrawal produced Forfeit Games there, or the team had not confirmed.
 *
 * Otherwise, when the withdrawal produced Forfeit Games at its table, every confirmation of
 * that table is removed, so the table returns to Playing (ADR 0011). The team's withdrawal
 * is then cleared.
 */
@Transactional
class UndoWithdrawalCommand(
    private val tournamentRepository: TournamentRepository,
    private val tournamentAccess: TournamentAccess,
    private val roundRepository: RoundRepository,
    private val teamRepository: TeamRepository,
    private val gameRepository: GameRepository,
    private val confirmationRepository: TableConfirmationRepository,
    private val tournamentEvents: TournamentEvents,
) : UndoWithdrawal {

    override fun undo(tournamentId: TournamentId, teamId: TeamId, admin: AdminUserId) {
        // Lock before reading, so a request that waited sees the state the first one committed.
        tournamentRepository.lock(tournamentId)
        val tournament = tournamentAccess.organizedOrThrow(tournamentId, admin)
        val team = teamRepository.findById(teamId, tournamentId)
            ?: throw NotFoundException("Team ${teamId.value} not found in tournament ${tournamentId.value}")
        val withdrawal = team.withdrawal ?: throw TeamNotWithdrawnException("Team ${teamId.value} is not withdrawn")

        if (tournament.status == TournamentStatus.PUBLISHED) {
            throw WithdrawalFinalException("Tournament ${tournamentId.value} is published; the withdrawal is final")
        }
        if (roundRepository.findByTournament(tournamentId).any { it.number > withdrawal.roundNumber }) {
            throw WithdrawalFinalException(
                "Round ${withdrawal.roundNumber} is followed by a drawn round; the withdrawal is final",
            )
        }

        val round = roundRepository.findByTournamentAndNumber(tournamentId, withdrawal.roundNumber)
        val table = round?.pairing?.tables?.firstOrNull { it.seats(teamId) }
        if (round != null && table != null) {
            val key = TableKey(tournamentId, round.number, table.id.value)
            // Shared with confirmations and game writes, so the table cannot change under this undo.
            roundRepository.lockTable(key)
            val confirmed = confirmationRepository.findByTable(key)
            val producedForfeits = tableForfeits(
                round = round,
                table = table,
                games = gameRepository.findByTable(tournamentId, round.number, table.id.value),
                withdrawals = mapOf(teamId to withdrawal),
            ).isNotEmpty()

            if (tournament.status == TournamentStatus.COMPLETED && (producedForfeits || teamId !in confirmed)) {
                throw WithdrawalFinalException(
                    "Table ${table.id.value} of round ${round.number} is completed; undoing would leave it unfinished",
                )
            }
            if (producedForfeits) {
                confirmed.forEach { confirmationRepository.remove(key, it) }
            }
        }

        teamRepository.updateWithdrawal(teamId, tournamentId, null)

        tournamentEvents.publish(TournamentEvent(TournamentEventType.TEAM_WITHDRAWAL_CHANGED, tournamentId))
    }
}
