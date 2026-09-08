import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Unmount anything rendered by a test so the next one starts on an empty document
afterEach(() => cleanup())

// jsdom implements no Element.scrollTo either. TimeScrollPicker positions its columns with it on
// mount, so any test that reveals a clock would throw before asserting anything. A no-op is the
// honest stub: scroll position IS the value there, and jsdom has no layout to compute one from —
// tests that care about the picked time set it through the component's props.
if (!Element.prototype.scrollTo) {
  Element.prototype.scrollTo = () => {}
}

// Nor `scrollIntoView`. The admin command palette keeps its highlighted row inside the scroll box
// with it, so arrowing down would throw before any assertion ran. A no-op is honest here for the
// same reason: jsdom has no layout, so there is no "inside the box" to compute — what the tests
// assert is which option is selected, which lives in `aria-selected`, not in scroll offset.
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {}
}

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
