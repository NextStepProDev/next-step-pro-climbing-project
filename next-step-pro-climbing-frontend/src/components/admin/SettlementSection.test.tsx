import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SettlementSection } from './SettlementSection'
import { ModalCloseContext } from '../ui/modalClose'
import { ToastProvider } from '../../context/ToastContext'
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
const settleOutstanding = vi.fn()

vi.mock('../../api/client', () => ({
  adminSettlementsApi: {
    getSection: (...args: unknown[]) => getSection(...args),
    save: (...args: unknown[]) => save(...args),
    remove: (...args: unknown[]) => remove(...args),
    listSources: (...args: unknown[]) => listSources(...args),
    assignSource: (...args: unknown[]) => assignSource(...args),
    settleOutstanding: (...args: unknown[]) => settleOutstanding(...args),
  },
}))

const TARGET_DATE = '2026-08-14'

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
    paidAmount: 0,
    balance: 0,
    credit: 0,
    settledOn: null,
    suggestedAmount: null,
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
    settleOutstanding.mockReset().mockResolvedValue({ settled: 1, balance: 0 })
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
      expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 150, 150, TARGET_DATE),
    )
  })

  it('leaves an amount outstanding when the box is not ticked', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '150')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() =>
      expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 150, null, null),
    )
  })

  it('reads a comma as a decimal separator', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '149,50')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() =>
      expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 149.5, null, null),
    )
  })

  it('clearing the field removes the amount rather than saving a zero', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ amount: 150, paidAmount: 150, settledOn: TARGET_DATE })],
    })
    const user = userEvent.setup()

    renderSection()

    await user.clear(await screen.findByLabelText('settlements.line.amountLabel'))
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    // "Not priced" and "free of charge" are different states, and only the second is a zero.
    await waitFor(() => expect(remove).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1'))
    expect(save).not.toHaveBeenCalled()
  })

  it('asks before the bin throws away a row that is holding money', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ amount: 150, paidAmount: 150, settledOn: TARGET_DATE })],
    })
    const user = userEvent.setup()

    renderSection()

    await user.click(await screen.findByRole('button', { name: 'settlements.line.clear' }))

    // Nothing has happened yet: the payment leaves revenue and the client's balance with nothing
    // anywhere recording that it arrived, which is the one irreversible thing this section does.
    expect(screen.getByText('settlements.clearPaid.message')).toBeInTheDocument()
    expect(screen.getByLabelText('settlements.line.amountLabel')).toHaveValue('150')

    await user.click(screen.getByRole('button', { name: 'confirm' }))
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() => expect(remove).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1'))
  })

  it('clears a row with no money against it without asking anything', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ amount: 150, paidAmount: 0 })],
    })
    const user = userEvent.setup()

    renderSection()

    await user.click(await screen.findByRole('button', { name: 'settlements.line.clear' }))

    // Correcting a price typed by mistake is the ordinary gesture here, and a confirmation on it
    // would be noise on every one of them.
    expect(screen.queryByText('settlements.clearPaid.message')).not.toBeInTheDocument()
    expect(screen.getByLabelText('settlements.line.amountLabel')).toHaveValue('')
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
      lines: [line({ orphaned: true, amount: 150, paidAmount: 150, settledOn: TARGET_DATE })],
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
      expect(save).toHaveBeenCalledWith('event', 'target-1', 'guest', 'guest-1', 1800, null, null),
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
    expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 600, null, null)
    expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-2', 600, null, null)
  })

  it('says so plainly when nobody is booked yet', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [] })

    renderSection()

    expect(await screen.findByText('settlements.section.empty')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'settlements.actions.save' })).not.toBeInTheDocument()
  })

  it('offers to pay an unpaid session out of the credit that person is holding', async () => {
    // ⚠️ The reported case, and note the balance: he overpaid 50 two months ago and this session is
    // priced at 50, so his account NETS TO ZERO while 50 of his money still sits on the older row.
    // Driving the offer off the net figure would hide it from precisely the client it is for.
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ amount: 50, paidAmount: 0, balance: 0, credit: 50 })],
    })
    const user = userEvent.setup()

    renderSection()

    expect(await screen.findByRole('button', { name: 'settlements.line.spendCreditLabel' })).toBeInTheDocument()
    // The chip states the account, which really is square; the button is what says there is money
    // parked that can close this row.
    expect(screen.queryByText('settlements.line.credit')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'settlements.line.spendCreditLabel' }))

    // The same endpoint the Settlements tab uses, called with nothing received: the server pulls the
    // credit back into a pool and pays the debts off with it. Dated with the SESSION's day, matching
    // what this row suggests as a payment date.
    await waitFor(() =>
      expect(settleOutstanding).toHaveBeenCalledWith('user', 'user-1', TARGET_DATE, 0),
    )
    // And it reports where the account landed rather than claiming this row is the one that got
    // paid — the credit goes to the oldest debt, which need not be the session on screen.
    expect(await screen.findByText('settlements.line.creditSpent')).toBeInTheDocument()
  })

  it('does not claim to have spent a credit that another tab already spent', async () => {
    // The figure on screen is whatever the query cache holds, so this race is ordinary: the server
    // finds nothing to pull into the pool and reaches no rows. Announcing "credit spent" on a
    // request that moved no money would be a lie about money.
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ amount: 50, paidAmount: 0, balance: 0, credit: 50 })],
    })
    settleOutstanding.mockResolvedValue({ settled: 0, balance: -50 })
    const user = userEvent.setup()

    renderSection()

    await user.click(await screen.findByRole('button', { name: 'settlements.line.spendCreditLabel' }))

    expect(await screen.findByText('settlements.line.creditGone')).toBeInTheDocument()
    expect(screen.queryByText('settlements.line.creditSpent')).not.toBeInTheDocument()
  })

  it('names a leftover debt as a debt rather than as a negative balance', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ amount: 200, paidAmount: 0, balance: -150, credit: 50 })],
    })
    settleOutstanding.mockResolvedValue({ settled: 1, balance: -150 })
    const user = userEvent.setup()

    renderSection()

    await user.click(await screen.findByRole('button', { name: 'settlements.line.spendCreditLabel' }))

    // "Saldo: -150,00 zł" is arithmetic; the line already has a word for this state.
    expect(await screen.findByText('settlements.line.creditSpentOwing')).toBeInTheDocument()
  })

  it('does not offer to spend a credit on a session that is already paid', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ amount: 50, paidAmount: 50, settledOn: TARGET_DATE, balance: 50, credit: 50 })],
    })

    renderSection()

    expect(await screen.findByText('settlements.line.credit')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'settlements.line.spendCreditLabel' }),
    ).not.toBeInTheDocument()
  })

  it('asks for the amount to be saved before it can be paid from the credit', async () => {
    // Something already arrived on this row, so the one-click path is not offered (it writes the
    // charge with nothing received first, which would erase that payment).
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ amount: 50, paidAmount: 20, balance: -30, credit: 50, settledOn: TARGET_DATE })],
    })
    const user = userEvent.setup()

    renderSection()

    const amount = await screen.findByLabelText('settlements.line.amountLabel')
    await user.clear(amount)
    await user.type(amount, '80')

    // The endpoint works on rows already in the database, so a figure still in the draft has nothing
    // for the credit to land against — offering the button here would pay off some other session.
    expect(
      screen.queryByRole('button', { name: 'settlements.line.spendCreditLabel' }),
    ).not.toBeInTheDocument()
    expect(screen.getByText('settlements.line.saveFirst')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'settlements.actions.saveAndSpendCredit' }),
    ).not.toBeInTheDocument()
  })

  it('prices a session and spends the credit in one click', async () => {
    // The reported case: 90 left over, a 100 session, nothing handed over. The ordinary Save closes
    // the modal, and the per-row button only appears on a saved row — so this used to take a save,
    // a reopen and a second click, and the hint pointing at it was on a screen that then vanished.
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ name: 'Bernadeta M.', balance: 90, credit: 90 })],
    })
    settleOutstanding.mockResolvedValue({ settled: 2, balance: -10 })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '100')
    expect(screen.getByText('settlements.line.spendOnSave')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'settlements.actions.saveAndSpendCredit' }))

    // The charge alone first — the payment is the pool's to record, or it would count twice.
    await waitFor(() =>
      expect(settleOutstanding).toHaveBeenCalledWith('user', 'user-1', TARGET_DATE, 0),
    )
    expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 100, null, null)
    expect(save.mock.invocationCallOrder[0]).toBeLessThan(
      settleOutstanding.mock.invocationCallOrder[0],
    )
    // The modal closes, so what the credit did has to travel in the toast.
    expect(
      await screen.findByText(/Bernadeta M\.: settlements\.line\.creditSpentOwing/),
    ).toBeInTheDocument()
    expect(closeModal).toHaveBeenCalled()
  })

  it('adds what was handed over to the credit, on the date it was handed over', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ balance: 90, credit: 90 })],
    })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '100')
    await user.click(screen.getByLabelText('settlements.line.settledLabel'))
    await user.type(screen.getByLabelText('settlements.line.receivedLabel'), '10')
    const date = screen.getByLabelText('settlements.line.settledOnLabel')
    await user.clear(date)
    await user.type(date, '2026-08-20')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.saveAndSpendCredit' }))

    // 10 + 90 pays the 100. The ordinary save would have written 10 of 100 and left the 90 parked
    // on an older session: "owes 90" and "holds 90" about one person at once.
    await waitFor(() =>
      expect(settleOutstanding).toHaveBeenCalledWith('user', 'user-1', '2026-08-20', 10),
    )
    expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 100, null, null)
  })

  it('will not guess what arrived when the box is ticked over a credit', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ balance: 90, credit: 90 })],
    })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '100')
    await user.click(screen.getByLabelText('settlements.line.settledLabel'))
    await user.click(screen.getByRole('button', { name: 'settlements.actions.saveAndSpendCredit' }))

    // "Paid in full" on top of a credit would hand the credit straight back as a new overpayment.
    expect(await screen.findByText('settlements.errors.receivedRequired')).toBeInTheDocument()
    expect(save).not.toHaveBeenCalled()
    expect(settleOutstanding).not.toHaveBeenCalled()
  })

  it('does not offer the combined save to somebody holding no credit', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '100')
    expect(
      screen.queryByRole('button', { name: 'settlements.actions.saveAndSpendCredit' }),
    ).not.toBeInTheDocument()
    expect(screen.queryByText('settlements.line.spendOnSave')).not.toBeInTheDocument()
  })

  it('refuses to save a settled row with nothing received instead of silently dropping the tick', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '50')
    await user.click(screen.getByRole('checkbox'))
    await user.type(screen.getByLabelText('settlements.line.receivedLabel'), '0')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    // The server drops the payment date when nothing arrived, so this used to come back unsettled
    // with the box unticked and nothing saying why. The case behind it has its own answer.
    expect(await screen.findByText('settlements.errors.zeroReceived')).toBeInTheDocument()
    expect(save).not.toHaveBeenCalled()
  })

  it('still lets a free session be ticked, where a zero received is the honest figure', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '0')
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() =>
      expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 0, 0, TARGET_DATE),
    )
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

  it('stays open when the save was refused, or the reason goes with it', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '50')
    await user.click(screen.getByRole('checkbox'))
    await user.type(screen.getByLabelText('settlements.line.receivedLabel'), '0')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    expect(await screen.findByText('settlements.errors.zeroReceived')).toBeInTheDocument()
    expect(closeModal).not.toHaveBeenCalled()
  })

  it('keeps the modal open after spending a credit, which reports its result here', async () => {
    // The credit pays the OLDEST debt first, so the sentence it leaves behind is the only place
    // the admin learns where the money actually went.
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ amount: 100, paidAmount: 0, credit: 40, balance: 40 })],
    })
    settleOutstanding.mockResolvedValue({ settled: 1, balance: 0 })
    const user = userEvent.setup()

    renderSection()

    await user.click(await screen.findByRole('button', { name: 'settlements.line.spendCreditLabel' }))

    expect(await screen.findByText('settlements.line.creditSpent')).toBeInTheDocument()
    expect(closeModal).not.toHaveBeenCalled()
  })

  it('saves normally when nothing is hosting it, rather than assuming a modal is there', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSectionWithoutModal()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '150')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() =>
      expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 150, null, null),
    )
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
      lines: [line({ orphaned: true, amount: 150, paidAmount: 150, settledOn: TARGET_DATE })],
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

  it('records what actually arrived when it differs from the charge', async () => {
    getSection.mockResolvedValue({ ...bulkOff, targetDate: TARGET_DATE, lines: [line()] })
    const user = userEvent.setup()

    renderSection()

    await user.type(await screen.findByLabelText('settlements.line.amountLabel'), '150')
    await user.click(screen.getByRole('checkbox'))
    // A two-hundred note against a hundred-and-fifty session — the ordinary way cash goes.
    await user.type(screen.getByLabelText('settlements.line.receivedLabel'), '200')
    await user.click(screen.getByRole('button', { name: 'settlements.actions.save' }))

    await waitFor(() =>
      expect(save).toHaveBeenCalledWith('slot', 'target-1', 'user', 'user-1', 150, 200, TARGET_DATE),
    )
  })

  it('shows what the person already has on account while you type', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ balance: 50 })],
    })

    renderSection()

    // The figure is only any use at the moment the next amount is entered, so it lives on the line.
    expect(await screen.findByText('settlements.line.credit')).toBeInTheDocument()
  })

  it('shows a shortfall as owed rather than as a credit', async () => {
    getSection.mockResolvedValue({
      ...bulkOff,
      targetDate: TARGET_DATE,
      lines: [line({ balance: -50 })],
    })

    renderSection()

    expect(await screen.findByText('settlements.line.debt')).toBeInTheDocument()
  })
})
