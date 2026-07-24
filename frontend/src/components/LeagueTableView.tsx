import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { countryFlag } from '../lib/format'
import { useT } from '../i18n'

export interface TableRow {
  rank: number
  userId: number
  displayName: string
  country: string | null
  points: number
  scoredPredictions: number
  admin?: boolean
}

interface Props {
  rows: TableRow[]
  me?: TableRow | null
}

/** Shared ranked-standings list used by all league kinds. */
export function LeagueTableView({ rows, me }: Props) {
  const { user } = useAuth()
  const t = useT()
  const meVisible = rows.some((row) => row.userId === user?.id)

  const row = (entry: TableRow, highlight: boolean, suffix = '') => (
    <li key={`${entry.userId}${suffix}`} className={highlight ? 'bg-emerald-500/10' : ''}>
      <Link to={`/players/${entry.userId}`} className="flex items-center gap-3 px-4 py-3 active:bg-slate-800">
        <span className="w-8 text-right text-sm font-bold tabular-nums text-slate-400">{entry.rank}</span>
        <span aria-hidden>{countryFlag(entry.country) || '·'}</span>
        <span className="min-w-0 flex-1 truncate text-sm font-medium">
          {entry.displayName}
          {suffix && <span className="text-slate-400"> {t('common.you')}</span>}
          {entry.admin && <span className="ml-1 text-xs text-amber-400" title="League admin">★</span>}
        </span>
        <span className="text-xs text-slate-500">{t('leagues.scored', { count: entry.scoredPredictions })}</span>
        <span className="w-10 text-right text-base font-bold tabular-nums text-emerald-400">{entry.points}</span>
        <span className="text-slate-600">›</span>
      </Link>
    </li>
  )

  return (
    <div className="space-y-3">
      <ol className="divide-y divide-slate-800 overflow-hidden rounded-2xl border border-slate-800 bg-slate-900">
        {rows.map((entry) => row(entry, entry.userId === user?.id))}
      </ol>
      {!meVisible && me && (
        <ol className="overflow-hidden rounded-2xl border border-emerald-800 bg-emerald-500/10">
          {row(me, false, '-me')}
        </ol>
      )}
    </div>
  )
}
