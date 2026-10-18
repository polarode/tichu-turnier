import type { Tournament, TournamentSettings } from '@/domain/tournament.domain.ts';
import type {
    TournamentDto,
    TournamentSettingsDto,
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

/** Domain → wire, used when building create/update request payloads. */
export function tournamentSettingsToDto(settings: TournamentSettings): TournamentSettingsDto {
    return {
        allowGrandTichu: settings.allowGrandTichu,
    };
}
