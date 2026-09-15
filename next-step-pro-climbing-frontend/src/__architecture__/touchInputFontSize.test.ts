import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

/**
 * Form controls must reach 16px on touch, and the rule must stay OUT of a Tailwind layer.
 *
 * Safari on iOS zooms the page in whenever a control with a font smaller than 16px takes focus,
 * and never zooms back out — not on blur, not when a modal closes, not on navigation. The page
 * simply stays magnified until the user pinches it back. Reported from the settlements screen,
 * where every saved amount ended in a pinch; the app writes its inputs as `text-sm`/`text-xs` in
 * 45 files, so it was never one screen's bug.
 *
 * Two things can silently undo this, and neither looks wrong in review:
 *
 * 1. **Wrapping it in `@layer`.** `.text-sm` lives in Tailwind's `utilities` layer and has higher
 *    specificity than an element selector, so the only reason this rule wins is that unlayered
 *    styles beat layered ones outright. Inside `@layer` it parses, applies to nothing, and the
 *    zoom comes back.
 * 2. **Dropping below 16px.** 15px reads as "close enough" and triggers exactly the same zoom.
 *
 * Neither produces an error, a warning, or a visible difference on a desktop — which is where the
 * change would be made.
 */
const CSS = join(__dirname, '..', 'index.css')

/**
 * ⚠️ Comments are stripped before anything is scanned, and that is not tidiness.
 *
 * The first version of this gate read the word `@layer` out of the comment ABOVE the rule — the
 * one explaining why the rule must not be layered — and reported the rule as layered. A gate that
 * fails on prose is a gate that gets deleted, and it would also have passed had a comment merely
 * mentioned `input` and `font-size` without any rule existing.
 */
function source(): string {
  return readFileSync(CSS, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '')
}

/** Start index of the coarse-pointer block, or -1. */
function coarseBlockStart(css: string): number {
  return css.search(/@media\s*\(\s*pointer:\s*coarse\s*\)/)
}

/** The `{ … }` body that follows `from`, matched by brace depth. */
function blockBody(css: string, from: number): string {
  const open = css.indexOf('{', from)
  let depth = 0
  for (let i = open; i < css.length; i++) {
    if (css[i] === '{') depth++
    else if (css[i] === '}' && --depth === 0) return css.slice(open + 1, i)
  }
  return ''
}

/** Ranges covered by every `@layer … { … }` block in the file. */
function layerRanges(css: string): Array<[number, number]> {
  const ranges: Array<[number, number]> = []
  const re = /@layer\b[^{;]*\{/g
  let m: RegExpExecArray | null
  while ((m = re.exec(css)) !== null) {
    const open = m.index + m[0].length - 1
    let depth = 0
    for (let i = open; i < css.length; i++) {
      if (css[i] === '{') depth++
      else if (css[i] === '}' && --depth === 0) {
        ranges.push([m.index, i])
        break
      }
    }
  }
  return ranges
}

describe('touch form controls', () => {
  it('raises inputs, selects and textareas to at least 16px on a coarse pointer', () => {
    const css = source()
    const start = coarseBlockStart(css)
    expect(start).toBeGreaterThan(-1)

    const body = blockBody(css, start)
    for (const control of ['input', 'select', 'textarea']) {
      expect(body).toMatch(new RegExp(`\\b${control}\\b`))
    }

    const size = body.match(/font-size:\s*(\d+(?:\.\d+)?)px/)
    expect(size).not.toBeNull()
    // 15px is not "close enough": iOS zooms on anything under 16.
    expect(Number(size![1])).toBeGreaterThanOrEqual(16)
  })

  it('keeps the rule unlayered, which is the only reason it beats the utility class', () => {
    const css = source()
    const start = coarseBlockStart(css)
    expect(start).toBeGreaterThan(-1)

    const inside = layerRanges(css).some(([from, to]) => start > from && start < to)
    expect(inside).toBe(false)
  })
})
