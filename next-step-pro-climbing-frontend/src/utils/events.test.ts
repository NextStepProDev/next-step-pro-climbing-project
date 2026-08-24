import { describe, it, expect } from 'vitest'
import { formatEventWhen } from './events'
import type { EventSummary } from '../types'

type When = Pick<EventSummary, 'startDate' | 'endDate' | 'startTime' | 'endTime' | 'isMultiDay'>

function event(overrides: Partial<When> = {}): When {
  const startDate = overrides.startDate ?? '2026-08-25'
  const endDate = overrides.endDate ?? startDate
  return {
    startDate,
    endDate,
    startTime: null,
    endTime: null,
    isMultiDay: startDate !== endDate,
    ...overrides,
  }
}

const opts = { dateFormat: 'dd.MM.yyyy', allDayLabel: 'Cały dzień' }

describe('formatEventWhen', () => {
  it('should put the hours next to the day when a single-day event carries them', () => {
    expect(formatEventWhen(event({ startTime: '18:00:00', endTime: '20:00:00' }), opts))
      .toBe('25.08.2026 · 18:00–20:00')
  })

  it('should accept a time already trimmed to HH:mm', () => {
    expect(formatEventWhen(event({ startTime: '18:00', endTime: '20:00' }), opts))
      .toBe('25.08.2026 · 18:00–20:00')
  })

  it('should name the day as all-day when a single-day event has no hours', () => {
    expect(formatEventWhen(event(), opts)).toBe('25.08.2026 · Cały dzień')
  })

  // Reachable through the API only (nullable columns, no CHECK) — but "all day" would be a lie
  // about an entry that does carry an hour.
  it('should not call a single day all-day when it carries one of the two clocks', () => {
    expect(formatEventWhen(event({ startTime: '18:00:00' }), opts)).toBe('25.08.2026')
    expect(formatEventWhen(event({ endTime: '20:00:00' }), opts)).toBe('25.08.2026')
  })

  // The one thing in here that is easy to "simplify" the wrong way. The backend allows an end
  // earlier than the start once the dates differ, so a Friday 18:00 -> Sunday 08:00 trip must
  // never collapse into "18:00–08:00", which would describe a single day.
  it('should keep each clock beside its own date on a multi-day span, never as a range', () => {
    const trip = event({
      startDate: '2026-08-28', endDate: '2026-08-30',
      startTime: '18:00:00', endTime: '08:00:00',
    })
    expect(formatEventWhen(trip, opts)).toBe('28.08.2026 18:00 – 30.08.2026 08:00')
    expect(formatEventWhen(trip, opts)).not.toContain('18:00–08:00')
  })

  it('should show only the hour it has when a multi-day event carries one side', () => {
    expect(formatEventWhen(event({ startDate: '2026-08-25', endDate: '2026-08-28', startTime: '18:00:00' }), opts))
      .toBe('25.08.2026 18:00 – 28.08.2026')
  })

  // A bare date range already reads as whole days, so no all-day label is added here.
  it('should leave a multi-day span without hours as a plain date range', () => {
    expect(formatEventWhen(event({ startDate: '2026-08-25', endDate: '2026-08-28' }), opts))
      .toBe('25.08.2026 – 28.08.2026')
  })

  // A 'yyyy-MM-dd' is a label, not a moment: new Date() would read it as UTC midnight and print
  // the previous day west of Greenwich. `npm run test:tz` runs this file again under
  // America/New_York, which is what actually proves it.
  it('should print the labelled day, not a moment shifted by the device zone', () => {
    expect(formatEventWhen(event({ startDate: '2026-01-01' }), opts))
      .toBe('01.01.2026 · Cały dzień')
  })
})
