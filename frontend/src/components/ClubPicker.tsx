import { useState } from 'react'
import { useCompetitions, useTeamSearch } from '../api/queries'
import { TeamBadge } from './TeamBadge'
import { useT } from '../i18n'
import type { Team } from '../api/types'

/**
 * Choosing one club out of several hundred.
 *
 * <p>Two rules shape it. The club already chosen is held here, not read back
 * out of the visible list — otherwise filtering it off screen and pressing
 * Save would wipe the player's club and drop them out of its league. And
 * nothing is listed until two letters are typed, since a list of everything
 * is what this screen exists to replace.
 */
export function ClubPicker({
  value,
  selected,
  onChange,
}: {
  value: number | null
  /** The chosen club as we know it, so it can be shown without searching for it. */
  selected: Team | null
  onChange: (club: Team | null) => void
}) {
  const t = useT()
  const [query, setQuery] = useState('')
  const [competition, setCompetition] = useState<number | null>(null)
  // leagues only: a club is filed under the league it plays in, so filtering
  // by a cup would narrow the list to nothing
  const { data: competitions } = useCompetitions(true)
  const searching = query.trim().length >= 2
  const { data: results, isFetching } = useTeamSearch(query, competition, searching)

  const inputClass =
    'w-full rounded-xl border border-slate-700 bg-slate-900 px-4 py-3 outline-none focus:border-emerald-500'

  return (
    <div className="space-y-2">
      <span className="text-sm text-slate-400">{t('profile.club')}</span>

      {/* the current choice, always visible, whatever the filter says */}
      <div className="flex items-center justify-between gap-3 rounded-xl border border-slate-700 bg-slate-900 px-4 py-3">
        {value && selected ? (
          <span className="flex min-w-0 items-center gap-2">
            <TeamBadge team={selected} />
            <span className="truncate text-slate-100">{selected.name}</span>
          </span>
        ) : (
          <span className="text-slate-500">{t('profile.notSet')}</span>
        )}
        {value && (
          <button
            type="button"
            onClick={() => onChange(null)}
            className="shrink-0 text-xs font-medium text-slate-400 underline"
          >
            {t('profile.clearClub')}
          </button>
        )}
      </div>

      <div className="flex gap-2">
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={t('profile.searchClub')}
          className={inputClass}
        />
        <select
          value={competition ?? ''}
          onChange={(e) => setCompetition(e.target.value ? Number(e.target.value) : null)}
          className="rounded-xl border border-slate-700 bg-slate-900 px-2 py-3 text-sm outline-none focus:border-emerald-500"
        >
          <option value="">{t('profile.allCompetitions')}</option>
          {competitions?.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </div>

      {!searching ? (
        <p className="text-xs text-slate-500">{t('profile.searchClubHint')}</p>
      ) : isFetching && !results ? (
        <p className="text-xs text-slate-500">{t('common.loading')}</p>
      ) : results && results.length > 0 ? (
        <ul className="max-h-64 space-y-1 overflow-y-auto">
          {results.map((club) => (
            <li key={club.id}>
              <button
                type="button"
                onClick={() => {
                  onChange(club)
                  setQuery('')
                }}
                className={`flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left ${
                  club.id === value ? 'bg-emerald-500/15 text-emerald-300' : 'text-slate-200 active:bg-slate-800'
                }`}
              >
                <TeamBadge team={club} />
                <span className="truncate">{club.name}</span>
                {club.tla && <span className="ml-auto shrink-0 text-xs text-slate-500">{club.tla}</span>}
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-xs text-slate-500">{t('profile.noClubFound', { query })}</p>
      )}
    </div>
  )
}
