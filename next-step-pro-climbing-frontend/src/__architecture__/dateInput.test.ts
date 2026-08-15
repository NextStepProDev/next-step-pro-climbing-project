import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { join, relative } from 'node:path'

/**
 * `DateInput` is the only place allowed to render a raw `<input type="date">`.
 *
 * Safari on macOS keeps the calendar popover open after a day is clicked, so a plain date input
 * reads as "the click did nothing" until you click somewhere else. DateInput dismisses it; nothing
 * about a bare `<input type="date">` looks wrong at the call site, which is exactly how the app
 * ended up with the fix in the events panel and not in the slot form everyone actually uses — the
 * workaround was written once in Feb 2026 and then left behind when that form was extracted into
 * its own component. Hence a gate rather than vigilance.
 */

const SRC_DIR = join(__dirname, '..')
const OWNER = join('components', 'ui', 'DateInput.tsx')

function componentFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) return componentFiles(path)
    // Tests may name the attribute while explaining what they simulate.
    return entry.name.endsWith('.tsx') && !entry.name.endsWith('.test.tsx') ? [path] : []
  })
}

/** Strips comments so commentary about date inputs never trips the gate. */
function stripComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')
}

describe('date fields', () => {
  it('routes every date input through DateInput', () => {
    const offenders = componentFiles(SRC_DIR)
      .map((path) => relative(SRC_DIR, path))
      .filter((path) => path !== OWNER)
      .filter((path) => /type=["']date["']/.test(stripComments(readFileSync(join(SRC_DIR, path), 'utf-8'))))

    expect(
      offenders,
      `A bare <input type="date"> leaves Safari's calendar popover open after a day is picked, ` +
        `so the pick looks like it did nothing. Use <DateInput> from components/ui/DateInput.\n` +
        offenders.join('\n'),
    ).toEqual([])
  })

  it('keeps DateInput dismissing the picker on a mouse pick', () => {
    // The blur IS the fix; without it this component is an alias for the bug it exists to prevent.
    const source = readFileSync(join(SRC_DIR, OWNER), 'utf-8')

    expect(source).toMatch(/pointerType !== 'touch'/)
    expect(source).toMatch(/dismissOnCommit\.current[\s\S]{0,40}blur\(\)/)
  })
})
