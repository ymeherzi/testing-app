import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useUnsubscribe } from '../api/queries'
import { useT } from '../i18n'

/**
 * Where the link at the bottom of every email lands.
 *
 * <p>The page does the work on load rather than asking for a confirming click:
 * someone who wants out should be out, not negotiating. It is a page and not a
 * plain GET endpoint because mail scanners follow every link in a message, and
 * would otherwise unsubscribe people who never clicked.
 */
export function UnsubscribePage() {
  const t = useT()
  const [params] = useSearchParams()
  const unsubscribe = useUnsubscribe()
  const [state, setState] = useState<'working' | 'done' | 'failed'>('working')
  const started = useRef(false)

  const u = params.get('u')
  const token = params.get('t')

  useEffect(() => {
    // React runs effects twice in development; the request is idempotent but
    // firing it twice makes the outcome flicker
    if (started.current) {
      return
    }
    started.current = true
    if (!u || !token) {
      setState('failed')
      return
    }
    unsubscribe
      .mutateAsync({ u, t: token })
      .then(() => setState('done'))
      .catch(() => setState('failed'))
  }, [u, token, unsubscribe])

  return (
    <div className="mx-auto flex min-h-dvh max-w-lg flex-col justify-center bg-slate-950 p-6 text-center text-slate-100">
      <h1 className="mb-3 text-2xl font-extrabold">{t('unsubscribe.title')}</h1>
      <p className="mb-8 text-sm text-slate-400">
        {state === 'working' && t('unsubscribe.working')}
        {state === 'done' && t('unsubscribe.done')}
        {state === 'failed' && t('unsubscribe.failed')}
      </p>
      <Link
        to="/"
        className="rounded-xl border border-slate-700 py-3 font-semibold text-slate-300 active:bg-slate-900"
      >
        {t('unsubscribe.backToApp')}
      </Link>
    </div>
  )
}
