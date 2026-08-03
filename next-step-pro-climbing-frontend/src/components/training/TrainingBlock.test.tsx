import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ReservationBlock, TrainingBlock } from './TrainingBlock'
import { makeAttachment, makeReservation, makeTask, makeTraining } from '../../test/factories'

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

  it('should render no clipboard actions in the all-day chip', () => {
    render(<TrainingBlock training={training} onClick={vi.fn()} density="chip" />)

    expect(screen.queryByLabelText('clipboard.copy')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('clipboard.cut')).not.toBeInTheDocument()
  })

  it('should render no clipboard actions on a month tile', () => {
    // 42 cells x 4 tiles x 2 buttons would be a wall of chrome. Arming the clipboard
    // happens from the detail modal, the week view or the day sheet instead.
    render(<TrainingBlock training={training} onClick={vi.fn()} density="tile" />)

    expect(screen.queryByLabelText('clipboard.copy')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('clipboard.cut')).not.toBeInTheDocument()
  })
})

describe('TrainingBlock — month tile', () => {
  it('should render the start time for a timed training', () => {
    render(<TrainingBlock training={makeTraining({ startTime: '17:00', endTime: '18:30' })} onClick={vi.fn()} density="tile" />)

    expect(screen.getByText('17:00')).toBeInTheDocument()
  })

  it('should render nothing at all in place of the time for an untimed training', () => {
    // Both times NULL is the DEFAULT case in this domain ("do it on Wednesday"), so a
    // dash or an "all day" label on every tile would be pure noise.
    const { container } = render(
      <TrainingBlock training={makeTraining({ startTime: null, endTime: null, title: 'Mobility' })} onClick={vi.fn()} density="tile" />,
    )

    expect(container.textContent).toBe('Mobility')
  })

  it('should carry the status on the left border rather than washing the tile', () => {
    const { container } = render(
      <TrainingBlock training={makeTraining({ status: 'COMPLETED' })} onClick={vi.fn()} density="tile" />,
    )
    const tile = container.firstElementChild!

    expect(tile.className).toContain('border-l-green-500')
    expect(tile.className).not.toMatch(/\bbg-green-/)
  })

  it('should give the tile at least a 24px tap target', () => {
    const { container } = render(<TrainingBlock training={makeTraining()} onClick={vi.fn()} density="tile" />)

    expect(container.firstElementChild!.className).toContain('min-h-6')
  })

  it('should flag a training that carries materials', () => {
    render(
      <TrainingBlock
        training={makeTraining({ attachments: [makeAttachment()] })}
        onClick={vi.fn()}
        density="tile"
      />,
    )

    expect(screen.getByLabelText('detail.materials')).toBeInTheDocument()
  })

  it('should show the RPE once the training has been rated', () => {
    render(<TrainingBlock training={makeTraining({ status: 'COMPLETED', rpe: 8 })} onClick={vi.fn()} density="tile" />)

    expect(screen.getByLabelText('RPE 8')).toBeInTheDocument()
  })
})

describe('TrainingBlock — a task is its own kind of entry', () => {
  it('should mark a task with its own icon', () => {
    render(<TrainingBlock training={makeTask()} onClick={vi.fn()} density="tile" />)

    expect(screen.getByLabelText('form.kind.TASK')).toBeInTheDocument()
  })

  it('should carry the kind on the border style, leaving colour to the status', () => {
    // Two orthogonal signals: neither has to give up its channel to the other
    const { container } = render(
      <TrainingBlock training={makeTask({ status: 'COMPLETED' })} onClick={vi.fn()} density="tile" />,
    )

    expect(container.firstElementChild!.className).toContain('border-dashed')
    expect(container.firstElementChild!.className).toContain('border-l-green-500')
  })

  it('should show a calorie ceiling when the task carries one', () => {
    render(<TrainingBlock training={makeTask({ targetCalories: 2200 })} onClick={vi.fn()} density="tile" />)

    // The badge, not the title: a number typed into a heading is exactly what the column avoids
    expect(screen.getByTitle('form.calories')).toHaveTextContent('2200')
  })

  it('should show no number for a task that has none', () => {
    // "Drink 3 litres" carries its number in the title and needs no field
    const { container } = render(
      <TrainingBlock training={makeTask({ title: 'Drink 3 litres', targetCalories: null })} onClick={vi.fn()} density="tile" />,
    )

    expect(container.textContent).toBe('Drink 3 litres')
  })

  it('should leave a training undashed', () => {
    const { container } = render(<TrainingBlock training={makeTraining()} onClick={vi.fn()} density="tile" />)

    expect(container.firstElementChild!.className).not.toContain('border-dashed')
    expect(screen.queryByLabelText('form.kind.TASK')).not.toBeInTheDocument()
  })
})

describe('TrainingBlock — armed clipboard (Fire Academy 129f7a7)', () => {
  it('should be a button that opens the detail while the clipboard is idle', async () => {
    const onClick = vi.fn()
    render(<TrainingBlock training={makeTraining()} onClick={onClick} density="tile" />)

    await userEvent.click(screen.getByRole('button'))

    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('should stop being a control while the clipboard is armed', () => {
    // Not a DISABLED button — a disabled control swallows the click and the tap does
    // nothing. It has to leave the keyboard path entirely so the click reaches the day
    // cell underneath, which is the paste target.
    render(<TrainingBlock training={makeTraining()} onClick={vi.fn()} density="tile" pasteActive />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('should not open the detail when the armed tile is clicked', async () => {
    const onClick = vi.fn()
    const { container } = render(
      <TrainingBlock training={makeTraining({ title: 'Strength' })} onClick={onClick} density="tile" pasteActive />,
    )

    await userEvent.click(screen.getByText('Strength'))

    expect(onClick).not.toHaveBeenCalled()
    expect(container.firstElementChild!.tagName).toBe('DIV')
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

  it('should render the dot in the all-day chip too', () => {
    const { container } = render(
      <ReservationBlock reservation={makeReservation({ isNew: true })} label="overlay.reservation" onClick={vi.fn()} density="chip" />,
    )

    expect(unreadDot(container)).not.toBeNull()
  })

  it('should render the dot on a month tile too', () => {
    const { container } = render(
      <ReservationBlock reservation={makeReservation({ isNew: true })} label="overlay.reservation" onClick={vi.fn()} density="tile" />,
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
