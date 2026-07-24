import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { I18nProvider } from './i18n'
import { AppShell } from './components/AppShell'
import { AdminPage } from './pages/AdminPage'
import { CreateLeaguePage } from './pages/CreateLeaguePage'
import { JoinDeepLinkPage } from './pages/JoinDeepLinkPage'
import { JoinLeaguePage } from './pages/JoinLeaguePage'
import { LeaguesPage } from './pages/LeaguesPage'
import { GlobalTablePage, PrivateLeaguePage, ScopedTablePage } from './pages/LeagueTablePage'
import { LoginPage } from './pages/LoginPage'
import { PlayerPage } from './pages/PlayerPage'
import { PredictPage } from './pages/PredictPage'
import { ProfilePage } from './pages/ProfilePage'
import { SignupPage } from './pages/SignupPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 15_000 },
  },
})

function RequireAuth() {
  const { user } = useAuth()
  return user ? <Outlet /> : <Navigate to="/login" replace />
}

function RequireAdmin() {
  const { user } = useAuth()
  return user?.admin ? <Outlet /> : <Navigate to="/" replace />
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <AuthProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/signup" element={<SignupPage />} />
              <Route path="/join/:code" element={<JoinDeepLinkPage />} />
              <Route element={<RequireAuth />}>
                <Route element={<AppShell />}>
                  <Route path="/" element={<PredictPage />} />
                  <Route path="/table" element={<LeaguesPage />} />
                  <Route path="/table/global" element={<GlobalTablePage />} />
                  <Route path="/table/country" element={<ScopedTablePage kind="country" />} />
                  <Route path="/table/club" element={<ScopedTablePage kind="club" />} />
                  <Route path="/table/create" element={<CreateLeaguePage />} />
                  <Route path="/table/join" element={<JoinLeaguePage />} />
                  <Route path="/table/league/:id" element={<PrivateLeaguePage />} />
                  <Route path="/players/:playerId" element={<PlayerPage />} />
                  <Route path="/profile" element={<ProfilePage />} />
                  <Route element={<RequireAdmin />}>
                    <Route path="/admin" element={<AdminPage />} />
                  </Route>
                </Route>
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </I18nProvider>
    </QueryClientProvider>
  )
}
