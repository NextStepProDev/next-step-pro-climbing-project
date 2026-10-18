import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { UserMoneyCard } from './UserMoneyCard'
import type { PayerSummary, Subscription } from '../../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const getPayerSummary = vi.fn()
const listSubscriptions = vi.fn()
const createSubscription = vi.fn()
const endSubscription = vi.fn()
const changeSubscriptionAmount = vi.fn()
const reopenSubscription = vi.fn()
const deleteMonthlyFee = vi.fn()
const restoreMonthlyFee = vi.fn()

vi.mock('../../../api/client', () => ({
  adminSettlementsApi: {
    getPayerSummary: (...a: unknown[]) => getPayerSummary(...a),
    listSubscriptions: (...a: unknown[]) => listSubscriptions(...a),
    createSubscription: (...a: unknown[]) => createSubscription(...a),
    endSubscription: (...a: unknown[]) => endSubscription(...a),
    changeSubscriptionAmount: (...a: unknown[]) => changeSubscriptionAmount(...a),
    reopenSubscription: (...a: unknown[]) => reopenSubscription(...a),
    deleteMonthlyFee: (...a: unknown[]) => deleteMonthlyFee(...a),
    restoreMonthlyFee: (...a: unknown[]) => restoreMonthlyFee(...a),
  },
}))

function summary(overrides: Partial<PayerSummary> = {}): PayerSummary {
  return {
    paid: 0,
    outstanding: 400,
    credit: 0,
    paymentCount: 0,
    lastPayment: null,
    recent: [{ date: '2026-10-01', title: null, monthlyFee: true, amount: 400, covered: 0, paidOn: null }],
    ...overrides,
  } as PayerSummary
}

function subscription(overrides: Partial<Subscription> = {}): Subscription {
  return {
    id: 'sub-1',
    amount: 400,
    startedOn: '2026-09-01',
    endedOn: null,
    active: true,
    skippedMonths: [],
    ...overrides,
  }
}

function renderCard() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <UserMoneyCard userId="user-1" />
    </QueryClientProvider>,
  )
}

describe('UserMoneyCard — subscription', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getPayerSummary.mockResolvedValue(summary())
    endSubscription.mockResolvedValue(undefined)
    restoreMonthlyFee.mockResolvedValue(undefined)
    deleteMonthlyFee.mockResolvedValue(undefined)
  })

  it('asks before ending a subscription, because ending deletes unpaid fees', async () => {
    listSubscriptions.mockResolvedValue([subscription()])
    const user = userEvent.setup()
    renderCard()

    await user.click(await screen.findByRole('button', { name: 'users.detail.money.subscription.end' }))

    // One click used to end it straight away, with the month field defaulting to today.
    expect(endSubscription).not.toHaveBeenCalled()
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('users.detail.money.subscription.endMessage')).toBeInTheDocument()
  })

  it('ends it once confirmed', async () => {
    listSubscriptions.mockResolvedValue([subscription()])
    const user = userEvent.setup()
    renderCard()

    await user.click(await screen.findByRole('button', { name: 'users.detail.money.subscription.end' }))
    const dialog = await screen.findByRole('dialog')
    const buttons = within(dialog).getAllByRole('button')
    await user.click(buttons[buttons.length - 1])

    await waitFor(() => expect(endSubscription).toHaveBeenCalledWith('sub-1', expect.any(String)))
  })

  it('does not let the last month sit before the first', async () => {
    listSubscriptions.mockResolvedValue([subscription()])
    renderCard()

    const field = await screen.findByLabelText('users.detail.money.subscription.endLabel')
    expect(field).toHaveAttribute('min', '2026-09-01')
  })

  it('lists skipped months and restores one', async () => {
    listSubscriptions.mockResolvedValue([subscription({ skippedMonths: ['2026-09-01'] })])
    const user = userEvent.setup()
    renderCard()

    // A deleted fee is remembered so the nightly run does not bill it again — and the admin has to
    // be able to see that, or the month looks forgotten rather than waived.
    const restore = await screen.findByRole('button', { name: 'users.detail.money.subscription.restore' })
    await user.click(restore)

    await waitFor(() => expect(restoreMonthlyFee).toHaveBeenCalledWith('user-1', '2026-09-01'))
  })

  it('offers to reopen only the newest subscription', async () => {
    // Reopening runs a subscription on from its start, so an older one would overlap the later.
    listSubscriptions.mockResolvedValue([
      subscription({ id: 'sub-2', startedOn: '2026-06-01', endedOn: '2026-08-01', active: false }),
      subscription({ id: 'sub-1', startedOn: '2026-01-01', endedOn: '2026-03-01', active: false }),
    ])
    renderCard()

    expect(await screen.findAllByRole('button', { name: 'users.detail.money.subscription.reopen' }))
      .toHaveLength(1)
  })

  it('shows no skipped-month line when nothing was waived', async () => {
    listSubscriptions.mockResolvedValue([subscription()])
    renderCard()

    await screen.findByRole('button', { name: 'users.detail.money.subscription.end' })
    expect(screen.queryByText('users.detail.money.subscription.skipped')).not.toBeInTheDocument()
  })

  it('confirms before deleting a monthly fee, and deletes it after', async () => {
    listSubscriptions.mockResolvedValue([subscription()])
    const user = userEvent.setup()
    renderCard()

    await user.click(await screen.findByRole('button', { name: 'users.detail.money.fee.remove' }))
    expect(deleteMonthlyFee).not.toHaveBeenCalled()
    const dialog = await screen.findByRole('dialog')
    const buttons = within(dialog).getAllByRole('button')
    await user.click(buttons[buttons.length - 1])

    await waitFor(() => expect(deleteMonthlyFee).toHaveBeenCalledWith('user-1', '2026-10-01'))
  })
})
