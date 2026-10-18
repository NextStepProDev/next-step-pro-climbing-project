import { describe, it, expect } from 'vitest'
import { weightProgress } from './weightProgress'

describe('weightProgress', () => {
  it('counts half the way down as half done', () => {
    expect(weightProgress(65, 70, 67.5)).toEqual({ percent: 50, remaining: 2.5 })
  })

  it('counts half the way up as half done', () => {
    expect(weightProgress(60, 56, 58)).toEqual({ percent: 50, remaining: 2 })
  })

  it('does not count moving AWAY from the target as progress', () => {
    // The bug: start 70, target 65, trend 72 showed a 40% bar.
    expect(weightProgress(65, 70, 72)).toEqual({ percent: 0, remaining: 7 })
    expect(weightProgress(60, 56, 54)).toEqual({ percent: 0, remaining: 6 })
  })

  it('stops at full, with nothing left, past the target', () => {
    expect(weightProgress(65, 70, 64)).toEqual({ percent: 100, remaining: 0 })
  })

  it('gives no bar when a number is missing or the goal has no length', () => {
    expect(weightProgress(null, 70, 68)).toBeNull()
    expect(weightProgress(65, null, 68)).toBeNull()
    expect(weightProgress(65, 70, null)).toBeNull()
    expect(weightProgress(70, 70, 68)).toBeNull()
  })
})
