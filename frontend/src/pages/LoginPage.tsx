import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useT } from '../i18n'
import { VerifyCodeForm } from '../auth/VerifyCodeForm'
import { GoogleSignInButton } from '../auth/GoogleSignInButton'

export function LoginPage() {
  const { login } = useAuth()
  const t = useT()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [pendingEmail, setPendingEmail] = useState<string | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const outcome = await login(email, password)
      if (outcome.signedIn) {
        navigate('/')
      } else {
        setPendingEmail(outcome.email)
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : t('auth.loginFailed'))
    } finally {
      setBusy(false)
    }
  }

  if (pendingEmail) {
    return <VerifyCodeForm email={pendingEmail} />
  }

  return (
    <div className="mx-auto flex min-h-dvh max-w-lg flex-col justify-center bg-slate-950 p-6 text-slate-100">
      <h1 className="mb-1 text-center text-3xl font-extrabold">⚽ {t('app.name')}</h1>
      <p className="mb-8 text-center text-sm text-slate-400">{t('app.tagline')}</p>
      <form onSubmit={submit} className="space-y-4">
        <input
          type="email"
          required
          placeholder={t('auth.email')}
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="w-full rounded-xl border border-slate-700 bg-slate-900 px-4 py-3 outline-none focus:border-emerald-500"
        />
        <input
          type="password"
          required
          placeholder={t('auth.password')}
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="w-full rounded-xl border border-slate-700 bg-slate-900 px-4 py-3 outline-none focus:border-emerald-500"
        />
        {error && <p className="text-sm text-red-400">{error}</p>}
        <button
          type="submit"
          disabled={busy}
          className="w-full rounded-xl bg-emerald-500 py-3 font-semibold text-emerald-950 active:bg-emerald-400 disabled:opacity-50"
        >
          {busy ? t('auth.signingIn') : t('auth.signIn')}
        </button>
      </form>
      <GoogleSignInButton />
      <p className="mt-6 text-center text-sm text-slate-400">
        {t('auth.newHere')}{' '}
        <Link to="/signup" className="font-medium text-emerald-400">
          {t('auth.createAccount')}
        </Link>
      </p>
    </div>
  )
}
