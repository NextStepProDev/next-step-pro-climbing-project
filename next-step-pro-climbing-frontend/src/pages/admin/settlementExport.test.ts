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
    settledOn: '2026-08-14',
    ...overrides,
  }
}

describe('settlementExport', () => {
  it('writes the amount as a plain number so Excel applies its own locale', () => {
    expect(toExportRows([row({ amount: 149.5 })], 'nierozliczone')[0][4]).toBe('149.50')
  })

  it('says outstanding rather than leaving the payment date blank', () => {
    // An empty cell reads as missing data; the word says it is owed, which is a fact, not a gap.
    expect(toExportRows([row({ settledOn: null })], 'nierozliczone')[0][5]).toBe('nierozliczone')
  })

  it('leaves a missing title empty rather than inventing one', () => {
    expect(toExportRows([row({ title: null })], 'nierozliczone')[0][2]).toBe('')
  })

  it('names the file after the year and the day it was taken', () => {
    expect(exportFileName(2026)).toMatch(/^rozliczenia_2026_\d{4}-\d{2}-\d{2}\.xlsx$/)
    expect(exportFileName(null)).toMatch(/^rozliczenia_wszystkie_/)
  })
})
