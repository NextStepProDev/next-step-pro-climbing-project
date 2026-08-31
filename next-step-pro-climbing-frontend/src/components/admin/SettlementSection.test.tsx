import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SettlementSection } from './SettlementSection'
import type { SettlementLine } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  // src/i18n.ts is pulled in transitively (utils/errors) and initialises on import
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const getSection = vi.fn()
const save = vi.fn()
const remove = vi.fn()
const listSources = vi.fn()
const assignSource = vi.fn()

vi.mock('../../api/client', () => ({
  adminSettlementsApi: {
    getSection: (...args: unknown[]) => getSection(...args),
    save: (...args: unknown[]) => save(...args),
    remove: (...args: unknown[]) => remove(...args),
    listSources: (...args: unknown[]) => listSources(...args),
    assignSource: (...args: unknown[]) => assignSource(...args),
  },
}))

const TARGET_DATE = '2026-08-14'

/** Most sessions are priced per participant; the bulk fields are absent unless a test sets them. */
const bulkOff = { payoutSourceId: null, payoutSourceName: null }

function line(overrides: Partial<SettlementLine> = {}): SettlementLine {
  return {
    payerType: 'user',
    payerId: 'user-1',
    name: 'Anna Kowalska',
    participants: 1,
    orphaned: false,
    amount: null,
    settledOn: null,
    suggestedAmount: null,
    ...overrides,
  }
}

function renderSection(target: 'slot' | 'event' = 'slot') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <SettlementSection target={target} targetId="target-1" />
    </QueryClientProvider>,
  )
}

describe('SettlementSection', () => {
  beforeEach(() => {
    getSection.mockReset()
    save.mockReset().mockResolvedValue(undefined)
    remove.mockReset().mockResolvedValue(undefined)
    listSources.mockReset().mockResolvedValue([{ id: 'src-1', name: 'SP nr 12', archived: false }])
    assignSource.mockReset().mockResolvedValue(undefined)
  })

  it('says whose money this is before anything is typed', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })

    renderSection()

    expect(await screen.findByText('Anna Kowalska')).toBeInTheDocument()
    expect(screen.getByText('settlements.section.onlyYouHint')).toBeInTheDocument()
  })

  it('keeps Save disabled until something actually changes', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSection()

    const saveButton = await screen.findByRole('button', { name: 'settlements.actions.save' })
    expect(saveButton).toBeDisabled()

    await user.type(screen.getByLabelText('settlements.line.amountLabel'), '150')
    expect(saveButton).toBeEnabled()
  })

  it('prefills the payment date with the SESSION date, not today', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '150')
    await user.click(screen.getByRole('checkbox'))

    // The money has to land in the month the session happened, not the month somebody
    // got round to ticking the box.
    expect(screen.getByLabelText('settlements.line.settledOnLabel')).toHaveValue(TARGET_DATE)

    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))
    await waitFor(() =>
      expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 150, TARGET_DATE),
    )
  })

  it('leaves an amount outstanding when the box is not ticked', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '150')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() =>
      expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 150, null),
    )
  })

  it('reads a comma as a decimal separator', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '149,50')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() =>
      expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 149.5, null),
    )
  })

  it('clearing the field removes the amount rather than saving a zero', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ amount: 150, settledOn: TARGET_DATE })],
    })
    const user = userEvent.setup()

    renderSection()

    await user.clear(await screen.findByLabelText('settlements.line.amountLabel'))
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    // "Not priced" and "free of charge" are different states, and only the second is a zero.
    await waitFor(() => expect(remove).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1'))
    expect(save).not.toHaveBeenCalled()
  })

  it('offers the last amount charged to this person without applying it', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ suggestedAmount: 150 })],
    })
    const user = userEvent.setup()

    renderSection()

    const field = await screen.findByLabelText('settlements.line.amountLabel')
    expect(field).toHaveValue('')

    await user.click(screen.getByRole('button', { name: /settlements.line.useLast/ }))
    expect(field).toHaveValue('150')
  })

  it('keeps a payer whose booking was cancelled on screen, flagged', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ orphaned: true, amount: 150, settledOn: TARGET_DATE })],
    })

    renderSection()

    // Dropping the row would make the money vanish from the screen while it still counts in the
    // monthly total — the worst of both readings.
    expect(await screen.findByText('Anna Kowalska')).toBeInTheDocument()
    expect(screen.getByText('settlements.line.orphaned')).toBeInTheDocument()
  })

  it('prices a guest like anybody else and shows the headcount', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ payerType: 'guest', payerId: 'guest-1', name: 'Ekipa z Krakowa', participants: 3 })],
    })
    const user = userEvent.setup()

    renderSection('event')

    expect(await screen.findByText(/settlements\.line\.guest/)).toBeInTheDocument()
    // The amount prices the whole booking, so the headcount has to be visible next to it.
    expect(screen.getByText(/settlements\.line\.people/)).toBeInTheDocument()

    await user.type(screen.getByLabelText('settlements.line.amountLabel'), '1800')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() =>
      expect(save).toHaveBeenCalledWith('event', 'target-1', 'guest', 'guest-1', 1800, null),
    )
  })

  it('fills every empty amount at once when several people are booked', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [
        line({ payerId: 'user-1', name: 'Anna Kowalska' }),
        line({ payerId: 'user-2', name: 'Piotr Nowak' }),
      ],
    })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.actions.setAll'), '600')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.setAll' }))
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    // Pricing a course means typing one number, not one per head.
    await waitFor(() => expect(save).toHaveBeenCalledTimes(2))
    expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 600, null)
    expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-2', 600, null)
  })

  it('says so plainly when nobody is booked yet', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [] })

    renderSection()

    expect(await screen.findByText('settlements.section.empty')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'settlements.actions.save' })).not.toBeInTheDocument()
  })
})

describe('SettlementSection — settled in bulk', () => {
  beforeEach(() => {
    getSection.mockReset()
    listSources.mockReset().mockResolvedValue([{ id: 'src-1', name: 'SP nr 12', archived: false }])
    assignSource.mockReset().mockResolvedValue(undefined)
  })

  it('replaces the per-participant fields rather than disabling them', async () => {
    getSection.mockResolvedValue({
      targetDate: TARGET_DATE,
      lines: [line()],
      payoutSourceId: 'src-1',
      payoutSourceName: 'SP nr 12',
    })

    renderSection()

    // There is nobody here to charge per head, so offering an amount field would invite a made-up
    // number — the field is absent, not greyed out.
    expect(await screen.findByText('settlements.section.bulk')).toBeInTheDocument()
    expect(screen.queryByLabelText('settlements.line.amountLabel')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'settlements.actions.save' })).not.toBeInTheDocument()
  })

  it('marks an ordinary session as settled in bulk', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSection()

    await user.click(await screen.findByRole('button', { name: 'settlements.section.markBulk' }))
    // The payer list is fetched only once the picker opens: this section loads on every slot an
    // admin opens, and almost none of them are settled in bulk.
    await waitFor(() => expect(listSources).toHaveBeenCalled())

    await user.selectOptions(screen.getByLabelText('settlements.section.bulkPayer'), 'src-1')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() => expect(assignSource).toHaveBeenCalledWith('slot', 'target-1', 'src-1'))
  })

  it('unmarks a session with null rather than a second endpoint', async () => {
    getSection.mockResolvedValue({
      targetDate: TARGET_DATE,
      lines: [],
      payoutSourceId: 'src-1',
      payoutSourceName: 'SP nr 12',
    })
    const user = userEvent.setup()

    renderSection()

    await user.click(await screen.findByRole('button', { name: 'settlements.section.clearBulk' }))
    await waitFor(() => expect(assignSource).toHaveBeenCalledWith('slot', 'target-1', null))
  })
})
