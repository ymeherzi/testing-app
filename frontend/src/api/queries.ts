import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type { GameweekView, LeagueTable, MatchView, Team, UserProfile } from './types'

export function useCurrentGameweek() {
  return useQuery({
    queryKey: ['gameweek', 'current'],
    queryFn: () => api<GameweekView>('/api/gameweeks/current'),
    refetchInterval: 60_000,
  })
}

export function useTeams() {
  return useQuery({
    queryKey: ['teams'],
    queryFn: () => api<Team[]>('/api/teams'),
    staleTime: 10 * 60_000,
  })
}

export function useGlobalTable(page: number, size = 50) {
  return useQuery({
    queryKey: ['table', 'global', page, size],
    queryFn: () => api<LeagueTable>(`/api/leagues/global/table?page=${page}&size=${size}`),
  })
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
    mutationFn: (input: { displayName: string; country: string | null; favouriteClubTeamId: number | null }) =>
      api<UserProfile>('/api/me', { method: 'PUT', body: JSON.stringify(input) }),
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
      type: 'WEEKEND' | 'MIDWEEK'
      windowStart: string
      windowEnd: string
    }) => api<GameweekView>('/api/admin/gameweeks', { method: 'POST', body: JSON.stringify(input) }),
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
  return { createGameweek, setFixtures, publish, sync, simulateResult }
}
