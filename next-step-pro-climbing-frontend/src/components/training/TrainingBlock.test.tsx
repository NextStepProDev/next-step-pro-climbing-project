import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ReservationBlock, TrainingBlock } from './TrainingBlock'
import { makeReservation, makeTraining } from '../../test/factories'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

/**
 * jsdom does no layout and Tailwind is not loaded in tests, so a tap target cannot be
 * measured with offsetWidth. Derive it from the utility classes instead: the icon size
 * plus the button's padding and border — the exact quantities #6576a11 changed.
 */
function tapTargetPx(button: HTMLElement): number {
  const icon = button.querySelector('svg')
  const iconClass = icon?.getAttribute('class') ?? ''
  const size = /w-(\d+(?:\.\d+)?)/.exec(iconClass)
  if (!size) throw new Error(`no width utility on the icon: "${iconClass}"`)
  const iconPx = Number(size[1]) * 4 // Tailwind spacing unit = 0.25rem = 4px

  const padding = /(?:^|\s)p-(\d+(?:\.\d+)?)/.exec(button.className)
  const paddingPx = padding ? Number(padding[1]) * 4 : 0

  const borderPx = /(?:^|\s)border(?:\s|$)/.test(button.className) ? 1 : 0

  return iconPx + 2 * paddingPx + 2 * borderPx
}

describe('TrainingBlock — copy/cut actions on touch (#89)', () => {
  const training = makeTraining({ title: 'Endurance circuits' })

  it('should render the copy and cut buttons without any hover interaction', () => {
    // The old build hid them behind a plain group-hover, so a touch device never
    // showed them at all and a tap landed on the block body instead
    render(<TrainingBlock training={training} onClick={vi.fn()} onCopy={vi.fn()} onCut={vi.fn()} />)

    expect(screen.getByLabelText('clipboard.copy')).toBeInTheDocument()
    expect(screen.getByLabelText('clipboard.cut')).toBeInTheDocument()
  })

  it('should keep the actions visible by default and only hide them on hover-capable devices', () => {
    render(<TrainingBlock training={training} onClick={vi.fn()} onCopy={vi.fn()} onCut={vi.fn()} />)

    const actions = screen.getByLabelText('clipboard.copy').parentElement!
    expect(actions.className).toContain('opacity-100')
    // Every hide/reveal rule must be gated on (hover:hover); an ungated opacity-0
    // is exactly the bug — invisible chips on a touch screen
    const hidingRules = actions.className.split(/\s+/).filter((c) => c.includes('opacity-0'))
    expect(hidingRules.length).toBeGreaterThan(0)
    expect(hidingRules.every((c) => c.startsWith('[@media(hover:hover)]:'))).toBe(true)
  })

  it('should give each action at least a 24px tap target', () => {
    render(<TrainingBlock training={training} onClick={vi.fn()} onCopy={vi.fn()} onCut={vi.fn()} />)

    expect(tapTargetPx(screen.getByLabelText('clipboard.copy'))).toBeGreaterThanOrEqual(24)
    expect(tapTargetPx(screen.getByLabelText('clipboard.cut'))).toBeGreaterThanOrEqual(24)
  })

  it('should copy without opening the detail modal', async () => {
    const onCopy = vi.fn()
    const onClick = vi.fn()
    render(<TrainingBlock training={training} onClick={onClick} onCopy={onCopy} onCut={vi.fn()} />)

    await userEvent.click(screen.getByLabelText('clipboard.copy'))

    expect(onCopy).toHaveBeenCalledTimes(1)
    expect(onClick).not.toHaveBeenCalled()
  })

  it('should cut without opening the detail modal', async () => {
    const onCut = vi.fn()
    const onClick = vi.fn()
    render(<TrainingBlock training={training} onClick={onClick} onCopy={vi.fn()} onCut={onCut} />)

    await userEvent.click(screen.getByLabelText('clipboard.cut'))

    expect(onCut).toHaveBeenCalledTimes(1)
    expect(onClick).not.toHaveBeenCalled()
  })

  it('should offer copy but no cut for a completed training', () => {
    // Completed entries are history: they may be re-planned forward, never moved away
    render(<TrainingBlock training={makeTraining({ status: 'COMPLETED' })} onClick={vi.fn()} onCopy={vi.fn()} />)

    expect(screen.getByLabelText('clipboard.copy')).toBeInTheDocument()
    expect(screen.queryByLabelText('clipboard.cut')).not.toBeInTheDocument()
  })

  it('should render no clipboard actions in the month view', () => {
    render(<TrainingBlock training={training} onClick={vi.fn()} compact />)

    expect(screen.queryByLabelText('clipboard.copy')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('clipboard.cut')).not.toBeInTheDocument()
  })
})

describe('ReservationBlock — unread dot for new athlete bookings (#79)', () => {
  // The dot is a bare span with no text or role, so the colour class is the only handle.
  const unreadDot = (container: HTMLElement) => container.querySelector('.bg-rose-500')

  it('should render the unread dot for a booking made since the coach last looked', () => {
    const { container } = render(
      <ReservationBlock reservation={makeReservation({ isNew: true })} label="overlay.reservation" onClick={vi.fn()} />,
    )

    expect(unreadDot(container)).not.toBeNull()
  })

  it('should render no dot for an already-seen booking', () => {
    const { container } = render(
      <ReservationBlock reservation={makeReservation({ isNew: false })} label="overlay.reservation" onClick={vi.fn()} />,
    )

    expect(unreadDot(container)).toBeNull()
  })

  it('should render the dot in the compact month variant too', () => {
    const { container } = render(
      <ReservationBlock reservation={makeReservation({ isNew: true })} label="overlay.reservation" onClick={vi.fn()} compact />,
    )

    expect(unreadDot(container)).not.toBeNull()
  })

  it('should show the rate CTA only to the athlete on a rateable booking', () => {
    const reservation = makeReservation({ canRate: true, rpe: null })
    const { rerender } = render(
      <ReservationBlock reservation={reservation} label="overlay.reservation" onClick={vi.fn()} />,
    )
    expect(screen.getByText('rpe.rateShort')).toBeInTheDocument()

    rerender(<ReservationBlock reservation={reservation} label="overlay.reservation" onClick={vi.fn()} isCoachView />)
    expect(screen.queryByText('rpe.rateShort')).not.toBeInTheDocument()
  })

  it('should show the score instead of the CTA once rated', () => {
    render(
      <ReservationBlock reservation={makeReservation({ canRate: true, rpe: 7 })} label="overlay.reservation" onClick={vi.fn()} />,
    )

    expect(screen.getByText(/RPE 7/)).toBeInTheDocument()
    expect(screen.queryByText('rpe.rateShort')).not.toBeInTheDocument()
  })
})

describe('TrainingBlock — unread dot', () => {
  it('should render the dot when the other side left unread activity', () => {
    const { container } = render(
      <TrainingBlock training={makeTraining({ hasUnreadActivity: true })} onClick={vi.fn()} />,
    )
    expect(container.querySelector('.bg-rose-500')).not.toBeNull()
  })

  it('should render no dot when everything has been seen', () => {
    const { container } = render(
      <TrainingBlock training={makeTraining({ hasUnreadActivity: false })} onClick={vi.fn()} />,
    )
    expect(container.querySelector('.bg-rose-500')).toBeNull()
  })
})
