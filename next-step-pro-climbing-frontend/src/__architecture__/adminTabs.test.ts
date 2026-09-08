import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { adminTabGroups, adminTabs } from '../pages/admin/adminTabs'

/**
 * The admin panel's tab list feeds three screens — the group menus, the hub tiles and the command
 * palette — but the routes that actually render those tabs live in `AdminPage.tsx`, and the labels
 * live in the locale files. Nothing connects them at compile time.
 *
 * A tab added to the list without a route is a menu entry that navigates to a blank page. A tab
 * without its `hub.hints.*` / `palette.keywords.*` entries renders the raw key path on a tile and
 * silently drops out of every keyword search. Both fail quietly; both fail here instead.
 */

const SRC = join(__dirname, '..')
const ADMIN_PAGE = readFileSync(join(SRC, 'pages', 'AdminPage.tsx'), 'utf-8')
const PL_ADMIN = JSON.parse(readFileSync(join(SRC, 'locales', 'pl', 'admin.json'), 'utf-8'))

function lookup(source: unknown, path: string): unknown {
  return path.split('.').reduce<unknown>(
    (node, key) =>
      node !== null && typeof node === 'object' ? (node as Record<string, unknown>)[key] : undefined,
    source,
  )
}

describe('admin tabs', () => {
  it('gives every tab a route in AdminPage', () => {
    const missing = adminTabs
      .map((tab) => tab.path.replace(/^\/admin\//, ''))
      .filter((relative) => !ADMIN_PAGE.includes(`path="${relative}"`))

    expect(missing, 'tabs whose path has no <Route> in AdminPage.tsx').toEqual([])
  })

  it('keeps the hub off the tab list', () => {
    // `/admin` is the landing screen and has no tab. A tab pointing there would be "active" on
    // every route in the panel, because `isTabActive` treats a path as covering its children.
    expect(adminTabs.map((tab) => tab.path)).not.toContain('/admin')
  })

  it('gives every tab a label, a hub hint and palette keywords', () => {
    const missing = adminTabs.flatMap((tab) =>
      [tab.labelKey, tab.hintKey, tab.keywordsKey].filter((key) => typeof lookup(PL_ADMIN, key) !== 'string'),
    )

    expect(missing, 'keys absent from locales/pl/admin.json').toEqual([])
  })

  it('names every group', () => {
    const missing = adminTabGroups
      .map((group) => group.groupKey)
      .filter((key) => typeof lookup(PL_ADMIN, key) !== 'string')

    expect(missing).toEqual([])
  })

  it('never lets one tab path be a prefix of another', () => {
    // `isTabActive` matches on a `/` boundary, so a genuine prefix pair would light two group
    // buttons at once. Adding `/admin/slots` next to `/admin` is exactly how that nearly happened.
    const paths = adminTabs.map((tab) => tab.path)
    const overlapping = paths.filter((path) =>
      paths.some((other) => other !== path && other.startsWith(path + '/')),
    )

    expect(overlapping).toEqual([])
  })
})
