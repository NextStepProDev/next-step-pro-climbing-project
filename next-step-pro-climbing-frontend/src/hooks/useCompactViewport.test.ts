import { describe, it, expect, vi, afterEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useCompactViewport } from './useCompactViewport'

/**
 * jsdom ships no matchMedia at all, so the hook needs a stand-in. It has to be a single
 * shared listener set across every matchMedia() call: the hook queries once to subscribe
 * and again to read, and a stub that hands out isolated objects would report changes the
 * hook never sees.
 */
function stubViewport(initialWidth: number) {
  const listeners = new Set<() => void>()
  let width = initialWidth
  let created = 0

  vi.stubGlobal('matchMedia', (query: string) => {
    created++
    const max = Number(/max-width:\s*(\d+)px/.exec(query)![1])
    return {
      get matches() { return width <= max },
      addEventListener: (_: string, listener: () => void) => { listeners.add(listener) },
      removeEventListener: (_: string, listener: () => void) => { listeners.delete(listener) },
    }
  })

  return {
    resizeTo(next: number) {
      width = next
      act(() => { listeners.forEach((l) => l()) })
    },
    get listenerCount() { return listeners.size },
    get queriesCreated() { return created },
  }
}

afterEach(() => vi.unstubAllGlobals())

describe('useCompactViewport', () => {
  it('should report compact below the sm breakpoint', () => {
    stubViewport(390)

    expect(renderHook(() => useCompactViewport()).result.current).toBe(true)
  })

  it('should not report compact at the breakpoint itself', () => {
    // Tailwind's sm is min-width: 640px, so 640 is already the wide side
    stubViewport(640)

    expect(renderHook(() => useCompactViewport()).result.current).toBe(false)
  })

  it('should report compact one pixel below the breakpoint', () => {
    stubViewport(639)

    expect(renderHook(() => useCompactViewport()).result.current).toBe(true)
  })

  it('should react to the viewport crossing the breakpoint', () => {
    const viewport = stubViewport(1280)
    const { result } = renderHook(() => useCompactViewport())
    expect(result.current).toBe(false)

    viewport.resizeTo(390)

    expect(result.current).toBe(true)
  })

  it('should react to the viewport crossing back', () => {
    const viewport = stubViewport(390)
    const { result } = renderHook(() => useCompactViewport())

    viewport.resizeTo(1280)

    expect(result.current).toBe(false)
  })

  it('should remove its listener on unmount', () => {
    const viewport = stubViewport(1280)
    const { unmount } = renderHook(() => useCompactViewport())
    expect(viewport.listenerCount).toBe(1)

    unmount()

    expect(viewport.listenerCount).toBe(0)
  })
})
