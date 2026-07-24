import type { GameweekSummary } from '../api/types'
import { useT } from '../i18n'

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
  const t = useT()
  if (!history || history.length <= 1) {
    return null
  }
  return (
    <select
      value={value ?? history[0].id}
      onChange={(e) => onChange(Number(e.target.value))}
      className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-1.5 text-sm outline-none focus:border-emerald-500"
      aria-label={t('predict.chooseGameweek')}
    >
      {history.map((gw) => (
        <option key={gw.id} value={gw.id}>
          {t('predict.gameweekLabel', { index: gw.weekIndex })}
          {showPoints && gw.myPredictions > 0 ? ` · ${t('common.points', { count: gw.myPoints })}` : ''}
        </option>
      ))}
    </select>
  )
}
