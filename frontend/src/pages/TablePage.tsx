import { useState } from 'react'
import { useGlobalTable } from '../api/queries'
import { useAuth } from '../auth/AuthContext'
import { countryFlag } from '../lib/format'

export function TablePage() {
  const [page, setPage] = useState(0)
  const { data: table, isPending } = useGlobalTable(page)
  const { user } = useAuth()

  if (isPending || !table) {
    return <p className="p-6 text-center text-slate-400">Loading table…</p>
  }

  const meVisible = table.entries.some((entry) => entry.userId === user?.id)

  return (
    <div className="space-y-4 p-4">
      <header className="pt-2">
        <h1 className="text-xl font-bold">Global league</h1>
        <p className="mt-1 text-sm text-slate-400">{table.totalPlayers} players worldwide</p>
      </header>

      <ol className="divide-y divide-slate-800 overflow-hidden rounded-2xl border border-slate-800 bg-slate-900">
        {table.entries.map((entry) => (
          <li
            key={entry.userId}
            className={`flex items-center gap-3 px-4 py-3 ${entry.userId === user?.id ? 'bg-emerald-500/10' : ''}`}
          >
            <span className="w-8 text-right text-sm font-bold tabular-nums text-slate-400">{entry.rank}</span>
            <span aria-hidden>{countryFlag(entry.country) || '·'}</span>
            <span className="min-w-0 flex-1 truncate text-sm font-medium">{entry.displayName}</span>
            <span className="text-xs text-slate-500">{entry.scoredPredictions} scored</span>
            <span className="w-10 text-right text-base font-bold tabular-nums text-emerald-400">{entry.points}</span>
          </li>
        ))}
      </ol>

      {!meVisible && table.me && (
        <div className="flex items-center gap-3 rounded-2xl border border-emerald-800 bg-emerald-500/10 px-4 py-3">
          <span className="w-8 text-right text-sm font-bold tabular-nums text-slate-400">{table.me.rank}</span>
          <span aria-hidden>{countryFlag(table.me.country) || '·'}</span>
          <span className="min-w-0 flex-1 truncate text-sm font-medium">{table.me.displayName} (you)</span>
          <span className="w-10 text-right text-base font-bold tabular-nums text-emerald-400">{table.me.points}</span>
        </div>
      )}

      <div className="flex justify-between text-sm">
        <button
          type="button"
          disabled={page === 0}
          onClick={() => setPage((p) => p - 1)}
          className="rounded-lg bg-slate-800 px-4 py-2 font-medium disabled:opacity-30"
        >
          ← Previous
        </button>
        <button
          type="button"
          disabled={(page + 1) * table.size >= table.totalPlayers}
          onClick={() => setPage((p) => p + 1)}
          className="rounded-lg bg-slate-800 px-4 py-2 font-medium disabled:opacity-30"
        >
          Next →
        </button>
      </div>
    </div>
  )
}
