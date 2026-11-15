package tichu.infra.rest.web

import tichu.domain.ForfeitCompensation
import tichu.domain.ForfeitGame
import tichu.domain.Withdrawal
import tichu.domain.WithdrawalReason
import jakarta.validation.constraints.NotNull
import java.time.Instant

/** The body of `POST /api/tournaments/{id}/teams/{teamId}/withdrawal` (ADR 0011). */
data class TeamWithdrawalRequestDto(
    @field:NotNull
    val reason: WithdrawalReason?,
    @field:NotNull
    val compensation: ForfeitCompensation?,
)

/** Wire format of a team's Withdrawal; `null` on the team when it plays on. */
data class WithdrawalResponseDto(
    val reason: String,
    val compensation: String,
    val roundNumber: Int,
    val withdrawnAt: Instant,
) {
    companion object {
        fun from(withdrawal: Withdrawal): WithdrawalResponseDto = WithdrawalResponseDto(
            reason = withdrawal.reason.name,
            compensation = withdrawal.compensation.name,
            roundNumber = withdrawal.roundNumber,
            withdrawnAt = withdrawal.withdrawnAt,
        )
    }
}

/**
 * One Forfeit Game (ADR 0011). [awardedTeamId] is null when nobody is awarded; the points are
 * the awarded team's compensation, 0 and 0 when nobody is awarded.
 */
data class ForfeitGameResponseDto(
    val gameNumber: Int,
    val withdrawnTeamId: String,
    val awardedTeamId: String?,
    val victoryPoints: Int,
    val tichuPoints: Int,
) {
    companion object {
        fun from(forfeit: ForfeitGame): ForfeitGameResponseDto {
            val outcome = forfeit.outcome()
            val awardedTo = forfeit.awardedTo
            return ForfeitGameResponseDto(
                gameNumber = forfeit.gameNumber,
                withdrawnTeamId = forfeit.withdrawnTeam.value,
                awardedTeamId = awardedTo?.value,
                victoryPoints = awardedTo?.let { outcome.victoryPoints.getValue(it) } ?: 0,
                tichuPoints = awardedTo?.let { outcome.teamPoints.getValue(it) } ?: 0,
            )
        }
    }
}
