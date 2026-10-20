import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

/**
 * The backend HTML-escapes free text on write (`StoredText.escapeAndCap`): a booking comment, a
 * proposal comment, an attachment's file name. React escapes again on render, so a field shown as a
 * bare text child prints the entities — "I'll be late" came out as "I&#39;ll be late" on nine
 * screens, the client's own "My reservations" among them (audit 2026-10-20). The training calendar
 * always decoded; the general calendar never did.
 *
 * Rendered as text, these fields go through `decodeHtmlEntities`. The check is by field name,
 * because those are what the escaped columns are called on the wire.
 */
const SRC = join(__dirname, '..')
const ESCAPED_FIELDS = ['comment', 'fileName']

function sourceFiles(dir: string = SRC): string[] {
  return readdirSync(dir).flatMap((name) => {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) return name === '__architecture__' || name === 'test' ? [] : sourceFiles(full)
    return name.endsWith('.tsx') && !name.endsWith('.test.tsx') ? [full] : []
  })
}

describe('escaped text is decoded before it is shown', () => {
  it('never renders an escaped field as a bare text child or file name', () => {
    const fields = ESCAPED_FIELDS.join('|')
    // A text child (`>{r.comment}<`, `"{r.comment}"`) or the file-name prop of a file card.
    const bare = new RegExp(`(?:[>"]\\s*\\{\\s*[\\w.?]+\\.(?:${fields})\\s*\\}|fileName=\\{\\s*[\\w.?]+\\.fileName\\s*\\})`, 'g')
    const offenders: string[] = []
    let scanned = 0
    for (const file of sourceFiles()) {
      const source = readFileSync(file, 'utf-8')
      scanned++
      for (const m of source.matchAll(bare)) offenders.push(`${file.slice(SRC.length + 1)}: ${m[0]}`)
    }
    expect(scanned).toBeGreaterThan(100)
    expect(offenders, 'Wrap it in decodeHtmlEntities(...)').toEqual([])
  })
})
