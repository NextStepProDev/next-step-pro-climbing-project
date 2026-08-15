import type { Ascent, AscentDiscipline, AscentStyle } from '../../types'

/**
 * Filtering and sorting of an already-loaded year of the logbook.
 *
 * The year is the server's job — it bounds how much travels over the wire. Everything here is a
 * slice of what already arrived: a few hundred rows, so a `useMemo` beats a request per keystroke
 * against a 40/min bucket.
 *
 * Pure on purpose — this is where the "sort by difficulty uses the rank, not the label" rule
 * lives, and it is the one rule a screenshot cannot catch.
 */

export interface AscentFilterState {
  discipline: AscentDiscipline | 'all'
  style: AscentStyle | 'all'
  area: string | 'all'
  search: string
}

export type AscentSortKey = 'date' | 'grade' | 'route' | 'area' | 'stars'
export type SortDirection = 'asc' | 'desc'

export const EMPTY_FILTERS: AscentFilterState = {
  discipline: 'all',
  style: 'all',
  area: 'all',
  search: '',
}

export function hasActiveFilters(filters: AscentFilterState): boolean {
  return filters.discipline !== 'all'
    || filters.style !== 'all'
    || filters.area !== 'all'
    || filters.search.trim() !== ''
}

export function filterAscents(entries: Ascent[], filters: AscentFilterState): Ascent[] {
  const needle = filters.search.trim().toLowerCase()

  return entries.filter(entry => {
    if (filters.discipline !== 'all' && entry.discipline !== filters.discipline) return false
    if (filters.style !== 'all' && entry.style !== filters.style) return false
    if (filters.area !== 'all' && entry.area !== filters.area) return false
    if (!needle) return true
    // The route is what anybody searches for; the crag is what they fall back to when the
    // route name has slipped their mind
    return entry.routeName.toLowerCase().includes(needle)
      || entry.crag.toLowerCase().includes(needle)
  })
}

export function sortAscents(entries: Ascent[], key: AscentSortKey, direction: SortDirection): Ascent[] {
  const factor = direction === 'asc' ? 1 : -1

  return [...entries].sort((a, b) => {
    const result = compare(a, b, key)
    // Date breaks every other tie, and follows the chosen direction with it: sorting by grade
    // descending reads newest-first inside one grade, ascending reads oldest-first. Ties on the
    // date itself fall through to the server's order (climbed_on DESC, created_at DESC), so a
    // single day reads most-recently-logged first.
    return (result !== 0 ? result : a.climbedOn.localeCompare(b.climbedOn)) * factor
  })
}

function compare(a: Ascent, b: Ascent, key: AscentSortKey): number {
  switch (key) {
    case 'date':
      return a.climbedOn.localeCompare(b.climbedOn)
    case 'grade':
      // gradeRank, never gradeLabel: "10a" would sort before "9c" as text, and the two scales
      // interleave alphabetically (6A, 6a, 6B, 6b) into an order that means nothing
      return a.gradeRank - b.gradeRank
    case 'route':
      return a.routeName.localeCompare(b.routeName, 'pl', { sensitivity: 'base' })
    case 'area':
      return a.area.localeCompare(b.area, 'pl', { sensitivity: 'base' })
    case 'stars':
      // Unrated sorts as the lowest — an unrated route is not a bad route, but it is the only
      // consistent place to put it
      return (a.qualityStars ?? -1) - (b.qualityStars ?? -1)
  }
}

/** Areas present in the loaded slice, for the filter dropdown. Alphabetical, deduplicated. */
export function areasIn(entries: Ascent[]): string[] {
  return [...new Set(entries.map(entry => entry.area))]
    .sort((a, b) => a.localeCompare(b, 'pl', { sensitivity: 'base' }))
}
