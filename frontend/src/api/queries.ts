import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type { ScoringScale } from '../lib/scoring'
import type {
  AccountView,
  CompetitionView,
  GameweekSummary,
  GameweekView,
  LeagueDetail,
  LeagueSummary,
  LeagueTable,
  MatchView,
  PlayerGameweekView,
  SuggestionView,
  ScopedTable,
  Team,
  UserProfile,
} from './types'

/**
 * What each scoring tier is worth. Published by the server so the numbers
 * live only in ScoringEngine; it cannot change while the app is open.
 */
export function useScoringScale() {
  return useQuery({
    queryKey: ['scoring-scale'],
    queryFn: () => api<ScoringScale>('/api/rules/scoring'),
    staleTime: Infinity,
  })
}

export function useCurrentGameweek() {
  return useQuery({
    queryKey: ['gameweek', 'current'],
    queryFn: () => api<GameweekView>('/api/gameweeks/current'),
    refetchInterval: 30_000, // live scores flow in during matchdays
  })
}

export function useGameweek(gameweekId: number | null) {
  return useQuery({
    queryKey: ['gameweek', gameweekId],
    queryFn: () => api<GameweekView>(`/api/gameweeks/${gameweekId}`),
    enabled: gameweekId != null && gameweekId > 0,
    refetchInterval: 30_000,
  })
}

export function useGameweekHistory() {
  return useQuery({
    queryKey: ['gameweek', 'history'],
    queryFn: () => api<GameweekSummary[]>('/api/gameweeks'),
    staleTime: 60_000,
  })
}

export function usePlayerGameweek(gameweekId: number | null, playerId: string | undefined) {
  return useQuery({
    queryKey: ['gameweek', 'player', gameweekId ?? 'current', playerId],
    queryFn: () =>
      api<PlayerGameweekView>(
        gameweekId
          ? `/api/gameweeks/${gameweekId}/players/${playerId}`
          : `/api/gameweeks/current/players/${playerId}`,
      ),
    enabled: Boolean(playerId),
    refetchInterval: 30_000,
  })
}

export function useTeams() {
  return useQuery({
    queryKey: ['teams'],
    queryFn: () => api<Team[]>('/api/teams'),
    staleTime: 10 * 60_000,
  })
}

/**
 * Club search, run on the server so it reuses the one place that knows
 * "atletico" is Atlético and "munich" is München.
 */
export function useTeamSearch(query: string, competition: number | null, enabled: boolean) {
  const params = new URLSearchParams()
  if (query.trim()) {
    params.set('q', query.trim())
  }
  if (competition) {
    params.set('competition', String(competition))
  }
  return useQuery({
    queryKey: ['teams', 'search', query.trim(), competition],
    queryFn: () => api<Team[]>(`/api/teams?${params}`),
    enabled,
    staleTime: 60_000,
  })
}

/** One club by id, so the profile can show the club already chosen. */
export function useTeamById(id: number | null) {
  return useQuery({
    queryKey: ['teams', 'byId', id],
    queryFn: async () => (await api<Team[]>(`/api/teams?id=${id}`))[0] ?? null,
    enabled: id != null && id > 0,
    staleTime: 10 * 60_000,
  })
}

/**
 * Whether this device is actually subscribed, which is not the same question as
 * whether the browser granted permission: a device can hold permission with no
 * subscription at all — after turning them off, or after the site data was
 * cleared — and it must then be offered the switch again.
 */
export function usePushSubscribed(enabled: boolean) {
  return useQuery({
    queryKey: ['push', 'subscribed'],
    queryFn: () => api<{ subscribed: boolean }>('/api/push/subscriptions'),
    enabled,
    staleTime: 30_000,
  })
}

export function useCompetitions(domesticOnly = false) {
  return useQuery({
    queryKey: ['competitions', domesticOnly],
    queryFn: () => api<CompetitionView[]>(`/api/competitions${domesticOnly ? '?domesticOnly=true' : ''}`),
    staleTime: 10 * 60_000,
  })
}

export function useGlobalTable(page: number, size = 50) {
  return useQuery({
    queryKey: ['table', 'global', page, size],
    queryFn: () => api<LeagueTable>(`/api/leagues/global/table?page=${page}&size=${size}`),
    refetchInterval: 60_000,
  })
}

export function useScopedTable(kind: 'country' | 'club' | 'competition', page: number, size = 50) {
  return useQuery({
    queryKey: ['table', kind, page, size],
    queryFn: () => api<ScopedTable>(`/api/leagues/${kind}/table?page=${page}&size=${size}`),
    refetchInterval: 60_000,
  })
}

export function useMyLeagues() {
  return useQuery({
    queryKey: ['leagues', 'mine'],
    queryFn: () => api<LeagueSummary[]>('/api/leagues/mine'),
  })
}

export function useLeagueDetail(id: number) {
  return useQuery({
    queryKey: ['leagues', 'detail', id],
    queryFn: () => api<LeagueDetail>(`/api/leagues/${id}`),
    enabled: id > 0,
    refetchInterval: 60_000,
  })
}

export function useLeagueActions() {
  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['leagues'] })
  const create = useMutation({
    mutationFn: (input: { name: string }) =>
      api<LeagueDetail>('/api/leagues', { method: 'POST', body: JSON.stringify(input) }),
    onSettled: invalidate,
  })
  const join = useMutation({
    mutationFn: (input: { code: string }) =>
      api<LeagueDetail>('/api/leagues/join', { method: 'POST', body: JSON.stringify(input) }),
    onSettled: invalidate,
  })
  const leave = useMutation({
    mutationFn: (leagueId: number) =>
      api(`/api/leagues/${leagueId}/members/me`, { method: 'DELETE' }),
    onSettled: invalidate,
  })
  const regenerateCode = useMutation({
    mutationFn: (leagueId: number) =>
      api<LeagueDetail>(`/api/leagues/${leagueId}/regenerate-code`, { method: 'POST' }),
    onSettled: invalidate,
  })
  return { create, join, leave, regenerateCode }
}

export function usePredictMutation(gameweekId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { fixtureId: number; homeGoals: number; awayGoals: number }) =>
      api(`/api/gameweeks/${gameweekId}/fixtures/${input.fixtureId}/prediction`, {
        method: 'PUT',
        body: JSON.stringify({ homeGoals: input.homeGoals, awayGoals: input.awayGoals }),
      }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['gameweek'] }),
  })
}

export function useUpdateProfile() {
  return useMutation({
    mutationFn: (input: {
      displayName: string
      country: string | null
      favouriteClubTeamId: number | null
      favouriteCompetitionId: number | null
    }) => api<UserProfile>('/api/me', { method: 'PUT', body: JSON.stringify(input) }),
  })
}

// --- admin ---

export function useAdminGameweeks(enabled: boolean) {
  return useQuery({
    queryKey: ['admin', 'gameweeks'],
    queryFn: () => api<GameweekView[]>('/api/admin/gameweeks'),
    enabled,
  })
}

export function useAdminMatchPool(enabled: boolean) {
  return useQuery({
    queryKey: ['admin', 'matches'],
    queryFn: () => api<MatchView[]>('/api/admin/matches'),
    enabled,
  })
}

/** Ranked alternatives for a window, so a swap is a choice, not a search. */
export function useAdminSuggestions(from: string, to: string, enabled: boolean) {
  return useQuery({
    queryKey: ['admin', 'suggestions', from, to],
    queryFn: () =>
      api<SuggestionView[]>(`/api/admin/gameweeks/suggestions?from=${from}&to=${to}&size=40`),
    enabled,
  })
}

/** Accounts, searched by address or name. Admin screen only. */
export function useAdminAccounts(query: string, enabled: boolean) {
  return useQuery({
    queryKey: ['admin', 'users', query.trim()],
    queryFn: () => api<AccountView[]>(`/api/admin/users?q=${encodeURIComponent(query.trim())}`),
    enabled,
  })
}

export function useAdminActions() {
  const queryClient = useQueryClient()
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin'] })
    queryClient.invalidateQueries({ queryKey: ['gameweek'] })
  }
  const createGameweek = useMutation({
    mutationFn: (input: {
      season: string
      weekIndex: number
      windowStart: string
      windowEnd: string
    }) => api<GameweekView>('/api/admin/gameweeks', { method: 'POST', body: JSON.stringify(input) }),
    onSettled: invalidate,
  })
  const composeGameweek = useMutation({
    mutationFn: (input: {
      season: string
      weekIndex: number
      windowStart: string
      windowEnd: string
      countsTowardsTable: boolean
      size: number
    }) => api<GameweekView>('/api/admin/gameweeks/compose', { method: 'POST', body: JSON.stringify(input) }),
    onSettled: invalidate,
  })
  const setFixtures = useMutation({
    mutationFn: (input: { gameweekId: number; matchIds: number[] }) =>
      api<GameweekView>(`/api/admin/gameweeks/${input.gameweekId}/fixtures`, {
        method: 'PUT',
        body: JSON.stringify({ matchIds: input.matchIds }),
      }),
    onSettled: invalidate,
  })
  /** Tops a card up, leaving the fixtures already on it — and their predictions — alone. */
  const addFixtures = useMutation({
    mutationFn: (input: { gameweekId: number; matchIds: number[] }) =>
      api<GameweekView>(`/api/admin/gameweeks/${input.gameweekId}/fixtures`, {
        method: 'POST',
        body: JSON.stringify({ matchIds: input.matchIds }),
      }),
    onSettled: invalidate,
  })
  const deleteAccount = useMutation({
    mutationFn: (id: string) => api<void>(`/api/admin/users/${id}`, { method: 'DELETE' }),
    onSettled: invalidate,
  })
  const publish = useMutation({
    mutationFn: (gameweekId: number) =>
      api<GameweekView>(`/api/admin/gameweeks/${gameweekId}/publish`, { method: 'POST' }),
    onSettled: invalidate,
  })
  const sync = useMutation({
    mutationFn: () => api('/api/admin/sync/fixtures', { method: 'POST' }),
    onSettled: invalidate,
  })
  const simulateResult = useMutation({
    mutationFn: (input: { matchId: number; homeScore: number; awayScore: number }) =>
      api(`/api/admin/matches/${input.matchId}/result`, {
        method: 'POST',
        body: JSON.stringify({ homeScore: input.homeScore, awayScore: input.awayScore }),
      }),
    onSettled: invalidate,
  })
  return { createGameweek, composeGameweek, setFixtures, addFixtures, publish, sync, simulateResult,
           deleteAccount }
}
