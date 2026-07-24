import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { useT } from '../i18n'

/** Six-digit code step shown after signup or a new-device login. */
export function VerifyCodeForm({ email }: { email: string }) {
  const t = useT()
  const { verify, resend } = useAuth()
  const navigate = useNavigate()
  const [code, setCode] = useState('')
  const [rememberDevice, setRememberDevice] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await verify(email, code, rememberDevice)
      navigate('/')
    } catch (e) {
      setError(e instanceof Error ? e.message : t('verify.failed'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto flex min-h-dvh max-w-lg flex-col justify-center bg-slate-950 p-6 text-slate-100">
      <h1 className="mb-1 text-center text-3xl font-extrabold">{t('verify.title')}</h1>
      <p className="mb-8 text-center text-sm text-slate-400">{t('verify.subtitle', { email })}</p>
      <form onSubmit={submit} className="space-y-4">
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
        <label className="flex items-center gap-2 text-sm text-slate-300">
          <input type="checkbox" checked={rememberDevice} onChange={(e) => setRememberDevice(e.target.checked)}
                 className="size-4 accent-emerald-500" />
          {t('verify.rememberDevice')}
        </label>
        {error && <p className="text-sm text-red-400">{error}</p>}
        {notice && <p className="text-sm text-emerald-400">{notice}</p>}
        <button
          type="submit"
          disabled={busy || code.length < 6}
          className="w-full rounded-xl bg-emerald-500 py-3 font-semibold text-emerald-950 active:bg-emerald-400 disabled:opacity-50"
        >
          {busy ? t('verify.checking') : t('verify.submit')}
        </button>
      </form>
      <button
        type="button"
        onClick={async () => {
          setNotice(null)
          setError(null)
          await resend(email)
          setNotice(t('verify.resent'))
        }}
        className="mt-6 text-center text-sm text-slate-400 underline"
      >
        {t('verify.resend')}
      </button>
    </div>
  )
}
