import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

/**
 * The desktop bar and the hamburger hand over at ONE breakpoint, written in four places: the tab
 * row and the right cluster (`hidden xl:flex`), the hamburger button and the drawer (`xl:hidden`).
 * If they drift apart there is a band of widths with both navigations or with none — with none,
 * a tablet user has no way to reach their account, the chat or log out.
 *
 * The breakpoint is `xl` because a signed-in bar does not fit below 1280 px (measured 2026-11-24:
 * at `md` it scrolled the page sideways from 768 to 1279 px).
 */

const LAYOUT = join(__dirname, '..', 'components', 'layout')
const navbar = readFileSync(join(LAYOUT, 'Navbar.tsx'), 'utf8')
const drawer = readFileSync(join(LAYOUT, 'MobileNavPanel.tsx'), 'utf8')

const BREAKPOINTS = ['sm', 'md', 'lg', 'xl', '2xl']

function shown(source: string): string[] {
  return BREAKPOINTS.filter((bp) => source.includes(`hidden ${bp}:flex`))
}

function hidden(source: string): string[] {
  return BREAKPOINTS.filter((bp) => new RegExp(`["\\s]${bp}:hidden[\\s"]`).test(source))
}

describe('navigation breakpoint', () => {
  it('should show the desktop bar from xl only', () => {
    expect(shown(navbar)).toEqual(['xl'])
    expect(navbar.match(/hidden xl:flex/g)?.length).toBe(2)
  })

  it('should hide the hamburger at the same breakpoint the bar appears', () => {
    expect(hidden(navbar)).toEqual(['xl'])
  })

  it('should hide the drawer at the same breakpoint the bar appears', () => {
    expect(hidden(drawer)).toEqual(['xl'])
  })
})
