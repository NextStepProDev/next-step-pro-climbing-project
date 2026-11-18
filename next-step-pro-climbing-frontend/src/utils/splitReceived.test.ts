import { describe, expect, it } from 'vitest'
import { splitReceived } from './splitReceived'

const line = (key: string, heads = 1, alreadyReceived = 0) => ({ key, heads, alreadyReceived })

describe('splitReceived', () => {
  it('should split evenly per head, so a booking for two takes two shares', () => {
    expect(splitReceived(300, [line('ania'), line('bartek'), line('kasia', 2)])).toEqual([
      { key: 'ania', amount: 75, payment: 75 },
      { key: 'bartek', amount: 75, payment: 75 },
      { key: 'kasia', amount: 150, payment: 150 },
    ])
  })

  it('should hand the leftover cents to the first heads so the shares add up to the total', () => {
    const shares = splitReceived(100, [line('a'), line('b'), line('c')])!
    expect(shares.map((s) => s.amount)).toEqual([33.34, 33.33, 33.33])
    const sumCents = shares.reduce((sum, s) => sum + Math.round(s.amount * 100), 0)
    expect(sumCents).toBe(10000)
  })

  it('should never record again money already entered at this session', () => {
    expect(splitReceived(200, [line('a', 1, 100), line('b', 1, 40), line('c', 1, 150)])).toEqual([
      { key: 'a', amount: 66.67, payment: 0 },
      { key: 'b', amount: 66.67, payment: 26.67 },
      { key: 'c', amount: 66.66, payment: 0 },
    ])
  })

  it('should count a booking with no headcount as one person', () => {
    expect(splitReceived(50, [line('a', 0), line('b')])!.map((s) => s.amount)).toEqual([25, 25])
  })

  it('should refuse when there is nobody to split between or nothing to split', () => {
    expect(splitReceived(300, [])).toBeNull()
    expect(splitReceived(0, [line('a')])).toBeNull()
  })
})
