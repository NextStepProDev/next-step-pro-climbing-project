import { describe, it, expect } from 'vitest'
import { dateTimeLocalChanged, toDateTimeLocalValue } from './dateTimeLocal'

describe('datetime-local values', () => {
  // A January instant read in October: in any zone with DST the offsets differ, which is exactly
  // where shifting by today's offset went wrong (Warsaw locally, New York under test:tz).
  const winter = '2026-01-15T10:30:45.123Z'

  it('round-trips an instant from the other side of a DST change to the same minute', () => {
    const value = toDateTimeLocalValue(winter)
    expect(new Date(value).getTime()).toBe(new Date('2026-01-15T10:30:00.000Z').getTime())
  })

  it('does not call an untouched field an edit, seconds and all', () => {
    expect(dateTimeLocalChanged(toDateTimeLocalValue(winter), winter)).toBe(false)
  })

  it('does see a real change', () => {
    expect(dateTimeLocalChanged('2026-01-15T09:00', winter)).toBe(true)
  })
})
