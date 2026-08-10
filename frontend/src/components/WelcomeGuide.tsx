import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { useScoringScale } from '../api/queries'
import type { UserProfile } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useT } from '../i18n'

/**
 * Shown once, on the first visit after signing up. Three screens answering
 * the questions a newcomer actually has — how do I score, when does it
 * close, who am I playing against — and a Skip that is visible from the
 * first screen rather than hidden at the end.
 *
 * "Seen" is stored on the account, so skipping it on a phone doesn't make it
 * reappear on a laptop. A failure to record that is swallowed: the guide is
 * a courtesy, and blocking someone from their predictions over it would be
 * worse than showing it twice.
 */
export function WelcomeGuide() {
  const t = useT()
  const { user, updateUser } = useAuth()
  const { data: scale } = useScoringScale()
  const [step, setStep] = useState(0)
  const [dismissed, setDismissed] = useState(false)

  if (!user || user.guideSeen || dismissed) {
    return null
  }

  const steps = [
    {
      title: t('guide.scoringTitle'),
      body: t('guide.scoringBody', {
        exact: scale?.EXACT ?? 3,
        difference: scale?.GOAL_DIFFERENCE ?? 2,
        outcome: scale?.OUTCOME ?? 1,
      }),
    },
    { title: t('guide.lockTitle'), body: t('guide.lockBody') },
    { title: t('guide.leaguesTitle'), body: t('guide.leaguesBody') },
  ]
  const last = step === steps.length - 1

  const close = async () => {
    setDismissed(true)
    try {
      const updated = await api<UserProfile>('/api/me/guide-seen', { method: 'POST' })
      updateUser(updated)
    } catch {
      // never block play over a guide; worst case it shows once more
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/80 p-4 sm:items-center">
      <div className="w-full max-w-md rounded-2xl border border-slate-800 bg-slate-900 p-6">
        <p className="mb-1 text-xs uppercase tracking-wide text-slate-500">
          {t('guide.step', { current: step + 1, total: steps.length })}
        </p>
        <h2 className="mb-3 text-xl font-bold text-slate-100">{steps[step].title}</h2>
        <p className="mb-6 whitespace-pre-line text-sm text-slate-300">{steps[step].body}</p>

        <div className="flex items-center justify-between gap-3">
          <button type="button" onClick={close} className="text-sm text-slate-400 underline">
            {t('guide.skip')}
          </button>
          {last ? (
            <button
              type="button"
              onClick={close}
              className="rounded-xl bg-emerald-500 px-5 py-2 font-semibold text-emerald-950 active:bg-emerald-400"
            >
              {t('guide.start')}
            </button>
          ) : (
            <button
              type="button"
              onClick={() => setStep(step + 1)}
              className="rounded-xl bg-emerald-500 px-5 py-2 font-semibold text-emerald-950 active:bg-emerald-400"
            >
              {t('guide.next')}
            </button>
          )}
        </div>

        {last && (
          <p className="mt-4 text-center text-xs text-slate-500">
            {t('guide.rulesHint')}{' '}
            <Link to="/rules" onClick={close} className="text-emerald-400 underline">
              {t('guide.rulesLink')}
            </Link>
          </p>
        )}
      </div>
    </div>
  )
}
