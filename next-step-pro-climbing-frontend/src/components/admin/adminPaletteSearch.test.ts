import { describe, it, expect, afterEach } from 'vitest'
import { Clock } from 'lucide-react'
import { filterAdminTabs, foldForSearch, canOpenPalette, type PaletteEntry } from './adminPaletteSearch'
import type { AdminTab } from '../../pages/admin/adminTabs'

function entry(path: string, label: string, keywords: string): PaletteEntry {
  const tab: AdminTab = {
    path,
    labelKey: 'tabs.x',
    hintKey: 'hub.hints.x',
    keywordsKey: 'palette.keywords.x',
    icon: Clock,
  }
  return { tab, label, keywords }
}

const ENTRIES: PaletteEntry[] = [
  entry('/admin/slots', 'Terminy', 'sloty · dostępność · urlop'),
  entry('/admin/settlements', 'Rozliczenia', 'pieniądze · dług · abonament'),
  entry('/admin/assets', 'Biblioteka mediów', 'pliki · media · obrazki'),
  entry('/admin/site', 'Strona główna', 'hero · plakietki · lokalizacja'),
  entry('/admin/users', 'Użytkownicy', 'konta · role · zawodnik'),
]

function labels(results: PaletteEntry[]): string[] {
  return results.map((r) => r.label)
}

describe('foldForSearch', () => {
  it('strips Polish diacritics, including the one NFD cannot decompose', () => {
    // `ł` is a letter in its own right with no combining form, so NFD leaves it untouched — the
    // one accent that would silently escape a generic diacritic strip.
    expect(foldForSearch('Zgłoszenia')).toBe('zgloszenia')
    expect(foldForSearch('Użytkownicy')).toBe('uzytkownicy')
    expect(foldForSearch('Pieniądze')).toBe('pieniadze')
  })
})

describe('filterAdminTabs', () => {
  it('lists the whole panel when nothing has been typed', () => {
    // The palette doubles as a map for someone who cannot remember what a tab is called, so an
    // empty query must not read as "no results".
    expect(filterAdminTabs(ENTRIES, '')).toEqual(ENTRIES)
    expect(filterAdminTabs(ENTRIES, '   ')).toEqual(ENTRIES)
  })

  it('matches a prefix of the label', () => {
    expect(labels(filterAdminTabs(ENTRIES, 'roz'))).toEqual(['Rozliczenia'])
  })

  it('matches a later word of the label', () => {
    expect(labels(filterAdminTabs(ENTRIES, 'medi'))).toEqual(['Biblioteka mediów'])
  })

  it('matches a keyword the label does not contain', () => {
    // The whole reason keywords exist: nothing in "Strona główna" says "hero".
    expect(labels(filterAdminTabs(ENTRIES, 'hero'))).toEqual(['Strona główna'])
    expect(labels(filterAdminTabs(ENTRIES, 'abonament'))).toEqual(['Rozliczenia'])
  })

  it('finds a tab typed without Polish characters', () => {
    expect(labels(filterAdminTabs(ENTRIES, 'uzytkownicy'))).toEqual(['Użytkownicy'])
    expect(labels(filterAdminTabs(ENTRIES, 'dostepnosc'))).toEqual(['Terminy'])
  })

  it('puts a label match ahead of a keyword match', () => {
    // "Terminy" only carries "urlop" as a keyword; a label hit must not lose to it.
    const results = labels(filterAdminTabs([...ENTRIES, entry('/x', 'Urlopy', 'terminy')], 'urlop'))
    expect(results[0]).toBe('Urlopy')
  })

  it('keeps the panel order between equally good matches', () => {
    // A list that reshuffles between keystrokes makes the arrow keys unusable. Four labels
    // contain "i" and rank the same; they must come back in the order the panel lists them.
    expect(labels(filterAdminTabs(ENTRIES, 'i')).slice(0, 4)).toEqual([
      'Terminy',
      'Rozliczenia',
      'Biblioteka mediów',
      'Użytkownicy',
    ])
  })

  it('returns nothing when nothing matches', () => {
    expect(filterAdminTabs(ENTRIES, 'zzzz')).toEqual([])
  })
})

describe('canOpenPalette', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('refuses while a dialog is open', () => {
    // Otherwise ⌘K over an event form opens the palette behind the modal, and a single Escape
    // closes both — `Modal` listens on `document` without stopping the event.
    const dialog = document.createElement('div')
    dialog.setAttribute('role', 'dialog')
    document.body.appendChild(dialog)

    expect(canOpenPalette()).toBe(false)
  })

  it('allows it on a plain panel screen', () => {
    expect(canOpenPalette()).toBe(true)
  })
})
