import { ApiError } from '../api/client'
import { isMessageKey, type Translate } from '../i18n'

/**
 * What to show the player when a call fails.
 *
 * <p>Server messages are written in English. Where the server names a reason
 * it knows we can translate — a refused password, say — we say it in the
 * player's own language and keep the English sentence only as a fallback.
 */
export function explain(error: unknown, t: Translate, fallback: string): string {
  if (error instanceof ApiError && error.code && isMessageKey(error.code)) {
    return t(error.code)
  }
  return error instanceof Error ? error.message : fallback
}
