import type { GameweekSummary } from '../api/types'

interface Props {
  history: GameweekSummary[] | undefined
  value: number | null
  onChange: (gameweekId: number) => void
  showPoints?: boolean
}

/**
 * Switches between gameweeks. Gameweeks are identified by number alone —
 * they span several days, so neither a cadence label nor a single date
 * describes them honestly.
 */
export function GameweekPicker({ history, value, onChange, showPoints = true }: Props) {
  if (!history || history.length <= 1) {
    return null
  }
  return (
    <select
      value={value ?? history[0].id}
      onChange={(e) => onChange(Number(e.target.value))}
      className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-1.5 text-sm outline-none focus:border-emerald-500"
      aria-label="Choose gameweek"
    >
      {history.map((gw) => (
        <option key={gw.id} value={gw.id}>
          GW{gw.weekIndex}
          {showPoints && gw.myPredictions > 0 ? ` · ${gw.myPoints} pts` : ''}
        </option>
      ))}
    </select>
  )
}
