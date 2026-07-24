import type { GameweekSummary } from '../api/types'

interface Props {
  history: GameweekSummary[] | undefined
  value: number | null
  onChange: (gameweekId: number | null) => void
  showPoints?: boolean
}

/** Switches between the live gameweek and past ones. */
export function GameweekPicker({ history, value, onChange, showPoints = true }: Props) {
  if (!history || history.length <= 1) {
    return null
  }
  return (
    <select
      value={value ?? ''}
      onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}
      className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-1.5 text-sm outline-none focus:border-emerald-500"
      aria-label="Choose gameweek"
    >
      <option value="">Current gameweek</option>
      {history.map((gw) => (
        <option key={gw.id} value={gw.id}>
          GW{gw.weekIndex} · {gw.type === 'MIDWEEK' ? 'Midweek' : 'Weekend'}
          {showPoints && gw.status === 'SCORED' ? ` · ${gw.myPoints} pts` : ''}
        </option>
      ))}
    </select>
  )
}
