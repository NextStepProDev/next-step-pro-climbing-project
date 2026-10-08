import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

/**
 * Every literal `settlements.*` key used in the code exists in the Polish dictionary.
 *
 * The parity gate compares the languages with EACH OTHER, so a key removed from all three at once
 * passes it, and component tests mock `t` to echo the key, so they pass too. The screen then shows
 * the raw key path. That happened once: the bulk-payout status replaced `payouts.awaiting` in the
 * month rows and the key was deleted, while the payer's yearly table still read it.
 *
 * Scoped to `settlements.` because that family lives in exactly one namespace (`admin`), so a
 * literal key can be resolved without knowing which namespace its `useTranslation` picked.
 * Template-literal keys (`status.${x}`) are skipped; they cannot be resolved statically.
 */

const SRC = join(__dirname, '..')
const PLURAL_SUFFIX = /_(zero|one|two|few|many|other)$/
const KEY = /['"](settlements\.[A-Za-z0-9_.]+)['"]/g

function leaves(value: unknown, prefix = ''): string[] {
  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) =>
      leaves(child, prefix ? `${prefix}.${key}` : key))
  }
  return [prefix.replace(PLURAL_SUFFIX, '')]
}

function sources(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return name === '__architecture__' ? [] : sources(path)
    return /\.tsx?$/.test(name) && !/\.test\.tsx?$/.test(name) ? [path] : []
  })
}

describe('settlement i18n keys', () => {
  it('resolves every literal settlements.* key against the Polish dictionary', () => {
    const known = new Set(leaves(JSON.parse(readFileSync(join(SRC, 'locales', 'pl', 'admin.json'), 'utf-8'))))
    const missing: string[] = []
    let seen = 0
    for (const file of sources(SRC)) {
      for (const match of readFileSync(file, 'utf-8').matchAll(KEY)) {
        seen++
        if (!known.has(match[1])) missing.push(`${relative(SRC, file)}: ${match[1]}`)
      }
    }
    // A broken regex finds nothing and the gate goes green guarding nothing.
    expect(seen).toBeGreaterThan(50)
    expect(missing).toEqual([])
  })
})
