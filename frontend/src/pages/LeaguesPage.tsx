import { Link } from 'react-router-dom'
import { useMyLeagues, useScopedTable, useTeams } from '../api/queries'
import { useAuth } from '../auth/AuthContext'
import { countryFlag } from '../lib/format'
import { useT } from '../i18n'
import { countryName } from '../lib/countries'

function Card({ to, icon, title, subtitle, disabled }: {
  to: string
  icon: string
  title: string
  subtitle: string
  disabled?: boolean
}) {
  const body = (
    <div
      className={`flex items-center gap-3 rounded-2xl border border-slate-800 bg-slate-900 px-4 py-4 ${
        disabled ? 'opacity-50' : 'active:bg-slate-800'
      }`}
    >
      <span className="text-2xl" aria-hidden>{icon}</span>
      <div className="min-w-0 flex-1">
        <p className="truncate font-semibold">{title}</p>
        <p className="truncate text-sm text-slate-400">{subtitle}</p>
      </div>
      {!disabled && <span className="text-slate-500">›</span>}
    </div>
  )
  return disabled ? body : <Link to={to}>{body}</Link>
}

export function LeaguesPage() {
  const t = useT()
  const { user } = useAuth()
  const { data: leagues } = useMyLeagues()
  const { data: teams } = useTeams()
  const { data: clubScope } = useScopedTable('club', 0, 1)
  const clubName =
    clubScope?.clubName ??
    teams?.find((t) => t.id === user?.favouriteClubTeamId)?.name ??
    null

  return (
    <div className="space-y-6 p-4">
      <header className="pt-2">
        <h1 className="text-xl font-bold">{t('leagues.title')}</h1>
        <p className="mt-1 text-sm text-slate-400">{t('leagues.subtitle')}</p>
      </header>

      <section className="space-y-2">
        <Card to="/table/global" icon="🌍" title={t('leagues.global')} subtitle={t('leagues.globalSubtitle')} />
        <Card
          to="/table/country"
          icon={countryFlag(user?.country ?? null) || '🏳️'}
          title={t('leagues.country')}
          subtitle={
            user?.country
              ? t('leagues.countrySubtitle', { country: countryName(user.country) ?? user.country })
              : t('leagues.countryUnset')
          }
          disabled={!user?.country}
        />
        <Card
          to="/table/club"
          icon="🛡️"
          title={t('leagues.club')}
          subtitle={clubName ? t('leagues.clubSubtitle', { club: clubName }) : t('leagues.clubUnset')}
          disabled={!user?.favouriteClubTeamId}
        />
      </section>

      <section className="space-y-2">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">{t('leagues.mine')}</h2>
          <div className="flex gap-2 text-sm">
            <Link to="/table/join" className="rounded-lg bg-slate-800 px-3 py-1.5 font-medium">{t('leagues.join')}</Link>
            <Link to="/table/create" className="rounded-lg bg-emerald-500 px-3 py-1.5 font-semibold text-emerald-950">
              {t('leagues.create')}
            </Link>
          </div>
        </div>
        {leagues?.length === 0 && (
          <p className="rounded-2xl border border-dashed border-slate-700 px-4 py-6 text-center text-sm text-slate-400">
            {t('leagues.empty')}
          </p>
        )}
        {leagues?.map((league) => (
          <Card
            key={league.id}
            to={`/table/league/${league.id}`}
            icon="🏟️"
            title={league.name}
            subtitle={
              t('leagues.memberCount', { count: league.memberCount }) +
              (league.myRank ? ` · ${t('leagues.yourRank', { rank: league.myRank })}` : '') +
              (league.admin ? ` · ${t('leagues.adminBadge')}` : '')
            }
          />
        ))}
      </section>
    </div>
  )
}
