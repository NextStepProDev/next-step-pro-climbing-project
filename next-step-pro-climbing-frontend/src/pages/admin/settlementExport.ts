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

/**
 * One spreadsheet row. A cell is a number only where the column is arithmetic — everything else is
 * text, and {@link exportSettlements} types the cells from that.
 */
export function toExportRows(
  rows: SettlementExportRow[],
  unpaid: string,
): (string | number)[][] {
  return rows.map((row) => [
    row.kind,
    // Left as text on purpose: ISO dates sort correctly as strings, and the payment column below
    // carries a word as well as dates, so neither can be a date cell.
    row.date,
    row.title ?? '',
    row.payer,
    // ⚠️ A real number, not `toFixed(2)`. Written as text, Excel keeps it text: SUM over the column
    // returns zero and sorting is alphabetical, so 1000.00 lands above 150.00 — in the one column
    // this file exists to be added up by. The separator worry that put a string here was about
    // writing a LOCALISED string ("150,00"); a numeric cell sidesteps it entirely, because Excel
    // renders the number in whatever locale the reader has.
    row.amount,
    // ⚠️ What arrived, beside what was charged. Since part payments exist the two differ routinely,
    // and the file used to carry only the charge — so 150 owed with 100 paid exported as "150" next
    // to a payment date and read as settled in full, in the document somebody reconciles with their
    // books. No third column for the remainder: two numeric columns let the spreadsheet subtract,
    // and a stored difference is one more figure that can disagree with the other two.
    row.paid,
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
  // The cell's type follows the value: a number becomes a numeric cell with two decimals, so the
  // reader's Excel renders it in their own locale and the column still sums.
  const body = toExportRows(rows, labels.unpaid).map((row) =>
    row.map((cell) =>
      typeof cell === 'number'
        ? { value: cell, type: Number, format: '#,##0.00' }
        : { value: cell, type: String },
    ),
  )

  await writeXlsxFile([summary, header, ...body], {
    columns: [
      { width: 18 }, { width: 12 }, { width: 32 }, { width: 26 },
      { width: 12 }, { width: 12 }, { width: 14 },
    ],
  }).toFile(exportFileName(request.year))
}
