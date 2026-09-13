import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

/**
 * The link that lets a keyboard user step over the navigation.
 *
 * Measured with a driven browser before it existed: the first control of the actual page sat 14 to
 * 15 Tab presses behind the navbar, on every route, paid again on every navigation. A screen reader
 * can jump straight to the `<main>` landmark, so this is for the people landmarks do not help —
 * sighted and keyboard-only.
 *
 * Three properties, and the fragile one is the order. A skip link that is not FIRST in the document
 * is a skip link you have to tab through the navigation to reach, which is the problem it was added
 * to solve; nothing about moving a JSX block looks wrong while doing it, so the order is asserted
 * here rather than left to review. Same reasoning as `noRawFetch`: a gate instead of vigilance.
 */
const LAYOUT = join(__dirname, '..', 'components', 'layout', 'Layout.tsx')

function layoutSource(): string {
  return readFileSync(LAYOUT, 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')
}

describe('skip-to-content link', () => {
  it('exists and points at the main landmark', () => {
    expect(layoutSource()).toMatch(/href="#main"/)
  })

  it('comes before the navigation, or it is not a way past it', () => {
    const source = layoutSource()
    const skip = source.indexOf('href="#main"')
    const navbar = source.indexOf('<Navbar')

    expect(skip).toBeGreaterThan(-1)
    expect(navbar).toBeGreaterThan(-1)
    expect(skip).toBeLessThan(navbar)
  })

  it('gives main the id it jumps to and a tabindex, so focus actually moves', () => {
    const source = layoutSource()
    // Without tabIndex the fragment scrolls the page and leaves focus where it was, so the next Tab
    // carries on through the navigation as if nothing had happened.
    expect(source).toMatch(/<main[^>]*id="main"/)
    expect(source).toMatch(/<main[^>]*tabIndex=\{-1\}/)
  })
})
