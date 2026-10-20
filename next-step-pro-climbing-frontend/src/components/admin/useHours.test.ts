import { describe, it, expect, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useHours } from './useMoney'

let language = 'pl'
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (k: string) => k, i18n: { language } }),
}))

describe('useHours — worked hours in the payout screens', () => {
  it('writes a Polish decimal comma, not a dot', () => {
    language = 'pl'
    const { result } = renderHook(() => useHours())
    expect(result.current(750)).toBe('12,5')
  })

  it('keeps the dot for English and one decimal for whole hours', () => {
    language = 'en'
    const { result } = renderHook(() => useHours())
    expect(result.current(750)).toBe('12.5')
    expect(result.current(120)).toBe('2.0')
  })
})
