import { describe, it, expect } from 'vitest'
import { differenceInCalendarDays, format, parseISO } from 'date-fns'
import { MONTH_GRID_DAYS, monthGridDays, monthGridRange, resolveInitialView } from './monthGrid'

describe('monthGridDays — a fixed six-row grid', () => {
  it('should return 42 days for a month that starts on a Monday', () => {
    // June 2026 starts on a Monday and has 30 days: the "natural" grid would be 35
    expect(monthGridDays(new Date(2026, 5, 15))).toHaveLength(MONTH_GRID_DAYS)
  })

  it('should return 42 days for a month that starts on a Sunday', () => {
    // The worst case: the 1st sits at the far end of the first row
    expect(monthGridDays(new Date(2026, 2, 15))).toHaveLength(MONTH_GRID_DAYS)
  })

  it('should return 42 days for February in a non-leap year', () => {
    // 28 days starting on a Sunday is the smallest month a calendar can be asked to draw
    expect(monthGridDays(new Date(2026, 1, 15))).toHaveLength(MONTH_GRID_DAYS)
  })

  it('should keep the grid the same height across every month of a year', () => {
    // The point of the fixed length: paging must never move the content below the grid
    const lengths = Array.from({ length: 12 }, (_, m) => monthGridDays(new Date(2026, m, 15)).length)

    expect(new Set(lengths)).toEqual(new Set([MONTH_GRID_DAYS]))
  })

  it('should start every grid on a Monday', () => {
    for (let m = 0; m < 12; m++) {
      expect(monthGridDays(new Date(2026, m, 15))[0].getDay()).toBe(1)
    }
  })

  it('should start on the Monday on or before the 1st', () => {
    // August 2026 starts on a Saturday, so the grid opens on 27 July
    expect(format(monthGridDays(new Date(2026, 7, 10))[0], 'yyyy-MM-dd')).toBe('2026-07-27')
  })

  it('should cover the whole month plus the days that pad it out', () => {
    const days = monthGridDays(new Date(2026, 7, 10)).map((d) => format(d, 'yyyy-MM-dd'))

    expect(days).toContain('2026-08-01')
    expect(days).toContain('2026-08-31')
    // Trailing days come from September — the old grid simply stopped and left a ragged edge
    expect(days).toContain('2026-09-01')
  })

  it('should produce 42 distinct consecutive days across a spring-forward change', () => {
    // Poland springs forward on 2026-03-29. Arithmetic on local Date objects can quietly
    // produce a 23-hour day and drop or duplicate one.
    const days = monthGridDays(new Date(2026, 2, 15)).map((d) => format(d, 'yyyy-MM-dd'))

    expect(new Set(days).size).toBe(MONTH_GRID_DAYS)
    expect(days).toContain('2026-03-28')
    expect(days).toContain('2026-03-29')
    expect(days).toContain('2026-03-30')
  })

  it('should produce 42 distinct consecutive days across a fall-back change', () => {
    // Poland falls back on 2026-10-25 — the mirror case, a 25-hour day
    const days = monthGridDays(new Date(2026, 9, 15)).map((d) => format(d, 'yyyy-MM-dd'))

    expect(new Set(days).size).toBe(MONTH_GRID_DAYS)
    expect(days).toContain('2026-10-24')
    expect(days).toContain('2026-10-25')
    expect(days).toContain('2026-10-26')
  })
})

describe('monthGridRange — what the API is asked for', () => {
  it('should span exactly the rendered grid', () => {
    const anchor = new Date(2026, 7, 10)
    const days = monthGridDays(anchor)
    const { from, to } = monthGridRange(anchor)

    expect(from).toBe(format(days[0], 'yyyy-MM-dd'))
    expect(to).toBe(format(days[MONTH_GRID_DAYS - 1], 'yyyy-MM-dd'))
  })

  it('should never exceed the 62-day limit the backend enforces', () => {
    // TrainingCalendarService.MAX_RANGE_DAYS = 62; a wider range is a 400, not a slow page.
    // Prefetching a neighbouring month or widening the grid would cross this silently.
    for (let m = 0; m < 12; m++) {
      const { from, to } = monthGridRange(new Date(2026, m, 15))

      expect(differenceInCalendarDays(parseISO(to), parseISO(from))).toBeLessThanOrEqual(62)
    }
  })

  it('should format dates without converting through UTC', () => {
    // A toISOString()-based implementation returns 2026-07-26 here: local midnight in
    // Warsaw is the previous day in UTC.
    expect(monthGridRange(new Date(2026, 7, 10)).from).toBe('2026-07-27')
  })
})

describe('resolveInitialView — the URL param wins over the viewport', () => {
  it('should honour an explicit month even on a phone', () => {
    expect(resolveInitialView('month', true)).toBe('month')
  })

  it('should honour an explicit week even on a desktop', () => {
    expect(resolveInitialView('week', false)).toBe('week')
  })

  it('should default a first visit to month on a wide screen', () => {
    expect(resolveInitialView(null, false)).toBe('month')
  })

  it('should default a first visit to week on a narrow screen', () => {
    expect(resolveInitialView(null, true)).toBe('week')
  })

  it('should fall back to the viewport for an unrecognised param', () => {
    // A stale or hand-edited ?cal= must not strand the user on a blank branch
    expect(resolveInitialView('agenda', true)).toBe('week')
    expect(resolveInitialView('agenda', false)).toBe('month')
  })
})
