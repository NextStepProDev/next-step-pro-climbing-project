import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AdminPayoutSourcePanel } from './AdminPayoutSourcePanel'
import type { PayoutSourceHistory } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const getSourceHistory = vi.fn()

vi.mock('../../api/client', () => ({
  adminSettlementsApi: {
    getSourceHistory: (...args: unknown[]) => getSourceHistory(...args),
    deletePayout: vi.fn(),
  },
}))

function history(overrides: Partial<PayoutSourceHistory> = {}): PayoutSourceHistory {
  return {
    id: 'src-1',
    name: 'Chwyciarnia',
    archived: false,
    firstActivity: '2026-03-01',
    lastActivity: '2026-05-01',
    months: 3,
    totalSessions: 3,
    totalMinutes: 270,
    sessionsWithoutHours: 0,
    totalAmount: 300,
    averageRatePerHour: 66.67,
    chart: [
      { month: '2026-03-01', amount: 300 },
      { month: '2026-04-01', amount: 0 },
      { month: '2026-05-01', amount: 0 },
    ],
    years: [{ year: 2026, sessions: 3, minutes: 270, sessionsWithoutHours: 0, amount: 300, ratePerHour: 66.67 }],
    periods: [{
      sourceId: 'src-1', sourceName: 'Chwyciarnia', month: '2026-05-01',
      sessions: 1, minutes: 90, sessionsWithoutHours: 0,
      amount: 0, ratePerHour: null, transfers: [],
      heldSessions: [
        { targetType: 'slot', targetId: 'slot-9', date: '2026-05-12', title: 'Grupa A', minutes: 90 },
      ],
    }],
    ...overrides,
  }
}

function renderPanel(path = '/admin/settlements/sources/src-1') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/admin/settlements/sources/:sourceId" element={<AdminPayoutSourcePanel />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AdminPayoutSourcePanel', () => {
  beforeEach(() => {
    getSourceHistory.mockReset().mockResolvedValue(history())
  })

  it('asks for the payer named in the address', async () => {
    renderPanel()

    expect(await screen.findByRole('heading', { name: 'Chwyciarnia' })).toBeInTheDocument()
    expect(getSourceHistory).toHaveBeenCalledWith('src-1')
  })

  it('draws a bar per month of the collaboration, empty ones included', async () => {
    renderPanel()

    // Three months, and the two with nothing in them are still drawn: a gap is a fact about the
    // collaboration, and closing it up would show a busier partner than the data has.
    const bars = await screen.findAllByRole('img')
    expect(bars).toHaveLength(3)
    expect(bars[1]).toHaveAccessibleName(expect.stringContaining('0,00'))
  })

  it('says which year a bar belongs to once the collaboration crosses one', async () => {
    getSourceHistory.mockResolvedValue(history({
      firstActivity: '2025-11-01',
      lastActivity: '2026-02-01',
      months: 4,
      chart: [
        { month: '2025-11-01', amount: 100 },
        { month: '2025-12-01', amount: 200 },
        { month: '2026-01-01', amount: 150 },
        { month: '2026-02-01', amount: 0 },
      ],
    }))

    renderPanel()

    // January carries its year, so a chart spanning several needs no legend — and two Januaries
    // side by side stop being indistinguishable.
    expect(await screen.findByText('sty 26')).toBeInTheDocument()
    expect(screen.getByText('lis')).toBeInTheDocument()
    // Four months of span, four bars, whatever the year boundary does to the labels.
    expect(screen.getAllByRole('img')).toHaveLength(4)
  })

  it('carries the year back to the tab it was opened from', async () => {
    renderPanel('/admin/settlements/sources/src-1?year=2025')

    expect(await screen.findByRole('link', { name: 'settlements.tab.title' }))
      .toHaveAttribute('href', '/admin/settlements?year=2025')
  })

  it('leaves the back link plain when no year was chosen', async () => {
    renderPanel()

    expect(await screen.findByRole('link', { name: 'settlements.tab.title' }))
      .toHaveAttribute('href', '/admin/settlements')
  })

  it('does not link the payer to the page it is already on', async () => {
    renderPanel()

    await screen.findByRole('heading', { name: 'Chwyciarnia' })
    // The month rows repeat the payer's name — the price of sharing one renderer with the tab, and
    // worth paying for it. What must not happen is that name being a link to the page it is on.
    expect(screen.queryByRole('link', { name: 'Chwyciarnia' })).not.toBeInTheDocument()
  })

  it('says a rate is missing rather than calling it zero', async () => {
    getSourceHistory.mockResolvedValue(history({
      totalAmount: 0,
      averageRatePerHour: null,
      years: [{ year: 2026, sessions: 3, minutes: 270, sessionsWithoutHours: 0, amount: 0, ratePerHour: null }],
    }))

    renderPanel()

    // A rate needs both halves; hours with no transfer yet is a gap, and a zero would be a claim.
    expect(await screen.findAllByText('—')).not.toHaveLength(0)
    expect(screen.getAllByText('settlements.tab.payouts.awaiting').length).toBeGreaterThan(0)
  })

  it('tells a payer with no history what to do instead of drawing empty axes', async () => {
    getSourceHistory.mockResolvedValue(history({
      firstActivity: null, lastActivity: null, months: 0,
      totalSessions: 0, totalMinutes: 0, totalAmount: 0, averageRatePerHour: null,
      chart: [], years: [], periods: [],
    }))

    renderPanel()

    expect(await screen.findByText('settlements.source.empty')).toBeInTheDocument()
    expect(screen.queryByText('settlements.source.chart')).not.toBeInTheDocument()
  })

  it('states that an archived payer is archived, next to the money they earned', async () => {
    getSourceHistory.mockResolvedValue(history({ archived: true }))

    renderPanel()

    expect(await screen.findByText('settlements.source.archived')).toBeInTheDocument()
  })
})
