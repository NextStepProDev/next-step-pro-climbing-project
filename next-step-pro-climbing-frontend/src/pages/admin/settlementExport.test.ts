import { describe, it, expect } from 'vitest'
import { exportFileName, toExportRows } from './settlementExport'
import type { SettlementExportRow } from '../../types'

function row(overrides: Partial<SettlementExportRow> = {}): SettlementExportRow {
  return {
    kind: 'Klient',
    date: '2026-08-14',
    title: 'Trening 1:1',
    payer: 'Anna Kowalska',
    amount: 150,
    paid: 150,
    settledOn: '2026-08-14',
    ...overrides,
  }
}

describe('settlementExport', () => {
  it('writes the amount as a number, so the column adds up and sorts', () => {
    // ⚠️ It used to be '149.50' — a string. Excel keeps a string a string: SUM over the column
    // returns zero and sorting is alphabetical, which puts 1000.00 above 150.00. This file exists
    // to be added up, so the one column it is added up by cannot be text.
    const amount = toExportRows([row({ amount: 149.5 })], 'nierozliczone')[0][4]
    expect(amount).toBe(149.5)
    expect(typeof amount).toBe('number')
  })

  it('carries what actually arrived beside what was charged', () => {
    // ⚠️ The file used to hold the charge alone. Since part payments exist, a row charged 150 with
    // 100 against it exported as "150" next to a payment date — which reads as settled in full, in
    // the one document somebody reconciles with their books. The same disagreement the user card
    // was fixed for, reappearing here.
    const row0 = toExportRows([row({ amount: 150, paid: 100 })], 'nierozliczone')[0]

    expect(row0[4]).toBe(150)
    expect(row0[5]).toBe(100)
    expect(typeof row0[5]).toBe('number')
  })

  it('keeps the payment date column text, because it also carries a word', () => {
    // Mixed content by design: a date or "outstanding". A column cannot be half date and half text,
    // and ISO dates sort correctly as strings anyway.
    const row0 = toExportRows([row()], 'nierozliczone')[0]
    expect(typeof row0[1]).toBe('string')
    expect(typeof row0[6]).toBe('string')
  })

  it('says outstanding rather than leaving the payment date blank', () => {
    // An empty cell reads as missing data; the word says it is owed, which is a fact, not a gap.
    expect(toExportRows([row({ settledOn: null })], 'nierozliczone')[0][6]).toBe('nierozliczone')
  })

  it('leaves a missing title empty rather than inventing one', () => {
    expect(toExportRows([row({ title: null })], 'nierozliczone')[0][2]).toBe('')
  })

  it('emits exactly as many cells as the panel passes headers', () => {
    // The header labels live in the panel and the cells live here, with nothing typed to hold them
    // in step: add a cell without a label and the file gets a column nobody can name. Seven is the
    // number the panel builds — kind, date, title, payer, charged, received, paid on.
    expect(toExportRows([row()], 'nierozliczone')[0]).toHaveLength(7)
  })

  it('names the file after the year and the day it was taken', () => {
    expect(exportFileName(2026)).toMatch(/^rozliczenia_2026_\d{4}-\d{2}-\d{2}\.xlsx$/)
    expect(exportFileName(null)).toMatch(/^rozliczenia_wszystkie_/)
  })
})
