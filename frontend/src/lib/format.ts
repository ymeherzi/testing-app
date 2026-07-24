import { currentLocale } from '../i18n'
import type { MessageKey } from '../i18n/en'

/** Human label for the time until a kickoff, e.g. "2h 05m" or "3d 4h". */
export function countdown(kickoffIso: string, now: Date = new Date()): string | null {
  const millis = new Date(kickoffIso).getTime() - now.getTime()
  if (millis <= 0) {
    return null
  }
  const totalMinutes = Math.floor(millis / 60_000)
  const days = Math.floor(totalMinutes / (60 * 24))
  const hours = Math.floor((totalMinutes % (60 * 24)) / 60)
  const minutes = totalMinutes % 60
  if (days > 0) {
    return `${days}d ${hours}h`
  }
  if (hours > 0) {
    return `${hours}h ${String(minutes).padStart(2, '0')}m`
  }
  return `${minutes}m`
}

export function kickoffDayLabel(kickoffIso: string): string {
  return new Date(kickoffIso).toLocaleDateString(currentLocale(), {
    weekday: 'long',
    day: 'numeric',
    month: 'short',
  })
}

export function kickoffTimeLabel(kickoffIso: string): string {
  return new Date(kickoffIso).toLocaleTimeString(currentLocale(), { hour: '2-digit', minute: '2-digit' })
}

/** Message key for a scoring tier, so the label follows the user's language. */
export function pointsLabelKey(points: number): MessageKey {
  switch (points) {
    case 3:
      return 'points.exact'
    case 2:
      return 'points.margin'
    case 1:
      return 'points.outcome'
    default:
      return 'points.missed'
  }
}

/** "FR" → 🇫🇷 (empty string when the code is absent). */
export function countryFlag(code: string | null): string {
  if (!code || code.length !== 2) {
    return ''
  }
  return String.fromCodePoint(...[...code.toUpperCase()].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65))
}
