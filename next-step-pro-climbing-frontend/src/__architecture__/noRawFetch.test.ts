import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'

/**
 * `fetchApi` is the only place allowed to call `fetch` directly.
 *
 * Nine multipart uploads used to hand-roll their own `fetch`, and so silently opted out of
 * everything fetchApi provides: the 401 refresh (an upload attempted after the access token
 * expired just failed), the 5xx retry that carries requests across a redeploy, the 30 s timeout,
 * and shared error normalisation. Nothing about a raw `fetch` looks wrong at the call site, which
 * is why the duplication survived several reviews — hence this gate rather than vigilance.
 */

const API_DIR = join(__dirname, '..', 'api')

function apiSourceFiles(): string[] {
  return readdirSync(API_DIR)
    .filter((name) => name.endsWith('.ts') && !name.endsWith('.test.ts'))
    .map((name) => join(API_DIR, name))
}

/** `fetch(` occurrences, ignoring comments so commentary about fetch never trips the gate. */
function countFetchCalls(source: string): number {
  const withoutComments = source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')
  return (withoutComments.match(/(?<![.\w])fetch\s*\(/g) ?? []).length
}

describe('api layer', () => {
  it('routes every request through fetchApi', () => {
    const offenders: string[] = []

    for (const file of apiSourceFiles()) {
      const source = readFileSync(file, 'utf-8')
      const calls = countFetchCalls(source)
      const name = file.split('/').pop()!

      // client.ts owns the single real fetch inside fetchApi; auth.ts owns one for the refresh
      // call, which cannot go through fetchApi without recursing through the refresh itself.
      const allowance = name === 'client.ts' || name === 'auth.ts' ? 1 : 0

      if (calls > allowance) {
        offenders.push(`${name}: ${calls} raw fetch() call(s), expected at most ${allowance}`)
      }
    }

    expect(
      offenders,
      `Raw fetch() bypasses fetchApi and therefore loses 401 refresh, 5xx retry, the 30 s timeout ` +
        `and error normalisation. Use fetchApi, or uploadApi for multipart bodies.\n${offenders.join('\n')}`,
    ).toEqual([])
  })

  it('keeps a fetchApi-based helper for multipart uploads', () => {
    // Uploads need FormData left untouched so the browser sets its own boundary; uploadApi is how
    // that happens without reintroducing a bespoke fetch per call site.
    const client = readFileSync(join(API_DIR, 'client.ts'), 'utf-8')

    expect(client).toContain('async function uploadApi')
    expect(client).toMatch(/uploadApi[\s\S]{0,400}fetchApi</)
  })
})
