import { describe, it, expect, beforeEach, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { TrainingWeekCalendar } from './TrainingWeekCalendar'
import { makeInvitation, makeReservation, makeTraining } from '../../test/factories'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

// The drag gesture itself is covered in hooks/useSlotDrag.test.ts. Here we only care about
// the wiring: which blocks get a drag handler at all, and what the calendar does on drop.
const drag = vi.hoisted(() => ({
  onSlotPointerDown: vi.fn(),
  onResizePointerDown: vi.fn(),
  options: null as { onDrop: (...args: string[]) => void } | null,
}))

vi.mock('../../hooks/useSlotDrag', () => ({
  useSlotDrag: (options: { onDrop: (...args: string[]) => void }) => {
    drag.options = options
    return {
      dragState: null,
      isBeingDragged: () => false,
      wasJustDragged: () => false,
      didJustDrag: () => false,
      onSlotPointerDown: drag.onSlotPointerDown,
      onResizePointerDown: drag.onResizePointerDown,
      longPressSlotId: null,
    }
  },
}))

const MONDAY = '2026-07-20'
const WEDNESDAY = '2026-07-22'

function renderWeek(props: Partial<React.ComponentProps<typeof TrainingWeekCalendar>> = {}) {
  const onTrainingMove = vi.fn()
  const onDayClick = vi.fn()
  const onPasteAt = vi.fn()
  const onTrainingClick = vi.fn()
  const view = render(
    <TrainingWeekCalendar
      startDate={MONDAY}
      trainings={[]}
      reservations={[]}
      invitations={[]}
      invitationLabel="overlay.invitation"
      onPrevWeek={vi.fn()}
      onNextWeek={vi.fn()}
      onToday={vi.fn()}
      onTrainingClick={onTrainingClick}
      onReservationClick={vi.fn()}
      onInvitationClick={vi.fn()}
      onDayClick={onDayClick}
      onTrainingMove={onTrainingMove}
      {...props}
    />,
  )
  return { ...view, onTrainingMove, onDayClick, onPasteAt, onTrainingClick }
}

/** Day columns are the only elements sized to the full 16-hour grid (16 * 40px). */
function dayColumns(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>('div[style]'))
    .filter((el) => el.style.height === '640px')
}

beforeEach(() => {
  drag.onSlotPointerDown.mockClear()
  drag.onResizePointerDown.mockClear()
  drag.options = null
})

describe('TrainingWeekCalendar — completed trainings are not movable (#97)', () => {
  const planned = makeTraining({ date: MONDAY, startTime: '10:00', endTime: '11:00', title: 'Planned session' })
  const completed = makeTraining({
    date: MONDAY, startTime: '12:00', endTime: '13:00', title: 'Completed session',
    status: 'COMPLETED', completedAt: '2026-07-20T13:05:00Z',
  })

  it('should start a drag for a planned training', () => {
    renderWeek({ trainings: [planned, completed] })

    fireEvent.pointerDown(screen.getByTitle('Planned session'))

    expect(drag.onSlotPointerDown).toHaveBeenCalledTimes(1)
    expect(drag.onSlotPointerDown.mock.calls[0].slice(0, 4))
      .toEqual([planned.id, MONDAY, '10:00', '11:00'])
  })

  it('should not start a drag for a completed training', () => {
    // A completed training is a record of what happened — dragging it into the future
    // would rewrite history (the backend rejects it too, see #97)
    renderWeek({ trainings: [planned, completed] })

    fireEvent.pointerDown(screen.getByTitle('Completed session'))

    expect(drag.onSlotPointerDown).not.toHaveBeenCalled()
  })

  it('should not offer a resize handle on a completed training', () => {
    renderWeek({ trainings: [completed] })

    const block = screen.getByTitle('Completed session').parentElement!
    expect(block.querySelector('.cursor-ns-resize')).toBeNull()
  })

  it('should offer a resize handle on a planned training', () => {
    renderWeek({ trainings: [planned] })

    const block = screen.getByTitle('Planned session').parentElement!
    expect(block.querySelector('.cursor-ns-resize')).not.toBeNull()
  })

  it('should not offer cut on a completed training but should still offer copy', () => {
    renderWeek({ trainings: [completed], onTrainingCopy: vi.fn(), onTrainingCut: vi.fn() })

    expect(screen.getByLabelText('clipboard.copy')).toBeInTheDocument()
    expect(screen.queryByLabelText('clipboard.cut')).not.toBeInTheDocument()
  })

  it('should not attach any drag handler when moving is disabled altogether', () => {
    renderWeek({ trainings: [planned], onTrainingMove: undefined })

    fireEvent.pointerDown(screen.getByTitle('Planned session'))

    expect(drag.onSlotPointerDown).not.toHaveBeenCalled()
  })
})

describe('TrainingWeekCalendar — drop handling', () => {
  const planned = makeTraining({ date: MONDAY, startTime: '10:00', endTime: '11:00', title: 'Planned session' })

  it('should forward a real move to onTrainingMove', () => {
    const { onTrainingMove } = renderWeek({ trainings: [planned] })

    drag.options!.onDrop(planned.id, WEDNESDAY, '14:00', '15:00', MONDAY, '10:00', '11:00')

    expect(onTrainingMove).toHaveBeenCalledWith(planned.id, WEDNESDAY, '14:00', '15:00')
  })

  it('should ignore a drop that lands back on the original date and times', () => {
    // Without this guard a stray drag would fire a pointless PUT and a "changed by coach" alert
    const { onTrainingMove } = renderWeek({ trainings: [planned] })

    drag.options!.onDrop(planned.id, MONDAY, '10:00', '11:00', MONDAY, '10:00', '11:00')

    expect(onTrainingMove).not.toHaveBeenCalled()
  })
})

describe('TrainingWeekCalendar — clicking empty space', () => {
  it('should prefill the clicked date and snapped time', () => {
    const { container, onDayClick } = renderWeek()

    // 60px below the top of the column = 8:30 after snapping to 30 min
    fireEvent.click(dayColumns(container)[0], { clientY: 60 })

    expect(onDayClick).toHaveBeenCalledWith(MONDAY, '08:30')
  })

  it('should target the column that was clicked', () => {
    const { container, onDayClick } = renderWeek()

    fireEvent.click(dayColumns(container)[2], { clientY: 0 })

    expect(onDayClick).toHaveBeenCalledWith(WEDNESDAY, '07:00')
  })

  it('should paste instead of creating while a training is on the clipboard', () => {
    const onPasteAt = vi.fn()
    const { container, onDayClick } = renderWeek({ pasteActive: true, onPasteAt })

    fireEvent.click(dayColumns(container)[0], { clientY: 60 })

    expect(onPasteAt).toHaveBeenCalledWith(MONDAY, '08:30')
    expect(onDayClick).not.toHaveBeenCalled()
  })

  it('should create an untimed training from the all-day lane', () => {
    const { onDayClick } = renderWeek()

    // The all-day cells are the only clickable day cells outside the hour grid
    fireEvent.click(screen.getByText('detail.allDay').nextElementSibling!)

    expect(onDayClick).toHaveBeenCalledWith(MONDAY)
  })

  it('should paste into the all-day lane instead of opening the create form', () => {
    // This lane is the only place an untimed entry — and every task — can land, so while the
    // clipboard is armed it has to be a drop target, not an add target
    const onPasteAt = vi.fn()
    const { onDayClick } = renderWeek({ pasteActive: true, onPasteAt })

    fireEvent.click(screen.getByText('detail.allDay').nextElementSibling!)

    // Explicit null, not undefined: dropping here means "no hour", not "keep the source's hour"
    expect(onPasteAt).toHaveBeenCalledWith(MONDAY, null)
    expect(onDayClick).not.toHaveBeenCalled()
  })
})

describe('TrainingWeekCalendar — untimed vs timed placement (V72)', () => {
  it('should keep an untimed training out of the hour grid', () => {
    const untimed = makeTraining({ date: MONDAY, startTime: null, endTime: null, title: 'Rest day' })
    const { container } = renderWeek({ trainings: [untimed] })

    const block = screen.getByTitle('Rest day')
    expect(dayColumns(container).some((col) => col.contains(block))).toBe(false)
  })

  it('should place a timed training inside its day column', () => {
    const timed = makeTraining({ date: WEDNESDAY, startTime: '10:00', endTime: '11:00', title: 'Session' })
    const { container } = renderWeek({ trainings: [timed] })

    const block = screen.getByTitle('Session')
    expect(dayColumns(container)[2].contains(block)).toBe(true)
  })

  it('should show reservations and invitations alongside trainings', () => {
    renderWeek({
      trainings: [makeTraining({ date: MONDAY, title: 'Session' })],
      reservations: [makeReservation({ date: MONDAY, startTime: '15:00', endTime: '16:00', title: 'Booked slot' })],
      invitations: [makeInvitation({ date: MONDAY, startTime: '18:00', endTime: '19:00', title: 'Held seat' })],
    })

    expect(screen.getByTitle('Session')).toBeInTheDocument()
    expect(screen.getByTitle('Booked slot')).toBeInTheDocument()
    expect(screen.getByTitle('overlay.invitation: Held seat')).toBeInTheDocument()
  })
})
