import { describe, expect, it } from 'vitest'
import { en } from './en'
import { fr } from './fr'

describe('catalogues', () => {
  it('French covers every English key', () => {
    expect(Object.keys(fr).sort()).toEqual(Object.keys(en).sort())
  })

  it('has no untranslated leftovers', () => {
    // keys whose French text is identical to English are suspicious unless
    // the word genuinely is the same (brand names, abbreviations, admin)
    // words that are genuinely identical in both languages
    const allowed = new Set([
      'app.name',
      'common.points',
      'leagues.adminBadge',
      'leagues.code',
      'nav.admin',
      'admin.create',
    ])
    const identical = Object.keys(en).filter(
      (key) => !allowed.has(key) && en[key as keyof typeof en] === fr[key as keyof typeof fr],
    )
    expect(identical).toEqual([])
  })

  it('keeps placeholders consistent between locales', () => {
    for (const key of Object.keys(en) as (keyof typeof en)[]) {
      const placeholders = (text: string) => (text.match(/\{(\w+)\}/g) ?? []).sort()
      expect(placeholders(fr[key]), `placeholders differ for ${key}`).toEqual(placeholders(en[key]))
    }
  })
})
