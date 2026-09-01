import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

/**
 * A day column is `minmax(0, 1fr)`, never a bare `1fr`.
 *
 * `1fr` is shorthand for `minmax(auto, 1fr)`, and that `auto` minimum means the track cannot
 * become narrower than its content's min-content width. One entry with a long title therefore
 * widens its own column and steals the width from the other six — and because the week view is
 * built from THREE separate grids stacked on top of each other (day headers, the all-day lane,
 * the hour grid), only the lane holding the long entry stretches. The other two keep equal
 * columns, so the three rows stop lining up and every chip in the lane sits under the wrong day.
 *
 * Measured on the 1:1 training calendar before this was fixed, at a 1440px viewport:
 *
 *   headers:      60px | 165 165 165 165 165 165 165
 *   all-day lane: 60px | 143 143 143 143 298 143 143   <- Friday's long title
 *   hour grid:    60px | 165 165 165 165 165 165 165
 *
 * `truncate` on the chip does not prevent it: the class lets the element shrink when its
 * container is already constrained, but the container still advertises the full nowrap width
 * upward as its min-content, which is exactly what the `auto` minimum reads. The fix has to be
 * on the track, not on the contents.
 *
 * Tailwind's own `grid-cols-*` utilities already expand to `minmax(0, 1fr)`, which is why the
 * month grids never had this. Only hand-written `gridTemplateColumns` is at risk.
 */

const SRC = join(__dirname, '..')

const WATCHED = ['components/calendar', 'components/training', 'components/admin', 'pages']

function sourceFiles(dir: string): string[] {
  const out: string[] = []
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) {
      out.push(...sourceFiles(full))
    } else if (
      (name.endsWith('.ts') || name.endsWith('.tsx'))
      && !name.endsWith('.test.ts')
      && !name.endsWith('.test.tsx')
    ) {
      out.push(full)
    }
  }
  return out
}

/** `1fr` inside a repeat() or a bare track list, not preceded by `minmax(`. */
const BARE_FR = /repeat\(\s*\d+\s*,\s*1fr\s*\)/

describe('grid tracks', () => {
  it('never sizes a day column with a bare 1fr', () => {
    const offenders: string[] = []

    for (const entry of WATCHED) {
      for (const file of sourceFiles(join(SRC, entry))) {
        const source = readFileSync(file, 'utf8')
        if (!source.includes('gridTemplateColumns')) continue

        for (const line of source.split('\n')) {
          if (!line.includes('gridTemplateColumns')) continue
          // Normalised so `repeat(7, 1fr)` and `repeat(7,1fr)` are the same offence.
          if (BARE_FR.test(line.replace(/\s+/g, ' ').replace(/, /g, ','))) {
            offenders.push(`${file.slice(SRC.length + 1)}: ${line.trim()}`)
          }
        }
      }
    }

    expect(offenders).toEqual([])
  })

  it('finds the grids it claims to guard', () => {
    // Self-check: a gate scanning the wrong tree passes silently and proves nothing.
    let guarded = 0
    for (const entry of WATCHED) {
      for (const file of sourceFiles(join(SRC, entry))) {
        if (readFileSync(file, 'utf8').includes('gridTemplateColumns')) guarded++
      }
    }
    expect(guarded).toBeGreaterThanOrEqual(2)
  })
})
