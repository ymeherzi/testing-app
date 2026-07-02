interface Props {
  label: string
  value: number
  onChange: (value: number) => void
  disabled?: boolean
}

export function ScoreStepper({ label, value, onChange, disabled }: Props) {
  return (
    <div className="flex flex-col items-center gap-1">
      <button
        type="button"
        aria-label={`${label}: one more goal`}
        disabled={disabled || value >= 20}
        onClick={() => onChange(value + 1)}
        className="h-8 w-10 rounded-lg bg-slate-800 text-lg font-bold text-slate-200 active:bg-slate-700 disabled:opacity-30"
      >
        +
      </button>
      <span className="w-10 text-center text-2xl font-bold tabular-nums">{value}</span>
      <button
        type="button"
        aria-label={`${label}: one goal fewer`}
        disabled={disabled || value <= 0}
        onClick={() => onChange(value - 1)}
        className="h-8 w-10 rounded-lg bg-slate-800 text-lg font-bold text-slate-200 active:bg-slate-700 disabled:opacity-30"
      >
        −
      </button>
    </div>
  )
}
