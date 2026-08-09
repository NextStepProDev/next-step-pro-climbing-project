import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

/**
 * Every invalidated query key root must be a key something actually queries.
 *
 * React Query matches invalidations by prefix and says nothing when a key matches no query, so a
 * stale or misspelled root is a silent no-op: the mutation "succeeds", the screen keeps showing
 * old data, and nothing anywhere reports a problem. This gate found three such dead invalidations
 * in AdminSitePanel (`heroImage`, `badgeImage`, `badgeLeftImage`), left behind when the public
 * home settings were consolidated into a single `/settings/home` query.
 */

const SRC = join(__dirname, '..')

/** `queryKey: ['root'...`, tagged with whether it sits inside an invalidateQueries call. */
const QUERY_KEY = /(invalidateQueries\(\s*\{\s*)?queryKey:\s*\[\s*['"]([A-Za-z0-9_]+)['"]/g

function sourceFiles(dir: string): string[] {
  const out: string[] = []
  for (const entry of readdirSync(dir)) {
    if (entry === 'node_modules' || entry === '__architecture__') continue
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      out.push(...sourceFiles(full))
    } else if (/\.tsx?$/.test(entry) && !/\.test\.tsx?$/.test(entry)) {
      out.push(full)
    }
  }
  return out
}

function collectRoots() {
  const queried = new Map<string, string[]>()
  const invalidated = new Map<string, string[]>()

  for (const file of sourceFiles(SRC)) {
    const source = readFileSync(file, 'utf-8')
    const relative = file.slice(SRC.length + 1)
    for (const match of source.matchAll(QUERY_KEY)) {
      const target = match[1] ? invalidated : queried
      const root = match[2]
      target.set(root, [...(target.get(root) ?? []), relative])
    }
  }
  return { queried, invalidated }
}

describe('react-query keys', () => {
  it('never invalidates a key root that nothing queries', () => {
    const { queried, invalidated } = collectRoots()

    expect(queried.size, 'Parsed no query keys at all — the gate broke').toBeGreaterThan(5)

    const dead = [...invalidated.entries()]
      .filter(([root]) => !queried.has(root))
      .map(([root, files]) => `'${root}' invalidated in ${[...new Set(files)].join(', ')} but never queried`)

    expect(
      dead,
      `Dead invalidations do nothing and give false confidence that a screen refreshes:\n${dead.join('\n')}`,
    ).toEqual([])
  })
})
