import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TrainingMonthDots } from './TrainingMonthDots'
import { MONTH_GRID_DAYS } from './monthGrid'
import { makeInvitation, makeReservation, makeTraining } from '../../test/factories'

// Interpolates every option so aria-labels can be targeted by the day they carry
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${Object.values(opts).join(',')}` : key,
    i18n: { language: 'pl' },
  }),
}))

const AUGUST = new Date(2026, 7, 10)

function renderDots(props: Partial<React.ComponentProps<typeof TrainingMonthDots>> = {}) {
  const handlers = { onMonthChange: vi.fn(), onDayExpand: vi.fn(), onPasteAt: vi.fn() }
  const view = render(
    <TrainingMonthDots
      currentMonth={AUGUST}
      trainings={[]}
      reservations={[]}
      invitations={[]}
      {...handlers}
      {...props}
    />,
  )
  const cell = (date: string) => screen.getByLabelText(`month.showDay:${date}`)
  const dotsIn = (date: string) =>
    Array.from(cell(date).querySelectorAll('span[class*="w-1.5"]'))
  return { ...view, ...handlers, cell, dotsIn }
}

describe('TrainingMonthDots — the grid', () => {
  it('should render the same six rows of seven as the tile grid', () => {
    renderDots()

    expect(screen.getAllByLabelText(/^month\.showDay:/)).toHaveLength(MONTH_GRID_DAYS)
  })

  it('should make every day a control that opens the sheet', () => {
    // At this width the cell cannot show the content, so the tap is the only way in
    const { cell, onDayExpand } = renderDots()

    cell('2026-08-12').click()

    expect(onDayExpand).toHaveBeenCalledWith('2026-08-12')
  })

  it('should offer no per-cell add button', () => {
    // 45px of width has no room for one; adding lives in the day sheet
    renderDots()

    expect(screen.queryByLabelText(/month\.addOnDay/)).not.toBeInTheDocument()
  })

  it('should dim days from a neighbouring month', () => {
    const { cell } = renderDots()

    expect(cell('2026-07-27').className).toContain('opacity-40')
    expect(cell('2026-08-12').className).not.toContain('opacity-40')
  })
})

describe('TrainingMonthDots — what a dot says', () => {
  it('should separate kinds by shape, since colour is spent on the status', () => {
    const { dotsIn } = renderDots({
      trainings: [makeTraining({ date: '2026-08-12' })],
      reservations: [makeReservation({ date: '2026-08-12' })],
      invitations: [makeInvitation({ date: '2026-08-12' })],
    })

    const classes = dotsIn('2026-08-12').map((d) => d.className)
    // Invitations first, matching the tile grid
    expect(classes[0]).toContain('border-amber-400')   // held seat: hollow circle
    expect(classes[1]).toContain('rounded-full')       // training: filled circle
    expect(classes[2]).toContain('rounded-[2px]')      // booking: square
  })

  it('should colour a training dot by its status', () => {
    const { dotsIn } = renderDots({
      trainings: [
        makeTraining({ date: '2026-08-12', status: 'COMPLETED' }),
        makeTraining({ date: '2026-08-13', status: 'MISSED' }),
        makeTraining({ date: '2026-08-14', status: 'PLANNED' }),
      ],
    })

    expect(dotsIn('2026-08-12')[0].className).toContain('bg-green-500')
    expect(dotsIn('2026-08-13')[0].className).toContain('bg-rose-500')
    expect(dotsIn('2026-08-14')[0].className).toContain('bg-indigo-400')
  })

  it('should cap the dots and count the remainder', () => {
    const { dotsIn, cell } = renderDots({
      trainings: Array.from({ length: 7 }, () => makeTraining({ date: '2026-08-12' })),
    })

    expect(dotsIn('2026-08-12')).toHaveLength(4)
    expect(cell('2026-08-12').textContent).toContain('month.more:3')
  })

  it('should mark a day that carries something unread', () => {
    const { cell } = renderDots({
      trainings: [makeTraining({ date: '2026-08-12', hasUnreadActivity: true })],
    })

    expect(cell('2026-08-12').querySelector('.bg-rose-500')).not.toBeNull()
  })

  it('should mark a day whose booking is new to the coach', () => {
    const { cell } = renderDots({
      reservations: [makeReservation({ date: '2026-08-12', isNew: true })],
    })

    expect(cell('2026-08-12').querySelector('.bg-rose-500')).not.toBeNull()
  })

  it('should leave a fully seen day unmarked', () => {
    const { cell } = renderDots({
      trainings: [makeTraining({ date: '2026-08-12', hasUnreadActivity: false })],
    })

    expect(cell('2026-08-12').querySelector('.bg-rose-500')).toBeNull()
  })
})

describe('TrainingMonthDots — armed clipboard', () => {
  it('should paste into the day instead of opening the sheet', () => {
    const { cell, onPasteAt, onDayExpand } = renderDots({ pasteActive: true })

    cell('2026-08-12').click()

    expect(onPasteAt).toHaveBeenCalledWith('2026-08-12')
    expect(onDayExpand).not.toHaveBeenCalled()
  })
})
