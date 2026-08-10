import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { PendingInviteHandler } from './PendingInviteHandler'
import { WelcomeGuide } from './WelcomeGuide'
import { useT } from '../i18n'

const tabClass = ({ isActive }: { isActive: boolean }) =>
  `flex flex-1 flex-col items-center gap-0.5 py-2 text-xs font-medium transition-colors ${
    isActive ? 'text-emerald-400' : 'text-slate-400 hover:text-slate-200'
  }`

export function AppShell() {
  const { user } = useAuth()
  const t = useT()
  return (
    <div className="mx-auto flex min-h-dvh max-w-lg flex-col bg-slate-950 text-slate-100">
      <PendingInviteHandler />
      <WelcomeGuide />
      <main className="flex-1 pb-20">
        <Outlet />
      </main>
      <nav className="fixed inset-x-0 bottom-0 z-10 border-t border-slate-800 bg-slate-900/95 backdrop-blur">
        <div className="mx-auto flex max-w-lg">
          <NavLink to="/" end className={tabClass}>
            <span aria-hidden className="text-lg">⚽</span>
            {t('nav.predict')}
          </NavLink>
          <NavLink to="/table" className={tabClass}>
            <span aria-hidden className="text-lg">🏆</span>
            {t('nav.leagues')}
          </NavLink>
          <NavLink to="/profile" className={tabClass}>
            <span aria-hidden className="text-lg">👤</span>
            {t('nav.profile')}
          </NavLink>
          {user?.admin && (
            <NavLink to="/admin" className={tabClass}>
              <span aria-hidden className="text-lg">🛠️</span>
              {t('nav.admin')}
            </NavLink>
          )}
        </div>
      </nav>
    </div>
  )
}
