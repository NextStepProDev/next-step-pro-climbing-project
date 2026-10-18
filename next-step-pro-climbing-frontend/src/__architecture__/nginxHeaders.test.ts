import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

/**
 * nginx's `add_header` inheritance, which reads as the opposite of what it does: a location with ANY
 * `add_header` of its own drops EVERY `add_header` inherited from the server block. `/assets/` added
 * one line for caching and silently shipped all JS and CSS without `nosniff` and without the CSP
 * (found by the 2026-10-18 audit). Nothing looks wrong in the file, so the rule is asserted here.
 *
 * A location that wants caching without that trap uses `expires` (which sets Cache-Control without
 * add_header) — see `location /`.
 */
const NGINX = join(__dirname, '..', '..', 'nginx.conf')

const REQUIRED = ['X-Content-Type-Options', 'Content-Security-Policy', 'Strict-Transport-Security']

function locations(source: string): { name: string; body: string }[] {
  const found: { name: string; body: string }[] = []
  const pattern = /location\s+([^{]+)\{/g
  let match: RegExpExecArray | null
  while ((match = pattern.exec(source)) !== null) {
    let depth = 1
    let i = pattern.lastIndex
    while (i < source.length && depth > 0) {
      if (source[i] === '{') depth++
      if (source[i] === '}') depth--
      i++
    }
    found.push({ name: match[1].trim(), body: source.slice(pattern.lastIndex, i - 1) })
  }
  return found
}

describe('nginx security headers', () => {
  const source = readFileSync(NGINX, 'utf8').replace(/^\s*#.*$/gm, '')

  it('finds the locations it guards', () => {
    expect(locations(source).some((l) => l.name === '/assets/')).toBe(true)
  })

  it('repeats every security header in a location that adds a header of its own', () => {
    const offenders = locations(source)
      .filter((l) => /\badd_header\b/.test(l.body))
      .flatMap((l) => REQUIRED.filter((h) => !l.body.includes(h)).map((h) => `${l.name}: missing ${h}`))
    expect(offenders).toEqual([])
  })

  it('never passes a client-chosen X-Forwarded-Host to the backend', () => {
    const proxied = locations(source).filter((l) => /\bproxy_pass\b/.test(l.body) && /proxy_set_header\s+Host/.test(l.body))
    const offenders = proxied.filter((l) => !/proxy_set_header\s+X-Forwarded-Host\s+\$host;/.test(l.body)).map((l) => l.name)
    expect(offenders).toEqual([])
  })
})
