import { describe, it, expect } from 'vitest'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

/**
 * Comments are written in English — everywhere in the frontend.
 *
 * Until 2026-11-19 nothing said so, and the code drifted: Polish comments sat in index.css,
 * the Navbar and the hero, next to English ones in the same files. A rule in CLAUDE.md alone is a
 * rule someone has to remember on every edit; this gate makes the drift fail CI instead.
 *
 * Detection is a heuristic on each comment line: quoted text ("…", „…", backticks) is removed
 * first, because English comments legitimately quote Polish UI strings. A line is flagged when
 * what remains has no common English word and either contains a Polish letter or at least two
 * common Polish words. The backend has the same gate (`CommentLanguageTest`), which also covers
 * the workflows and the hub.
 */
const ROOT = join(__dirname, '..', '..')
const SKIPPED_DIRS = new Set(['node_modules', 'dist', 'coverage', 'locales', 'public', 'test-results', 'playwright-report'])

const POLISH = new Set(
  ('w z na do i się że ze jest nie dla przy oraz po od jako ten tego już też lub albo bez przez gdzie kiedy ' +
    'tak tylko gdy więc bo ale czy jak żeby musi być można będzie który która które jeśli tutaj tym tej są brak').split(' '),
)
const ENGLISH = new Set('the an and or of in on at by for with from as is are was be it this that not than but when if has have'.split(' '))
const POLISH_LETTER = /[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]/
const QUOTED = /\{@code[^}]*\}|"[^"]*"|„[^”"]*[”"]|“[^”]*”|`[^`]*`|«[^»]*»/g

function looksPolish(text: string): boolean {
  const unquoted = text.replace(QUOTED, ' ')
  const words = unquoted.toLowerCase().match(/(?<![\p{L}\d_-])[a-ząćęłńóśźż]+(?![\p{L}\d_-])/gu) ?? []
  if (words.some((w) => ENGLISH.has(w))) return false
  return words.filter((w) => POLISH.has(w)).length >= 2 || POLISH_LETTER.test(unquoted)
}

type Syntax = 'c' | 'hash' | 'html'

function syntaxOf(name: string): Syntax | null {
  if (/\.(ts|tsx|js|mjs|cjs|jsx|css)$/.test(name)) return 'c'
  if (/\.(ya?ml|conf|sh)$/.test(name) || name.startsWith('Dockerfile')) return 'hash'
  if (name.endsWith('.html')) return 'html'
  return null
}

function comments(source: string, syntax: Syntax): Array<[number, string]> {
  const lines = source.split('\n')
  const out: Array<[number, string]> = []
  if (syntax === 'html') {
    for (const m of source.matchAll(/<!--([\s\S]*?)-->/g)) {
      const first = source.slice(0, m.index).split('\n').length
      m[1].split('\n').forEach((part, i) => out.push([first + i, part]))
    }
    return out
  }
  let inBlock = false
  lines.forEach((line, i) => {
    const n = i + 1
    if (syntax === 'hash') {
      const m = line.match(/(^\s*#|\s#(?=\s|$))(.*)$/)
      if (m) out.push([n, m[2]])
      return
    }
    if (inBlock) {
      const end = line.indexOf('*/')
      out.push([n, end < 0 ? line : line.slice(0, end)])
      inBlock = end < 0
      return
    }
    const open = line.match(/(^|[\s{(])\/\*/)
    if (open && open.index !== undefined) {
      const rest = line.slice(open.index + open[0].length)
      const end = rest.indexOf('*/')
      out.push([n, end < 0 ? rest : rest.slice(0, end)])
      inBlock = end < 0
      return
    }
    const lc = line.match(/(^|\s)\/\/(.*)$/)
    if (lc) out.push([n, lc[2]])
  })
  return out
}

function files(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    if (SKIPPED_DIRS.has(name) || name.startsWith('.')) return []
    const path = join(dir, name)
    return statSync(path).isDirectory() ? files(path) : [path]
  })
}

describe('comment language', () => {
  it('every comment in the frontend is in English', () => {
    const offenders: string[] = []
    for (const path of files(ROOT)) {
      const syntax = syntaxOf(path)
      if (!syntax) continue
      for (const [line, text] of comments(readFileSync(path, 'utf8'), syntax)) {
        if (looksPolish(text)) offenders.push(`${relative(ROOT, path)}:${line}: ${text.trim()}`)
      }
    }
    expect(offenders).toEqual([])
  })

  it('catches Polish and ignores quoted UI text', () => {
    expect(looksPolish(' Motto nad kalendarzem, wchodzi jako ostatnie w nagłówku')).toBe(true)
    expect(looksPolish(' Szerszy niż treść i to jest świadome')).toBe(true)
    expect(looksPolish(' The heading says "Co zabrać" on the page')).toBe(false)
    expect(looksPolish(' NFD handles ó/ą/ę, but not ł')).toBe(false)
  })
})
