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
const settleOutstanding = vi.fn()
const createSource = vi.fn()
const deletePayout = vi.fn()
const setSourceArchived = vi.fn()

vi.mock('../../api/client', () => ({
  adminSettlementsApi: {
    getOverview: (...args: unknown[]) => getOverview(...args),
    save: (...args: unknown[]) => save(...args),
    createPayout: (...args: unknown[]) => createPayout(...args),
    settleOutstanding: (...args: unknown[]) => settleOutstanding(...args),
    createSource: (...args: unknown[]) => createSource(...args),
    deletePayout: (...args: unknown[]) => deletePayout(...args),
    setSourceArchived: (...args: unknown[]) => setSourceArchived(...args),
  },
}))

function makeOverview(overrides: Partial<SettlementOverview> = {}): SettlementOverview {
  return {
    years: [2026, 2025],
    year: 2026,
    unpriced: { count: 0, windowDays: 90, sessions: [] },
    outstanding: { total: 0, count: 0, oldest: null, items: [], credits: [] },
    revenue: {
      total: 0,
      monthlyAverage: null,
      months: Array.from({ length: 12 }, (_, i) => ({
        month: `2026-${String(i + 1).padStart(2, '0')}-01`,
        amount: 0,
      })),
      fromSlots: 0,
      fromEvents: 0,
      fromSubscriptions: 0,
      fromPayouts: 0,
      previousMonths: [],
      previousTotal: 0,
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
    settleOutstanding.mockReset().mockResolvedValue({ settled: 2, balance: 0 })
    createSource.mockReset().mockResolvedValue({ id: 'src-2', name: 'Klub XYZ', archived: false })
    deletePayout.mockReset().mockResolvedValue(undefined)
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
        credits: [],
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

  it('groups debts by person and settles a whole month on one date', async () => {
    getOverview.mockResolvedValue(makeOverview({
      outstanding: {
        total: 680,
        count: 3,
        oldest: '2026-08-05',
        credits: [],
        items: [
          {
            targetType: 'slot', targetId: 'slot-1', date: '2026-08-05', title: 'Trening 1:1',
            payerType: 'user', payerId: 'anna', name: 'Anna Kowalska', amount: 150,
          },
          {
            targetType: 'slot', targetId: 'slot-2', date: '2026-08-12', title: 'Trening grupowy',
            payerType: 'user', payerId: 'anna', name: 'Anna Kowalska', amount: 80,
          },
          {
            targetType: 'slot', targetId: 'slot-3', date: '2026-08-19', title: 'Trening 1:1',
            payerType: 'user', payerId: 'piotr', name: 'Piotr Nowak', amount: 450,
          },
        ],
      },
    }))
    const user = userEvent.setup()

    renderPanel()

    // Two people, not three debts: one person settles a month at a time.
    const settleButtons = await screen.findAllByRole('button', {
      name: 'settlements.tab.outstanding.settleAll',
    })
    expect(settleButtons).toHaveLength(2)

    // ⚠️ Today, NOT each session's own day: one transfer covered the month, so the only date true
    // of all of it is the day it arrived.
    const dateFields = screen.getAllByLabelText('settlements.tab.outstanding.paidOnLabel')
    await user.clear(dateFields[0])
    await user.type(dateFields[0], '2026-08-31')
    await user.click(settleButtons[0])

    await waitFor(() =>
      expect(settleOutstanding).toHaveBeenCalledWith('user', 'anna', '2026-08-31', 230),
    )
    expect(settleOutstanding).toHaveBeenCalledTimes(1)
  })

  it('says a debtor is holding your money, and asks only for the rest', async () => {
    getOverview.mockResolvedValue(makeOverview({
      outstanding: {
        total: 50,
        count: 1,
        oldest: '2026-08-19',
        // He paid 100 for a 50 session two months ago, so the next 50 is already covered.
        credits: [{ payerType: 'user', payerId: 'anna', credit: 50 }],
        items: [{
          targetType: 'slot', targetId: 'slot-1', date: '2026-08-19', title: 'Trening 1:1',
          payerType: 'user', payerId: 'anna', name: 'Anna Kowalska', amount: 50,
        }],
      },
    }))
    const user = userEvent.setup()

    renderPanel()

    // The row is genuinely open, so the figures above stay gross — but a screen that says only
    // "owes 50" about somebody who already handed the money over is a demand for it twice.
    expect(await screen.findByText('settlements.tab.outstanding.credit')).toBeInTheDocument()

    // And the field asks for what is actually left: the server pulls the credit into the pool
    // before paying rows off, so typing the gross figure would hand him a second overpayment.
    await user.click(screen.getByRole('button', { name: 'settlements.tab.outstanding.settleAll' }))

    await waitFor(() =>
      expect(settleOutstanding).toHaveBeenCalledWith('user', 'anna', expect.any(String), 0),
    )
  })

  it('offers a round amount even when the debts add up to a float tail', async () => {
    // 299.23 + 44.95 + 326.98 is 671.1600000000001 in binary floating point, and roughly three
    // groups in ten land somewhere like it. The heading beside this field renders 671,16 zł through
    // Intl, so anything but a rounded default puts two different totals on one row of one screen.
    getOverview.mockResolvedValue(makeOverview({
      outstanding: {
        total: 671.16,
        count: 3,
        oldest: '2026-08-05',
        credits: [],
        items: [
          {
            targetType: 'slot', targetId: 'slot-1', date: '2026-08-05', title: 'Trening 1:1',
            payerType: 'user', payerId: 'anna', name: 'Anna Kowalska', amount: 299.23,
          },
          {
            targetType: 'slot', targetId: 'slot-2', date: '2026-08-12', title: 'Trening 1:1',
            payerType: 'user', payerId: 'anna', name: 'Anna Kowalska', amount: 44.95,
          },
          {
            targetType: 'slot', targetId: 'slot-3', date: '2026-08-19', title: 'Trening 1:1',
            payerType: 'user', payerId: 'anna', name: 'Anna Kowalska', amount: 326.98,
          },
        ],
      },
    }))

    renderPanel()

    const received = await screen.findByLabelText('settlements.tab.outstanding.receivedLabel')
    expect(received).toHaveValue('671.16')
  })

  it('refuses an amount the server would reject, rather than letting it be sent', async () => {
    // The ceiling here is the SETTLEMENT one: this money lands on a settlement row, which the server
    // caps at 100000. Reusing the higher bulk-transfer ceiling left the button live on an amount
    // that then came back 400.
    getOverview.mockResolvedValue(makeOverview({
      outstanding: {
        total: 150,
        count: 1,
        oldest: '2026-08-05',
        credits: [],
        items: [{
          targetType: 'slot', targetId: 'slot-1', date: '2026-08-05', title: 'Trening 1:1',
          payerType: 'user', payerId: 'anna', name: 'Anna Kowalska', amount: 150,
        }],
      },
    }))
    const user = userEvent.setup()

    renderPanel()

    const received = await screen.findByLabelText('settlements.tab.outstanding.receivedLabel')
    const settle = screen.getByRole('button', { name: 'settlements.tab.outstanding.settleAll' })
    expect(settle).toBeEnabled()

    await user.clear(received)
    await user.type(received, '200000')

    expect(settle).toBeDisabled()
    expect(settleOutstanding).not.toHaveBeenCalled()
  })

  it('shows what a person owes it for only once the group is opened', async () => {
    getOverview.mockResolvedValue(makeOverview({
      outstanding: {
        total: 230,
        count: 2,
        oldest: '2026-08-05',
        credits: [],
        items: [
          {
            targetType: 'slot', targetId: 'slot-1', date: '2026-08-05', title: 'Trening 1:1',
            payerType: 'user', payerId: 'anna', name: 'Anna Kowalska', amount: 150,
          },
          {
            targetType: 'event', targetId: 'event-2', date: '2026-08-12', title: 'Kurs skalny',
            payerType: 'user', payerId: 'anna', name: 'Anna Kowalska', amount: 80,
          },
        ],
      },
    }))
    const user = userEvent.setup()

    renderPanel()

    expect(await screen.findByText('Anna Kowalska')).toBeInTheDocument()
    expect(screen.queryByText('Kurs skalny')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { expanded: false }))

    // Opened, each session is still its own link into the calendar.
    expect(screen.getByText('Kurs skalny')).toBeInTheDocument()
    const links = screen.getAllByRole('link', { name: 'settlements.tab.outstanding.open' })
    expect(links[1]).toHaveAttribute('href', '/calendar?date=2026-08-12&event=event-2')
  })

  it('accounts for every source of revenue in the split, not just the two from sessions', async () => {
    // ⚠️ The split is read against the headline total. Bulk transfers were computed by the server
    // and never drawn, so a coach earning mostly from a school saw a bar that did not add up to the
    // number above it — and a retainer used to be counted as slot income, which claims session
    // earnings for a client whose sessions are deliberately left unpriced.
    getOverview.mockResolvedValue(makeOverview({
      revenue: {
        ...makeOverview().revenue,
        total: 3050,
        fromSlots: 150,
        fromEvents: 600,
        fromSubscriptions: 900,
        fromPayouts: 1400,
      },
    }))

    renderPanel()

    expect(await screen.findByText('settlements.tab.revenue.fromSlots')).toBeInTheDocument()
    expect(screen.getByText('settlements.tab.revenue.fromEvents')).toBeInTheDocument()
    expect(screen.getByText('settlements.tab.revenue.fromSubscriptions')).toBeInTheDocument()
    expect(screen.getByText('settlements.tab.revenue.fromPayouts')).toBeInTheDocument()
  })

  it('leaves out a source that earned nothing rather than drawing an empty row', async () => {
    getOverview.mockResolvedValue(makeOverview({
      revenue: { ...makeOverview().revenue, total: 150, fromSlots: 150 },
    }))

    renderPanel()

    expect(await screen.findByText('settlements.tab.revenue.fromSlots')).toBeInTheDocument()
    expect(screen.queryByText('settlements.tab.revenue.fromPayouts')).not.toBeInTheDocument()
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

  it('compares a year with the same months a year earlier, never with last month', async () => {
    getOverview.mockResolvedValue(makeOverview({
      revenue: {
        ...makeOverview().revenue,
        total: 1000,
        previousTotal: 800,
        previousMonths: Array.from({ length: 12 }, (_, i) => ({
          month: `2025-${String(i + 1).padStart(2, '0')}-01`,
          amount: i === 8 ? 800 : 0,
        })),
      },
    }))

    renderPanel()

    // Climbing is seasonal: a month-over-month arrow would call a quiet October a bad month when it
    // is simply October.
    expect(await screen.findByText(/25%/)).toBeInTheDocument()
    expect(screen.getByText('settlements.tab.revenue.vsLastYear')).toBeInTheDocument()
  })

  it('offers no comparison when there is no previous year to compare with', async () => {
    getOverview.mockResolvedValue(makeOverview({
      revenue: { ...makeOverview().revenue, total: 1000 },
    }))

    renderPanel()

    // A first year would otherwise read as -100%, which says the business collapsed rather than
    // that it had not started.
    await screen.findByText('settlements.tab.revenue.title')
    expect(screen.queryByText('settlements.tab.revenue.vsLastYear')).not.toBeInTheDocument()
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
            sessions: 12, minutes: 1080, sessionsWithoutHours: 0,
            amount: 1400, ratePerHour: 77.78,
            transfers: [{ id: 'p-1', amount: 1400, receivedOn: '2026-11-08' }],
          },
          {
            sourceId: 'src-1', sourceName: 'SP nr 12', month: '2026-11-01',
            sessions: 4, minutes: 240, sessionsWithoutHours: 0,
            amount: 0, ratePerHour: null, transfers: [],
          },
        ],
      },
    }))

    renderPanel()

    expect(await screen.findByText('settlements.tab.payouts.title')).toBeInTheDocument()
    // The invoice nobody has paid — the row this table is worth having for.
    expect(screen.getByText('settlements.tab.payouts.awaiting')).toBeInTheDocument()
    // And the one figure that exists nowhere else: 1400 over eighteen HOURS, not twelve sessions.
    expect(screen.getByText(/77[.,]78/)).toBeInTheDocument()
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

  it('offers a way to add the first payer when the tab is otherwise empty', async () => {
    getOverview.mockResolvedValue(makeOverview({ years: [], year: 2026 }))
    const user = userEvent.setup()

    renderPanel()

    // ⚠️ The payer manager used to live only inside a card that hides itself when there are no
    // payers, so the first one could never be created and the session picker pointed at a card
    // that was not there.
    await user.type(
      await screen.findByLabelText('settlements.tab.payouts.newPayer'),
      'SP nr 12',
    )
    await user.click(screen.getByRole('button', { name: 'settlements.tab.payouts.addPayer' }))

    await waitFor(() => expect(createSource).toHaveBeenCalledWith('SP nr 12'))
  })

  it('lets a mistyped transfer be deleted', async () => {
    getOverview.mockResolvedValue(makeOverview({
      payouts: {
        sources: [{ id: 'src-1', name: 'SP nr 12', archived: false }],
        total: 15400,
        periods: [{
          sourceId: 'src-1', sourceName: 'SP nr 12', month: '2026-10-01',
          sessions: 12, minutes: 1080, sessionsWithoutHours: 0,
          amount: 15400, ratePerHour: 855.56,
          transfers: [
            { id: 'p-1', amount: 1400, receivedOn: '2026-11-08' },
            { id: 'p-2', amount: 14000, receivedOn: '2026-11-09' },
          ],
        }],
      },
    }))
    const user = userEvent.setup()

    renderPanel()

    // The row is an aggregate, so without a way down to the arrivals a fat finger is permanent.
    await user.click(await screen.findByRole('button', { expanded: false }))
    await user.click(screen.getAllByRole('button', { name: 'settlements.tab.payouts.deleteTransfer' })[1])

    await waitFor(() => expect(deletePayout).toHaveBeenCalledWith('p-2'))
  })

  it('hides the bulk card entirely until there is a payer or a period', async () => {
    getOverview.mockResolvedValue(makeOverview())

    renderPanel()

    await screen.findByText('settlements.tab.revenue.title')
    expect(screen.queryByText('settlements.tab.payouts.title')).not.toBeInTheDocument()
  })
})
