import fontDataUri from '../../assets/fonts/NotoSans-Regular.ttf?inline'
import { todayInWarsaw } from '../../utils/calendarDate'
import type { AscentFilterState } from './ascentFiltering'
import type { Ascent, AscentDiscipline, AscentStyle, AscentTerrain } from '../../types'

/**
 * Excel and PDF export of the logbook.
 *
 * Generated in the browser, not on the server: the rows are already here, and the production box
 * is a single ARM core that has better things to do than lay out a PDF.
 *
 * This module is imported dynamically by the section, and the libraries are imported dynamically
 * from inside it — two levels, because the embedded font lives HERE. Without the outer level it
 * would ride along in the chunk that loads every time somebody opens the tab.
 */

export interface AscentExportRequest {
  /** Exactly what the table shows, in the order it shows it. Anything else is a lie. */
  entries: Ascent[]
  terrain: AscentTerrain
  year: number | null
  filters: AscentFilterState
  /** Set when the coach exports somebody else's logbook, so the filename says whose. */
  athleteName?: string
  labels: {
    title: string
    /** One line describing the filter — a file a week later does not know what it is. */
    summary: string
    columns: string[]
    disciplines: Record<AscentDiscipline, string>
    styles: Record<AscentStyle, string>
    seasons: { summer: string; winter: string }
  }
}

export async function exportAscents(kind: 'xlsx' | 'pdf', request: AscentExportRequest): Promise<void> {
  if (kind === 'xlsx') return exportXlsx(request)
  return exportPdf(request)
}

/** ASCII slug — diacritics in a filename are a needless risk on a FAT-formatted stick. */
export function slugify(value: string): string {
  return value
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .replace(/ł/g, 'l')
    .replace(/Ł/g, 'L')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

export function exportFileName(extension: string, year: number | null, athleteName?: string,
                               request?: { terrain?: AscentTerrain }): string {
  const parts = [request?.terrain === 'MOUNTAIN' ? 'przejscia-gorskie' : 'przejscia']
  if (athleteName) parts.push(slugify(athleteName))
  parts.push(year === null ? 'wszystkie' : String(year))
  // Warsaw's today, not the device's — the same clock the rest of the calendar answers with
  parts.push(todayInWarsaw())
  return `${parts.join('_')}.${extension}`
}

export function toExportRows(entries: Ascent[],
                             disciplines: Record<AscentDiscipline, string>,
                             seasons?: { summer: string; winter: string },
                             styles?: Record<AscentStyle, string>): string[][] {
  return entries.map(entry => entry.terrain === 'MOUNTAIN'
    ? [
      entry.climbedOn,
      entry.winter ? (seasons?.winter ?? 'winter') : (seasons?.summer ?? 'summer'),
      entry.area,
      entry.crag,
      entry.routeName,
      entry.gradeLabel,
      entry.originalGrade ?? '',
      styles?.[entry.style] ?? entry.style,
      entry.lengthMeters?.toString() ?? '',
      entry.pitches?.toString() ?? '',
      entry.durationMinutes != null ? String(Math.round(entry.durationMinutes / 6) / 10) : '',
      entry.ledGradeLabel ?? '',
      entry.ledPitches?.toString() ?? '',
      entry.partners ?? '',
      entry.comment ?? '',
    ]
    : [
      entry.climbedOn,
      entry.discipline ? disciplines[entry.discipline] : '',
      entry.area,
      entry.crag,
      entry.routeName,
      entry.gradeLabel,
      styles?.[entry.style] ?? entry.style,
      entry.attempts?.toString() ?? '',
      entry.qualityStars?.toString() ?? '',
      entry.comment ?? '',
    ])
}

async function exportXlsx(request: AscentExportRequest): Promise<void> {
  // The /browser entry point: the package has no root export, and the node one would drag in
  // fs at build time
  const { default: writeXlsxFile } = await import('write-excel-file/browser')

  const { labels, entries } = request
  // First row says what the file is — a week later nobody remembers which filter produced it
  const summary = [{ value: labels.summary, type: String }]
  const header = labels.columns.map(column => ({ value: column, type: String, fontWeight: 'bold' as const }))
  const rows = toExportRows(entries, labels.disciplines, labels.seasons, labels.styles)
    .map(row => row.map(cell => ({ value: cell, type: String })))

  await writeXlsxFile([summary, header, ...rows], {
    // Comment last and widest; the rest sized to what they hold
    columns: [
      { width: 12 }, { width: 12 }, { width: 20 }, { width: 20 }, { width: 28 },
      { width: 10 }, { width: 8 }, { width: 9 }, { width: 9 }, { width: 50 },
    ],
  }).toFile(exportFileName('xlsx', request.year, request.athleteName, request))
}

async function exportPdf(request: AscentExportRequest): Promise<void> {
  const [{ jsPDF }, { default: autoTable }] = await Promise.all([
    import('jspdf'),
    import('jspdf-autotable'),
  ])

  const { labels, entries } = request
  // Landscape: ten columns do not fit across a portrait A4 without turning the route names
  // into two-character stumps
  const doc = new jsPDF({ orientation: 'landscape', unit: 'pt', format: 'a4' })

  // The stock PDF fonts have no ą/ę/ł, so the face is embedded. It has to be named in EVERY
  // style block below — set only in `styles`, some cells fall back to Helvetica and the Polish
  // characters vanish selectively, which reads as a corrupt file rather than a missing setting.
  //
  // Only the Regular weight is embedded (a second file would be ~160 kB in the export chunk),
  // and that makes `fontStyle` load-bearing too: autoTable asks for BOLD headers by default,
  // jsPDF finds no bold NotoSans and silently serves Times-Bold, so "Skała" rendered as
  // "S k a B a" in the header row while every data cell was fine. Hence fontStyle: 'normal'
  // in headStyles, with the header set apart by its fill rather than by its weight.
  const base64 = fontDataUri.slice(fontDataUri.indexOf(',') + 1)
  doc.addFileToVFS('NotoSans-Regular.ttf', base64)
  doc.addFont('NotoSans-Regular.ttf', 'NotoSans', 'normal')
  doc.setFont('NotoSans')

  doc.setFontSize(14)
  doc.text(labels.title, 40, 40)
  doc.setFontSize(9)
  doc.setTextColor(120)
  doc.text(labels.summary, 40, 56)
  doc.setTextColor(0)

  autoTable(doc, {
    startY: 72,
    head: [labels.columns],
    body: toExportRows(entries, labels.disciplines, labels.seasons, labels.styles),
    styles: { font: 'NotoSans', fontStyle: 'normal', fontSize: 8, cellPadding: 4, overflow: 'linebreak' },
    headStyles: { font: 'NotoSans', fontStyle: 'normal', fillColor: [38, 44, 54], textColor: 255 },
    bodyStyles: { font: 'NotoSans', fontStyle: 'normal' },
    columnStyles: {
      0: { cellWidth: 55 },
      9: { cellWidth: 180 },
    },
  })

  doc.save(exportFileName('pdf', request.year, request.athleteName, request))
}
