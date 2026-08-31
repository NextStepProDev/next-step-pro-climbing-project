/**
 * Money formatting and parsing for the settlements feature.
 *
 * One currency, PLN, and no currency column in the database — a field that always holds the same
 * value is a box to fill in on every form and a breakdown nobody will ever use. If the business
 * ever bills in a second currency, this file is where that starts, not a scattered `zł` suffix.
 */

/** Mirrors `chk_settlements_amount_range` in V92 and the Bean Validation bounds on the request. */
export const MIN_AMOUNT = 0
export const MAX_AMOUNT = 100000
/** A bulk transfer covers a month of work, so it has its own, higher ceiling (V93). */
export const MAX_PAYOUT_AMOUNT = 1000000

/**
 * Renders an amount the way the viewer's language writes money.
 *
 * `Intl` rather than a hand-rolled `${n} zł`: Polish writes `150,00 zł` and English `PLN 150.00`,
 * and the separator is the half people misread when they are checking their own books.
 */
export function formatPln(amount: number, language: string): string {
  return new Intl.NumberFormat(language, {
    style: 'currency',
    currency: 'PLN',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount)
}

/**
 * Reads what an admin typed into an amount field.
 *
 * Accepts a comma as the decimal separator, because a Polish keyboard's numeric block has one and
 * the currency it types is written with one. Returns `null` for anything that is not a usable
 * amount — including blank, so the caller can tell "cleared the field" from "typed a zero", which
 * are different states: no row versus free of charge.
 */
export function parseAmount(input: string, max: number = MAX_AMOUNT): number | null {
  const normalized = input.replace(/\s/g, '').replace(',', '.')
  if (normalized === '') return null
  const value = Number(normalized)
  if (!Number.isFinite(value)) return null
  // The ceiling is a parameter because the two money models have different ones: reusing the
  // settlement cap for a transfer left the Save button disabled with nothing on screen saying why.
  if (value < MIN_AMOUNT || value > max) return null
  // Two decimals, matching NUMERIC(10,2): rounding here rather than letting the server do it
  // silently means the field shows what will actually be stored.
  return Math.round(value * 100) / 100
}
