import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useSlotDrag } from './useSlotDrag'

// Grid geometry mirrored from the hook (7:00-23:00, 40px per hour)
const HOUR_HEIGHT = 40
const START_HOUR = 7
const COLUMN_WIDTH = 100
const COLUMN_HEIGHT = 16 * HOUR_HEIGHT

const DAYS = [
  '2026-07-20', '2026-07-21', '2026-07-22', '2026-07-23',
  '2026-07-24', '2026-07-25', '2026-07-26',
].map((date) => ({ date }))

/** Seven day columns laid out left to right, top of the grid at y = 0. */
function makeColumns(): HTMLDivElement[] {
  return DAYS.map((_, i) => {
    const el = document.createElement('div')
    const left = i * COLUMN_WIDTH
    el.getBoundingClientRect = () => ({
      left, right: left + COLUMN_WIDTH, top: 0, bottom: COLUMN_HEIGHT,
      width: COLUMN_WIDTH, height: COLUMN_HEIGHT, x: left, y: 0,
      toJSON: () => ({}),
    }) as DOMRect
    return el
  })
}

/** y pixel of a "HH:mm" time inside a column */
function yOf(time: string): number {
  const [h, m] = time.split(':').map(Number)
  return ((h * 60 + m) - START_HOUR * 60) / 60 * HOUR_HEIGHT
}

/** x pixel inside the column for a given day index */
function xOfDay(dayIndex: number): number {
  return dayIndex * COLUMN_WIDTH + COLUMN_WIDTH / 2
}

function reactPointerDown(clientX: number, clientY: number, pointerType = 'mouse') {
  return {
    button: 0,
    clientX,
    clientY,
    pointerType,
    preventDefault: vi.fn(),
    stopPropagation: vi.fn(),
    target: document.createElement('div'),
  } as unknown as React.PointerEvent<HTMLElement>
}

function documentPointerEvent(type: string, clientX: number, clientY: number, pointerType = 'mouse') {
  const event = new MouseEvent(type, { clientX, clientY, bubbles: true })
  Object.defineProperty(event, 'pointerType', { value: pointerType })
  return event
}

function setup(overrides: { enabled?: boolean; snapMinutes?: number } = {}) {
  const columns = makeColumns()
  const dayColumnRefs = { current: columns } as React.MutableRefObject<(HTMLDivElement | null)[]>
  const onDrop = vi.fn()
  const view = renderHook(() =>
    useSlotDrag({
      days: DAYS,
      dayColumnRefs,
      onDrop,
      enabled: overrides.enabled ?? true,
      snapMinutes: overrides.snapMinutes ?? 30,
    }),
  )
  return { ...view, onDrop, columns }
}

/** Grab a block at `grabY`, drag to (toX, toY), release. */
function dragBlock(
  result: { current: ReturnType<typeof useSlotDrag> },
  slot: { id: string; date: string; start: string; end: string },
  from: { x: number; y: number },
  to: { x: number; y: number },
) {
  act(() => {
    result.current.onSlotPointerDown(slot.id, slot.date, slot.start, slot.end, reactPointerDown(from.x, from.y))
  })
  act(() => {
    document.dispatchEvent(documentPointerEvent('pointermove', to.x, to.y))
  })
  act(() => {
    document.dispatchEvent(documentPointerEvent('pointerup', to.x, to.y))
  })
}

const SLOT = { id: 'training-1', date: DAYS[1].date, start: '10:00', end: '11:00' }

describe('useSlotDrag — move', () => {
  it('should drop on the day column the pointer was released over', () => {
    const { result, onDrop } = setup()

    dragBlock(result, SLOT, { x: xOfDay(1), y: yOf('10:00') }, { x: xOfDay(3), y: yOf('13:00') })

    expect(onDrop).toHaveBeenCalledTimes(1)
    expect(onDrop).toHaveBeenCalledWith(
      'training-1', DAYS[3].date, '13:00', '14:00',
      DAYS[1].date, '10:00', '11:00',
    )
  })

  it('should preserve the duration when moving', () => {
    const { result, onDrop } = setup()
    const twoHours = { id: 'training-2', date: DAYS[0].date, start: '09:00', end: '11:00' }

    dragBlock(result, twoHours, { x: xOfDay(0), y: yOf('09:00') }, { x: xOfDay(0), y: yOf('15:00') })

    const [, , newStart, newEnd] = onDrop.mock.calls[0]
    expect(newStart).toBe('15:00')
    expect(newEnd).toBe('17:00')
  })

  it('should keep the grab offset so the block does not jump under the cursor', () => {
    const { result, onDrop } = setup()

    // Grabbed 30 min below the block top; dropping the cursor at 14:30 puts the block at 14:00
    dragBlock(result, SLOT, { x: xOfDay(1), y: yOf('10:30') }, { x: xOfDay(1), y: yOf('14:30') })

    expect(onDrop.mock.calls[0][2]).toBe('14:00')
  })

  it('should snap an in-between drop down to the 30-minute grid', () => {
    const { result, onDrop } = setup()

    // 8px past 13:00 = 12 min -> snaps back down to 13:00
    dragBlock(result, SLOT, { x: xOfDay(1), y: yOf('10:00') }, { x: xOfDay(1), y: yOf('13:00') + 8 })

    expect(onDrop.mock.calls[0][2]).toBe('13:00')
  })

  it('should snap an in-between drop up to the 30-minute grid', () => {
    const { result, onDrop } = setup()

    // 13px past 13:00 = 19.5 min -> snaps up to 13:30
    dragBlock(result, SLOT, { x: xOfDay(1), y: yOf('10:00') }, { x: xOfDay(1), y: yOf('13:00') + 13 })

    expect(onDrop.mock.calls[0][2]).toBe('13:30')
  })

  it('should honour a finer snap step when configured', () => {
    const { result, onDrop } = setup({ snapMinutes: 15 })

    dragBlock(result, SLOT, { x: xOfDay(1), y: yOf('10:00') }, { x: xOfDay(1), y: yOf('13:15') })

    expect(onDrop.mock.calls[0][2]).toBe('13:15')
  })

  it('should clamp a drop above the grid to the start hour', () => {
    const { result, onDrop } = setup()

    dragBlock(result, SLOT, { x: xOfDay(1), y: yOf('10:00') }, { x: xOfDay(1), y: -500 })

    expect(onDrop.mock.calls[0].slice(2, 4)).toEqual(['07:00', '08:00'])
  })

  it('should clamp a drop below the grid so the block still ends by 23:00', () => {
    const { result, onDrop } = setup()

    dragBlock(result, SLOT, { x: xOfDay(1), y: yOf('10:00') }, { x: xOfDay(1), y: 5000 })

    expect(onDrop.mock.calls[0].slice(2, 4)).toEqual(['22:00', '23:00'])
  })

  it('should pick the nearest column when released outside the grid horizontally', () => {
    const { result, onDrop } = setup()

    dragBlock(result, SLOT, { x: xOfDay(1), y: yOf('10:00') }, { x: -400, y: yOf('12:00') })

    expect(onDrop.mock.calls[0][1]).toBe(DAYS[0].date)
  })

  it('should not drop when the pointer never passed the drag threshold', () => {
    const { result, onDrop } = setup()

    // 3px of jitter — a click, not a drag
    dragBlock(result, SLOT, { x: xOfDay(1), y: yOf('10:00') }, { x: xOfDay(1) + 3, y: yOf('10:00') + 3 })

    expect(onDrop).not.toHaveBeenCalled()
  })

  it('should not drop on a plain press and release', () => {
    const { result, onDrop } = setup()

    act(() => {
      result.current.onSlotPointerDown(SLOT.id, SLOT.date, SLOT.start, SLOT.end,
        reactPointerDown(xOfDay(1), yOf('10:00')))
    })
    act(() => {
      document.dispatchEvent(documentPointerEvent('pointerup', xOfDay(1), yOf('10:00')))
    })

    expect(onDrop).not.toHaveBeenCalled()
  })

  it('should ignore drags entirely when disabled', () => {
    const { result, onDrop } = setup({ enabled: false })

    dragBlock(result, SLOT, { x: xOfDay(1), y: yOf('10:00') }, { x: xOfDay(3), y: yOf('13:00') })

    expect(onDrop).not.toHaveBeenCalled()
    expect(result.current.dragState).toBeNull()
  })

  it('should ignore a non-primary mouse button', () => {
    const { result, onDrop } = setup()
    const rightClick = { ...reactPointerDown(xOfDay(1), yOf('10:00')), button: 2 } as React.PointerEvent<HTMLElement>

    act(() => {
      result.current.onSlotPointerDown(SLOT.id, SLOT.date, SLOT.start, SLOT.end, rightClick)
    })
    act(() => {
      document.dispatchEvent(documentPointerEvent('pointermove', xOfDay(3), yOf('13:00')))
      document.dispatchEvent(documentPointerEvent('pointerup', xOfDay(3), yOf('13:00')))
    })

    expect(onDrop).not.toHaveBeenCalled()
  })
})

describe('useSlotDrag — resize', () => {
  it('should extend the end time and keep date and start', () => {
    const { result, onDrop } = setup()

    act(() => {
      result.current.onResizePointerDown(SLOT.id, SLOT.date, SLOT.start, SLOT.end,
        reactPointerDown(xOfDay(1), yOf('11:00')))
    })
    act(() => {
      document.dispatchEvent(documentPointerEvent('pointermove', xOfDay(1), yOf('13:30')))
    })
    act(() => {
      document.dispatchEvent(documentPointerEvent('pointerup', xOfDay(1), yOf('13:30')))
    })

    expect(onDrop).toHaveBeenCalledWith(
      'training-1', DAYS[1].date, '10:00', '13:30',
      DAYS[1].date, '10:00', '11:00',
    )
  })

  it('should keep a minimum duration when dragging the bottom edge above the start', () => {
    const { result, onDrop } = setup()

    act(() => {
      result.current.onResizePointerDown(SLOT.id, SLOT.date, SLOT.start, SLOT.end,
        reactPointerDown(xOfDay(1), yOf('11:00')))
    })
    act(() => {
      document.dispatchEvent(documentPointerEvent('pointermove', xOfDay(1), yOf('08:00')))
    })
    act(() => {
      document.dispatchEvent(documentPointerEvent('pointerup', xOfDay(1), yOf('08:00')))
    })

    const [, , start, end] = onDrop.mock.calls[0]
    expect(start).toBe('10:00')
    expect(end).toBe('10:30')
  })

  it('should not let a resize run past the end of the grid', () => {
    const { result, onDrop } = setup()

    act(() => {
      result.current.onResizePointerDown(SLOT.id, SLOT.date, SLOT.start, SLOT.end,
        reactPointerDown(xOfDay(1), yOf('11:00')))
    })
    act(() => {
      document.dispatchEvent(documentPointerEvent('pointermove', xOfDay(1), 5000))
    })
    act(() => {
      document.dispatchEvent(documentPointerEvent('pointerup', xOfDay(1), 5000))
    })

    expect(onDrop.mock.calls[0][3]).toBe('23:00')
  })
})

describe('useSlotDrag — click guards after a drop', () => {
  it('should report the dragged slot as just-dragged so its click does not open the modal', () => {
    const { result } = setup()

    dragBlock(result, SLOT, { x: xOfDay(1), y: yOf('10:00') }, { x: xOfDay(3), y: yOf('13:00') })

    expect(result.current.wasJustDragged('training-1')).toBe(true)
    expect(result.current.wasJustDragged('another-training')).toBe(false)
    // The click after a drop lands on the day column, not the block — the any-drag guard
    // is what stops it from opening the add-training form
    expect(result.current.didJustDrag()).toBe(true)
  })

  it('should clear the guard shortly after the drop', async () => {
    vi.useFakeTimers()
    try {
      const { result } = setup()

      dragBlock(result, SLOT, { x: xOfDay(1), y: yOf('10:00') }, { x: xOfDay(3), y: yOf('13:00') })
      expect(result.current.didJustDrag()).toBe(true)

      act(() => { vi.advanceTimersByTime(200) })
      expect(result.current.didJustDrag()).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })

  it('should report no drag after a plain click', () => {
    const { result } = setup()

    act(() => {
      result.current.onSlotPointerDown(SLOT.id, SLOT.date, SLOT.start, SLOT.end,
        reactPointerDown(xOfDay(1), yOf('10:00')))
    })
    act(() => {
      document.dispatchEvent(documentPointerEvent('pointerup', xOfDay(1), yOf('10:00')))
    })

    expect(result.current.didJustDrag()).toBe(false)
    expect(result.current.wasJustDragged('training-1')).toBe(false)
  })
})

describe('useSlotDrag — touch', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('should not drag on touch before the long press fires (page scrolling stays possible)', () => {
    const { result, onDrop } = setup()

    act(() => {
      result.current.onSlotPointerDown(SLOT.id, SLOT.date, SLOT.start, SLOT.end,
        reactPointerDown(xOfDay(1), yOf('10:00'), 'touch'))
    })
    act(() => {
      document.dispatchEvent(documentPointerEvent('pointermove', xOfDay(3), yOf('13:00'), 'touch'))
      document.dispatchEvent(documentPointerEvent('pointerup', xOfDay(3), yOf('13:00'), 'touch'))
    })

    expect(onDrop).not.toHaveBeenCalled()
    expect(result.current.longPressSlotId).toBeNull()
  })

  it('should arm the drag after a long press and then move normally', () => {
    const { result, onDrop } = setup()

    act(() => {
      result.current.onSlotPointerDown(SLOT.id, SLOT.date, SLOT.start, SLOT.end,
        reactPointerDown(xOfDay(1), yOf('10:00'), 'touch'))
    })
    act(() => { vi.advanceTimersByTime(400) })

    expect(result.current.longPressSlotId).toBe('training-1')

    act(() => {
      document.dispatchEvent(documentPointerEvent('pointermove', xOfDay(3), yOf('13:00'), 'touch'))
    })
    act(() => {
      document.dispatchEvent(documentPointerEvent('pointerup', xOfDay(3), yOf('13:00'), 'touch'))
    })

    expect(onDrop).toHaveBeenCalledWith(
      'training-1', DAYS[3].date, '13:00', '14:00',
      DAYS[1].date, '10:00', '11:00',
    )
  })

  it('should cancel the long press when the finger scrolls away', () => {
    const { result, onDrop } = setup()

    act(() => {
      result.current.onSlotPointerDown(SLOT.id, SLOT.date, SLOT.start, SLOT.end,
        reactPointerDown(xOfDay(1), yOf('10:00'), 'touch'))
    })
    act(() => {
      const scroll = new Event('touchmove', { bubbles: true }) as TouchEvent
      Object.defineProperty(scroll, 'touches', {
        value: [{ clientX: xOfDay(1), clientY: yOf('10:00') + 60 }],
      })
      document.dispatchEvent(scroll)
    })
    act(() => { vi.advanceTimersByTime(400) })

    expect(result.current.longPressSlotId).toBeNull()

    act(() => {
      document.dispatchEvent(documentPointerEvent('pointermove', xOfDay(3), yOf('13:00'), 'touch'))
      document.dispatchEvent(documentPointerEvent('pointerup', xOfDay(3), yOf('13:00'), 'touch'))
    })
    expect(onDrop).not.toHaveBeenCalled()
  })
})
