import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SettlementSection } from './SettlementSection'
import { ModalCloseContext } from '../ui/modalClose'
import { ToastProvider } from '../../context/ToastContext'
import type { LinePayment, SettlementLine } from '../../types'

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
const addPayment = vi.fn()
const deletePayment = vi.fn()

vi.mock('../../api/client', () => ({
  adminSettlementsApi: {
    getSection: (...args: unknown[]) => getSection(...args),
    save: (...args: unknown[]) => save(...args),
    remove: (...args: unknown[]) => remove(...args),
    listSources: (...args: unknown[]) => listSources(...args),
    assignSource: (...args: unknown[]) => assignSource(...args),
    addPayment: (...args: unknown[]) => addPayment(...args),
    deletePayment: (...args: unknown[]) => deletePayment(...args),
  },
}))

const TARGET_DATE = '2026-08-14'
const HERE = { type: 'slot', id: 'target-1' }

/** Most sessions are priced per participant; the bulk fields are absent unless a test sets them. */
const bulkOff = { coveredBy: null }

function line(overrides: Partial<SettlementLine> = {}): SettlementLine {
  return {
    payerType: 'user',
    payerId: 'user-1',
    name: 'Anna Kowalska',
    participants: 1,
    orphaned: false,
    amount: null,
    covered: 0,
    remaining: 0,
    paidOn: null,
    accountDebt: 0,
    accountCredit: 0,
    payments: [],
    suggestedAmount: null,
    ...overrides,
  }
}

function payment(overrides: Partial<LinePayment> = {}): LinePayment {
  return {
    id: 'pay-1',
    amount: 200,
    receivedOn: TARGET_DATE,
    enteredHere: true,
    shares: [],
    unallocated: 0,
    ...overrides,
  }
}

/**
 * The section always renders inside a `Modal` in the app, so the harness supplies what a modal
 * supplies: the guarded close through context, and the toast host. `closeModal` is the spy the
 * close-on-save tests read.
 */
const closeModal = vi.fn()

function renderSection(
  target: 'slot' | 'event' = 'slot',
  onDirtyChange?: (dirty: boolean) => void,
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <ModalCloseContext.Provider value={closeModal}>
          <SettlementSection target={target} targetId="target-1" onDirtyChange={onDirtyChange} />
        </ModalCloseContext.Provider>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

/** Rendered with no modal around it, which is what `useModalClose` returning null has to survive. */
function renderSectionWithoutModal() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <SettlementSection target="slot" targetId="target-1" />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('SettlementSection', () => {
  beforeEach(() => {
    closeModal.mockReset()
    getSection.mockReset()
    save.mockReset().mockResolvedValue(undefined)
    remove.mockReset().mockResolvedValue(undefined)
    listSources.mockReset().mockResolvedValue([{ id: 'src-1', name: 'SP nr 12', archived: false }])
    assignSource.mockReset().mockResolvedValue(undefined)
    addPayment.mockReset().mockResolvedValue({ debt: 0, credit: 0 })
    deletePayment.mockReset().mockResolvedValue(undefined)
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

  it('saves the charge alone — money is never part of the price', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '150')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() => expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 150))
    expect(addPayment).not.toHaveBeenCalled()
  })

  it('reads a comma as a decimal separator', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '149,50')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() => expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 149.5))
  })

  it('records exactly what was handed over, dated with the SESSION day by default', async () => {
    // The owner's report: 140 due, 200 handed over. The 200 is what has to be stored — the split is
    // the server's business, and it never rewrites the figure typed here.
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    addPayment.mockResolvedValue({ debt: 0, credit: 40 })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '140')
    await user.type(screen.getByLabelText('settlements.line.paymentLabel'), '200')
    // The money lands in the month the session happened unless the admin says otherwise.
    expect(screen.getByLabelText('settlements.line.paymentDateLabel')).toHaveValue(TARGET_DATE)
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() =>
      expect(addPayment).toHaveBeenCalledWith('user', 'user-1', 200, TARGET_DATE, HERE),
    )
    expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 140)
    // The charge first, so the payment is checked against a ledger that already has it.
    expect(save.mock.invocationCallOrder[0]).toBeLessThan(addPayment.mock.invocationCallOrder[0])
    // Where the account stands goes into the toast, because the modal closes.
    expect(await screen.findByText(/settlements\.actions\.resultCredit/)).toBeInTheDocument()
  })

  it('records a payment on a row whose price is already saved without rewriting the price', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ amount: 150, remaining: 150 })],
    })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.paymentLabel'), '100')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() => expect(addPayment).toHaveBeenCalledWith('user', 'user-1', 100, TARGET_DATE, HERE))
    expect(save).not.toHaveBeenCalled()
  })

  it('refuses a zero payment instead of recording nothing as something', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line({ amount: 50 })] })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.paymentLabel'), '0')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    expect(await screen.findByText('settlements.errors.invalidPayment')).toBeInTheDocument()
    expect(addPayment).not.toHaveBeenCalled()
    expect(closeModal).not.toHaveBeenCalled()
  })

  it('shows every payment exactly as handed over, with where it went', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({
        amount: 140,
        covered: 140,
        paidOn: TARGET_DATE,
        accountCredit: 40,
        payments: [payment({
          amount: 200,
          shares: [
            { targetTitle: 'Trening', targetDate: '2026-08-07', thisEntry: false, monthlyFee: false, amount: 20 },
            { targetTitle: null, targetDate: TARGET_DATE, thisEntry: true, monthlyFee: false, amount: 140 },
          ],
          unallocated: 40,
        })],
      })],
    })

    renderSection()

    // The figure the owner remembers — never the 140 the old screen rewrote it to.
    expect(await screen.findByText('settlements.payment.entry')).toBeInTheDocument()
    expect(screen.getByText(/settlements\.payment\.shareOther · settlements\.payment\.shareThis · settlements\.payment\.shareCredit/))
      .toBeInTheDocument()
    expect(screen.getByText('settlements.line.statusPaid')).toBeInTheDocument()
    expect(screen.getByText('settlements.line.accountCredit')).toBeInTheDocument()
  })

  it('says a payment was typed at another session when it only covers part of this one', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ amount: 120, covered: 120, payments: [payment({ enteredHere: false })] })],
    })

    renderSection()

    expect(await screen.findByText('settlements.payment.elsewhere')).toBeInTheDocument()
  })

  it('deletes a payment only after asking, and keeps the modal open to enter the right one', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ amount: 150, covered: 150, payments: [payment({ id: 'pay-9', amount: 150 })] })],
    })
    const user = userEvent.setup()

    renderSection()

    await user.click(await screen.findByRole('button', { name: 'settlements.payment.delete' }))
    expect(deletePayment).not.toHaveBeenCalled()
    expect(screen.getByText('settlements.deletePayment.message')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'confirm' }))

    await waitFor(() => expect(deletePayment).toHaveBeenCalledWith('pay-9'))
    expect(closeModal).not.toHaveBeenCalled()
  })

  it('states what is still owed, and what is owed elsewhere first', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ amount: 150, covered: 100, remaining: 50, accountDebt: 80 })],
    })

    renderSection()

    expect(await screen.findByText('settlements.line.statusShort')).toBeInTheDocument()
    // 80 owed in total, 50 of it here: a payment typed now pays the other 30 first.
    expect(screen.getByText('settlements.line.otherDebt')).toBeInTheDocument()
  })

  it('clearing the field removes the amount rather than saving a zero', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ amount: 150, covered: 150, paidOn: TARGET_DATE })],
    })
    const user = userEvent.setup()

    renderSection()

    // No confirmation, even though money covers it: the payment is a record of its own, so removing
    // the price turns it into credit instead of making it disappear.
    await user.click(await screen.findByRole('button', { name: 'settlements.line.clear' }))
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
      lines: [line({ orphaned: true, amount: 150, covered: 150, payments: [payment()] })],
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
    await user.type(screen.getByLabelText('settlements.line.paymentLabel'), '1800')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() =>
      expect(save).toHaveBeenCalledWith('event', 'target-1', 'guest', 'guest-1', 1800),
    )
    expect(addPayment).toHaveBeenCalledWith('guest', 'guest-1', 1800, TARGET_DATE, { type: 'event', id: 'target-1' })
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
    expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 600)
    expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-2', 600)
  })

  it('says so plainly when nobody is booked yet', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [] })

    renderSection()

    expect(await screen.findByText('settlements.section.empty')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'settlements.actions.save' })).not.toBeInTheDocument()
  })

  it('does not enter a payment twice when a later person fails', async () => {
    // Payments are written one person at a time. If the second fails, the first is already on the
    // ledger — a retry must not enter it again.
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [
        line({ payerId: 'user-1', name: 'Anna Kowalska', amount: 100 }),
        line({ payerId: 'user-2', name: 'Piotr Nowak', amount: 100 }),
      ],
    })
    addPayment
      .mockResolvedValueOnce({ debt: 0, credit: 0 })
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValue({ debt: 0, credit: 0 })
    const user = userEvent.setup()

    renderSection()

    const [first, second] = await screen.findAllByLabelText('settlements.line.paymentLabel')
    await user.type(first, '100')
    await user.type(second, '100')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))
    expect(await screen.findByText('boom')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() => expect(addPayment).toHaveBeenCalledTimes(3))
    expect(addPayment.mock.calls.filter((call) => call[1] === 'user-1')).toHaveLength(1)
  })

  it('closes the modal once the money is written down, and says that it was', async () => {
    // Pricing is the last thing done on a session, and the admin who arrived from the Settlements
    // tab is sent back to it by the host modal's close — so this is also what refreshes that list.
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '150')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() => expect(closeModal).toHaveBeenCalled())
    // A modal that simply vanishes does not say whether anything was saved.
    expect(await screen.findByText('settlements.actions.saved')).toBeInTheDocument()
  })

  /* The host modal's unsaved-work guard reads the LAST thing this section reported, and after a
     save that report is still the one from before it: `setDrafts({})` has not rendered by the time
     the close is called. Without the push, finishing the work asked whether to discard it. */
  it('reports itself clean before it closes, so the guard does not ask about saved money', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const reports: boolean[] = []
    // What the guard would have seen at the instant the close was requested
    let dirtyWhenClosed: boolean | undefined
    closeModal.mockImplementation(() => { dirtyWhenClosed = reports.at(-1) })
    const user = userEvent.setup()

    renderSection('slot', (dirty) => reports.push(dirty))

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '150')
    expect(reports.at(-1)).toBe(true)

    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() => expect(closeModal).toHaveBeenCalled())
    expect(dirtyWhenClosed).toBe(false)
  })

  it('counts a typed payment as unsaved work, so the guard asks before throwing it away', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line({ amount: 50 })] })
    const reports: boolean[] = []
    const user = userEvent.setup()

    renderSection('slot', (dirty) => reports.push(dirty))

    await user.type(await screen.findByLabelText('settlements.line.paymentLabel'), '50')
    expect(reports.at(-1)).toBe(true)
  })

  it('saves normally when nothing is hosting it, rather than assuming a modal is there', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSectionWithoutModal()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '150')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() => expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 150))
    // ⚠️ Asserting only that the write went out passes even when the close then throws: the
    // request is made in `mutationFn`, before anything in `onSuccess` runs. What separates the two
    // is whether the mutation ends in success, so the assertion has to be that nothing was
    // reported as going wrong. Verified by deliberately calling the close unguarded.
    expect(await screen.findByText('settlements.actions.saved')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})

describe('SettlementSection — settled in bulk', () => {
  beforeEach(() => {
    closeModal.mockReset()
    getSection.mockReset()
    listSources.mockReset().mockResolvedValue([{ id: 'src-1', name: 'SP nr 12', archived: false }])
    assignSource.mockReset().mockResolvedValue(undefined)
  })

  it('replaces the per-participant fields rather than disabling them', async () => {
    getSection.mockResolvedValue({
      targetDate: TARGET_DATE,
      lines: [line()],
      coveredBy: { kind: 'source', id: 'src-1', name: 'SP nr 12' },
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

    await waitFor(() => expect(assignSource).toHaveBeenCalledWith('slot', 'target-1', 'src-1', null))
    // Naming a payer ends the work on this session exactly as saving the amounts does, so it leaves
    // the same way — and the toast names who was written down, since a vanished modal confirms
    // nothing. The two Save buttons of this section used to behave differently.
    await waitFor(() => expect(closeModal).toHaveBeenCalled())
    expect(await screen.findByText('settlements.section.bulkSaved')).toBeInTheDocument()
  })

  it('says why the payer was refused instead of leaving the picker as it was', async () => {
    // Now that a successful pick closes the modal, silence is the only thing that would tell a
    // refusal apart from a save — and the server refuses this for five real reasons.
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    assignSource.mockRejectedValue(new Error('Ten termin ma już wpisane kwoty'))
    const user = userEvent.setup()

    renderSection()

    await user.click(await screen.findByRole('button', { name: 'settlements.section.markBulk' }))
    await waitFor(() => expect(listSources).toHaveBeenCalled())
    await user.selectOptions(screen.getByLabelText('settlements.section.bulkPayer'), 'src-1')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ten termin ma już wpisane kwoty')
    expect(closeModal).not.toHaveBeenCalled()
  })

  it('puts the subscription out of reach when somebody else is on the session', async () => {
    // The server refuses this combination, so offering it and failing afterwards only moves the
    // refusal to a worse moment. A retainer covers one PERSON while the mark covers the SESSION,
    // and marking it would take the cash payer beside her out of pricing altogether.
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line(), line({ payerId: 'user-2', name: 'Piotr Nowak' })],
    })
    const user = userEvent.setup()

    renderSection()

    await user.click(await screen.findByRole('button', { name: 'settlements.section.markBulk' }))
    await waitFor(() => expect(listSources).toHaveBeenCalled())

    const subscriptions = screen.getByRole('group', {
      name: 'settlements.section.groupSubscriptionShared',
    })
    expect(subscriptions).toBeDisabled()
    // And the reason is on screen: a greyed-out option with no explanation reads as a broken app.
    expect(screen.getByText('settlements.section.subscriptionSharedHint')).toBeInTheDocument()

    // The institution stays available — a school really does pay for a whole group.
    await user.selectOptions(screen.getByLabelText('settlements.section.bulkPayer'), 'src-1')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))
    await waitFor(() => expect(assignSource).toHaveBeenCalledWith('slot', 'target-1', 'src-1', null))
  })

  it('still offers the subscription when a cancelled booking is the only other line', async () => {
    // An orphaned row is money taken from somebody who has since cancelled. They are not on the
    // session any more, so they do not make it shared — the server counts it the same way.
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line(), line({ payerId: 'user-2', name: 'Piotr Nowak', orphaned: true, amount: 150 })],
    })
    const user = userEvent.setup()

    renderSection()

    await user.click(await screen.findByRole('button', { name: 'settlements.section.markBulk' }))
    await waitFor(() => expect(listSources).toHaveBeenCalled())

    expect(screen.getByRole('group', { name: 'settlements.section.groupSubscription' }))
      .not.toBeDisabled()
  })

  it('does not offer a subscription to somebody who has cancelled', async () => {
    // Same defect as the greyed-out group, one row over: the server's first check is whether the
    // person is on the session at all, so a cancelled payer could only ever be refused after the
    // click. Their row stays visible above — the money is real — but they are not a payer here.
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ orphaned: true, amount: 150, covered: 150, paidOn: TARGET_DATE })],
    })
    const user = userEvent.setup()

    renderSection()

    await user.click(await screen.findByRole('button', { name: 'settlements.section.markBulk' }))
    await waitFor(() => expect(listSources).toHaveBeenCalled())

    expect(screen.queryByRole('group', { name: 'settlements.section.groupSubscription' }))
      .not.toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Anna Kowalska' })).not.toBeInTheDocument()
  })

  it('reopens the picker on the payer already covering the session', async () => {
    // Both kinds, because the bug was in exactly one of them: an institution's entry is its own
    // id, a person's carries a prefix so the two cannot collide inside one dropdown, and the
    // coverage arrives as a bare id either way.
    for (const [coveredBy, expected] of [
      [{ kind: 'subscription', id: 'user-1', name: 'Anna Kowalska' }, 'user:user-1'],
      [{ kind: 'source', id: 'src-1', name: 'SP nr 12' }, 'src-1'],
    ] as const) {
      getSection.mockResolvedValue({ targetDate: TARGET_DATE, lines: [line()], coveredBy })
      const user = userEvent.setup()

      const view = renderSection()
      await user.click(await screen.findByRole('button', { name: 'settlements.section.changeBulk' }))
      await waitFor(() => expect(listSources).toHaveBeenCalled())

      expect(screen.getByLabelText('settlements.section.bulkPayer')).toHaveValue(expected)
      view.unmount()
    }
  })

  it('unmarks a session with null rather than a second endpoint', async () => {
    getSection.mockResolvedValue({
      targetDate: TARGET_DATE,
      lines: [],
      coveredBy: { kind: 'source', id: 'src-1', name: 'SP nr 12' },
    })
    const user = userEvent.setup()

    renderSection()

    await user.click(await screen.findByRole('button', { name: 'settlements.section.clearBulk' }))
    await waitFor(() => expect(assignSource).toHaveBeenCalledWith('slot', 'target-1', null, null))
    // ⚠️ Clearing does NOT close, unlike naming a payer: it hands the session back to
    // per-participant pricing, which happens in this very section — closing would take away the
    // fields it just asked for.
    expect(closeModal).not.toHaveBeenCalled()
  })
  it('offers the participant\'s own subscription, not just institutions', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSection()

    await user.click(await screen.findByRole('button', { name: 'settlements.section.markBulk' }))
    await waitFor(() => expect(listSources).toHaveBeenCalled())

    // The whole point of this half: a session covered by the client's retainer leaves the pricing
    // queue WITHOUT a zero, so zero keeps meaning "free of charge".
    await user.selectOptions(
      screen.getByLabelText('settlements.section.bulkPayer'),
      'user:user-1',
    )
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() =>
      expect(assignSource).toHaveBeenCalledWith('slot', 'target-1', null, 'user-1'),
    )
  })

  it('does not offer a guest, who has no account to hold a subscription', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ payerType: 'guest', payerId: 'guest-1', name: 'Marek' })],
    })
    const user = userEvent.setup()

    renderSection()

    await user.click(await screen.findByRole('button', { name: 'settlements.section.markBulk' }))
    await waitFor(() => expect(listSources).toHaveBeenCalled())

    expect(screen.queryByRole('option', { name: 'Marek' })).not.toBeInTheDocument()
  })
})
