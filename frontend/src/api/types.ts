export interface UserProfile {
  /** public, unguessable id — sequential ids never leave the server */
  id: string
  email: string
  displayName: string
  country: string | null
  favouriteClubTeamId: number | null
  admin: boolean
  /** false until the welcome guide has been finished or skipped */
  guideSeen: boolean
}

export interface AuthResponse {
  token: string | null
  user: UserProfile | null
  /** true when a six-digit code was emailed and is needed to continue */
  verificationRequired: boolean
  email: string
  deviceToken: string | null
}

export interface AuthOptions {
  google: boolean
}

export interface Team {
  id: number
  name: string
  shortName: string | null
  crestUrl: string | null
}

export interface PredictionView {
  homeGoals: number
  awayGoals: number
  points: number | null
}

export interface FixtureView {
  fixtureId: number
  competitionCode: string
  competitionName: string
  homeTeam: Team
  awayTeam: Team
  kickoffUtc: string
  matchStatus: string
  homeScore: number | null
  awayScore: number | null
  locked: boolean
  prediction: PredictionView | null
}

export interface GameweekView {
  id: number
  season: string
  weekIndex: number
  type: 'WEEKEND' | 'MIDWEEK'
  status: 'DRAFT' | 'PUBLISHED' | 'SCORED'
  windowStart: string
  windowEnd: string
  /** false for a warm-up round: points are shown but never ranked */
  countsTowardsTable: boolean
  fixtures: FixtureView[]
}

export interface MatchView {
  id: number
  competitionCode: string
  homeTeam: Team
  awayTeam: Team
  kickoffUtc: string
  status: string
  homeScore: number | null
  awayScore: number | null
}

export interface TableEntry {
  rank: number
  userId: string
  displayName: string
  country: string | null
  points: number
  scoredPredictions: number
}

export interface LeagueTable {
  entries: TableEntry[]
  me: TableEntry | null
  page: number
  size: number
  totalPlayers: number
}

export interface GameweekSummary {
  id: number
  season: string
  weekIndex: number
  type: 'WEEKEND' | 'MIDWEEK'
  status: 'DRAFT' | 'PUBLISHED' | 'SCORED'
  windowStart: string
  windowEnd: string
  countsTowardsTable: boolean
  fixtureCount: number
  myPoints: number
  myPredictions: number
}

export interface PlayerGameweekView {
  playerId: string
  displayName: string
  country: string | null
  gameweekId: number
  weekIndex: number
  season: string
  points: number
  revealedCount: number
  hiddenCount: number
  fixtures: FixtureView[]
}

export interface ScopedTable {
  available: boolean
  country: string | null
  clubTeamId: number | null
  clubName: string | null
  clubCrestUrl: string | null
  table: LeagueTable | null
}

export interface MemberEntry {
  rank: number
  userId: string
  displayName: string
  country: string | null
  points: number
  scoredPredictions: number
  admin: boolean
}

export interface LeagueSummary {
  id: number
  name: string
  inviteCode: string
  admin: boolean
  memberCount: number
  myRank: number | null
  myPoints: number
}

export interface LeagueDetail {
  id: number
  name: string
  inviteCode: string
  admin: boolean
  maxMembers: number
  members: MemberEntry[]
  me: MemberEntry | null
}
