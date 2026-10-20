import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

/**
 * Labels a person reads or hears come from the translations, never from a literal in the markup.
 *
 * Found 2026-10-19: the gallery lightbox, "scroll to top" and "share" announced themselves in English
 * to Polish and Spanish screen readers, and admin tooltips ("Kopiuj slot", "Wytnij slot") stayed
 * Polish in English and Spanish. The i18n parity gate checks the keys that exist; it cannot see a
 * string that never became a key. This one looks at the attributes where such strings hide.
 */
const SRC = join(__dirname, '..')

// Names that are the same in every language, and the one page written per language on purpose.
const ALLOWED_VALUES = new Set(['Facebook', 'YouTube', 'Instagram', 'WhatsApp', 'Next Step Pro Climbing', 'PZA', 'FAQ'])
const ALLOWED_FILES = new Set(['pages/PrivacyPolicyPage.tsx'])

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

describe('literal labels', () => {
  it('never hard-codes a title, aria-label, alt or placeholder a person reads', () => {
    const offenders: string[] = []
    for (const file of sourceFiles()) {
      const name = file.slice(SRC.length + 1)
      if (ALLOWED_FILES.has(name)) continue
      const source = readFileSync(file, 'utf-8')
      for (const m of source.matchAll(/\b(title|aria-label|alt|placeholder)="([^"]*)"/g)) {
        const value = m[2]
        if (!/[A-Za-zÀ-ž]{3}/.test(value)) continue // empty alt, "#", "+48 ..." examples
        if (ALLOWED_VALUES.has(value) || value.startsWith('https://')) continue
        offenders.push(`${name}: ${m[1]}="${value}"`)
      }
    }
    expect(offenders, 'Use t(...) — the i18n parity gate only sees strings that became keys').toEqual([])
  })

  // The same strings in expression form slipped past the check above (found 2026-10-20): every
  // modal's close button said "Close …" in all three languages, the theme switch said "Switch to
  // light mode", and the user picker's placeholder was Polish everywhere. A sentence is what gives
  // them away — a capitalised word or two words with a space — while the keys, enum values and
  // `=== 'dark'` comparisons that share these expressions are single lower-case or dotted words.
  it('never hard-codes one inside an expression either', () => {
    const offenders: string[] = []
    for (const file of sourceFiles()) {
      const name = file.slice(SRC.length + 1)
      if (ALLOWED_FILES.has(name)) continue
      const source = readFileSync(file, 'utf-8')
      for (const m of source.matchAll(/\b(title|aria-label|alt|placeholder)=\{((?:[^{}]|\{(?:[^{}]|\{[^{}]*\})*\})*)\}/g)) {
        const expression = m[2].replace(/\bt\(\s*(['"`])[^'"`]*\1/g, 't(')
        for (const literal of expression.matchAll(/'([^']*)'|"([^"]*)"|`([^`]*)`/g)) {
          const text = (literal[1] ?? literal[2] ?? literal[3]).replace(/\$\{[^}]*\}/g, '').trim()
          if (!/[a-zà-ž]{3}/.test(text) || ALLOWED_VALUES.has(text)) continue
          if (/^[dMyLEHhmsaoQ.,:\-/ ']+$/.test(text)) continue // a date-fns pattern, not words
          if (!/\s/.test(text) && !/^[A-ZÀ-Ž][a-zà-ž]/.test(text)) continue
          offenders.push(`${name}: ${m[1]}={… '${text}' …}`)
        }
      }
    }
    expect(offenders, 'Use t(...) — the i18n parity gate only sees strings that became keys').toEqual([])
  })

  // The admin editors carried some eighty Polish strings straight in the JSX until 2026-10-19 —
  // including a hand-rolled plural that said "Wysłano 22 powiadomień". Polish letters outside a
  // comment are the cheapest tell of a string that skipped the translations.
  it('keeps Polish text out of the code, comments aside', () => {
    const transliteration = new Set(['utils/slugify.ts', 'utils/personSearch.ts'])
    const offenders: string[] = []
    const walk = (dir: string): string[] => readdirSync(dir).flatMap((name) => {
      const full = join(dir, name)
      if (statSync(full).isDirectory()) return name === '__architecture__' || name === 'test' ? [] : walk(full)
      return /\.(ts|tsx)$/.test(name) && !/\.test\.tsx?$/.test(name) ? [full] : []
    })
    for (const file of walk(SRC)) {
      const name = file.slice(SRC.length + 1)
      if (ALLOWED_FILES.has(name) || transliteration.has(name)) continue
      const code = readFileSync(file, 'utf-8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '')
      code.split('\n').forEach((line) => {
        if (/[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]/.test(line)) offenders.push(`${name}: ${line.trim().slice(0, 100)}`)
      })
    }
    expect(offenders, 'Move the text to locales/ and read it with t(...)').toEqual([])
  })
})
