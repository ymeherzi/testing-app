import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useCompetitions } from '../api/queries'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { Team } from '../api/types'
import { countries } from '../lib/countries'
import { peekPendingInvite } from '../lib/invite'
import { useT } from '../i18n'
import { ClubPicker } from '../components/ClubPicker'
import { VerifyCodeForm } from '../auth/VerifyCodeForm'
import { GoogleSignInButton } from '../auth/GoogleSignInButton'

export function SignupPage() {
  const { signup } = useAuth()
  const t = useT()
  const navigate = useNavigate()
  // leagues only: nobody supports a cup
  const { data: championships } = useCompetitions(true)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [country, setCountry] = useState('')
  const [club, setClub] = useState<Team | null>(null)
  const [competitionId, setCompetitionId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [pendingEmail, setPendingEmail] = useState<string | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const outcome = await signup({
        email,
        password,
        displayName,
        country: country || null,
        favouriteClubTeamId: club?.id ?? null,
        favouriteCompetitionId: competitionId,
      })
      if (outcome.signedIn) {
        navigate('/')
      } else {
        setPendingEmail(outcome.email)
      }
    } catch (e) {
      if (e instanceof ApiError && e.errors) {
        setError(Object.values(e.errors).join(' · '))
      } else {
        setError(e instanceof Error ? e.message : t('auth.signupFailed'))
      }
    } finally {
      setBusy(false)
    }
  }

  const inputClass =
    'w-full rounded-xl border border-slate-700 bg-slate-900 px-4 py-3 outline-none focus:border-emerald-500'

  if (pendingEmail) {
    return <VerifyCodeForm email={pendingEmail} />
  }

  return (
    <div className="mx-auto flex min-h-dvh max-w-lg flex-col justify-center bg-slate-950 p-6 text-slate-100">
      <h1 className="mb-1 text-center text-3xl font-extrabold">{t('auth.signupTitle')}</h1>
      {peekPendingInvite() && (
        <p className="mb-4 rounded-xl border border-emerald-800 bg-emerald-500/10 px-4 py-3 text-center text-sm text-emerald-300">
          {t('auth.inviteBanner')}
        </p>
      )}
      <p className="mb-8 text-center text-sm text-slate-400">
        {t('auth.signupSubtitle')}
      </p>
      <form onSubmit={submit} className="space-y-4">
        <input type="email" required placeholder={t('auth.email')} autoComplete="email" value={email}
               onChange={(e) => setEmail(e.target.value)} className={inputClass} />
        <input type="password" required minLength={8} placeholder={t('auth.passwordHint')}
               autoComplete="new-password" value={password}
               onChange={(e) => setPassword(e.target.value)} className={inputClass} />
        <input type="text" required minLength={2} maxLength={50} placeholder={t('auth.displayName')} value={displayName}
               onChange={(e) => setDisplayName(e.target.value)} className={inputClass} />
        <select value={country} onChange={(e) => setCountry(e.target.value)} className={inputClass}>
          <option value="">{t('auth.countryOptional')}</option>
          {countries().map((c) => (
            <option key={c.code} value={c.code}>
              {c.name}
            </option>
          ))}
        </select>
        <ClubPicker
          value={club?.id ?? null}
          selected={club}
          onChange={setClub}
          label={t('auth.clubOptional')}
        />
        <select
          value={competitionId ?? ''}
          onChange={(e) => setCompetitionId(e.target.value ? Number(e.target.value) : null)}
          className={inputClass}
        >
          <option value="">{t('auth.competitionOptional')}</option>
          {championships?.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        {error && <p className="text-sm text-red-400">{error}</p>}
        <button
          type="submit"
          disabled={busy}
          className="w-full rounded-xl bg-emerald-500 py-3 font-semibold text-emerald-950 active:bg-emerald-400 disabled:opacity-50"
        >
          {busy ? t('auth.signupPending') : t('auth.signupSubmit')}
        </button>
      </form>
      <GoogleSignInButton />
      <p className="mt-6 text-center text-sm text-slate-400">
        {t('auth.alreadyPlaying')}{' '}
        <Link to="/login" className="font-medium text-emerald-400">
          {t('auth.signIn')}
        </Link>
      </p>
    </div>
  )
}
