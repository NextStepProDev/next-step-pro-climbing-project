import { todayInWarsaw } from '../../utils/calendarDate'
import type { PaymentExportRow, SettlementExportRow } from '../../types'

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
  /**
   * The cash sheet: every payment exactly as handed over. The charges sheet says what is covered,
   * which is DERIVED (oldest debt first); this one is the record an accountant reconciles against
   * the bank and the till.
   */
  payments: PaymentExportRow[]
  year: number | null
  labels: {
    /** One line saying what this file is — a week later nobody remembers which year produced it. */
    summary: string
    columns: string[]
    unpaid: string
    chargesSheet: string
    paymentsSheet: string
    paymentColumns: string[]
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
    // ⚠️ How much of it is covered, beside what was charged. The two differ routinely, and a file
    // carrying only the charge next to a date read as settled in full. No third column for the
    // remainder: two numeric columns let the spreadsheet subtract, and a stored difference is one
    // more figure that can disagree with the other two.
    row.covered,
    // Empty would read as missing data; the word says it is owed, which is a fact rather than a gap.
    row.paidOn ?? unpaid,
  ])
}

/** One row of the cash sheet — a payment as handed over. */
export function toPaymentRows(payments: PaymentExportRow[]): (string | number)[][] {
  return payments.map((payment) => [
    payment.receivedOn,
    payment.payer,
    payment.amount,
    payment.enteredAt ?? '',
  ])
}

/** The cell's type follows the value, so amounts stay numeric and the column still sums. */
function toCells(rows: (string | number)[][]) {
  return rows.map((row) =>
    row.map((cell) =>
      typeof cell === 'number'
        ? { value: cell, type: Number, format: '#,##0.00' }
        : { value: cell, type: String },
    ),
  )
}

export async function exportSettlements(request: SettlementExportRequest): Promise<void> {
  // The /browser entry point: the package has no root export, and the node one would drag in fs
  // at build time.
  const { default: writeXlsxFile } = await import('write-excel-file/browser')

  const { labels, rows, payments } = request
  const summary = [{ value: labels.summary, type: String }]
  const headerOf = (columns: string[]) => columns.map((column) => ({
    value: column,
    type: String,
    fontWeight: 'bold' as const,
  }))

  await writeXlsxFile([
    {
      data: [summary, headerOf(labels.columns), ...toCells(toExportRows(rows, labels.unpaid))],
      sheet: labels.chargesSheet,
      columns: [
        { width: 18 }, { width: 12 }, { width: 32 }, { width: 26 },
        { width: 12 }, { width: 12 }, { width: 14 },
      ],
    },
    {
      data: [summary, headerOf(labels.paymentColumns), ...toCells(toPaymentRows(payments))],
      sheet: labels.paymentsSheet,
      columns: [{ width: 12 }, { width: 26 }, { width: 12 }, { width: 32 }],
    },
  ]).toFile(exportFileName(request.year))
}
