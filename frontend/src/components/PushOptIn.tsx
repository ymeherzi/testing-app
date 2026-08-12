import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { disablePush, enablePush, pushAvailability } from '../lib/push'
import { usePushSubscribed } from '../api/queries'
import { readStored, writeStored } from '../lib/storage'
import { useT } from '../i18n'

/** Set when the prompt on the predictions screen has been waved away. */
const DISMISSED_KEY = 'pushPromptDismissed'

/**
 * The one control for round notifications.
 *
 * <p>Compact by design: it sits on the profile, and the prompt on the
 * predictions screen is the same component. What it must never do is offer a
 * button that cannot work — on an iPhone that has not installed the app, the
 * subscribe call fails with nothing a player could act on, so the instruction
 * takes the button's place.
 *
 * <p>The compact prompt can be dismissed. It sat above the fixtures on every
 * visit with no way out, which is how a suggestion turns into nagging; the
 * profile still carries the full control for whoever changes their mind.
 */
export function PushOptIn({ compact = false }: { compact?: boolean }) {
  const t = useT()
  const [availability, setAvailability] = useState(pushAvailability)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [dismissed, setDismissed] = useState(() => readStored(DISMISSED_KEY) === 'true')
  const queryClient = useQueryClient()
  // permission is what the browser granted; this is whether the device is
  // actually registered with us. Holding one without the other is normal —
  // after switching them off, or after the site data was cleared — and the
  // screen used to offer nothing at all in that state.
  const { data: registration } = usePushSubscribed(availability === 'granted' || availability === 'ready')
  const subscribed = availability === 'granted' && registration?.subscribed === true
  const canEnable = availability === 'ready' || (availability === 'granted' && registration?.subscribed === false)
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['push', 'subscribed'] })

  const enable = async () => {
    setBusy(true)
    setError(null)
    try {
      setAvailability((await enablePush()) ? 'granted' : 'denied')
      refresh()
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
      refresh()
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
  if (compact && (subscribed || availability === 'denied' || dismissed)) {
    return null
  }

  return (
    <div className="space-y-2 rounded-xl border border-slate-800 bg-slate-900 p-4">
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm font-medium text-slate-200">{t('push.title')}</p>
        {compact && (
          <button
            type="button"
            onClick={() => {
              writeStored(DISMISSED_KEY, 'true')
              setDismissed(true)
            }}
            aria-label={t('push.dismiss')}
            className="-mr-1 -mt-1 shrink-0 rounded-lg px-2 py-1 text-lg leading-none text-slate-500 active:bg-slate-800"
          >
            ×
          </button>
        )}
      </div>
      <p className="text-xs text-slate-400">
        {availability === 'needs-install' && t('push.needsInstall')}
        {availability === 'denied' && t('push.denied')}
        {subscribed && t('push.on')}
        {canEnable && t('push.hint')}
      </p>
      {error && <p className="text-xs text-red-400">{error}</p>}
      {canEnable && (
        <button
          type="button"
          onClick={enable}
          disabled={busy}
          className="w-full rounded-xl bg-emerald-500 py-2 text-sm font-semibold text-emerald-950 disabled:opacity-50"
        >
          {busy ? t('push.enabling') : t('push.enable')}
        </button>
      )}
      {compact && canEnable && (
        <button
          type="button"
          onClick={() => {
            writeStored(DISMISSED_KEY, 'true')
            setDismissed(true)
          }}
          className="w-full py-1 text-xs font-medium text-slate-500"
        >
          {t('push.later')}
        </button>
      )}
      {subscribed && !compact && (
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
