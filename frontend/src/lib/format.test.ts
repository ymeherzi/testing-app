import { describe, expect, it } from 'vitest'
import { countdown, countryFlag, pointsLabel } from './format'

describe('countdown', () => {
  const now = new Date('2026-07-01T12:00:00Z')

  it('is null once kickoff has passed', () => {
    expect(countdown('2026-07-01T11:59:00Z', now)).toBeNull()
    expect(countdown('2026-07-01T12:00:00Z', now)).toBeNull()
  })

  it('formats minutes, hours and days', () => {
    expect(countdown('2026-07-01T12:45:00Z', now)).toBe('45m')
    expect(countdown('2026-07-01T14:05:00Z', now)).toBe('2h 05m')
    expect(countdown('2026-07-04T16:00:00Z', now)).toBe('3d 4h')
  })
})

describe('pointsLabel', () => {
  it('labels every tier', () => {
    expect(pointsLabel(3)).toBe('Exact score')
    expect(pointsLabel(2)).toBe('Right margin')
    expect(pointsLabel(1)).toBe('Right call')
    expect(pointsLabel(0)).toBe('Missed')
  })
})

describe('countryFlag', () => {
  it('maps ISO codes to regional indicators', () => {
    expect(countryFlag('FR')).toBe('🇫🇷')
    expect(countryFlag('tn')).toBe('🇹🇳')
    expect(countryFlag(null)).toBe('')
  })
})
