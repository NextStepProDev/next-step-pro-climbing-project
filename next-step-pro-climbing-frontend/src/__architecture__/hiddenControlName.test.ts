import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

/**
 * A control hidden with `sr-only` and redrawn as a switch carries its own accessible name.
 *
 * Found 2026-10-19: the three Settings switches (notifications, newsletter, public ascents) were a
 * bare checkbox inside a <label> holding only the drawn track — the text beside them was not tied
 * to the control, so a screen reader announced "checkbox, checked" and nothing else, on the very
 * screen where people decide what we may send them. The visible text cannot name a control it is
 * not connected to, so the tag itself has to say aria-label or aria-labelledby.
 */
const SRC = join(__dirname, '..')

function sourceFiles(dir: string = SRC): string[] {
  const out: string[] = []
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) {
      if (name !== '__architecture__' && name !== 'test') out.push(...sourceFiles(full))
    } else if (name.endsWith('.tsx') && !name.endsWith('.test.tsx')) {
      out.push(full)
    }
  }
  return out
}

describe('hidden controls', () => {
  it('never hides a checkbox or radio without naming it', () => {
    const offenders: string[] = []
    let seen = 0
    for (const file of sourceFiles()) {
      const source = readFileSync(file, 'utf-8')
      for (const tag of source.match(/<input\b[\s\S]*?\/>/g) ?? []) {
        if (!/type="(checkbox|radio)"/.test(tag) || !/\bsr-only\b/.test(tag)) continue
        seen++
        if (!/aria-label(ledby)?=/.test(tag)) {
          offenders.push(`${file.slice(SRC.length + 1)}: ${tag.replace(/\s+/g, ' ').slice(0, 120)}`)
        }
      }
    }
    expect(seen).toBeGreaterThan(0)
    expect(offenders, 'Give the hidden control aria-label or aria-labelledby').toEqual([])
  })
})
