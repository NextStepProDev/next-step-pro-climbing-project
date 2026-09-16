import { describe, it, expect, vi, afterEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { usePointerFine } from './usePointerFine'

/**
 * Same stand-in shape as useCompactViewport's: jsdom ships no matchMedia, and the listener set
 * has to be shared across every matchMedia() call because the hook queries once to subscribe
 * and again to read.
 */
function stubPointer(initial: boolean) {
  const listeners = new Set<() => void>()
  let fine = initial

  vi.stubGlobal('matchMedia', (query: string) => {
    // The hook must ask for BOTH: a stylus hovers nothing, a mouse on a touchscreen laptop does
    expect(query).toContain('hover: hover')
    expect(query).toContain('pointer: fine')
    return {
      get matches() { return fine },
      addEventListener: (_: string, listener: () => void) => { listeners.add(listener) },
      removeEventListener: (_: string, listener: () => void) => { listeners.delete(listener) },
    }
  })

  return {
    switchTo(next: boolean) {
      fine = next
      act(() => { listeners.forEach((l) => l()) })
    },
    get listenerCount() { return listeners.size },
  }
}

afterEach(() => vi.unstubAllGlobals())

describe('usePointerFine', () => {
  it('should report a hovering pointer', () => {
    stubPointer(true)

    expect(renderHook(() => usePointerFine()).result.current).toBe(true)
  })

  it('should report no hovering pointer on a touch screen', () => {
    stubPointer(false)

    expect(renderHook(() => usePointerFine()).result.current).toBe(false)
  })

  it('should follow a change of input device', () => {
    // A tablet with a keyboard case attached and removed again, or a desktop browser's
    // device emulation being switched on mid-session.
    const pointer = stubPointer(true)
    const { result } = renderHook(() => usePointerFine())

    pointer.switchTo(false)

    expect(result.current).toBe(false)
  })

  it('should drop its listener on unmount', () => {
    const pointer = stubPointer(true)
    const { unmount } = renderHook(() => usePointerFine())

    unmount()

    expect(pointer.listenerCount).toBe(0)
  })
})
