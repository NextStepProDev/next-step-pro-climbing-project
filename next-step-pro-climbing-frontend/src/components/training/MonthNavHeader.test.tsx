import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MonthNavHeader } from './MonthNavHeader'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'pl' },
  }),
}))

function renderNav(currentMonth: Date) {
  const onMonthChange = vi.fn()
  render(<MonthNavHeader currentMonth={currentMonth} onMonthChange={onMonthChange} />)
  return {
    onMonthChange,
    next: () => screen.getByLabelText('nav.nextMonth'),
    prev: () => screen.getByLabelText('nav.prevMonth'),
  }
}

/** The month the callback was handed, as "yyyy-MM" — the day itself is the anchor's business. */
function monthOf(call: unknown[]): string {
  const d = call[0] as Date
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

describe('MonthNavHeader', () => {
  /**
   * The anchor keeps its day of the month, so paging used to run through Date.setMonth, which
   * OVERFLOWS: 31 January + 1 month = "31 February" = 3 March. February was unreachable going
   * forward, and the calendar looked like it had lost a month.
   */
  it('lands on February when paging forward from the 31st of January', async () => {
    const user = userEvent.setup()
    const { onMonthChange, next } = renderNav(new Date(2026, 0, 31))

    await user.click(next())

    expect(monthOf(onMonthChange.mock.calls[0])).toBe('2026-02')
  })

  it('lands on September when paging forward from the 31st of August', async () => {
    const user = userEvent.setup()
    const { onMonthChange, next } = renderNav(new Date(2026, 7, 31))

    await user.click(next())

    expect(monthOf(onMonthChange.mock.calls[0])).toBe('2026-09')
  })

  it('lands on February when paging backward from the 31st of March', async () => {
    const user = userEvent.setup()
    const { onMonthChange, prev } = renderNav(new Date(2026, 2, 31))

    await user.click(prev())

    expect(monthOf(onMonthChange.mock.calls[0])).toBe('2026-02')
  })

  it('pages normally from a day every month has', async () => {
    const user = userEvent.setup()
    const { onMonthChange, next, prev } = renderNav(new Date(2026, 7, 10))

    await user.click(next())
    await user.click(prev())

    expect(monthOf(onMonthChange.mock.calls[0])).toBe('2026-09')
    expect(monthOf(onMonthChange.mock.calls[1])).toBe('2026-07')
  })

  it('crosses the year boundary in both directions', async () => {
    const user = userEvent.setup()
    const { onMonthChange, next } = renderNav(new Date(2026, 11, 31))

    await user.click(next())

    expect(monthOf(onMonthChange.mock.calls[0])).toBe('2027-01')
  })
})
