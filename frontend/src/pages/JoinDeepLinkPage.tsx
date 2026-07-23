import { useEffect, useRef } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useLeagueActions } from '../api/queries'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { stashPendingInvite } from '../lib/invite'

/**
 * Shareable invite link target (/join/CODE). Authenticated users join
 * immediately; everyone else has the code stashed through signup/login and
 * PendingInviteHandler completes the join once they're in.
 */
export function JoinDeepLinkPage() {
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
    if (!user) {
      stashPendingInvite(code)
      navigate('/signup', { replace: true })
      return
    }
    join
      .mutateAsync({ code })
      .then((league) => navigate(`/table/league/${league.id}`, { replace: true }))
      .catch((e) => {
        if (e instanceof ApiError && e.status === 409) {
          // already a member — just open the leagues hub
          navigate('/table', { replace: true })
        } else {
          navigate('/table/join', { replace: true })
        }
      })
  }, [code, user, join, navigate])

  return <p className="p-8 text-center text-slate-400">Joining league…</p>
}
