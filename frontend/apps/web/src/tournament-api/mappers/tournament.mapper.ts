import type {
    Tournament,
    TournamentSettings,
    TournamentWriteInput,
} from '@/domain/tournament.domain.ts';
import type {
    TournamentDto,
    TournamentSettingsDto,
    TournamentWriteRequestDto,
} from '@/tournament-api/dtos/tournament.dto.ts';

/** Pure mapping functions. Unit-tested in isolation (see `__tests__`). */
export function tournamentDtoToDomain(dto: TournamentDto): Tournament {
    return {
        id: dto.id,
        name: dto.name,
        description: dto.description,
        maxTeams: dto.maxTeams,
        settings: tournamentSettingsDtoToDomain(dto.settings),
        status: dto.status,
        createdAt: new Date(dto.createdAt),
        updatedAt: new Date(dto.updatedAt),
    };
}

export function tournamentSettingsDtoToDomain(dto: TournamentSettingsDto): TournamentSettings {
    return {
        allowGrandTichu: dto.allowGrandTichu,
    };
}

/** Domain settings → wire, used when building create/update request payloads. */
export function tournamentSettingsToDto(settings: TournamentSettings): TournamentSettingsDto {
    return {
        allowGrandTichu: settings.allowGrandTichu,
    };
}

/** Domain write input → wire payload; keeps the DTO out of the hooks. */
export function tournamentWriteInputToDto(
    input: TournamentWriteInput,
): TournamentWriteRequestDto {
    return {
        name: input.name,
        description: input.description,
        maxTeams: input.maxTeams,
        settings: tournamentSettingsToDto(input.settings),
    };
}
