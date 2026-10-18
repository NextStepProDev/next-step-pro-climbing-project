import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

/**
 * Dates and numbers are formatted in the reader's language, never a hard-coded one.
 *
 * Found 2026-10: the news list, the article page and both CMS panels formatted the publication
 * date with `toLocaleDateString('pl-PL')`, so an English or Spanish page said "18 października 2026"
 * in the middle of a translated layout. The language is `i18n.language` (or `useDateLocale()` for
 * date-fns); a literal tag in a formatter call is the bug.
 */
const SRC = join(__dirname, '..')

function sourceFiles(dir: string = SRC): string[] {
  const out: string[] = []
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) {
      if (name !== '__architecture__' && name !== 'test') out.push(...sourceFiles(full))
    } else if ((name.endsWith('.ts') || name.endsWith('.tsx'))
      && !name.endsWith('.test.ts') && !name.endsWith('.test.tsx')) {
      out.push(full)
    }
  }
  return out
}

// Machine formats, never shown to a reader: 'en-CA' is the one Intl locale that prints a date as
// yyyy-MM-dd, which is how calendarDate reads the Warsaw wall clock.
const ALLOWED = new Set(['utils/calendarDate.ts'])

const LITERAL_LOCALE =/(?:toLocale(?:Date|Time)?String|Intl\.(?:DateTimeFormat|NumberFormat|RelativeTimeFormat))\(\s*['"`][a-z]{2}(?:-[A-Z]{2})?['"`]/g

describe('locale', () => {
  it('never hard-codes a language into a date or number formatter', () => {
    const offenders: string[] = []
    let scanned = 0
    for (const file of sourceFiles()) {
      scanned++
      if (ALLOWED.has(file.slice(SRC.length + 1))) continue
      const source = readFileSync(file, 'utf-8')
      for (const hit of source.match(LITERAL_LOCALE) ?? []) {
        offenders.push(`${file.slice(SRC.length + 1)}: ${hit}`)
      }
    }
    expect(scanned).toBeGreaterThan(100)
    expect(offenders, "Pass i18n.language (or useDateLocale() for date-fns)").toEqual([])
  })
})
