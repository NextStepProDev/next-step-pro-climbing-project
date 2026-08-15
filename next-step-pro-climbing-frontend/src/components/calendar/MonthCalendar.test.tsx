import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { MonthCalendar } from './MonthCalendar'
import type { DaySummary } from '../../types'

// utils/events pulls in src/i18n, which needs the real initReactI18next — only the hook is faked.
vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${Object.values(opts).join(',')}` : key,
    i18n: { language: 'pl' },
  }),
}))

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ isAuthenticated: false }),
}))

// Far enough ahead that nothing in the grid counts as past, whatever day the suite runs on.
const JUNE_2030 = new Date(2030, 5, 15)

function makeDay(date: string, overrides: Partial<DaySummary> = {}): DaySummary {
  return {
    date,
    totalSlots: 0,
    availableSlots: 0,
    hasUserReservation: false,
    hasAvailabilityWindow: false,
    hasReservedSeats: false,
    unavailableRanges: [],
    ...overrides,
  }
}

function renderMonth(days: DaySummary[]) {
  const view = render(
    <MonthCalendar
      currentMonth={JUNE_2030}
      onMonthChange={vi.fn()}
      days={days}
      events={[]}
      onDayClick={vi.fn()}
      eventColorMap={new Map()}
    />,
  )
  const cellFor = (dayNumber: string) =>
    Array.from(view.container.querySelectorAll<HTMLElement>('.aspect-square')).find(
      (cell) => cell.querySelector('div')?.textContent === dayNumber,
    ) as HTMLElement
  return { ...view, cellFor }
}

describe('MonthCalendar — instructor absence', () => {
  it('should name the hours instead of claiming the whole day is off', () => {
    const cell = renderMonth([
      makeDay('2030-06-10', { unavailableRanges: [{ startTime: '18:00:00', endTime: '20:00:00' }] }),
    ]).cellFor('10')

    expect(cell.textContent).toContain('18–20')
    // The whole-cell tint is what used to read as "nothing can be done that day"
    expect(cell.className).not.toContain('bg-slate-500/10')
  })

  it('should keep the minutes of an absence that does not start on the hour', () => {
    const cell = renderMonth([
      makeDay('2030-06-10', { unavailableRanges: [{ startTime: '08:30:00', endTime: '11:00:00' }] }),
    ]).cellFor('10')

    expect(cell.textContent).toContain('08:30–11')
  })

  it('should show every absence of the day, in the order the API sorted them', () => {
    const cell = renderMonth([
      makeDay('2030-06-10', {
        unavailableRanges: [
          { startTime: '08:30:00', endTime: '11:00:00' },
          { startTime: '17:00:00', endTime: '21:30:00' },
        ],
      }),
    ]).cellFor('10')

    expect(cell.textContent).toContain('08:30–11')
    expect(cell.textContent).toContain('17–21:30')
    expect(cell.textContent!.indexOf('08:30–11')).toBeLessThan(cell.textContent!.indexOf('17–21:30'))
  })

  it('should still announce the free sessions of a day that also carries an absence', () => {
    // The point of the whole change: an absence from 18:00 does not close the morning
    const cell = renderMonth([
      makeDay('2030-06-10', {
        totalSlots: 2,
        availableSlots: 2,
        unavailableRanges: [{ startTime: '18:00:00', endTime: '20:00:00' }],
      }),
    ]).cellFor('10')

    expect(cell.textContent).toContain('training')
    expect(cell.textContent).toContain('18–20')
  })

  it('should leave a day without absences alone', () => {
    const cell = renderMonth([makeDay('2030-06-10')]).cellFor('10')

    expect(cell.textContent).not.toContain('day.unavailable')
  })
})
