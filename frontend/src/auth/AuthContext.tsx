import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { api, getToken, setToken } from '../api/client'
import type { AuthResponse, UserProfile } from '../api/types'

const USER_KEY = 'predictor.user'

interface AuthState {
  user: UserProfile | null
  login: (email: string, password: string) => Promise<void>
  signup: (input: SignupInput) => Promise<void>
  logout: () => void
  updateUser: (user: UserProfile) => void
}

export interface SignupInput {
  email: string
  password: string
  displayName: string
  country: string | null
  favouriteClubTeamId: number | null
}

const AuthContext = createContext<AuthState | null>(null)

function storedUser(): UserProfile | null {
  if (!getToken()) {
    return null
  }
  const raw = localStorage.getItem(USER_KEY)
  return raw ? (JSON.parse(raw) as UserProfile) : null
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(storedUser)

  const persist = useCallback((auth: AuthResponse) => {
    setToken(auth.token)
    localStorage.setItem(USER_KEY, JSON.stringify(auth.user))
    setUser(auth.user)
  }, [])

  const login = useCallback(
    async (email: string, password: string) => {
      persist(await api<AuthResponse>('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }))
    },
    [persist],
  )

  const signup = useCallback(
    async (input: SignupInput) => {
      persist(await api<AuthResponse>('/api/auth/signup', { method: 'POST', body: JSON.stringify(input) }))
    },
    [persist],
  )

  const logout = useCallback(() => {
    setToken(null)
    localStorage.removeItem(USER_KEY)
    setUser(null)
  }, [])

  const updateUser = useCallback((updated: UserProfile) => {
    localStorage.setItem(USER_KEY, JSON.stringify(updated))
    setUser(updated)
  }, [])

  const value = useMemo(
    () => ({ user, login, signup, logout, updateUser }),
    [user, login, signup, logout, updateUser],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}
