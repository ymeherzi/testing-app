import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useGlobalTable, useLeagueActions, useLeagueDetail, useScopedTable } from '../api/queries'
import { InviteShareButton } from '../components/InviteShareButton'
import { LeagueTableView } from '../components/LeagueTableView'
import { useAuth } from '../auth/AuthContext'
import { countryFlag } from '../lib/format'
import { useT } from '../i18n'
import { countryName } from '../lib/countries'

function BackLink() {
  const t = useT()
  return (
    <Link to="/table" className="text-sm text-slate-400">
      {t('nav.backToLeagues')}
    </Link>
  )
}

export function GlobalTablePage() {
  const t = useT()
  const [page, setPage] = useState(0)
  const { data: table } = useGlobalTable(page)
  if (!table) {
    return <p className="p-6 text-center text-slate-400">{t('common.loading')}</p>
  }
  return (
    <div className="space-y-4 p-4">
      <BackLink />
      <header>
        <h1 className="text-xl font-bold">🌍 {t('leagues.global')}</h1>
        <p className="mt-1 text-sm text-slate-400">{t('leagues.playersWorldwide', { count: table.totalPlayers })}</p>
      </header>
      <LeagueTableView rows={table.entries} me={table.me} />
      <Pagination page={page} setPage={setPage} size={table.size} total={table.totalPlayers} />
    </div>
  )
}

export function ScopedTablePage({ kind }: { kind: 'country' | 'club' | 'competition' }) {
  const t = useT()
  const [page, setPage] = useState(0)
  const { data: scoped } = useScopedTable(kind, page)
  if (!scoped) {
    return <p className="p-6 text-center text-slate-400">{t('common.loading')}</p>
  }
  if (!scoped.available || !scoped.table) {
    return (
      <div className="space-y-4 p-4">
        <BackLink />
        <p className="rounded-2xl border border-dashed border-slate-700 px-4 py-8 text-center text-sm text-slate-400">
          {t('leagues.setAttribute', {
            attribute: t(
              kind === 'country'
                ? 'leagues.attributeCountry'
                : kind === 'club'
                  ? 'leagues.attributeClub'
                  : 'leagues.attributeCompetition',
            ),
          })}{' '}
          <Link to="/profile" className="text-emerald-400">{t('nav.profile')}</Link>{' '}
          {t('leagues.toEnter')}
        </p>
      </div>
    )
  }
  const title =
    kind === 'country'
      ? `${countryFlag(scoped.country)} ${t('leagues.countrySubtitle', {
          country: countryName(scoped.country) ?? scoped.country ?? '',
        })}`
      : kind === 'competition'
        ? `🏆 ${t('leagues.competitionSubtitle', { competition: scoped.competitionName ?? '' })}`
        : `🛡️ ${t('leagues.clubFans', { club: scoped.clubName ?? '' })}`
  return (
    <div className="space-y-4 p-4">
      <BackLink />
      <header>
        <h1 className="text-xl font-bold">{title}</h1>
        <p className="mt-1 text-sm text-slate-400">{t('leagues.playerCount', { count: scoped.table.totalPlayers })}</p>
      </header>
      <LeagueTableView rows={scoped.table.entries} me={scoped.table.me} />
      <Pagination page={page} setPage={setPage} size={scoped.table.size} total={scoped.table.totalPlayers} />
    </div>
  )
}

export function PrivateLeaguePage() {
  const t = useT()
  const { id } = useParams()
  const leagueId = Number(id)
  const { data: league, error } = useLeagueDetail(leagueId)
  const { leave, regenerateCode } = useLeagueActions()
  const { user } = useAuth()
  const navigate = useNavigate()

  if (error) {
    return (
      <div className="space-y-4 p-4">
        <BackLink />
        <p className="p-6 text-center text-slate-400">{t('leagues.notFound')}</p>
      </div>
    )
  }
  if (!league) {
    return <p className="p-6 text-center text-slate-400">{t('common.loading')}</p>
  }

  const confirmLeave = async () => {
    const isEmptyingLeague = league.admin && league.members.length === 1
    const message = isEmptyingLeague
      ? t('leagues.deleteConfirm')
      : t('leagues.leaveConfirm')
    if (window.confirm(message)) {
      try {
        await leave.mutateAsync(league.id)
        navigate('/table')
      } catch (e) {
        window.alert(e instanceof Error ? e.message : t('leagues.leaveFailed'))
      }
    }
  }

  return (
    <div className="space-y-4 p-4">
      <BackLink />
      <header className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold">🏟️ {league.name}</h1>
          <p className="mt-1 text-sm text-slate-400">
            {t('leagues.membersOf', { members: league.members.length, max: league.maxMembers })} ·{' '}
            {t('leagues.code')} <span className="font-mono">{league.inviteCode}</span>
          </p>
        </div>
        <InviteShareButton code={league.inviteCode} leagueName={league.name} />
      </header>
      <LeagueTableView
        rows={league.members}
        me={league.me && !league.members.some((m) => m.userId === user?.id) ? league.me : null}
      />
      <div className="flex justify-between text-sm">
        {league.admin ? (
          <button
            type="button"
            onClick={() => regenerateCode.mutate(league.id)}
            className="rounded-lg bg-slate-800 px-4 py-2 font-medium"
          >
            {t('leagues.newCode')}
          </button>
        ) : (
          <span />
        )}
        <button type="button" onClick={confirmLeave} className="rounded-lg border border-red-900 px-4 py-2 font-medium text-red-400">
          {t('leagues.leave')}
        </button>
      </div>
    </div>
  )
}

function Pagination({ page, setPage, size, total }: {
  page: number
  setPage: (fn: (p: number) => number) => void
  size: number
  total: number
}) {
  const t = useT()
  if (total <= size) {
    return null
  }
  return (
    <div className="flex justify-between text-sm">
      <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}
              className="rounded-lg bg-slate-800 px-4 py-2 font-medium disabled:opacity-30">
        {t('common.previous')}
      </button>
      <button type="button" disabled={(page + 1) * size >= total} onClick={() => setPage((p) => p + 1)}
              className="rounded-lg bg-slate-800 px-4 py-2 font-medium disabled:opacity-30">
        {t('common.next')}
      </button>
    </div>
  )
}
