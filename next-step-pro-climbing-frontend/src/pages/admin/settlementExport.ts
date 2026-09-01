import { todayInWarsaw } from '../../utils/calendarDate'
import type { SettlementExportRow } from '../../types'

/**
 * The file an accountant asks for in January.
 *
 * XLSX only, unlike the ascent log which also offers PDF: this is a spreadsheet somebody sorts,
 * filters and adds up, and a PDF of it would be a picture of numbers nobody can work with. Skipping
 * it also keeps jspdf out of this chunk entirely.
 *
 * Generated in the browser, like the logbook export, and imported dynamically by the panel so the
 * library only loads when somebody actually exports.
 */
export interface SettlementExportRequest {
  rows: SettlementExportRow[]
  year: number | null
  labels: {
    /** One line saying what this file is — a week later nobody remembers which year produced it. */
    summary: string
    columns: string[]
    unpaid: string
  }
}

/** ASCII slug — diacritics in a filename are a needless risk on a FAT-formatted stick. */
export function exportFileName(year: number | null): string {
  // Warsaw's today, not the device's — the same clock the rest of the app answers with.
  return `rozliczenia_${year === null ? 'wszystkie' : year}_${todayInWarsaw()}.xlsx`
}

export function toExportRows(rows: SettlementExportRow[], unpaid: string): string[][] {
  return rows.map((row) => [
    row.kind,
    row.date,
    row.title ?? '',
    row.payer,
    // A plain number as text: Excel's own locale decides the separator, and forcing one here is how
    // a Polish spreadsheet ends up reading 150,00 as a date.
    row.amount.toFixed(2),
    // Empty would read as missing data; the word says it is owed, which is a fact rather than a gap.
    row.settledOn ?? unpaid,
  ])
}

export async function exportSettlements(request: SettlementExportRequest): Promise<void> {
  // The /browser entry point: the package has no root export, and the node one would drag in fs
  // at build time.
  const { default: writeXlsxFile } = await import('write-excel-file/browser')

  const { labels, rows } = request
  const summary = [{ value: labels.summary, type: String }]
  const header = labels.columns.map((column) => ({
    value: column,
    type: String,
    fontWeight: 'bold' as const,
  }))
  const body = toExportRows(rows, labels.unpaid).map((row) =>
    row.map((cell) => ({ value: cell, type: String })),
  )

  await writeXlsxFile([summary, header, ...body], {
    columns: [{ width: 18 }, { width: 12 }, { width: 32 }, { width: 26 }, { width: 12 }, { width: 14 }],
  }).toFile(exportFileName(request.year))
}
