import { useState } from 'react'
import { disablePush, enablePush, pushAvailability } from '../lib/push'
import { useT } from '../i18n'

/**
 * The one control for round notifications.
 *
 * <p>Compact by design: it sits on the profile, and the prompt on the
 * predictions screen is the same component. What it must never do is offer a
 * button that cannot work — on an iPhone that has not installed the app, the
 * subscribe call fails with nothing a player could act on, so the instruction
 * takes the button's place.
 */
export function PushOptIn({ compact = false }: { compact?: boolean }) {
  const t = useT()
  const [availability, setAvailability] = useState(pushAvailability)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const enable = async () => {
    setBusy(true)
    setError(null)
    try {
      setAvailability((await enablePush()) ? 'granted' : 'denied')
    } catch (e) {
      setError(e instanceof Error ? e.message : t('push.failed'))
    } finally {
      setBusy(false)
    }
  }

  const disable = async () => {
    setBusy(true)
    try {
      await disablePush()
      setAvailability('ready')
    } catch (e) {
      setError(e instanceof Error ? e.message : t('push.failed'))
    } finally {
      setBusy(false)
    }
  }

  if (availability === 'unsupported') {
    return null
  }
  // on the predictions screen there is nothing to say once they are on, or
  // once the player has said no in the browser
  if (compact && (availability === 'granted' || availability === 'denied')) {
    return null
  }

  return (
    <div className="space-y-2 rounded-xl border border-slate-800 bg-slate-900 p-4">
      <p className="text-sm font-medium text-slate-200">{t('push.title')}</p>
      <p className="text-xs text-slate-400">
        {availability === 'needs-install' && t('push.needsInstall')}
        {availability === 'denied' && t('push.denied')}
        {availability === 'granted' && t('push.on')}
        {availability === 'ready' && t('push.hint')}
      </p>
      {error && <p className="text-xs text-red-400">{error}</p>}
      {availability === 'ready' && (
        <button
          type="button"
          onClick={enable}
          disabled={busy}
          className="w-full rounded-xl bg-emerald-500 py-2 text-sm font-semibold text-emerald-950 disabled:opacity-50"
        >
          {busy ? t('push.enabling') : t('push.enable')}
        </button>
      )}
      {availability === 'granted' && !compact && (
        <button
          type="button"
          onClick={disable}
          disabled={busy}
          className="w-full rounded-xl border border-slate-700 py-2 text-sm font-semibold text-slate-300 disabled:opacity-50"
        >
          {t('push.disable')}
        </button>
      )}
    </div>
  )
}
