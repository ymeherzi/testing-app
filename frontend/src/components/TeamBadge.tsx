import type { Team } from '../api/types'

/** Crest image, or an initials disc when the provider has no crest. */
export function TeamBadge({ team, size = 32 }: { team: Team; size?: number }) {
  const label = team.shortName ?? team.name
  if (team.crestUrl) {
    return <img src={team.crestUrl} alt="" width={size} height={size} className="shrink-0 rounded-full object-contain" />
  }
  const initials = label
    .split(/\s+/)
    .map((word) => word[0])
    .join('')
    .slice(0, 3)
    .toUpperCase()
  return (
    <span
      aria-hidden
      style={{ width: size, height: size }}
      className="flex shrink-0 items-center justify-center rounded-full bg-slate-700 text-[10px] font-bold text-slate-200"
    >
      {initials}
    </span>
  )
}
