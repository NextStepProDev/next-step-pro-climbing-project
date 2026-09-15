import { describe, it, expect } from 'vitest'
import { travellingPayoutSource } from './slotClipboard'
import type { SettlementSection } from '../types'

const section = (coveredBy: SettlementSection['coveredBy']): SettlementSection => ({
  targetDate: '2026-09-15',
  lines: [],
  coveredBy,
})

describe('travellingPayoutSource — what a copied slot may take with it', () => {
  it('carries the institution that settles the session', () => {
    // The case the whole thing exists for: a school session repeats weekly, and a copy without
    // its contractor rebuilds the invisible session once a week.
    expect(travellingPayoutSource(section({ kind: 'source', id: 'source-1', name: 'SP nr 5' })))
      .toEqual({ id: 'source-1', name: 'SP nr 5' })
  })

  it('leaves a retainer behind, because it covers a person and not the hour', () => {
    // Nobody is on the slot about to be created, so the server refuses this — carrying it would
    // turn every paste of such a session into an error about a copy that was fine.
    expect(travellingPayoutSource(section({ kind: 'subscription', id: 'user-1', name: 'Anna' })))
      .toBeNull()
  })

  it('carries nothing for an ordinary session', () => {
    expect(travellingPayoutSource(section(null))).toBeNull()
  })

  it('carries nothing when the read failed, rather than refusing to copy', () => {
    // Copying must keep working when a money read does not come back; the session then lands in
    // the "no payer" queue, which is the backstop for exactly this.
    expect(travellingPayoutSource(null)).toBeNull()
  })
})
