import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import type { AuthOptions } from '../api/types'
import { useAuth } from './AuthContext'
import { useT } from '../i18n'

const GSI_SRC = 'https://accounts.google.com/gsi/client'

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: { client_id: string; callback: (r: { credential: string }) => void }) => void
          renderButton: (parent: HTMLElement, options: Record<string, unknown>) => void
        }
      }
    }
  }
}

function loadGsi(): Promise<void> {
  if (document.querySelector(`script[src="${GSI_SRC}"]`)) {
    return Promise.resolve()
  }
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = GSI_SRC
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Google sign-in unavailable'))
    document.head.appendChild(script)
  })
}

/** Renders Google's own button, but only where the server has it configured. */
export function GoogleSignInButton() {
  const t = useT()
  const { signInWithGoogle } = useAuth()
  const navigate = useNavigate()
  const container = useRef<HTMLDivElement>(null)
  const [error, setError] = useState<string | null>(null)
  const { data: options } = useQuery({
    queryKey: ['auth', 'options'],
    queryFn: () => api<AuthOptions & { clientId?: string }>('/api/auth/options'),
    staleTime: 5 * 60_000,
  })

  useEffect(() => {
    if (!options?.google || !options.clientId || !container.current) {
      return
    }
    let cancelled = false
    loadGsi()
      .then(() => {
        if (cancelled || !window.google || !container.current) {
          return
        }
        window.google.accounts.id.initialize({
          client_id: options.clientId!,
          callback: async (response) => {
            try {
              await signInWithGoogle(response.credential)
              navigate('/')
            } catch (e) {
              setError(e instanceof Error ? e.message : t('auth.googleFailed'))
            }
          },
        })
        window.google.accounts.id.renderButton(container.current, {
          theme: 'filled_black',
          size: 'large',
          width: 320,
          text: 'continue_with',
        })
      })
      .catch(() => setError(t('auth.googleFailed')))
    return () => {
      cancelled = true
    }
  }, [options, signInWithGoogle, navigate, t])

  if (!options?.google) {
    return null
  }
  return (
    <div className="mt-6 space-y-2">
      <div className="flex items-center gap-3 text-xs text-slate-500">
        <span className="h-px flex-1 bg-slate-800" />
        {t('auth.or')}
        <span className="h-px flex-1 bg-slate-800" />
      </div>
      <div ref={container} className="flex justify-center" />
      {error && <p className="text-center text-sm text-red-400">{error}</p>}
    </div>
  )
}
