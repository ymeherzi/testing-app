import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useLeagueActions } from '../api/queries'
import { clearPendingInvite, peekPendingInvite } from '../lib/invite'
import { joinScreenFor } from '../pages/JoinDeepLinkPage'

/**
 * Mounted inside the authenticated shell: completes an invite that was stashed
 * before signup or login (deep link → auth → auto-join).
 *
 * <p>The code is read without being consumed, and only forgotten once the
 * server has answered. Taking it first meant a refused or interrupted call —
 * an expired token, no signal on the way out of the tunnel — lost the
 * invitation for good, with the player left in the game and not in the league.
 *
 * <p>A failure is not swallowed either: the player lands on the manual screen
 * with the code already filled in and the reason in front of them.
 */
export function PendingInviteHandler() {
  const { join } = useLeagueActions()
  const navigate = useNavigate()
  const fired = useRef(false)

  useEffect(() => {
    if (fired.current) {
      return
    }
    const code = peekPendingInvite()
    if (!code) {
      return
    }
    fired.current = true
    join
      .mutateAsync({ code })
      .then((league) => {
        clearPendingInvite()
        navigate(`/table/league/${league.id}`)
      })
      .catch((e: unknown) => {
        clearPendingInvite()
        navigate(joinScreenFor(code, e))
      })
  }, [join, navigate])

  return null
}
