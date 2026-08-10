import { Link } from 'react-router-dom'
import { useScoringScale } from '../api/queries'
import { useT } from '../i18n'

/** One row of the scoring scale: points, then what earns them. */
function Tier({ points, label }: { points: number | undefined; label: string }) {
  return (
    <li className="flex items-start gap-3 rounded-xl border border-slate-800 bg-slate-900 p-3">
      <span className="min-w-9 rounded-lg bg-emerald-500 px-2 py-1 text-center font-bold text-emerald-950">
        {points ?? '—'}
      </span>
      <span className="text-sm text-slate-300">{label}</span>
    </li>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mb-8">
      <h2 className="mb-3 text-lg font-bold">{title}</h2>
      {children}
    </section>
  )
}

/**
 * The rules, reachable at any time from the profile. The scoring numbers
 * repeat what `lib/scoring.ts` implements — which itself mirrors the
 * backend ScoringEngine, the only source of truth for official points.
 */
export function RulesPage() {
  const t = useT()
  // the numbers come from the server, so this page can never contradict it
  const { data: scale } = useScoringScale()

  return (
    <div className="mx-auto max-w-lg p-4 pb-24 text-slate-100">
      <h1 className="mb-6 text-2xl font-extrabold">{t('rules.title')}</h1>

      <Section title={t('rules.scoringTitle')}>
        <ul className="space-y-2">
          <Tier points={scale?.EXACT} label={t('rules.tierExact')} />
          <Tier points={scale?.GOAL_DIFFERENCE} label={t('rules.tierDifference')} />
          <Tier points={scale?.OUTCOME} label={t('rules.tierOutcome')} />
          <Tier points={scale?.MISS} label={t('rules.tierMiss')} />
        </ul>
        <p className="mt-3 text-sm text-slate-400">{t('rules.drawNote')}</p>
      </Section>

      <Section title={t('rules.lockTitle')}>
        <p className="text-sm text-slate-300">{t('rules.lockBody')}</p>
      </Section>

      <Section title={t('rules.gameweekTitle')}>
        <p className="text-sm text-slate-300">{t('rules.gameweekBody')}</p>
      </Section>

      <Section title={t('rules.leaguesTitle')}>
        <p className="text-sm text-slate-300">{t('rules.leaguesBody')}</p>
      </Section>

      <Link to="/" className="font-medium text-emerald-400">
        {t('rules.backToPredict')}
      </Link>
    </div>
  )
}
