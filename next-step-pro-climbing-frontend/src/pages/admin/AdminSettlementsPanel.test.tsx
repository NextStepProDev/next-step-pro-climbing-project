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
    unassigned: { count: 0, windowDays: 90, sessions: [] },
    unpriced: { count: 0, windowDays: 90, sessions: [] },
    outstanding: { total: 0, count: 0, oldest: null, items: [], credits: [] },
    credits: { total: 0, payers: 0, items: [] },
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

  it('lists sessions that have nobody to bill and links each one straight in', async () => {
    getOverview.mockResolvedValue(makeOverview({
      unassigned: {
        count: 2,
        windowDays: 90,
        sessions: [
          { targetType: 'slot', targetId: 'slot-1', date: '2026-08-18', title: 'SP nr 5' },
          // The untitled one is the whole point: nothing on the calendar names this as work.
          { targetType: 'slot', targetId: 'slot-2', date: '2026-08-25', title: null },
        ],
      },
    }))

    renderPanel()

    expect(await screen.findByText('settlements.tab.unassigned.title')).toBeInTheDocument()
    expect(screen.getByText('settlements.tab.unassigned.scope')).toBeInTheDocument()

    const links = screen.getAllByRole('link', { name: 'settlements.tab.unassigned.open' })
    expect(links[0]).toHaveAttribute('href', '/calendar?date=2026-08-18&slot=slot-1')
    expect(links[1]).toHaveTextContent('settlements.tab.outstanding.untitled.slot')
  })

  it('does not claim the tab is empty while sessions are waiting for a payer', async () => {
    // The shape this catches: somebody who only ever teaches for a school has no settlements, no
    // years and no debts, so every other source of content is zero — and the queue below is the
    // only reason they opened the tab.
    getOverview.mockResolvedValue(makeOverview({
      years: [],
      year: 2026,
      unassigned: {
        count: 1,
        windowDays: 90,
        sessions: [{ targetType: 'slot', targetId: 'slot-1', date: '2026-08-18', title: null }],
      },
    }))

    renderPanel()

    expect(await screen.findByText('settlements.tab.unassigned.title')).toBeInTheDocument()
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

    // A screen that says only "owes 50" about somebody who already handed the money over is a
    // demand for it twice: the gross figure is named as gross, beside the credit that covers it.
    expect(await screen.findByText('settlements.tab.outstanding.credit')).toBeInTheDocument()

    // And the field asks for what is actually left: the server pulls the credit into the pool
    // before paying rows off, so typing the gross figure would hand him a second overpayment.
    // Nothing changes hands, so the button says the credit pays rather than "receive 0".
    await user.click(
      screen.getByRole('button', { name: 'settlements.tab.outstanding.settleFromCredit' }),
    )

    await waitFor(() =>
      expect(settleOutstanding).toHaveBeenCalledWith('user', 'anna', expect.any(String), 0),
    )
  })

  it('leads with what is left to collect, not with the gross debt', async () => {
    // The case that was reported: 90 left over from before, a 100 session priced, nothing paid.
    // Gross on top, the row read as "she owes me 100" and the button promised to settle 100.
    getOverview.mockResolvedValue(makeOverview({
      outstanding: {
        total: 100,
        count: 1,
        oldest: '2026-09-25',
        credits: [{ payerType: 'user', payerId: 'bernadeta', credit: 90 }],
        items: [{
          targetType: 'slot', targetId: 'slot-1', date: '2026-09-25', title: 'Trening 1:1',
          payerType: 'user', payerId: 'bernadeta', name: 'Bernadeta M.', amount: 100,
        }],
      },
    }))

    renderPanel()

    const received = await screen.findByLabelText('settlements.tab.outstanding.receivedLabel')
    expect(received).toHaveValue('10.00')
    // Both the section heading and the row lead with the net; the heading names the gross apart.
    expect(screen.getAllByText(/10,00/).length).toBeGreaterThanOrEqual(2)
    expect(screen.getByText(/settlements\.tab\.outstanding\.grossTotal/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'settlements.tab.outstanding.settleAll' }))
      .toBeEnabled()
  })

  it('does not mention a gross total when nobody holds a credit', async () => {
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

    renderPanel()

    await screen.findByLabelText('settlements.tab.outstanding.receivedLabel')
    expect(screen.queryByText(/settlements\.tab\.outstanding\.grossTotal/)).not.toBeInTheDocument()
    expect(screen.queryByText('settlements.tab.outstanding.credit')).not.toBeInTheDocument()
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

  it('keeps two standing fees of one person apart, in both money lists', async () => {
    // ⚠️ A monthly coaching fee has no calendar entry, so it travels with `targetId: null` — and
    // `uq_settlements_monthly` is unique on (user, month), so one person can hold several. Keying a
    // row by target alone collapses every one of them onto "month:null": React sees duplicate keys
    // among siblings and is free to reuse the wrong node, so two months render as one.
    const months = (payerId: string) => [
      {
        targetType: 'month' as const, targetId: null, date: '2026-01-01', title: null,
        payerType: 'user' as const, payerId, name: 'Anna Kowalska', amount: 120,
      },
      {
        targetType: 'month' as const, targetId: null, date: '2026-02-01', title: null,
        payerType: 'user' as const, payerId, name: 'Anna Kowalska', amount: 120,
      },
    ]
    getOverview.mockResolvedValue(makeOverview({
      outstanding: {
        total: 240, count: 2, oldest: '2026-01-01', credits: [], items: months('anna'),
      },
      credits: { total: 240, payers: 1, items: months('piotr') },
    }))
    const user = userEvent.setup()
    // The damage is in reconciliation, not in the first paint — duplicates still render, and React
    // reports the broken contract here rather than by dropping a row. Asserting on the warning is
    // what makes this test fail while the bug is present.
    const errors: string[] = []
    const spy = vi.spyOn(console, 'error').mockImplementation((...args) => {
      errors.push(args.map(String).join(' '))
    })

    try {
      renderPanel()

      await screen.findByText('settlements.tab.outstanding.title')
      for (const group of screen.getAllByRole('button', { expanded: false })) {
        await user.click(group)
      }

      expect(screen.getAllByText('settlements.tab.outstanding.untitled.month')).toHaveLength(2)
      expect(screen.getAllByText('settlements.tab.credits.untitled.month')).toHaveLength(2)
      expect(errors.filter((line) => /same key/i.test(line))).toEqual([])
    } finally {
      spy.mockRestore()
    }
  })

  it('says nothing about overpayments when nobody is holding your money', async () => {
    // The ordinary state. Debt says "all settled" when it is empty because zero there is news;
    // an empty overpayments card would be a heading about nothing.
    getOverview.mockResolvedValue(makeOverview())

    renderPanel()

    await screen.findByText('settlements.tab.title')
    expect(screen.queryByText('settlements.tab.credits.title')).not.toBeInTheDocument()
  })

  it('survives a backend that predates the overpayments field', async () => {
    // ⚠️ `credits` is a whole new TOP-LEVEL field, so during a deploy a browser holding the new
    // bundle against the previous backend gets undefined, not an empty list. Dereferencing it in
    // a useMemo is a white screen for the entire tab, not one missing card.
    const withoutCredits: Partial<SettlementOverview> = makeOverview()
    delete withoutCredits.credits
    getOverview.mockResolvedValue(withoutCredits)

    renderPanel()

    // The rest of the tab still renders, which is the whole point.
    expect(await screen.findByText('settlements.tab.title')).toBeInTheDocument()
    expect(screen.queryByText('settlements.tab.credits.title')).not.toBeInTheDocument()
  })

  it('names somebody in credit with nothing owing, and the session parking the money', async () => {
    // ⚠️ The case that started this: credit used to be computed only for debtors, so this person
    // appeared in no figure on the tab at all — not revenue (it did arrive), not debt (he owes
    // nothing), not the credit note (which only annotates debtors).
    getOverview.mockResolvedValue(makeOverview({
      credits: {
        total: 150,
        payers: 1,
        items: [{
          targetType: 'slot', targetId: 'slot-9', date: '2026-09-12', title: 'Trening 1:1',
          payerType: 'user', payerId: 'piotr', name: 'Piotr Zieliński', amount: 150,
        }],
      },
    }))
    const user = userEvent.setup()

    renderPanel()

    expect(await screen.findByText('Piotr Zieliński')).toBeInTheDocument()
    // Closed, the card says who and how much; the session is the answer to "where is it", which
    // is the only place the figure can be corrected.
    expect(screen.queryByText('Trening 1:1')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { expanded: false }))

    expect(screen.getByRole('link', { name: 'settlements.tab.credits.open' }))
      .toHaveAttribute('href', '/calendar?date=2026-09-12&slot=slot-9')
    expect(screen.getByText('settlements.tab.credits.openUser')).toBeInTheDocument()
  })

  it('states both rules it quietly follows: the whole history, and debtors listed elsewhere', async () => {
    // A section that disobeys the filter above it, or drops people from itself, is otherwise
    // indistinguishable from a broken one.
    getOverview.mockResolvedValue(makeOverview({
      credits: {
        total: 40,
        payers: 1,
        items: [{
          targetType: 'slot', targetId: 'slot-4', date: '2025-11-02', title: 'Trening 1:1',
          payerType: 'user', payerId: 'piotr', name: 'Piotr Zieliński', amount: 40,
        }],
      },
    }))

    renderPanel()

    expect(await screen.findByText('settlements.tab.credits.ignoresYear')).toBeInTheDocument()
    expect(screen.getByText('settlements.tab.credits.excludesDebtors')).toBeInTheDocument()
  })

  it('marks a guest as one and offers them no client card, because they have none', async () => {
    getOverview.mockResolvedValue(makeOverview({
      credits: {
        total: 30,
        payers: 1,
        items: [{
          targetType: 'event', targetId: 'event-3', date: '2026-09-02', title: 'Wspinanie',
          payerType: 'guest', payerId: 'guest-1', name: 'Marek (600…)', amount: 30,
        }],
      },
    }))
    const user = userEvent.setup()

    renderPanel()

    // The label rides in the row's own name, so a reader sees it without opening anything.
    expect(await screen.findByRole('button', { name: /settlements\.line\.guest/ }))
      .toBeInTheDocument()

    await user.click(screen.getByRole('button', { expanded: false }))

    // The session is still reachable — it is where a guest's figure gets corrected.
    expect(screen.getByRole('link', { name: 'settlements.tab.credits.open' }))
      .toHaveAttribute('href', '/calendar?date=2026-09-02&event=event-3')
    expect(screen.queryByText('settlements.tab.credits.openUser')).not.toBeInTheDocument()
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

  // ⚠️ The bug this card was hiding: the manager that creates a contractor lives INSIDE it, and
  // the card used to `return null` when there were none — so the only door shut exactly when it
  // was needed, and bulk payouts could not be started at all. The whole-tab empty state has a
  // second copy of the manager, which is why this looked fixed; but that state needs the tab to
  // be empty of everything, so it only ever helped a brand-new install. The default fixture here
  // is the real case: settlement years present, zero contractors.
  it('offers a way to add a first contractor even though the tab is already in use', async () => {
    getOverview.mockResolvedValue(makeOverview())

    renderPanel()

    expect(await screen.findByText('settlements.tab.payouts.title')).toBeInTheDocument()
    expect(screen.getByLabelText('settlements.tab.payouts.newPayer')).toBeInTheDocument()
    expect(screen.getByText('settlements.tab.payouts.setupHint')).toBeInTheDocument()
  })

  it('actually creates the contractor it offered to create', async () => {
    getOverview.mockResolvedValue(makeOverview())
    const user = userEvent.setup()

    renderPanel()

    await user.type(await screen.findByLabelText('settlements.tab.payouts.newPayer'), 'Ściana XYZ')
    await user.click(screen.getByRole('button', { name: 'settlements.tab.payouts.addPayer' }))

    await waitFor(() => expect(createSource).toHaveBeenCalledWith('Ściana XYZ'))
  })

  // Two ways in on one screen would be worse than none: the admin fills one, nothing happens to
  // the other, and both look broken.
  it('does not put two contractor fields on a tab that is empty of everything', async () => {
    getOverview.mockResolvedValue(makeOverview({
      years: [],
      year: 2026,
      payouts: { sources: [], total: 0, periods: [] },
    }))

    renderPanel()

    await screen.findByText('settlements.tab.empty')
    expect(screen.getAllByLabelText('settlements.tab.payouts.newPayer')).toHaveLength(1)
  })

  // Empty is a setup prompt, not a table: neither the axis note nor a transfer button means
  // anything before the first contractor exists.
  it('shows no table and no transfer button until there is a contractor', async () => {
    getOverview.mockResolvedValue(makeOverview())

    renderPanel()

    await screen.findByText('settlements.tab.payouts.title')
    expect(screen.queryByText('settlements.tab.payouts.axis')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'settlements.tab.payouts.add' })).not.toBeInTheDocument()
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
            transfers: [{ id: 'p-1', amount: 1400, receivedOn: '2026-11-08' }], heldSessions: [],
          },
          {
            sourceId: 'src-1', sourceName: 'SP nr 12', month: '2026-11-01',
            sessions: 4, minutes: 240, sessionsWithoutHours: 0,
            amount: 0, ratePerHour: null, transfers: [], heldSessions: [],
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

  it('opens a month nobody has paid for yet and lists the sessions behind its figures', async () => {
    // The row this table is worth having — work done, no transfer — was the one row that could not
    // be opened, because opening was gated on transfers. The sessions are what the count and the
    // hours are made of, so when the rate looks wrong this is the way down to why.
    getOverview.mockResolvedValue(makeOverview({
      payouts: {
        sources: [{ id: 'src-1', name: 'Chwyciarnia', archived: false }],
        total: 0,
        periods: [{
          sourceId: 'src-1', sourceName: 'Chwyciarnia', month: '2026-09-01',
          sessions: 2, minutes: 180, sessionsWithoutHours: 1,
          amount: 0, ratePerHour: null,
          transfers: [],
          heldSessions: [
            { targetType: 'slot', targetId: 'slot-1', date: '2026-09-08', title: 'Grupa A', minutes: 90 },
            { targetType: 'slot', targetId: 'slot-2', date: '2026-09-15', title: null, minutes: 90 },
            { targetType: 'event', targetId: 'ev-1', date: '2026-09-20', title: 'Wyjazd', minutes: null },
          ],
        }],
      },
    }))
    const user = userEvent.setup()

    renderPanel()

    await user.click(await screen.findByRole('button', { name: /2026/ }))

    expect(screen.getByText('Grupa A')).toBeInTheDocument()
    // An untitled session is the ordinary case here, so the row still has to be openable.
    expect(screen.getByText('settlements.tab.outstanding.untitled.slot')).toBeInTheDocument()
    // The entry with no knowable length is named rather than folded in at zero — it is the "+N
    // without hours" from the row above, given a face.
    expect(screen.getByText('settlements.tab.payouts.noHours')).toBeInTheDocument()

    // By name rather than by position: the payer's own name is a link too, so an index here would
    // silently start asserting about a different row the next time one is added.
    expect(screen.getByRole('link', { name: 'Grupa A' }))
      .toHaveAttribute('href', '/calendar?date=2026-09-08&slot=slot-1')
    // ⚠️ An event links as an event: a deep link built from the wrong kind opens the wrong screen
    // while looking perfectly right in the table it came from.
    expect(screen.getByRole('link', { name: 'Wyjazd' }))
      .toHaveAttribute('href', '/calendar?date=2026-09-20&event=ev-1')
  })

  it('opens the payer itself from the table that names them', async () => {
    getOverview.mockResolvedValue(makeOverview({
      year: 2025,
      payouts: {
        sources: [{ id: 'src-1', name: 'Chwyciarnia', archived: false }],
        total: 0,
        periods: [{
          sourceId: 'src-1', sourceName: 'Chwyciarnia', month: '2026-09-01',
          sessions: 1, minutes: 90, sessionsWithoutHours: 0,
          amount: 0, ratePerHour: null, transfers: [], heldSessions: [],
        }],
      },
    }))

    renderPanel('/admin/settlements?year=2025')

    // The year travels with the link, so the back arrow returns to the tab as it was left.
    expect(await screen.findByRole('link', { name: 'Chwyciarnia' }))
      .toHaveAttribute('href', '/admin/settlements/sources/src-1?year=2025')
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
          heldSessions: [],
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

  /**
   * ⚠️ This test used to assert the OPPOSITE — that the card stays hidden until a payer or a
   * period exists — and it passed happily while making bulk payouts impossible to start, because
   * the only control that creates a contractor lives inside that card.
   *
   * A green test asserting a defect is worse than no test at all: it argues against the fix and
   * makes the next person assume the behaviour was deliberate. Kept and inverted rather than
   * deleted, so the reason survives.
   */
  it('keeps the bulk card reachable even with no payer and no period', async () => {
    getOverview.mockResolvedValue(makeOverview())

    renderPanel()

    await screen.findByText('settlements.tab.revenue.title')
    expect(screen.getByText('settlements.tab.payouts.title')).toBeInTheDocument()
  })
})
