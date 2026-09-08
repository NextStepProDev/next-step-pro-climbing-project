import type { AdminTab } from '../../pages/admin/adminTabs'

/** One searchable row: the tab plus its already-translated label and keyword list. */
export interface PaletteEntry {
  tab: AdminTab
  label: string
  /** Synonyms the label does not contain ("hero", "cennik"), separated by `·`. */
  keywords: string
}

/**
 * Fold a string down to what a hurried admin actually types: lowercase, no accents.
 *
 * NFD decomposition handles ó/ą/ę/ś/ż/ź/ć/ń, but **not `ł`** — it is a distinct letter with no
 * combining form, so it survives the strip and "zgloszenia" would miss "zgłoszenia". It gets its
 * own replacement.
 */
export function foldForSearch(value: string): string {
  return value
    .toLowerCase()
    .replace(/ł/g, 'l')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
}

/**
 * Rank a single entry against a folded query. Lower is better; `null` means no match.
 *
 * The ordering exists so that typing "ka" puts "Kalendarze treningowe" above a tab that merely
 * lists "kalendarz" among its keywords — a prefix of the name is a much stronger signal of intent
 * than a synonym buried in a list.
 */
function rank(entry: PaletteEntry, query: string): number | null {
  const label = foldForSearch(entry.label)
  if (label.startsWith(query)) return 0
  // A later word of the label: "media library" should be reachable by typing "lib".
  if (label.split(/\s+/).some((word) => word.startsWith(query))) return 1
  if (label.includes(query)) return 2
  if (foldForSearch(entry.keywords).includes(query)) return 3
  return null
}

/**
 * Filter and order palette entries.
 *
 * An empty query returns **everything, in its original order** rather than nothing: this is a jump
 * list of eighteen destinations, so showing the whole panel is the point — the palette doubles as
 * a map for someone who cannot remember what a tab is called.
 */
export function filterAdminTabs(entries: PaletteEntry[], query: string): PaletteEntry[] {
  const folded = foldForSearch(query.trim())
  if (!folded) return entries

  return entries
    .map((entry, index) => ({ entry, index, score: rank(entry, folded) }))
    .filter((row): row is { entry: PaletteEntry; index: number; score: number } => row.score !== null)
    // `index` breaks ties so the result keeps the panel's own grouping order — a list that
    // reshuffles between keystrokes makes the arrow keys unusable.
    .sort((a, b) => a.score - b.score || a.index - b.index)
    .map((row) => row.entry)
}

/** Whether to advertise the shortcut as `⌘K` or `Ctrl K`. */
export function isMacLike(): boolean {
  return typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.userAgent)
}

/**
 * Should a global ⌘K open the palette right now?
 *
 * No, while any dialog is open. `Modal` and `ConfirmModal` both register Escape on `document`
 * without stopping it, so a palette opened underneath an event form would leave one Escape
 * closing both — and the palette would be unreachable behind the modal's backdrop anyway.
 */
export function canOpenPalette(doc: Document = document): boolean {
  return doc.querySelector('[role="dialog"]') === null
}

export type { AdminTab }
