import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { api, getToken, setToken } from '../api/client'
import type { AuthResponse, UserProfile } from '../api/types'
import { deviceToken, rememberDeviceToken } from './device'
import { clearStored, readStored, writeStored } from '../lib/storage'

const USER_KEY = 'user'

/** Either we're signed in, or a code is waiting in the user's inbox. */
export type AuthOutcome = { signedIn: true } | { signedIn: false; email: string }

interface AuthState {
  user: UserProfile | null
  login: (email: string, password: string) => Promise<AuthOutcome>
  signup: (input: SignupInput) => Promise<AuthOutcome>
  verify: (email: string, code: string, rememberDevice: boolean) => Promise<void>
  resend: (email: string) => Promise<void>
  forgotPassword: (email: string) => Promise<void>
  resetPassword: (email: string, code: string, password: string) => Promise<void>
  signInWithGoogle: (idToken: string) => Promise<void>
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
  const raw = readStored(USER_KEY)
  return raw ? (JSON.parse(raw) as UserProfile) : null
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(storedUser)

  const persist = useCallback((auth: AuthResponse): AuthOutcome => {
    if (auth.verificationRequired || !auth.token || !auth.user) {
      return { signedIn: false, email: auth.email }
    }
    setToken(auth.token)
    rememberDeviceToken(auth.deviceToken)
    writeStored(USER_KEY, JSON.stringify(auth.user))
    setUser(auth.user)
    return { signedIn: true }
  }, [])

  const login = useCallback(
    async (email: string, password: string) =>
      persist(
        await api<AuthResponse>('/api/auth/login', {
          method: 'POST',
          body: JSON.stringify({ email, password, deviceToken: deviceToken() }),
        }),
      ),
    [persist],
  )

  const signup = useCallback(
    async (input: SignupInput) =>
      persist(await api<AuthResponse>('/api/auth/signup', { method: 'POST', body: JSON.stringify(input) })),
    [persist],
  )

  const verify = useCallback(
    async (email: string, code: string, rememberDevice: boolean) => {
      persist(
        await api<AuthResponse>('/api/auth/verify', {
          method: 'POST',
          body: JSON.stringify({ email, code, rememberDevice }),
        }),
      )
    },
    [persist],
  )

  const resend = useCallback(async (email: string) => {
    await api<AuthResponse>('/api/auth/resend', { method: 'POST', body: JSON.stringify({ email }) })
  }, [])

  const forgotPassword = useCallback(async (email: string) => {
    await api<AuthResponse>('/api/auth/forgot', { method: 'POST', body: JSON.stringify({ email }) })
  }, [])

  const resetPassword = useCallback(
    async (email: string, code: string, password: string) => {
      persist(
        await api<AuthResponse>('/api/auth/reset', {
          method: 'POST',
          body: JSON.stringify({ email, code, password }),
        }),
      )
    },
    [persist],
  )

  const signInWithGoogle = useCallback(
    async (idToken: string) => {
      persist(await api<AuthResponse>('/api/auth/google', { method: 'POST', body: JSON.stringify({ idToken }) }))
    },
    [persist],
  )

  const logout = useCallback(() => {
    setToken(null)
    clearStored(USER_KEY)
    setUser(null)
  }, [])

  const updateUser = useCallback((updated: UserProfile) => {
    writeStored(USER_KEY, JSON.stringify(updated))
    setUser(updated)
  }, [])

  const value = useMemo(
    () => ({ user, login, signup, verify, resend, forgotPassword, resetPassword,
              signInWithGoogle, logout, updateUser }),
    [user, login, signup, verify, resend, forgotPassword, resetPassword,
     signInWithGoogle, logout, updateUser],
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
