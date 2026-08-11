import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useT } from '../i18n'
import { explain } from '../lib/apiMessage'

/**
 * Password recovery in one screen: ask for the address, then take the code
 * and the new password together. Splitting them over two pages would only
 * add a step — the code is the proof, and it is already in the inbox.
 */
export function ForgotPasswordPage() {
  const t = useT()
  const { forgotPassword, resetPassword } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const run = async (event: FormEvent, action: () => Promise<void>) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (e) {
      setError(explain(e, t, t('forgot.failed')))
    } finally {
      setBusy(false)
    }
  }

  const requestCode = (event: FormEvent) =>
    run(event, async () => {
      await forgotPassword(email)
      setSent(true)
    })

  const applyReset = (event: FormEvent) =>
    run(event, async () => {
      await resetPassword(email, code, password)
      navigate('/')
    })

  return (
    <div className="mx-auto flex min-h-dvh max-w-lg flex-col justify-center bg-slate-950 p-6 text-slate-100">
      <h1 className="mb-1 text-center text-3xl font-extrabold">{t('forgot.title')}</h1>
      <p className="mb-8 text-center text-sm text-slate-400">
        {sent ? t('forgot.codeSent', { email }) : t('forgot.subtitle')}
      </p>

      {!sent ? (
        <form onSubmit={requestCode} className="space-y-4">
          <input
            type="email"
            required
            placeholder={t('auth.email')}
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full rounded-xl border border-slate-700 bg-slate-900 px-4 py-3 outline-none focus:border-emerald-500"
          />
          {error && <p className="text-sm text-red-400">{error}</p>}
          <button
            type="submit"
            disabled={busy}
            className="w-full rounded-xl bg-emerald-500 py-3 font-semibold text-emerald-950 active:bg-emerald-400 disabled:opacity-50"
          >
            {busy ? t('forgot.sending') : t('forgot.sendCode')}
          </button>
        </form>
      ) : (
        <form onSubmit={applyReset} className="space-y-4">
          <input
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            required
            maxLength={6}
            placeholder="······"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
            className="w-full rounded-xl border border-slate-700 bg-slate-900 px-4 py-3 text-center font-mono text-2xl tracking-[0.5em] outline-none focus:border-emerald-500"
          />
          <input
            type="password"
            required
            minLength={10}
            placeholder={t('forgot.newPassword')}
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full rounded-xl border border-slate-700 bg-slate-900 px-4 py-3 outline-none focus:border-emerald-500"
          />
          {error && <p className="text-sm text-red-400">{error}</p>}
          <button
            type="submit"
            disabled={busy || code.length < 6 || password.length < 8}
            className="w-full rounded-xl bg-emerald-500 py-3 font-semibold text-emerald-950 active:bg-emerald-400 disabled:opacity-50"
          >
            {busy ? t('forgot.saving') : t('forgot.submit')}
          </button>
        </form>
      )}

      <p className="mt-6 text-center text-sm text-slate-400">
        <Link to="/login" className="font-medium text-emerald-400">
          {t('forgot.backToSignIn')}
        </Link>
      </p>
    </div>
  )
}
