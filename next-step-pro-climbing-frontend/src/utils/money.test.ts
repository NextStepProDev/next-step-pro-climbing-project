import { describe, it, expect } from 'vitest'
import {
  MAX_SUBSCRIPTION_BACKDATE_MONTHS,
  earliestSubscriptionStart,
  parseAmount,
} from './money'

/**
 * The arithmetic behind a date field's `min`, which is the brake on the one mistake in this feature
 * that writes dozens of rows: creating a subscription bills every month it already covers, so a slip
 * of the year produced 81 fee rows in a single request.
 *
 * It has to agree with the server's own check to the month. Being a month out in either direction
 * gives the worse of both: a field offering a date the save then refuses, or a field refusing one the
 * save would have taken.
 */
describe('earliestSubscriptionStart', () => {
  it('counts back whole months from the given day', () => {
    expect(earliestSubscriptionStart('2026-09-13')).toBe('2024-09-01')
  })

  it('crosses the year boundary without drifting', () => {
    // January is the case that catches an off-by-one in the month/year split.
    expect(earliestSubscriptionStart('2026-01-31')).toBe('2024-01-01')
    expect(earliestSubscriptionStart('2026-02-01')).toBe('2024-02-01')
  })

  it('always lands on the first of a month, because a fee is billed per month', () => {
    for (const day of ['2026-03-31', '2026-12-01', '2027-07-15']) {
      expect(earliestSubscriptionStart(day)).toMatch(/^\d{4}-\d{2}-01$/)
    }
  })

  it('stays in step with the constant it mirrors', () => {
    // Not a restatement of the implementation: this is what breaks the day somebody edits one of the
    // two numbers that have to agree with the server.
    expect(MAX_SUBSCRIPTION_BACKDATE_MONTHS).toBe(24)
    expect(earliestSubscriptionStart('2026-09-01')).toBe('2024-09-01')
  })
})

/**
 * `parseAmount` had no test at all while every amount in the feature went through it.
 */
describe('parseAmount', () => {
  it('accepts a comma, because that is what a Polish numeric keypad types', () => {
    expect(parseAmount('149,50')).toBe(149.5)
  })

  it('accepts an amount pasted with spaces in it, including a non-breaking one', () => {
    expect(parseAmount('100 000')).toBe(100000)
    expect(parseAmount('1 500,50')).toBe(1500.5)
  })

  it('tells a cleared field from a typed zero, which are different states', () => {
    // No row versus free of charge — the caller deletes on the first and saves on the second.
    expect(parseAmount('')).toBeNull()
    expect(parseAmount('0')).toBe(0)
  })

  it('refuses what is not an amount rather than guessing', () => {
    expect(parseAmount('1,000,50')).toBeNull()
    expect(parseAmount('150 zł')).toBeNull()
    expect(parseAmount('-5')).toBeNull()
    expect(parseAmount('abc')).toBeNull()
  })

  it('refuses anything over the ceiling it was given', () => {
    expect(parseAmount('100001')).toBeNull()
    // The transfer ceiling is higher, and passing the wrong one left Save enabled on an amount the
    // server refuses — or dead on one it would have taken.
    expect(parseAmount('100001', 1000000)).toBe(100001)
  })

  it('rounds to the two decimals the column stores', () => {
    expect(parseAmount('10,999')).toBe(11)
  })
})
