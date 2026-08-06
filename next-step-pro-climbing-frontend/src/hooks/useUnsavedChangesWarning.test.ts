import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useUnsavedChangesWarning } from './useUnsavedChangesWarning'

/** Just enough of a spy to read back what it was called with. */
interface CallLog { mock: { calls: unknown[][] } }

describe('useUnsavedChangesWarning', () => {
  let add: CallLog
  let remove: CallLog

  beforeEach(() => {
    add = vi.spyOn(window, 'addEventListener') as unknown as CallLog
    remove = vi.spyOn(window, 'removeEventListener') as unknown as CallLog
  })
  afterEach(() => vi.restoreAllMocks())

  const listeners = (spy: CallLog) =>
    spy.mock.calls.filter((call) => call[0] === 'beforeunload')

  it('should not arm the prompt while there is nothing to lose', () => {
    renderHook(() => useUnsavedChangesWarning(false))
    expect(listeners(add)).toHaveLength(0)
  })

  it('should arm the prompt once there are unsaved changes', () => {
    renderHook(() => useUnsavedChangesWarning(true))
    expect(listeners(add)).toHaveLength(1)
  })

  it('should disarm as soon as the work is saved', () => {
    // Saving flips dirty back to false; leaving the listener attached would nag on a
    // refresh with nothing left to lose, and users learn to click through those.
    const { rerender } = renderHook(({ dirty }) => useUnsavedChangesWarning(dirty), {
      initialProps: { dirty: true },
    })
    rerender({ dirty: false })
    expect(listeners(remove)).toHaveLength(1)
  })

  it('should disarm when the editor unmounts', () => {
    const { unmount } = renderHook(() => useUnsavedChangesWarning(true))
    unmount()
    expect(listeners(remove)).toHaveLength(1)
  })

  it('should both preventDefault and set returnValue', () => {
    // Chrome keys off one, older engines off the other; setting only one shows no prompt
    renderHook(() => useUnsavedChangesWarning(true))
    const handler = listeners(add)[0][1] as (e: Event) => void
    const event = { preventDefault: vi.fn(), returnValue: undefined } as unknown as BeforeUnloadEvent
    handler(event)
    expect(event.preventDefault).toHaveBeenCalled()
    expect(event.returnValue).toBe('')
  })
})
