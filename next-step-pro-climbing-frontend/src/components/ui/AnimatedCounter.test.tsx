import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render } from '@testing-library/react'
import { AnimatedCounter } from './AnimatedCounter'

function mockReducedMotion(reduce: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: reduce && query.includes('prefers-reduced-motion'),
    media: query, addEventListener: vi.fn(), removeEventListener: vi.fn(),
    addListener: vi.fn(), removeListener: vi.fn(), onchange: null, dispatchEvent: vi.fn(),
  }))
}

beforeEach(() => {
  // jsdom has no IntersectionObserver; the counter only starts once seen, so it never starts here
  window.IntersectionObserver = class { observe() {} disconnect() {} } as never
})

describe('AnimatedCounter', () => {
  it('gives a screen reader the final number, never the one mid-count', () => {
    mockReducedMotion(false)
    const { container } = render(<AnimatedCounter target={200} suffix="+" />)

    // Not yet scrolled into view: the digits still read 0+, the reader must hear 200+
    expect(container.querySelector('.sr-only')?.textContent).toBe('200+')
    expect(container.querySelector('[aria-hidden="true"]')?.textContent).toBe('0+')
  })

  it('shows the final number straight away when the system asks for less motion', () => {
    mockReducedMotion(true)
    const { container } = render(<AnimatedCounter target={200} suffix="+" />)

    expect(container.querySelector('[aria-hidden="true"]')?.textContent).toBe('200+')
  })
})
