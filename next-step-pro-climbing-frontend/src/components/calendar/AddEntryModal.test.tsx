import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AddEntryModal } from './AddEntryModal'

// The t mock echoes the key, so every label below is queried by its key.
vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${Object.values(opts).join(',')}` : key,
    i18n: { language: 'pl' },
  }),
}))

function renderChooser(date = '2030-06-10') {
  const onPick = vi.fn()
  const onClose = vi.fn()
  render(<AddEntryModal isOpen onClose={onClose} date={date} onPick={onPick} />)
  return { onPick, onClose }
}

const tile = (key: string) => screen.getByRole('button', { name: new RegExp(`addEntry\\.${key}`) })

describe('AddEntryModal — the "+" shows both things that decide the entry', () => {
  it('should offer a slot and all three lengths an event can have', () => {
    renderChooser()

    expect(tile('slotHours')).toBeInTheDocument()
    expect(tile('eventHours')).toBeInTheDocument()
    expect(tile('eventAllDay')).toBeInTheDocument()
    expect(tile('eventRange')).toBeInTheDocument()
  })

  it('should report the tile that was pressed and no other', async () => {
    const user = userEvent.setup()
    const { onPick } = renderChooser()

    await user.click(tile('slotHoursHint'))
    expect(onPick.mock.calls[0][0].shape).toBe('slotHours')

    await user.click(tile('eventAllDayHint'))
    expect(onPick.mock.calls[1][0].shape).toBe('eventAllDay')
  })

  // The case that broke the first design: recreational climbing is an EVENT lasting two hours,
  // and a chooser that derives the row from the duration alone has no door for it. Opening the
  // form with the clocks already set is the whole point — otherwise this tile is just the all-day
  // one with an extra checkbox to untick.
  it('should open an hourly event with its clocks already showing', async () => {
    const user = userEvent.setup()
    const { onPick } = renderChooser('2030-06-10')

    await user.click(tile('eventHoursHint'))

    expect(onPick).toHaveBeenCalledWith({
      shape: 'eventHours',
      startDate: '2030-06-10',
      endDate: '2030-06-10',
      // ⚠️ The same hours the event form falls back to when "all day" is unticked by hand. The
      // two doors have to land on the same form.
      startTime: '10:00',
      endTime: '17:00',
    })
  })

  it('should leave the other three without times, so their forms open as all-day', async () => {
    const user = userEvent.setup()
    const { onPick } = renderChooser()

    await user.click(tile('slotHoursHint'))
    await user.click(tile('eventAllDayHint'))
    await user.click(tile('eventRangeHint'))

    for (const call of onPick.mock.calls) {
      expect(call[0].startTime).toBeUndefined()
      expect(call[0].endTime).toBeUndefined()
    }
  })

  // The tile promises several days, so the form has to open as a range. Opening it with the same
  // date on both ends would put a one-day event behind a tile that said otherwise, and the admin
  // would have to notice and fix it.
  it('should give a range a second day, so the form matches the tile', async () => {
    const user = userEvent.setup()
    const { onPick } = renderChooser('2030-06-10')

    await user.click(tile('eventRangeHint'))

    expect(onPick).toHaveBeenCalledWith({
      shape: 'eventRange',
      startDate: '2030-06-10',
      endDate: '2030-06-11',
    })
  })

  // Month-end is where string arithmetic on a date label goes wrong, and nothing on screen would
  // show it: the form would simply open on a date nobody chose.
  it('should roll a range over the end of a month rather than adding to the number', async () => {
    const user = userEvent.setup()
    const { onPick } = renderChooser('2030-06-30')

    await user.click(tile('eventRangeHint'))

    expect(onPick.mock.calls[0][0].endDate).toBe('2030-07-01')
  })

  it('should leave every shape but a range on the single day that was opened', async () => {
    const user = userEvent.setup()
    const { onPick } = renderChooser('2030-06-10')

    await user.click(tile('slotHoursHint'))
    await user.click(tile('eventAllDayHint'))

    expect(onPick.mock.calls[0][0]).toMatchObject({ startDate: '2030-06-10', endDate: '2030-06-10' })
    expect(onPick.mock.calls[1][0]).toMatchObject({ startDate: '2030-06-10', endDate: '2030-06-10' })
  })

  // A 'yyyy-MM-dd' from the API is a label, not a moment: `new Date(s)` would read it as midnight
  // UTC and name the day before anywhere west of Greenwich.
  it('should name the day the admin opened, not the one before it', () => {
    renderChooser()

    expect(screen.getByText(/^addEntry\.title:10/)).toBeInTheDocument()
  })

  // `?date=` is hand-editable and reaches this component raw. `format` on an Invalid Date throws,
  // and a throw in render takes the whole calendar page down — not just this modal.
  it('should fall back to the raw label rather than throw on an unparseable date', () => {
    expect(() => renderChooser('2030-06-31')).not.toThrow()

    expect(screen.getByText('addEntry.title:2030-06-31')).toBeInTheDocument()
  })

  // The same unparseable label reaches the date arithmetic, where `addDays` keeps it invalid and
  // `format` would throw — on a click rather than on render, which is worse: the page is up and
  // then disappears under the admin.
  it('should not throw when a range is picked on an unparseable date', async () => {
    const user = userEvent.setup()
    const { onPick } = renderChooser('2030-06-31')

    await user.click(tile('eventRangeHint'))

    expect(onPick).toHaveBeenCalledWith({
      shape: 'eventRange',
      startDate: '2030-06-31',
      endDate: '2030-06-31',
    })
  })
})
