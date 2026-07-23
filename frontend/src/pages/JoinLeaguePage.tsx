import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useLeagueActions } from '../api/queries'
import { ApiError } from '../api/client'

export function JoinLeaguePage() {
  const { join } = useLeagueActions()
  const navigate = useNavigate()
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    try {
      const league = await join.mutateAsync({ code })
      navigate(`/table/league/${league.id}`)
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        setError('That code was not found — check it with whoever invited you.')
      } else {
        setError(e instanceof Error ? e.message : 'Could not join')
      }
    }
  }

  return (
    <div className="space-y-6 p-4">
      <Link to="/table" className="text-sm text-slate-400">‹ Leagues</Link>
      <header>
        <h1 className="text-xl font-bold">Join a league</h1>
        <p className="mt-1 text-sm text-slate-400">Enter the 8-character invite code you were given.</p>
      </header>
      <form onSubmit={submit} className="space-y-4">
        <input
          type="text"
          required
          maxLength={12}
          placeholder="INVITE CODE"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          className="w-full rounded-xl border border-slate-700 bg-slate-900 px-4 py-3 text-center font-mono text-lg tracking-widest outline-none focus:border-emerald-500"
        />
        {error && <p className="text-sm text-red-400">{error}</p>}
        <button
          type="submit"
          disabled={join.isPending}
          className="w-full rounded-xl bg-emerald-500 py-3 font-semibold text-emerald-950 active:bg-emerald-400 disabled:opacity-50"
        >
          {join.isPending ? 'Joining…' : 'Join league'}
        </button>
      </form>
    </div>
  )
}
