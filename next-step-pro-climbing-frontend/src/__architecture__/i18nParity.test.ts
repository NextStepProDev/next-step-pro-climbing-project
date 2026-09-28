import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'

/**
 * Key parity across pl / en / es.
 *
 * A missing key renders as the raw key path in the UI — no build error, no console noise, and
 * only visible to someone browsing in that language. Exactly the kind of defect a review in one
 * language never surfaces.
 *
 * The plural suffixes matter: i18next appends CLDR categories per language, and Polish has
 * `_few`/`_many` where English only has `_one`/`_other`. Comparing raw key names reports ~38
 * false positives, so suffixes are normalised away and the base key is compared instead.
 */

const LOCALES_DIR = join(__dirname, '..', 'locales')
const LANGUAGES = ['pl', 'en', 'es'] as const
const BASE = 'pl'

const PLURAL_SUFFIX = /_(zero|one|two|few|many|other)$/

function flatten(value: unknown, prefix = ''): Set<string> {
  const keys = new Set<string>()
  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    for (const [key, child] of Object.entries(value as Record<string, unknown>)) {
      const path = prefix ? `${prefix}.${key}` : key
      for (const nested of flatten(child, path)) keys.add(nested)
    }
  } else {
    keys.add(prefix.replace(PLURAL_SUFFIX, ''))
  }
  return keys
}

function keysOf(language: string, file: string): Set<string> {
  return flatten(JSON.parse(readFileSync(join(LOCALES_DIR, language, file), 'utf-8')))
}

const namespaceFiles = readdirSync(join(LOCALES_DIR, BASE)).filter((f) => f.endsWith('.json'))

describe('i18n', () => {
  it('ships the same namespaces in every language', () => {
    for (const language of LANGUAGES) {
      const files = readdirSync(join(LOCALES_DIR, language)).filter((f) => f.endsWith('.json'))
      expect(files.sort(), `${language} namespace files differ from ${BASE}`).toEqual(
        [...namespaceFiles].sort(),
      )
    }
  })

  it.each(namespaceFiles)('keeps %s in parity across pl/en/es', (file) => {
    const base = keysOf(BASE, file)
    expect(base.size, `${file} parsed as empty — the gate broke, not the translations`).toBeGreaterThan(0)

    const problems: string[] = []
    for (const language of LANGUAGES.filter((l) => l !== BASE)) {
      const keys = keysOf(language, file)
      const missing = [...base].filter((k) => !keys.has(k)).sort()
      const extra = [...keys].filter((k) => !base.has(k)).sort()

      if (missing.length) problems.push(`${language}/${file} missing: ${missing.join(', ')}`)
      // Extra keys are usually a rename left half-done, or a typo of a real key.
      if (extra.length) problems.push(`${language}/${file} has keys absent from ${BASE}: ${extra.join(', ')}`)
    }

    expect(problems, problems.join('\n')).toEqual([])
  })

  /*
   * Parity alone is blind to a plural written with another language's categories: copying the
   * Polish `_one/_few/_many` into English passes it, and then every count but 1 renders the raw
   * key ("training" in the month cell, "gallery.photo" under an album). The categories required
   * are the ones a real count reaches in that language, so Polish is not asked for `_other`
   * (fractions only) and Spanish not for `_many` (millions). A bare base key stands in for
   * `_one` and nothing else: i18next falls back to it for EVERY missing category, so in Polish
   * a base + `_other` pair printed the singular for 2–4.
   */
  it.each(LANGUAGES)('gives every %s plural the forms its counts actually reach', (language) => {
    const rules = new Intl.PluralRules(language)
    const reached = new Set(Array.from({ length: 1001 }, (_, n) => rules.select(n)))

    const problems: string[] = []
    const walk = (node: Record<string, unknown>, path: string, file: string) => {
      const bases = new Set<string>()
      for (const [key, child] of Object.entries(node)) {
        if (child !== null && typeof child === 'object') walk(child as Record<string, unknown>, `${path}${key}.`, file)
        const match = PLURAL_SUFFIX.exec(key)
        if (match) bases.add(key.slice(0, match.index))
      }
      for (const base of bases) {
        const absent = [...reached].filter(
          (category) => !(`${base}_${category}` in node) && !(category === 'one' && base in node),
        )
        if (absent.length) problems.push(`${language}/${file} ${path}${base} lacks _${absent.join(', _')}`)
      }
    }
    for (const file of namespaceFiles) {
      walk(JSON.parse(readFileSync(join(LOCALES_DIR, language, file), 'utf-8')), '', file)
    }

    expect(reached.size, 'Intl.PluralRules reported no categories — the gate broke').toBeGreaterThan(1)
    expect(problems, problems.join('\n')).toEqual([])
  })
})
