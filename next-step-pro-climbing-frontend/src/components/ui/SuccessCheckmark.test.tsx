import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from '@testing-library/react'
import { SuccessCheckmark } from './SuccessCheckmark'

describe('SuccessCheckmark', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('calls onDone once, 1.5 s after it appears', () => {
    const onDone = vi.fn()
    render(<SuccessCheckmark onDone={onDone} />)

    vi.advanceTimersByTime(1499)
    expect(onDone).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('keeps its 1.5 s when the parent re-renders with a new inline callback', () => {
    // Every caller passes an arrow function; with it as an effect dependency, each re-render of
    // the parent used to restart the timer.
    const onDone = vi.fn()
    const { rerender } = render(<SuccessCheckmark onDone={() => onDone('first')} />)

    vi.advanceTimersByTime(1000)
    rerender(<SuccessCheckmark onDone={() => onDone('latest')} />)
    vi.advanceTimersByTime(500)

    expect(onDone).toHaveBeenCalledExactlyOnceWith('latest')
  })
})
