import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { addDays, format } from 'date-fns'
import userEvent from '@testing-library/user-event'
import { WeightChart } from './WeightChart'
import type { WeightEntry } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const entries: WeightEntry[] = [
  { measuredOn: '2026-07-01', weightKg: 72.0, trendKg: 72.0 },
  { measuredOn: '2026-07-15', weightKg: 71.0, trendKg: 71.5 },
  { measuredOn: '2026-08-01', weightKg: 70.5, trendKg: 70.8 },
]

describe('WeightChart', () => {
  it('draws the daily readings as separate marks from the trend line', () => {
    const { container } = render(<WeightChart entries={entries} />)

    // One faint dot per reading, plus the single emphasised current-trend dot
    expect(container.querySelectorAll('circle')).toHaveLength(entries.length + 1)
    expect(container.querySelector('path')).toBeInTheDocument()
  })

  it('labels only the current trend, leaving the rest to the table', () => {
    render(<WeightChart entries={entries} />)

    expect(screen.getByText('70,8 kg')).toBeInTheDocument()
    expect(screen.queryByText('72,0 kg')).not.toBeInTheDocument()
  })

  it('exposes every value in a table so nothing is hover-only', async () => {
    render(<WeightChart entries={entries} />)

    await userEvent.click(screen.getByText('weight.showTable'))

    const table = screen.getByRole('table')
    expect(table).toBeInTheDocument()
    for (const entry of entries) {
      expect(table).toHaveTextContent(entry.weightKg.toString().replace('.', ','))
    }
  })

  it('names both marks so identity never rests on colour', () => {
    render(<WeightChart entries={entries} />)

    expect(screen.getByText('weight.legendTrend')).toBeInTheDocument()
    expect(screen.getByText('weight.legendDaily')).toBeInTheDocument()
  })

  it('spaces points by the calendar rather than by their order', () => {
    const { container } = render(<WeightChart entries={entries} />)
    const dots = [...container.querySelectorAll('circle')].slice(0, entries.length)
    const xs = dots.map((d) => Number(d.getAttribute('cx')))

    // 1 Jul -> 15 Jul is 14 days, 15 Jul -> 1 Aug is 17: the second gap must be wider
    expect(xs[2] - xs[1]).toBeGreaterThan(xs[1] - xs[0])
  })

  it('drops the daily dots once they would be denser than one per two pixels', () => {
    // 586px of plot / 2px = 293 marks; past that they stop being dots and become a smear
    const dense: WeightEntry[] = Array.from({ length: 350 }, (_, i) => ({
      measuredOn: format(addDays(new Date(2026, 0, 1), i), 'yyyy-MM-dd'),
      weightKg: 70 + (i % 2) * 0.4,
      trendKg: 70.2,
    }))

    const { container } = render(<WeightChart entries={dense} />)

    // Only the single emphasised current-trend dot survives
    expect(container.querySelectorAll('circle')).toHaveLength(1)
    expect(container.querySelector('path')).toBeInTheDocument()
  })

  it('stops naming the daily mark in the legend once it is not drawn', () => {
    const dense: WeightEntry[] = Array.from({ length: 350 }, (_, i) => ({
      measuredOn: format(addDays(new Date(2026, 0, 1), i), 'yyyy-MM-dd'),
      weightKg: 70,
      trendKg: 70,
    }))

    render(<WeightChart entries={dense} />)

    expect(screen.getByText('weight.legendTrend')).toBeInTheDocument()
    expect(screen.queryByText('weight.legendDaily')).not.toBeInTheDocument()
    expect(screen.getByText('weight.dailyHiddenHint')).toBeInTheDocument()
  })

  it('keeps the table complete even when the dots are hidden', () => {
    // The a11y path must not depend on the density decision
    const dense: WeightEntry[] = Array.from({ length: 350 }, (_, i) => ({
      measuredOn: format(addDays(new Date(2026, 0, 1), i), 'yyyy-MM-dd'),
      weightKg: 70,
      trendKg: 70,
    }))

    render(<WeightChart entries={dense} />)
    expect(screen.getByText('weight.showTable')).toBeInTheDocument()
  })

  it('renders nothing without readings', () => {
    const { container } = render(<WeightChart entries={[]} />)
    expect(container).toBeEmptyDOMElement()
  })
})
