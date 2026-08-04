import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TrainingMonthCalendar } from './TrainingMonthCalendar'
import { MONTH_GRID_DAYS } from './monthGrid'
import { makeInvitation, makeReservation, makeTraining } from '../../test/factories'

// Interpolates the options so "+N" and the per-day add labels stay distinguishable, while
// every other string is the raw key like the rest of the suite.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${Object.values(opts).join(',')}` : key,
    i18n: { language: 'pl' },
  }),
}))

// August 2026 starts on a Saturday, so the grid opens on 27 July and runs into September —
// the case where padding days matter most.
const AUGUST = new Date(2026, 7, 10)

function renderMonth(props: Partial<React.ComponentProps<typeof TrainingMonthCalendar>> = {}) {
  const handlers = {
    onMonthChange: vi.fn(),
    onTrainingClick: vi.fn(),
    onReservationClick: vi.fn(),
    onInvitationClick: vi.fn(),
    onDayClick: vi.fn(),
    onDayExpand: vi.fn(),
    onPasteAt: vi.fn(),
  }
  const view = render(
    <TrainingMonthCalendar
      currentMonth={AUGUST}
      trainings={[]}
      reservations={[]}
      invitations={[]}
      invitationLabel="overlay.invitation"
      {...handlers}
      {...props}
    />,
  )
  // The day number is the only text every cell carries
  const cells = () => Array.from(view.container.querySelectorAll<HTMLElement>('.min-h-32'))
  const cellFor = (day: string) =>
    cells().find((c) => c.querySelector('div')?.textContent === day) as HTMLElement
  return { ...view, ...handlers, cells, cellFor }
}

describe('TrainingMonthCalendar — the grid', () => {
  it('should always render six rows of seven', () => {
    expect(renderMonth().cells()).toHaveLength(MONTH_GRID_DAYS)
  })

  it('should keep the same cell count for a month that starts on a Monday', () => {
    // Without a fixed length this month would be 35 cells and the page below would jump
    expect(renderMonth({ currentMonth: new Date(2026, 5, 15) }).cells()).toHaveLength(MONTH_GRID_DAYS)
  })

  it('should dim the days that belong to a neighbouring month', () => {
    const { cells } = renderMonth()
    // The grid opens on 27 July: the first four cells are padding
    expect(cells()[0].className).toContain('opacity-40')
    expect(cells()[5].className).not.toContain('opacity-40')
  })

  it('should keep padding days clickable', () => {
    // They used to be inert filler divs, so 31 July was unreachable from the August grid
    const { cells, onDayClick } = renderMonth()

    cells()[0].click()

    expect(onDayClick).toHaveBeenCalledWith('2026-07-27')
  })
})

describe('TrainingMonthCalendar — entries in a cell', () => {
  it('should order invitations before trainings before reservations', () => {
    const { cellFor } = renderMonth({
      trainings: [makeTraining({ date: '2026-08-12', title: 'Strength' })],
      reservations: [makeReservation({ date: '2026-08-12', title: 'Booked slot' })],
      invitations: [makeInvitation({ date: '2026-08-12', title: 'Held seat' })],
    })

    const labels = within(cellFor('12')).getAllByRole('button').map((b) => b.textContent)

    // The action-needed entry must never end up behind "+N"
    expect(labels[0]).toContain('Held seat')
    expect(labels[1]).toContain('Strength')
    expect(labels[2]).toContain('Booked slot')
  })

  it('should show at most four entries and offer the rest behind a button', () => {
    const { cellFor } = renderMonth({
      trainings: Array.from({ length: 6 }, (_, i) =>
        makeTraining({ date: '2026-08-12', title: `Session ${i}` }),
      ),
    })

    const cell = within(cellFor('12'))
    expect(cell.getByText('month.more:2')).toBeInTheDocument()
    expect(cell.queryByText('Session 4')).not.toBeInTheDocument()
  })

  it('should open the day sheet from the overflow button', () => {
    // It used to be a bare <div> of text, so the hidden entries were unreachable
    const { cellFor, onDayExpand } = renderMonth({
      trainings: Array.from({ length: 6 }, () => makeTraining({ date: '2026-08-12' })),
    })

    within(cellFor('12')).getByText('month.more:2').click()

    expect(onDayExpand).toHaveBeenCalledWith('2026-08-12')
  })

  it('should render no overflow button when everything fits', () => {
    const { cellFor } = renderMonth({
      trainings: Array.from({ length: 4 }, () => makeTraining({ date: '2026-08-12' })),
    })

    expect(within(cellFor('12')).queryByText(/^month\.more/)).not.toBeInTheDocument()
  })

  it('should not offer clipboard controls on the tiles', () => {
    // 42 cells of copy/cut chips would drown the plan itself; the day sheet carries them
    renderMonth({ trainings: [makeTraining({ date: '2026-08-12' })] })

    expect(screen.queryByLabelText('clipboard.copy')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('clipboard.cut')).not.toBeInTheDocument()
  })
})

describe('TrainingMonthCalendar — the per-day add button (Fire Academy 35924b8)', () => {
  const addButton = (view: ReturnType<typeof renderMonth>, date: string) =>
    view.getByLabelText(`month.addOnDate:${date}`)

  it('should give every day its own add button', () => {
    const view = renderMonth()

    expect(view.getAllByLabelText(/^month\.addOnDate:/)).toHaveLength(MONTH_GRID_DAYS)
  })

  it('should open the form on that day', () => {
    const view = renderMonth()

    addButton(view, '2026-08-12').click()

    expect(view.onDayClick).toHaveBeenCalledWith('2026-08-12')
  })

  it('should gate its hidden state on the pointer, not on hover', () => {
    // `hover:` alone leaves the hidden base state standing on a touch screen, where nothing
    // can bring it back — and there the button is the only discoverable way to add anything.
    const view = renderMonth()

    const hiding = addButton(view, '2026-08-12').className.split(/\s+/).filter((c) => c.includes('opacity-0'))
    expect(hiding.length).toBeGreaterThan(0)
    expect(hiding.every((c) => c.startsWith('pointer-fine:'))).toBe(true)
  })

  it('should fade rather than unmount, so the grid never twitches', () => {
    // Opacity keeps the slot's height and keeps the button tabbable, which
    // group-focus-within then reveals
    const view = renderMonth()
    const cls = addButton(view, '2026-08-12').className

    expect(cls).not.toContain('hidden')
    expect(cls).toContain('pointer-fine:group-focus-within:opacity-100')
  })

  it('should use one transition utility, not two fighting over a property', () => {
    // Two transition-* utilities fight over the same property and the winner depends on
    // stylesheet order rather than on the order written here
    const view = renderMonth()
    const classes = addButton(view, '2026-08-12').className.split(/\s+/)

    expect(classes).toContain('transition')
    expect(classes.filter((c) => c.startsWith('transition-'))).toHaveLength(0)
  })

  it('should become a paste hint while the clipboard is armed', () => {
    // An add button there would be a second meaning for the same slot
    const view = renderMonth({ pasteActive: true })

    expect(view.queryByLabelText(/^month\.addOnDate:/)).not.toBeInTheDocument()
    expect(view.getAllByText('month.pasteHere')).toHaveLength(MONTH_GRID_DAYS)
  })
})

describe('TrainingMonthCalendar — armed clipboard (Fire Academy 129f7a7)', () => {
  it('should paste into the cell instead of opening the add form', () => {
    const { cellFor, onPasteAt, onDayClick } = renderMonth({ pasteActive: true })

    cellFor('12').click()

    expect(onPasteAt).toHaveBeenCalledWith('2026-08-12')
    expect(onDayClick).not.toHaveBeenCalled()
  })

  it('should let a click on a training reach the cell underneath', async () => {
    // The bug: the tile stayed a <button>, so one tap both pasted and opened the detail.
    // The cell's closest('button') guard only works because the tile stops being one.
    const { onPasteAt, onTrainingClick } = renderMonth({
      pasteActive: true,
      trainings: [makeTraining({ date: '2026-08-12', title: 'Strength' })],
    })

    await userEvent.click(screen.getByText('Strength'))

    expect(onPasteAt).toHaveBeenCalledWith('2026-08-12')
    expect(onTrainingClick).not.toHaveBeenCalled()
  })

  it('should let a click on the overflow button paste rather than expand', () => {
    // "+N" is a real button, so the cell guard would swallow the paste unless it handles it
    const { cellFor, onPasteAt, onDayExpand } = renderMonth({
      pasteActive: true,
      trainings: Array.from({ length: 6 }, () => makeTraining({ date: '2026-08-12' })),
    })

    within(cellFor('12')).getByText('month.more:2').click()

    expect(onPasteAt).toHaveBeenCalledWith('2026-08-12')
    expect(onDayExpand).not.toHaveBeenCalled()
  })

  it('should open the detail again once the clipboard is disarmed', async () => {
    const { onTrainingClick } = renderMonth({
      trainings: [makeTraining({ date: '2026-08-12', title: 'Strength' })],
    })

    await userEvent.click(screen.getByText('Strength'))

    expect(onTrainingClick).toHaveBeenCalledTimes(1)
  })
})
