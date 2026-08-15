import { useEffect, useRef } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useLeagueActions } from '../api/queries'
import { useAuth } from '../auth/AuthContext'
import { clearPendingInvite, stashPendingInvite } from '../lib/invite'
import { useT } from '../i18n'

/** The manual screen, carrying the code that was in the link and the refusal. */
export function joinScreenFor(code: string, error: unknown): string {
  const params = new URLSearchParams({ code })
  if (error instanceof Error && error.message) {
    params.set('reason', error.message)
  }
  return `/table/join?${params}`
}

/**
 * Shareable invite link target (/join/CODE).
 *
 * <p>The code is stashed first, always — signed in or not. A session can expire
 * between the tap and the call, and the client answers a 401 by sending the
 * player to /login; without stashing first, the code is lost in transit and
 * nothing ever joins them. It is cleared once the join has an answer.
 *
 * <p>When the join is refused, the player is handed to the manual screen
 * <em>with the code filled in</em> and told why. Landing on an empty box asking
 * for a code that was in the link they just tapped is the definition of a dead
 * end.
 */
export function JoinDeepLinkPage() {
  const t = useT()
  const { code } = useParams()
  const { user } = useAuth()
  const { join } = useLeagueActions()
  const navigate = useNavigate()
  const fired = useRef(false)

  useEffect(() => {
    if (fired.current || !code) {
      return
    }
    fired.current = true
    stashPendingInvite(code)
    if (!user) {
      // PendingInviteHandler finishes the job once they are through signup
      navigate('/signup', { replace: true })
      return
    }
    join
      .mutateAsync({ code })
      .then((league) => {
        clearPendingInvite()
        navigate(`/table/league/${league.id}`, { replace: true })
      })
      .catch((e: unknown) => {
        clearPendingInvite()
        // the server says why — code unknown, already a member, league full —
        // and the player is the one who needs to read it
        navigate(joinScreenFor(code, e), { replace: true })
      })
  }, [code, user, join, navigate])

  return <p className="p-8 text-center text-slate-400">{t('joinLeague.joining')}</p>
}
