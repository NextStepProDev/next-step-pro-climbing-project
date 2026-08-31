import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AdminSettlementsPanel } from './AdminSettlementsPanel'
import type { SettlementOverview } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const getOverview = vi.fn()
const save = vi.fn()
const createPayout = vi.fn()
const createSource = vi.fn()
const setSourceArchived = vi.fn()

vi.mock('../../api/client', () => ({
  adminSettlementsApi: {
    getOverview: (...args: unknown[]) => getOverview(...args),
    save: (...args: unknown[]) => save(...args),
    createPayout: (...args: unknown[]) => createPayout(...args),
    createSource: (...args: unknown[]) => createSource(...args),
    setSourceArchived: (...args: unknown[]) => setSourceArchived(...args),
  },
}))

function makeOverview(overrides: Partial<SettlementOverview> = {}): SettlementOverview {
  return {
    years: [2026, 2025],
    year: 2026,
    unpriced: { count: 0, windowDays: 90, sessions: [] },
    outstanding: { total: 0, count: 0, oldest: null, items: [] },
    revenue: {
      total: 0,
      monthlyAverage: null,
      months: Array.from({ length: 12 }, (_, i) => ({
        month: `2026-${String(i + 1).padStart(2, '0')}-01`,
        amount: 0,
      })),
      fromSlots: 0,
      fromEvents: 0,
      fromPayouts: 0,
    },
    people: [],
    payouts: { sources: [], total: 0, periods: [] },
    ...overrides,
  }
}

function renderPanel(initialPath = '/admin/settlements') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[initialPath]}>
        <AdminSettlementsPanel />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AdminSettlementsPanel', () => {
  beforeEach(() => {
    getOverview.mockReset()
    save.mockReset().mockResolvedValue(undefined)
    createPayout.mockReset().mockResolvedValue('payout-1')
    createSource.mockReset().mockResolvedValue({ id: 'src-2', name: 'Klub XYZ', archived: false })
    setSourceArchived.mockReset().mockResolvedValue(undefined)
  })

  it('asks for no particular year, so the server can pick the newest one holding data', async () => {
    getOverview.mockResolvedValue(makeOverview())

    renderPanel()

    await screen.findByText('settlements.tab.title')
    expect(getOverview).toHaveBeenCalledWith(undefined)
  })

  it('passes the year from the URL through', async () => {
    getOverview.mockResolvedValue(makeOverview({ year: 2025 }))

    renderPanel('/admin/settlements?year=2025')

    await screen.findByText('settlements.tab.title')
    expect(getOverview).toHaveBeenCalledWith('2025')
  })

  it('lists sessions nobody priced and links each one straight in', async () => {
    getOverview.mockResolvedValue(makeOverview({
      unpriced: {
        count: 2,
        windowDays: 90,
        sessions: [
          {
            targetType: 'slot', targetId: 'slot-9', date: '2026-08-20',
            title: 'Trening 1:1', payerCount: 1,
          },
          {
            targetType: 'event', targetId: 'event-9', date: '2026-08-24',
            title: 'Kurs skalny', payerCount: 5,
          },
        ],
      },
    }))

    renderPanel()

    expect(await screen.findByText('settlements.tab.unpriced.title')).toBeInTheDocument()
    // Both rules are stated, because an older session missing from the list is policy, not a bug.
    expect(screen.getByText('settlements.tab.unpriced.scope')).toBeInTheDocument()

    const links = screen.getAllByRole('link', { name: 'settlements.tab.unpriced.open' })
    expect(links[0]).toHaveAttribute('href', '/calendar?date=2026-08-20&slot=slot-9')
    // An event is one row however many days it ran and however many people are on it.
    expect(links[1]).toHaveAttribute('href', '/calendar?date=2026-08-24&event=event-9')
    expect(links[1]).toHaveTextContent('Kurs skalny')
  })

  it('hides the pricing queue entirely when there is nothing to price', async () => {
    getOverview.mockResolvedValue(makeOverview())

    renderPanel()

    await screen.findByText('settlements.tab.revenue.title')
    expect(screen.queryByText('settlements.tab.unpriced.title')).not.toBeInTheDocument()
  })

  it('does not claim the tab is empty while there are sessions to price', async () => {
    getOverview.mockResolvedValue(makeOverview({
      years: [],
      year: 2026,
      unpriced: {
        count: 1,
        windowDays: 90,
        sessions: [{
          targetType: 'slot', targetId: 'slot-9', date: '2026-08-20',
          title: 'Trening 1:1', payerCount: 1,
        }],
      },
    }))

    renderPanel()

    // Nothing has been priced yet, so there are no settlements and no years — but the queue is
    // exactly the work the admin came here to do.
    expect(await screen.findByText('settlements.tab.unpriced.title')).toBeInTheDocument()
    expect(screen.queryByText('settlements.tab.empty')).not.toBeInTheDocument()
  })

  it('says out loud that outstanding debt ignores the year picker', async () => {
    getOverview.mockResolvedValue(makeOverview({
      outstanding: {
        total: 450,
        count: 1,
        oldest: '2026-03-12',
        items: [{
          targetType: 'slot',
          targetId: 'slot-1',
          date: '2026-03-12',
          title: 'Trening 1:1',
          payerType: 'user',
          payerId: 'user-1',
          name: 'Piotr Nowak',
          amount: 450,
        }],
      },
    }))

    renderPanel()

    // A section that quietly disobeys the filter above it is indistinguishable from a broken filter.
    expect(await screen.findByText('settlements.tab.outstanding.ignoresYear')).toBeInTheDocument()
    expect(screen.getByText('Piotr Nowak')).toBeInTheDocument()
  })

  it('links each debt straight into its own session', async () => {
    getOverview.mockResolvedValue(makeOverview({
      outstanding: {
        total: 1050,
        count: 2,
        oldest: '2026-03-12',
        items: [
          {
            targetType: 'slot', targetId: 'slot-1', date: '2026-03-12', title: 'Trening 1:1',
            payerType: 'user', payerId: 'user-1', name: 'Piotr Nowak', amount: 450,
          },
          {
            targetType: 'event', targetId: 'event-1', date: '2026-06-04', title: 'Kurs skalny',
            payerType: 'guest', payerId: 'guest-1', name: 'Ekipa z Krakowa', amount: 600,
          },
        ],
      },
    }))

    renderPanel()

    // The visible text is the session title; the accessible name comes from the aria-label, which
    // under this test's i18n mock is the raw key — hence matching by key and asserting in list
    // order (oldest debt first).
    const links = await screen.findAllByRole('link', { name: 'settlements.tab.outstanding.open' })

    // Collecting a debt must not start with hunting through the calendar for the day it was on.
    expect(links[0]).toHaveAttribute('href', '/calendar?date=2026-03-12&slot=slot-1')
    expect(links[0]).toHaveTextContent('Trening 1:1')
    // An event is addressed as an event: its per-day slots are bookkeeping the admin never sees.
    expect(links[1]).toHaveAttribute('href', '/calendar?date=2026-06-04&event=event-1')
    expect(links[1]).toHaveTextContent('Kurs skalny')
  })

  it('settles a debt on the SESSION date, not today', async () => {
    getOverview.mockResolvedValue(makeOverview({
      outstanding: {
        total: 450,
        count: 1,
        oldest: '2026-03-12',
        items: [{
          targetType: 'slot',
          targetId: 'slot-1',
          date: '2026-03-12',
          title: 'Trening 1:1',
          payerType: 'user',
          payerId: 'user-1',
          name: 'Piotr Nowak',
          amount: 450,
        }],
      },
    }))
    const user = userEvent.setup()

    renderPanel()

    await user.click(await screen.findByRole('button', { name: 'settlements.tab.outstanding.settle' }))

    await waitFor(() =>
      expect(save).toHaveBeenCalledWith('slot', 'slot-1', 'user', 'user-1', 450, '2026-03-12'),
    )
  })

  it('links a registered payer to their card and leaves a guest unlinked', async () => {
    getOverview.mockResolvedValue(makeOverview({
      revenue: { ...makeOverview().revenue, total: 900, fromSlots: 300, fromEvents: 600 },
      people: [
        {
          payerType: 'user', userId: 'user-1', name: 'Anna Kowalska',
          settlementCount: 2, paid: 300, outstanding: 0, lastPayment: '2026-03-01',
        },
        {
          payerType: 'guest', userId: null, name: 'Ekipa z Krakowa',
          settlementCount: 1, paid: 600, outstanding: 0, lastPayment: '2026-07-01',
        },
      ],
    }))

    renderPanel()

    expect(await screen.findByRole('link', { name: 'Anna Kowalska' }))
      .toHaveAttribute('href', '/admin/users/user-1')
    // No account, so no card to link to — the null userId IS that signal.
    expect(screen.queryByRole('link', { name: 'Ekipa z Krakowa' })).not.toBeInTheDocument()
    expect(screen.getByText('Ekipa z Krakowa')).toBeInTheDocument()
  })

  it('hides the average tile rather than claiming a zero', async () => {
    getOverview.mockResolvedValue(makeOverview())

    renderPanel()

    await screen.findByText('settlements.tab.revenue.title')
    expect(screen.queryByText('settlements.tab.revenue.average')).not.toBeInTheDocument()
    expect(screen.getByText('settlements.tab.revenue.total')).toBeInTheDocument()
  })

  it('says there is nothing yet only when there is genuinely nothing', async () => {
    getOverview.mockResolvedValue(makeOverview({ years: [], year: 2026 }))

    renderPanel()

    expect(await screen.findByText('settlements.tab.empty')).toBeInTheDocument()
    expect(screen.queryByText('settlements.tab.revenue.title')).not.toBeInTheDocument()
  })

  it('shows a month of work nobody has paid for yet, and no rate for it', async () => {
    getOverview.mockResolvedValue(makeOverview({
      payouts: {
        sources: [{ id: 'src-1', name: 'SP nr 12', archived: false }],
        total: 1400,
        periods: [
          {
            sourceId: 'src-1', sourceName: 'SP nr 12', month: '2026-10-01',
            sessions: 12, amount: 1400, ratePerSession: 116.67,
          },
          {
            sourceId: 'src-1', sourceName: 'SP nr 12', month: '2026-11-01',
            sessions: 4, amount: 0, ratePerSession: null,
          },
        ],
      },
    }))

    renderPanel()

    expect(await screen.findByText('settlements.tab.payouts.title')).toBeInTheDocument()
    // The invoice nobody has paid — the row this table is worth having for.
    expect(screen.getByText('settlements.tab.payouts.awaiting')).toBeInTheDocument()
    // And the one figure that exists nowhere else: 1400 over twelve sessions.
    expect(screen.getByText(/116[.,]67/)).toBeInTheDocument()
  })

  it('records a transfer with both of its dates', async () => {
    getOverview.mockResolvedValue(makeOverview({
      payouts: {
        sources: [{ id: 'src-1', name: 'SP nr 12', archived: false }],
        total: 0,
        periods: [],
      },
    }))
    const user = userEvent.setup()

    renderPanel()

    await user.click(await screen.findByRole('button', { name: 'settlements.tab.payouts.add' }))
    await user.type(screen.getByLabelText('settlements.tab.payouts.periodField'), '2026-10-15')
    await user.type(screen.getByLabelText('settlements.tab.payouts.amountField'), '1400')
    await user.type(screen.getByLabelText('settlements.tab.payouts.receivedField'), '2026-11-08')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    // Any day of the work month is sent as-is; the server snaps it to the first.
    await waitFor(() =>
      expect(createPayout).toHaveBeenCalledWith('src-1', '2026-10-15', 1400, '2026-11-08'),
    )
  })

  it('hides the bulk card entirely until there is a payer or a period', async () => {
    getOverview.mockResolvedValue(makeOverview())

    renderPanel()

    await screen.findByText('settlements.tab.revenue.title')
    expect(screen.queryByText('settlements.tab.payouts.title')).not.toBeInTheDocument()
  })
})
