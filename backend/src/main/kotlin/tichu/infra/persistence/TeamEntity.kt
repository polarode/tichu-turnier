package tichu.infra.persistence

import tichu.domain.AdminUserId
import tichu.domain.ForfeitCompensation
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TournamentId
import tichu.domain.Withdrawal
import tichu.domain.WithdrawalReason
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Persistence model for [Team]. Lives in the infra layer — the domain entity stays
 * framework-free.
 *
 * The players are a separate [PlayerEntity] table referencing this one with
 * ON DELETE CASCADE (see the V5 migration); the JPA adapter loads them per team
 * and hands them to the domain. Uniqueness of `name` is per tournament and backed
 * by a database constraint, so the application-level check cannot be raced around.
 * An update replaces the team's name and its two player rows (see
 * [JpaTeamRepository.save]).
 *
 * The withdrawal columns (V24) are all null or all set. [from] writes every field of
 * the domain team, so a save with no withdrawal clears the stored one.
 */
@Entity
@Table(name = "teams")
class TeamEntity(
    @Id
    val id: String,
    @Column(name = "tournament_id", nullable = false)
    val tournamentId: String,
    @Column(name = "name", nullable = false)
    val name: String,
    /** The [WithdrawalReason] name; null unless the team withdrew (V24 keeps the set whole). */
    @Column(name = "withdrawal_reason", nullable = true)
    val withdrawalReason: String? = null,
    /** The [ForfeitCompensation] name; null unless the team withdrew. */
    @Column(name = "forfeit_compensation", nullable = true)
    val forfeitCompensation: String? = null,
    @Column(name = "withdrawal_round", nullable = true)
    val withdrawalRound: Int? = null,
    @Column(name = "withdrawn_at", nullable = true)
    val withdrawnAt: Instant? = null,
    /** The withdrawing organizer's admin id; no foreign key, as with corrected_by (V23). */
    @Column(name = "withdrawn_by", nullable = true)
    val withdrawnBy: String? = null,
) {
    fun toDomain(players: List<PlayerEntity>): Team = Team(
        id = TeamId(id),
        tournamentId = TournamentId(tournamentId),
        name = name,
        players = players.map { it.toDomain() },
        withdrawal = toWithdrawal(),
    )

    /** The V24 check guarantees all five columns are set together, so the required values are present here. */
    private fun toWithdrawal(): Withdrawal? {
        val reason = withdrawalReason ?: return null
        return Withdrawal(
            reason = WithdrawalReason.valueOf(reason),
            compensation = ForfeitCompensation.valueOf(requireNotNull(forfeitCompensation)),
            roundNumber = requireNotNull(withdrawalRound),
            withdrawnAt = requireNotNull(withdrawnAt),
            withdrawnBy = AdminUserId(requireNotNull(withdrawnBy)),
        )
    }

    /** The same team row with its withdrawal columns replaced; the name and players are not touched here. */
    fun withWithdrawal(withdrawal: Withdrawal?): TeamEntity = TeamEntity(
        id = id,
        tournamentId = tournamentId,
        name = name,
        withdrawalReason = withdrawal?.reason?.name,
        forfeitCompensation = withdrawal?.compensation?.name,
        withdrawalRound = withdrawal?.roundNumber,
        withdrawnAt = withdrawal?.withdrawnAt,
        withdrawnBy = withdrawal?.withdrawnBy?.value,
    )

    companion object {
        /** The row of [team]; its withdrawal columns come from [withWithdrawal], the one place they are mapped. */
        fun from(team: Team): TeamEntity = TeamEntity(
            id = team.id.value,
            tournamentId = team.tournamentId.value,
            name = team.name,
        ).withWithdrawal(team.withdrawal)
    }
}
