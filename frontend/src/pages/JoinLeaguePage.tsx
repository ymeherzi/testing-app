import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useLeagueActions } from '../api/queries'
import { ApiError } from '../api/client'
import { useT } from '../i18n'

export function JoinLeaguePage() {
  const t = useT()
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
        setError(t('joinLeague.notFound'))
      } else {
        setError(e instanceof Error ? e.message : t('joinLeague.failed'))
      }
    }
  }

  return (
    <div className="space-y-6 p-4">
      <Link to="/table" className="text-sm text-slate-400">{t('nav.backToLeagues')}</Link>
      <header>
        <h1 className="text-xl font-bold">{t('joinLeague.title')}</h1>
        <p className="mt-1 text-sm text-slate-400">{t('joinLeague.subtitle')}</p>
      </header>
      <form onSubmit={submit} className="space-y-4">
        <input
          type="text"
          required
          maxLength={12}
          placeholder={t('joinLeague.codePlaceholder')}
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
          {join.isPending ? t('joinLeague.pending') : t('joinLeague.submit')}
        </button>
      </form>
    </div>
  )
}
