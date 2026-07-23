import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useLeagueActions } from '../api/queries'
import { popPendingInvite } from '../lib/invite'

/**
 * Mounted inside the authenticated shell: completes an invite that was
 * stashed before signup/login (deep link → auth → auto-join).
 */
export function PendingInviteHandler() {
  const { join } = useLeagueActions()
  const navigate = useNavigate()
  const fired = useRef(false)

  useEffect(() => {
    if (fired.current) {
      return
    }
    const code = popPendingInvite()
    if (!code) {
      return
    }
    fired.current = true
    join
      .mutateAsync({ code })
      .then((league) => navigate(`/table/league/${league.id}`))
      .catch(() => navigate('/table'))
  }, [join, navigate])

  return null
}
