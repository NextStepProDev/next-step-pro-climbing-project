import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Unmount anything rendered by a test so the next one starts on an empty document
afterEach(() => cleanup())

// jsdom implements no IntersectionObserver. Components that lazy-load on scroll (private file
// thumbnails) would otherwise throw on mount, so the stub reports "already visible": a test that
// cannot scroll should still see what a reader would.
if (!('IntersectionObserver' in globalThis)) {
  class ImmediateIntersectionObserver {
    root = null
    rootMargin = ''
    scrollMargin = ''
    thresholds: ReadonlyArray<number> = []
    callback: IntersectionObserverCallback

    constructor(callback: IntersectionObserverCallback) {
      this.callback = callback
    }

    observe(target: Element) {
      this.callback(
        [{ isIntersecting: true, target } as IntersectionObserverEntry],
        this as unknown as IntersectionObserver,
      )
    }
    unobserve() {}
    disconnect() {}
    takeRecords(): IntersectionObserverEntry[] {
      return []
    }
  }
  globalThis.IntersectionObserver = ImmediateIntersectionObserver as unknown as typeof IntersectionObserver
}
