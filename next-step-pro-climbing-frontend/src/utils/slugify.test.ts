import { describe, it, expect } from 'vitest'
import { slugify } from './slugify'

describe('slugify', () => {
  it('strips Polish diacritics, including the stroked l', () => {
    expect(slugify('Jan Kowalski')).toBe('jan-kowalski')
    expect(slugify('Michał Wójcik')).toBe('michal-wojcik')
    expect(slugify('Łukasz Ćwik')).toBe('lukasz-cwik')
  })

  it('collapses punctuation and trims separators', () => {
    expect(slugify('  Anna   Wspinaczka!  ')).toBe('anna-wspinaczka')
  })
})
