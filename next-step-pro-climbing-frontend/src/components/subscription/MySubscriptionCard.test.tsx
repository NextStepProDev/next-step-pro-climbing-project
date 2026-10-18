import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MySubscriptionCard } from './MySubscriptionCard'
import type { MySubscription } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const get = vi.fn()
vi.mock('../../api/client', () => ({
  mySubscriptionApi: { get: (...a: unknown[]) => get(...a) },
}))

function renderCard() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MySubscriptionCard />
    </QueryClientProvider>,
  )
}

const running: MySubscription = {
  current: { amount: 400, startedOn: '2026-09-01', endedOn: null, active: true },
  months: [
    { month: '2026-10-01', amount: 400, covered: 100, remaining: 300, paidOn: null, skipped: false },
    { month: '2026-09-01', amount: null, covered: 0, remaining: 0, paidOn: null, skipped: true },
  ],
  due: 300,
}

describe('MySubscriptionCard', () => {
  beforeEach(() => get.mockReset())

  it('renders nothing for somebody who never had a subscription', async () => {
    get.mockResolvedValue({ current: null, months: [], due: 0 })
    const { container } = renderCard()

    await vi.waitFor(() => expect(get).toHaveBeenCalled())
    expect(container).toBeEmptyDOMElement()
  })

  it('names what is owed, a part payment, and a waived month', async () => {
    get.mockResolvedValue(running)
    renderCard()

    expect(await screen.findByText('subscription.title')).toBeInTheDocument()
    expect(screen.getByText('subscription.due')).toBeInTheDocument()
    expect(screen.getByText('subscription.short')).toBeInTheDocument()
    expect(screen.getByText('subscription.waived')).toBeInTheDocument()
  })

  it('does not shout about a debt that is not there', async () => {
    get.mockResolvedValue({ ...running, due: 0, months: [] })
    renderCard()

    await screen.findByText('subscription.title')
    expect(screen.queryByText('subscription.due')).not.toBeInTheDocument()
  })
})
