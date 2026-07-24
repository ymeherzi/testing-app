import { describe, expect, it } from 'vitest'
import { countries, countryName } from './countries'

describe('countries', () => {
  it('covers the world, not a curated shortlist', () => {
    const list = countries('en')
    expect(list.length).toBeGreaterThan(200)
    const codes = list.map((c) => c.code)
    // previously-missing examples from every continent
    expect(codes).toEqual(expect.arrayContaining(['LY', 'SD', 'SY', 'IQ', 'PS', 'AL', 'HR', 'PE', 'VN', 'NZ']))
  })

  it('is sorted alphabetically in the requested locale', () => {
    const names = countries('en').map((c) => c.name)
    expect([...names].sort((a, b) => a.localeCompare(b, 'en'))).toEqual(names)
  })

  it('localizes names, so French users read French', () => {
    expect(countryName('DE', 'fr')).toBe('Allemagne')
    expect(countryName('DE', 'en')).toBe('Germany')
    expect(countryName(null, 'en')).toBeNull()
  })
})
