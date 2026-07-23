import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useLeagueActions } from '../api/queries'

export function CreateLeaguePage() {
  const { create } = useLeagueActions()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    try {
      const league = await create.mutateAsync({ name })
      navigate(`/table/league/${league.id}`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not create the league')
    }
  }

  return (
    <div className="space-y-6 p-4">
      <Link to="/table" className="text-sm text-slate-400">‹ Leagues</Link>
      <header>
        <h1 className="text-xl font-bold">Create a league</h1>
        <p className="mt-1 text-sm text-slate-400">
          You'll get an invite link to share — friends who join compete from the current gameweek onward.
        </p>
      </header>
      <form onSubmit={submit} className="space-y-4">
        <input
          type="text"
          required
          minLength={3}
          maxLength={60}
          placeholder="League name (e.g. Office Legends)"
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="w-full rounded-xl border border-slate-700 bg-slate-900 px-4 py-3 outline-none focus:border-emerald-500"
        />
        {error && <p className="text-sm text-red-400">{error}</p>}
        <button
          type="submit"
          disabled={create.isPending}
          className="w-full rounded-xl bg-emerald-500 py-3 font-semibold text-emerald-950 active:bg-emerald-400 disabled:opacity-50"
        >
          {create.isPending ? 'Creating…' : 'Create league'}
        </button>
      </form>
    </div>
  )
}
