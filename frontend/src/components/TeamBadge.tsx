import { useState } from 'react'
import type { Team } from '../api/types'

/**
 * Crest image, or an initials disc when there is none — or when the one we
 * have fails to load. Fixtures added by hand carry no crest, and a provider
 * URL can rot, so the disc is the normal case rather than an edge case.
 */
export function TeamBadge({ team, size = 32 }: { team: Team; size?: number }) {
  const [failed, setFailed] = useState(false)
  const label = team.shortName ?? team.name
  if (team.crestUrl && !failed) {
    return (
      <img
        src={team.crestUrl}
        alt=""
        loading="lazy"
        width={size}
        height={size}
        onError={() => setFailed(true)}
        style={{ width: size, height: size }}
        className="shrink-0 rounded-full object-contain"
      />
    )
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
