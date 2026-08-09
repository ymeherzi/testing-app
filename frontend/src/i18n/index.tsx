import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { en, type MessageKey, type Messages } from './en'
import { fr } from './fr'
import { readStored, writeStored } from '../lib/storage'

export const LOCALES = { en: 'English', fr: 'Français' } as const
export type Locale = keyof typeof LOCALES

const CATALOGUES: Record<Locale, Messages> = { en, fr }
const STORAGE_KEY = 'locale'

/**
 * The active locale is also readable outside React (see currentLocale) so
 * date and country formatting can follow the user's choice without every
 * helper taking a locale argument.
 */
let active: Locale = 'en'

export function currentLocale(): Locale {
  return active
}

function detectLocale(): Locale {
  const stored = readStored(STORAGE_KEY)
  if (stored && stored in CATALOGUES) {
    return stored as Locale
  }
  for (const preference of navigator.languages ?? [navigator.language]) {
    const base = preference.split('-')[0]
    if (base in CATALOGUES) {
      return base as Locale
    }
  }
  return 'en'
}

export type Translate = (key: MessageKey, params?: Record<string, string | number>) => string

function translator(locale: Locale): Translate {
  const catalogue = CATALOGUES[locale]
  const plurals = new Intl.PluralRules(locale)
  return (key, params) => {
    let template: string | undefined
    if (params?.count !== undefined) {
      const variant = `${key}_${plurals.select(Number(params.count))}` as MessageKey
      template = catalogue[variant]
    }
    template ??= catalogue[key] ?? en[key] ?? key
    if (!params) {
      return template
    }
    return template.replace(/\{(\w+)\}/g, (match, name: string) =>
      params[name] !== undefined ? String(params[name]) : match,
    )
  }
}

interface I18nState {
  locale: Locale
  setLocale: (locale: Locale) => void
  t: Translate
}

const I18nContext = createContext<I18nState | null>(null)

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(() => {
    const detected = detectLocale()
    active = detected
    document.documentElement.lang = detected
    return detected
  })

  const setLocale = useCallback((next: Locale) => {
    writeStored(STORAGE_KEY, next)
    active = next
    document.documentElement.lang = next
    setLocaleState(next)
  }, [])

  const value = useMemo(() => ({ locale, setLocale, t: translator(locale) }), [locale, setLocale])
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export function useI18n(): I18nState {
  const context = useContext(I18nContext)
  if (!context) {
    throw new Error('useI18n must be used within I18nProvider')
  }
  return context
}

/** Shorthand for components that only need the translate function. */
export function useT(): Translate {
  return useI18n().t
}
