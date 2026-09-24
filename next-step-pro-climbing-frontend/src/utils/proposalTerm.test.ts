import { describe, it, expect } from 'vitest'
import { pl } from 'date-fns/locale'
import { formatTerm } from './proposalTerm'

describe('formatTerm — one line naming a term', () => {
  it('should name the day and the hours', () => {
    expect(formatTerm({ date: '2026-10-14', startTime: '18:00:00', endTime: '20:00:00' }, pl, 'cały dzień'))
      .toBe('śr. 14.10, 18:00–20:00')
  })

  it('should say "all day" when there are no hours', () => {
    expect(formatTerm({ date: '2026-10-14', startTime: null, endTime: null }, pl, 'cały dzień'))
      .toBe('śr. 14.10, cały dzień')
  })

  it('should span the days of a multi-day entry', () => {
    expect(formatTerm({ date: '2026-10-14', endDate: '2026-10-16', startTime: null, endTime: null }, pl, 'cały dzień'))
      .toBe('śr. 14.10 – pt. 16.10, cały dzień')
  })
})
