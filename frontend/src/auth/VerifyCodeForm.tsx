import { useRef, useState, type FormEvent } from 'react'
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
  const [resending, setResending] = useState(false)
  const input = useRef<HTMLInputElement>(null)

  /**
   * A refused code has to go. The field is full at six digits, so leaving it
   * there means the next code cannot be typed until the player clears it by
   * hand — and they are already annoyed by then.
   */
  const startOver = () => {
    setCode('')
    input.current?.focus()
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await verify(email, code, rememberDevice)
      navigate('/')
    } catch (e) {
      setError(e instanceof Error ? e.message : t('verify.failed'))
      startOver()
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
          ref={input}
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
        disabled={resending}
        onClick={async () => {
          setNotice(null)
          setError(null)
          setResending(true)
          try {
            await resend(email)
            setNotice(t('verify.resent'))
            // the code on its way is a different one: clear the field so the
            // player types the new one into an empty box
            startOver()
          } catch (e) {
            // mail can be refused (502) — saying so beats a silent no-op
            setError(e instanceof Error ? e.message : t('verify.failed'))
          } finally {
            setResending(false)
          }
        }}
        className="mt-6 text-center text-sm text-slate-400 underline disabled:opacity-50"
      >
        {resending ? t('verify.resending') : t('verify.resend')}
      </button>
      {/* under the button that produced it: on a phone the eye is at the
          bottom of the screen, and a confirmation above the fold is a
          confirmation nobody sees */}
      {notice && <p className="mt-3 text-center text-sm text-emerald-400">{notice}</p>}
    </div>
  )
}
