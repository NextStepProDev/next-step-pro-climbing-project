import { describe, it, expect } from 'vitest'
import { areasIn, filterAscents, hasActiveFilters, sortAscents, EMPTY_FILTERS } from './ascentFiltering'
import type { Ascent } from '../../types'

function ascent(overrides: Partial<Ascent> = {}): Ascent {
  return {
    id: overrides.routeName ?? Math.random().toString(),
    terrain: 'ROCK',
    climbedOn: '2026-05-01',
    discipline: 'SPORT',
    gradeScale: 'FRENCH_ROUTE',
    grade: 'FR_7A',
    gradeLabel: '7a',
    gradeRank: 140,
    style: 'RP',
    area: 'Jura Północna',
    crag: 'Dolina Bolechowicka',
    routeName: 'Pajęczyna',
    attempts: null,
    qualityStars: null,
    comment: null,
    winter: null,
    originalGrade: null,
    lengthMeters: null,
    pitches: null,
    durationMinutes: null,
    ledGrade: null,
    ledGradeLabel: null,
    ledGradeRank: null,
    ledPitches: null,
    partners: null,
    createdAt: '2026-05-01T10:00:00Z',
    ...overrides,
  }
}

describe('filterAscents', () => {
  it('matches the route name case-insensitively', () => {
    const entries = [ascent({ routeName: 'Wielkie Ciśnienie' }), ascent({ routeName: 'Pajęczyna' })]

    const result = filterAscents(entries, { ...EMPTY_FILTERS, search: 'wielkie' })

    expect(result.map(e => e.routeName)).toEqual(['Wielkie Ciśnienie'])
  })

  it('falls back to the crag when the route name has slipped the mind', () => {
    const entries = [ascent({ crag: 'Kołoczek' }), ascent({ crag: 'Rzędkowice' })]

    const result = filterAscents(entries, { ...EMPTY_FILTERS, search: 'kołoczek' })

    expect(result).toHaveLength(1)
  })

  it('combines discipline, style and area', () => {
    const entries = [
      ascent({ routeName: 'A', discipline: 'SPORT', style: 'OS', area: 'Jura' }),
      ascent({ routeName: 'B', discipline: 'SPORT', style: 'RP', area: 'Jura' }),
      ascent({ routeName: 'C', discipline: 'BOULDER', style: 'OS', area: 'Jura' }),
      ascent({ routeName: 'D', discipline: 'SPORT', style: 'OS', area: 'Sokoliki' }),
    ]

    const result = filterAscents(entries, {
      discipline: 'SPORT', style: 'OS', area: 'Jura', search: '',
    })

    expect(result.map(e => e.routeName)).toEqual(['A'])
  })

  it('treats an empty filter set as no filter at all', () => {
    const entries = [ascent(), ascent()]

    expect(filterAscents(entries, EMPTY_FILTERS)).toHaveLength(2)
    expect(hasActiveFilters(EMPTY_FILTERS)).toBe(false)
    expect(hasActiveFilters({ ...EMPTY_FILTERS, search: '  ' })).toBe(false)
    expect(hasActiveFilters({ ...EMPTY_FILTERS, style: 'OS' })).toBe(true)
  })
})

describe('sortAscents', () => {
  // The rule this file exists for: "10a" sorts before "9c" as text, and the two scales
  // interleave alphabetically into an order that means nothing
  it('sorts by difficulty on the rank, not on the label', () => {
    const entries = [
      ascent({ routeName: 'nine', gradeLabel: '9c', gradeRank: 300 }),
      ascent({ routeName: 'seven', gradeLabel: '7a', gradeRank: 140 }),
      ascent({ routeName: 'eight', gradeLabel: '8b', gradeRank: 220 }),
    ]

    const result = sortAscents(entries, 'grade', 'desc')

    expect(result.map(e => e.routeName)).toEqual(['nine', 'eight', 'seven'])
  })

  it('does not mutate the input', () => {
    const entries = [ascent({ gradeRank: 10 }), ascent({ gradeRank: 300 })]
    const before = [...entries]

    sortAscents(entries, 'grade', 'desc')

    expect(entries).toEqual(before)
  })

  it('breaks ties on the date so the order is stable between renders', () => {
    const entries = [
      ascent({ routeName: 'older', climbedOn: '2026-01-01', gradeRank: 140 }),
      ascent({ routeName: 'newer', climbedOn: '2026-06-01', gradeRank: 140 }),
    ]

    expect(sortAscents(entries, 'grade', 'desc').map(e => e.routeName))
      .toEqual(['newer', 'older'])
  })

  it('sorts unrated routes below rated ones', () => {
    const entries = [
      ascent({ routeName: 'unrated', qualityStars: null }),
      ascent({ routeName: 'one star', qualityStars: 1 }),
    ]

    expect(sortAscents(entries, 'stars', 'desc').map(e => e.routeName))
      .toEqual(['one star', 'unrated'])
  })
})

describe('areasIn', () => {
  it('deduplicates and sorts with Polish collation', () => {
    const entries = [
      ascent({ area: 'Sokoliki' }),
      ascent({ area: 'Jura Północna' }),
      ascent({ area: 'Jura Północna' }),
    ]

    expect(areasIn(entries)).toEqual(['Jura Północna', 'Sokoliki'])
  })
})
