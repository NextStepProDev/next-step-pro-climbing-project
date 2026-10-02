import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { Building2, Coins, Lock, Trash2, X } from 'lucide-react'
import { Button } from '../ui/Button'
import { ConfirmModal } from '../ui/ConfirmModal'
import { DateInput } from '../ui/DateInput'
import { useModalClose } from '../ui/modalClose'
import { useToast } from '../../context/ToastContext'
import { adminSettlementsApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { parseAmount } from '../../utils/money'
import { parseCalendarDate } from '../../utils/calendarDate'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { useMoney } from './useMoney'
import type {
  LinePayment,
  PayoutSource,
  PaymentShare,
  SettlementLine,
  SettlementTarget,
} from '../../types'

interface SettlementSectionProps {
  target: SettlementTarget
  targetId: string
  /**
   * Reports whether anything has been typed and not yet saved, so the surrounding modal can ask
   * before the backdrop, the X or Escape throws it away. Called during render, never from an
   * effect — the guard is read by an event handler, and a report that costs a render arrives one
   * tick too late (see {@link useChildDirty}).
   */
  onDirtyChange?: (dirty: boolean) => void
}

/**
 * Local edits, keyed by payer. Amounts stay strings until save — see `parseAmount`.
 *
 * ⚠️ Two different things, and they are never merged again: `amount` is the CHARGE (what the session
 * costs this person) and `payment` is MONEY HANDED OVER NOW. Until V100 the second was a "received"
 * figure stored on the charge's own row, and paying off a backlog had to rewrite it — a 200 handed
 * over came back as 140, and "correcting" it counted the same notes twice. A payment is now its own
 * record, added and never edited.
 */
interface Draft {
  amount: string
  payment: string
  paidOn: string
}

const payerKey = (line: SettlementLine) => `${line.payerType}:${line.payerId}`

/**
 * What each participant owes for one session, and the money they hand over. Admin-only.
 *
 * ONE component for both call sites (slot and event), the same reason `AdminPrivateNote` is one:
 * a change to how money behaves is one edit, not two. It owns its own query and mutations, so the
 * host modal passes nothing but an address.
 *
 * The amounts are fetched from their own endpoint rather than riding along in the slot/event
 * payload, and that is what keeps them private: those payloads are served to anonymous visitors
 * and cached under calendarMonth/Week/Day. Callers must still gate on the admin role — this
 * component would happily render for anybody, and the 403 would arrive too late to be good UX.
 *
 * Nothing here decides which session a payment pays for. The server does that on every read,
 * oldest debt first, and sends back the split — so the screen only ever SHOWS where money went and
 * never moves it. That is what replaced the three "settle from credit / pay off the debt" buttons:
 * each of them existed to move money between rows by hand.
 *
 * Saving is batched. A per-row Save button would be the obvious build and is wrong for the shape
 * of the work: pricing a course means typing the same number three times, so one button that
 * writes every changed row is the difference between one click and six.
 */
export function SettlementSection({ target, targetId, onDirtyChange }: SettlementSectionProps) {
  const { t } = useTranslation('admin')
  const money = useMoney()
  const locale = useDateLocale()
  const queryClient = useQueryClient()
  /**
   * Saving is the last thing anybody does on a session, so the modal gets out of the way — and
   * when the admin arrived from the Settlements tab, the host modal's close is also what navigates
   * back to the list they came from (`deepLinkReturnTo` in `CalendarPage`), which is why this goes
   * through the modal rather than closing anything itself.
   *
   * `null` outside a `Modal`, and then nothing closes: the section is written to work anywhere,
   * and a component that assumed its own host would be a lie the day somebody embeds it in a page.
   */
  const closeModal = useModalClose()
  // A modal that vanishes is not, on its own, a confirmation that the money was written down.
  const { showToast } = useToast()
  const [drafts, setDrafts] = useState<Record<string, Draft>>({})
  const [bulkAmount, setBulkAmount] = useState('')
  const [invalidKeys, setInvalidKeys] = useState<string[]>([])
  // Apart from the charge's, so the field that is actually wrong is the one that turns red.
  const [invalidPaymentKeys, setInvalidPaymentKeys] = useState<string[]>([])
  const [picking, setPicking] = useState(false)
  const [deleting, setDeleting] = useState<{ line: SettlementLine; payment: LinePayment } | null>(null)

  const queryKey = ['admin', 'settlements', target, targetId]
  const { data, isLoading } = useQuery({
    queryKey,
    queryFn: () => adminSettlementsApi.getSection(target, targetId),
  })

  // Only once the picker is open. The section loads on every slot an admin opens, and the payer
  // list is of no use to the far more common case where nobody settles this in bulk.
  const { data: sources } = useQuery({
    queryKey: ['admin', 'settlements', 'sources'],
    queryFn: () => adminSettlementsApi.listSources(),
    enabled: picking,
  })

  const assignMutation = useMutation({
    mutationFn: (choice: { sourceId: string | null; subscriberId: string | null; name?: string }) =>
      adminSettlementsApi.assignSource(target, targetId, choice.sourceId, choice.subscriberId),
    onSuccess: (_result, choice) => {
      setPicking(false)
      queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] })
      // The nav dot counts sessions with no payer — naming one has to put it out now, not on the
      // next poll, or the admin who just fixed it sees the dot still lit and goes looking again.
      queryClient.invalidateQueries({ queryKey: ['admin', 'notifications'] })
      // Naming a payer ENDS the work on this session, exactly like saving the amounts does, so it
      // leaves the same way — one click back to wherever the admin came from, and a toast that
      // says what was written, because a modal that simply vanishes confirms nothing.
      //
      // ⚠️ Only when a payer was named. Clearing one puts the session back into per-participant
      // pricing, which is done HERE — closing then would take away the fields the admin just
      // asked for.
      if (choice.sourceId || choice.subscriberId) {
        showToast(t('settlements.section.bulkSaved', { name: choice.name ?? '' }))
        // ⚠️ Pushed before closing, not left to the next render. This section's close is the
        // modal's GUARDED close, and the guard reads the last value reported — which is still the
        // one from before this write. Without this, finishing the work would ask whether to
        // discard it.
        onDirtyChange?.(false)
        closeModal?.()
      }
    },
  })

  const targetDate = data?.targetDate ?? ''
  const lines = useMemo(() => data?.lines ?? [], [data])

  /**
   * The saved state of one row expressed as a draft, so "changed" is one comparison. The payment
   * date defaults to the SESSION's date, not today: money then lands in the month the session
   * happened rather than the month somebody got round to typing it in.
   */
  const saved = useMemo(() => {
    const map: Record<string, Draft> = {}
    for (const line of lines) {
      map[payerKey(line)] = {
        amount: line.amount === null ? '' : String(line.amount),
        payment: '',
        paidOn: targetDate,
      }
    }
    return map
  }, [lines, targetDate])

  const draftFor = (line: SettlementLine): Draft => drafts[payerKey(line)] ?? saved[payerKey(line)]

  const patch = (line: SettlementLine, change: Partial<Draft>) => {
    const key = payerKey(line)
    setInvalidKeys((keys) => keys.filter((k) => k !== key))
    setInvalidPaymentKeys((keys) => keys.filter((k) => k !== key))
    setDrafts((prev) => ({ ...prev, [key]: { ...(prev[key] ?? saved[key]), ...change } }))
  }

  const isRowDirty = (line: SettlementLine) => {
    const draft = drafts[payerKey(line)]
    if (!draft) return false
    // The date alone is not a change: an untouched picker beside an empty payment field is not
    // something anybody did.
    return draft.amount.trim() !== saved[payerKey(line)].amount || draft.payment.trim() !== ''
  }

  const dirtyLines = lines.filter(isRowDirty)

  /**
   * Writes every changed row: charges first, then the money. The order is not load-bearing for the
   * figures (the split is derived on read), but it is for the guard — a payment typed beside a
   * first-ever price would otherwise be checked against a ledger that has no charge for it yet.
   *
   * Returns one sentence per person who handed something over, for the toast.
   */
  const writeDrafts = async (): Promise<string[]> => {
    const invalid: string[] = []
    const payments: { line: SettlementLine; amount: number; paidOn: string }[] = []
    for (const line of dirtyLines) {
      const draft = draftFor(line)
      const raw = draft.payment.trim()
      if (raw === '') continue
      const amount = parseAmount(raw)
      // Zero is not a payment: "free" is a charge of 0, and a zero receipt would be a record of
      // nothing that still shows up in the list.
      if (amount === null || amount <= 0) invalid.push(payerKey(line))
      else payments.push({ line, amount, paidOn: draft.paidOn !== '' ? draft.paidOn : targetDate })
    }
    if (invalid.length > 0) {
      setInvalidPaymentKeys(invalid)
      throw new Error(t('settlements.errors.invalidPayment'))
    }

    for (const line of dirtyLines) {
      const draft = draftFor(line)
      const key = payerKey(line)
      const raw = draft.amount.trim()
      if (raw === saved[key].amount) continue
      if (raw === '') {
        // Clearing the field removes the charge: back to "not priced", a different state from
        // priced at zero. Any money it was covering is NOT lost — it becomes credit.
        await adminSettlementsApi.remove(target, targetId, line.payerType, line.payerId)
        continue
      }
      const amount = parseAmount(raw)
      if (amount === null) {
        invalid.push(key)
        continue
      }
      await adminSettlementsApi.save(target, targetId, line.payerType, line.payerId, amount)
    }
    if (invalid.length > 0) {
      setInvalidKeys(invalid)
      throw new Error(t('settlements.errors.invalidAmount'))
    }

    const reports: string[] = []
    for (const { line, amount, paidOn } of payments) {
      const result = await adminSettlementsApi.addPayment(
        line.payerType, line.payerId, amount, paidOn, { type: target, id: targetId },
      )
      // ⚠️ Dropped from the drafts the moment it is recorded. Should a LATER person's call fail,
      // retrying would otherwise enter this payment a second time.
      setDrafts((prev) => {
        const next = { ...prev }
        delete next[payerKey(line)]
        return next
      })
      reports.push(
        result.debt > 0
          ? t('settlements.actions.resultDebt', { name: line.name, amount: money(result.debt) })
          : result.credit > 0
            ? t('settlements.actions.resultCredit', { name: line.name, amount: money(result.credit) })
            : t('settlements.actions.resultSquare', { name: line.name }),
      )
    }
    return reports
  }

  const saveMutation = useMutation({
    mutationFn: writeDrafts,
    // Money may have been recorded before a failure, so the figures on screen are refreshed either way.
    onError: () => queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] }),
    onSuccess: (reports) => {
      setDrafts({})
      setInvalidKeys([])
      setInvalidPaymentKeys([])
      // The whole ['admin','settlements'] prefix, not just this session's key: the Settlements tab
      // lives under it too, so a figure written here shows up there without a manual refresh.
      // ⚠️ This has to happen even though the modal is about to close — the tab is unmounted at
      // this moment, so the invalidation is what marks its cached page stale.
      queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] })
      // Where each account now stands travels in the toast, because the modal is about to close —
      // and it is the account, not "paid": money covers the OLDEST debt first.
      showToast([t('settlements.actions.saved'), ...reports].join(' '))
      // ⚠️ The report is pushed first: `setDrafts({})` above has not rendered yet, so the guard
      // behind that close would still be holding `true` and would ask whether to discard the very
      // thing just written.
      onDirtyChange?.(false)
      closeModal?.()
    },
  })

  /**
   * The only correction a payment has. Stays in the modal: the admin is usually about to type the
   * right figure, and the rows have to show the debt the removal put back.
   */
  const deletePayment = useMutation({
    mutationFn: (paymentId: string) => adminSettlementsApi.deletePayment(paymentId),
    onSuccess: () => {
      setDeleting(null)
      queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] })
      showToast(t('settlements.deletePayment.done'))
    },
  })

  /**
   * Writes the same amount into EVERY row, not only the blank ones — which is what the button says
   * and what pricing a course actually is: one number, not one per head. A per-person discount is
   * then re-typed on that one line, which is rarer than the uniform case.
   */
  const applyToAll = () => {
    const amount = parseAmount(bulkAmount)
    if (amount === null) return
    setDrafts((prev) => {
      const next = { ...prev }
      for (const line of lines) {
        const key = payerKey(line)
        next[key] = { ...(next[key] ?? saved[key]), amount: String(amount) }
      }
      return next
    })
    setBulkAmount('')
  }

  const totals = useMemo(() => {
    let total = 0
    let paid = 0
    for (const line of lines) {
      const amount = parseAmount((drafts[payerKey(line)] ?? saved[payerKey(line)]).amount)
      if (amount === null) continue
      total += amount
      // What the server says is covered, from the saved figures — the split is never guessed here.
      paid += line.covered
    }
    return { total, paid }
  }, [lines, drafts, saved])

  const day = (date: string) => format(parseCalendarDate(date), 'dd.MM.yyyy', { locale })

  const describeShare = (share: PaymentShare) => {
    if (share.thisEntry) return t('settlements.payment.shareThis', { amount: money(share.amount) })
    if (share.monthlyFee) {
      return t('settlements.payment.shareFee', {
        amount: money(share.amount),
        month: format(parseCalendarDate(share.targetDate), 'LLLL yyyy', { locale }),
      })
    }
    return t('settlements.payment.shareOther', {
      amount: money(share.amount),
      title: share.targetTitle ?? t('settlements.payment.untitled'),
      date: day(share.targetDate),
    })
  }

  // Reported during render on purpose — see the prop's doc and useChildDirty. Above the early
  // return, so a refetch that flips `isLoading` cannot leave the host holding a stale `true`.
  // The bulk field counts even before it is applied: a price typed there and nowhere else is the
  // whole of what the admin has done so far.
  onDirtyChange?.(dirtyLines.length > 0 || bulkAmount.trim() !== '')

  if (isLoading) return null

  return (
    <div className="mt-4 rounded-lg border border-emerald-500/25 bg-emerald-500/5 p-3 space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="flex items-center gap-2 text-sm font-medium text-surface-200">
          <Coins className="w-4 h-4 text-emerald-500" />
          {t('settlements.section.title')}
        </span>
        {lines.length > 0 && (
          <span className="text-xs text-surface-400">
            {t('settlements.section.totals', {
              total: money(totals.total),
              paid: money(totals.paid),
            })}
          </span>
        )}
      </div>

      <p className="flex items-start gap-1.5 text-xs text-surface-400">
        <Lock className="w-3 h-3 mt-0.5 shrink-0" />
        {t('settlements.section.onlyYouHint')}
      </p>

      {data?.coveredBy ? (
        /* Settled in bulk: there is nobody here to charge per head, so the per-participant fields
           are not merely disabled but absent — offering them would invite an invented amount. */
        <div className="space-y-2">
          <p className="flex items-center gap-2 text-sm text-surface-200">
            <Building2 className="w-4 h-4 text-surface-400 shrink-0" />
            {t(
              data.coveredBy.kind === 'subscription'
                ? 'settlements.section.bulkSubscription'
                : 'settlements.section.bulk',
              { name: data.coveredBy.name },
            )}
          </p>
          <p className="text-xs text-surface-500">{t('settlements.section.bulkHint')}</p>
          {picking ? (
            <SourcePicker
              sources={sources ?? []}
              participants={lines}
              // ⚠️ One dropdown holds two kinds of payer, so a person's entry is prefixed to keep
              // it apart from an institution's. The coverage arrives as a bare id, and without the
              // same prefix it matched no entry at all: reopening the picker on a session covered
              // by a retainer showed "choose a payer" instead of the person covering it.
              current={
                data.coveredBy.kind === 'subscription'
                  ? `user:${data.coveredBy.id}`
                  : data.coveredBy.id
              }
              pending={assignMutation.isPending}
              error={assignMutation.error}
              onPick={(choice) => assignMutation.mutate(choice)}
              onCancel={() => setPicking(false)}
            />
          ) : (
            <div className="flex gap-2">
              <Button size="sm" variant="ghost" onClick={() => setPicking(true)}>
                {t('settlements.section.changeBulk')}
              </Button>
              <Button
                size="sm"
                variant="ghost"
                onClick={() => assignMutation.mutate({ sourceId: null, subscriberId: null })}
                loading={assignMutation.isPending}
              >
                {t('settlements.section.clearBulk')}
              </Button>
            </div>
          )}
        </div>
      ) : lines.length === 0 && !picking ? (
        <div className="space-y-2">
          <p className="text-sm text-surface-400">{t('settlements.section.empty')}</p>
          <Button size="sm" variant="ghost" onClick={() => setPicking(true)}>
            {t('settlements.section.markBulk')}
          </Button>
        </div>
      ) : picking ? (
        <SourcePicker
          sources={sources ?? []}
          participants={lines}
          current={null}
          pending={assignMutation.isPending}
          error={assignMutation.error}
          onPick={(choice) => assignMutation.mutate(choice)}
          onCancel={() => setPicking(false)}
        />
      ) : (
        <>
          {lines.length > 1 && (
            <div className="flex items-center gap-2">
              <input
                inputMode="decimal"
                value={bulkAmount}
                onChange={(e) => setBulkAmount(e.target.value)}
                placeholder={t('settlements.line.amountPlaceholder')}
                aria-label={t('settlements.actions.setAll')}
                className="w-24 bg-surface-800 border border-surface-600 rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500"
              />
              <Button
                size="sm"
                variant="ghost"
                onClick={applyToAll}
                disabled={parseAmount(bulkAmount) === null}
              >
                {t('settlements.actions.setAll')}
              </Button>
            </div>
          )}

          {/* One person is one block, and the rule separating them is doing real work: a row can
              grow by a status line and a list of payments while the row under it is one line, and
              without a rule it stops being obvious where one person's figures end. */}
          <ul className="divide-y divide-surface-800">
            {lines.map((line) => {
              const key = payerKey(line)
              const draft = draftFor(line)
              const otherDebt = Math.max(line.accountDebt - line.remaining, 0)
              return (
                /* `items-start`, not `items-center`: the name block is the tall one, and centring
                   the fields against it floated the amount into the middle of somebody's notes
                   instead of level with their name. */
                <li key={key} className="flex flex-wrap items-start gap-2 py-2 first:pt-0 last:pb-0">
                  <span className="min-w-[9rem] flex-1 text-sm text-surface-200">
                    {line.name}
                    {line.participants > 1 && (
                      // Deliberately not i18next's `count`: that switches on plural rules, and
                      // Polish needs four forms of a string this short earns none of. The headcount
                      // is here because the amount prices the whole booking, not a head.
                      <span className="text-surface-400">
                        {' '}
                        {t('settlements.line.people', { n: line.participants })}
                      </span>
                    )}
                    {line.payerType === 'guest' && (
                      <span className="text-surface-500"> · {t('settlements.line.guest')}</span>
                    )}
                    {line.orphaned && (
                      <span className="block text-xs text-amber-500">
                        {t('settlements.line.orphaned')}
                      </span>
                    )}
                    {/* Where this charge stands, as the SERVER worked it out from all of this
                        person's payments. Nothing on this screen can set it.

                        ⚠️ Colours: amber is work (chase it), green is done, and a credit stays
                        neutral — it is a memo for pricing, not a task and not an achievement. The
                        word carries the meaning; the colour only says whether it is work. */}
                    {line.amount !== null && (
                      line.remaining === 0 ? (
                        <span className="block text-xs text-emerald-400">
                          {line.amount === 0
                            ? t('settlements.line.statusFree')
                            : line.paidOn
                              ? t('settlements.line.statusPaid', { date: day(line.paidOn) })
                              : t('settlements.line.statusPaidNoDate')}
                        </span>
                      ) : (
                        <span className="block text-xs text-amber-500">
                          {line.covered > 0
                            ? t('settlements.line.statusShort', { amount: money(line.remaining) })
                            : t('settlements.line.statusUnpaid')}
                        </span>
                      )
                    )}
                    {/* The rest of the account, because a payment typed here pays the OLDEST debt
                        first — the admin has to know that before the toast tells him. */}
                    {otherDebt > 0 && (
                      <span className="block text-[11px] text-amber-500">
                        {t('settlements.line.otherDebt', { amount: money(otherDebt) })}
                      </span>
                    )}
                    {line.accountCredit > 0 && (
                      <span className="block text-[11px] text-surface-300">
                        {t('settlements.line.accountCredit', { amount: money(line.accountCredit) })}
                      </span>
                    )}
                  </span>

                  {/* The fields, as a table that is the same table on every row.

                      They used to be siblings of the name in one wrapping flex line, and there was
                      not enough width for them: inside this modal (452px of content) the controls
                      wrapped at a different point on every row, so the amount column sat at three
                      different x positions with four people booked.

                      So the controls get their own box with fixed tracks, which makes their total
                      width a constant and therefore the name column a constant too. Every cell is
                      placed explicitly, which is what lets the conditional bin be absent rather
                      than padded: with auto-placement a row without it would shift the next cell
                      up into its column.

                      Row 1 is the charge (and its bin), row 2 is money handed over now. The date
                      spans the last two columns: a native date field will not render much under
                      140px, and spanning puts the bin's column to work instead of widening the
                      grid at the name's expense. Below `sm` this is a plain wrapping flex. */}
                  <div className="flex flex-wrap items-start gap-2 sm:grid sm:grid-cols-[6rem_7rem_1.75rem] sm:gap-x-2 sm:gap-y-1">
                    <span className="flex flex-col sm:col-start-1 sm:row-start-1">
                      <input
                        inputMode="decimal"
                        value={draft.amount}
                        onChange={(e) => patch(line, { amount: e.target.value })}
                        placeholder={
                          line.suggestedAmount !== null
                            ? String(line.suggestedAmount)
                            : t('settlements.line.amountPlaceholder')
                        }
                        aria-label={t('settlements.line.amountLabel', { name: line.name })}
                        className={`w-24 bg-surface-800 border rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500 ${
                          invalidKeys.includes(key) ? 'border-rose-500' : 'border-surface-600'
                        }`}
                      />
                      <span className="mt-0.5 text-[11px] text-surface-500">
                        {t('settlements.line.dueHint')}
                      </span>
                      {line.suggestedAmount !== null && draft.amount.trim() === '' && (
                        <button
                          type="button"
                          onClick={() => patch(line, { amount: String(line.suggestedAmount) })}
                          className="mt-0.5 text-left text-[11px] text-primary-400 hover:text-primary-300 transition-colors"
                        >
                          {t('settlements.line.useLast', {
                            amount: money(line.suggestedAmount),
                          })}
                        </button>
                      )}
                    </span>

                    {saved[key].amount !== '' && (
                      <button
                        type="button"
                        onClick={() => patch(line, { amount: '' })}
                        aria-label={t('settlements.line.clear', { name: line.name })}
                        className="p-1.5 rounded text-rose-400/70 hover:text-rose-400 transition-colors sm:col-start-3 sm:row-start-1 sm:flex sm:items-center sm:justify-center sm:h-[1.875rem] sm:w-7 sm:p-0"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    )}

                    <span className="flex flex-col sm:col-start-1 sm:row-start-2">
                      <input
                        inputMode="decimal"
                        value={draft.payment}
                        onChange={(e) => patch(line, { payment: e.target.value })}
                        placeholder={t('settlements.line.paymentPlaceholder')}
                        aria-label={t('settlements.line.paymentLabel', { name: line.name })}
                        className={`w-24 bg-surface-800 border rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500 ${
                          invalidPaymentKeys.includes(key) ? 'border-rose-500' : 'border-surface-600'
                        }`}
                      />
                      <span className="mt-0.5 text-[11px] text-surface-500">
                        {t('settlements.line.paymentHint')}
                      </span>
                    </span>

                    {/* Only once there is money to date — a picker beside an empty field invites a
                        date with nothing behind it. */}
                    {draft.payment.trim() !== '' && (
                      <span className="flex flex-col sm:col-start-2 sm:row-start-2 sm:col-span-2">
                        <DateInput
                          value={draft.paidOn}
                          onChange={(value) => patch(line, { paidOn: value })}
                          aria-label={t('settlements.line.paymentDateLabel', { name: line.name })}
                          className="bg-surface-800 border border-surface-600 rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500 sm:w-full"
                        />
                        <span className="mt-0.5 text-[11px] text-surface-500">
                          {t('settlements.line.paymentDateHint')}
                        </span>
                      </span>
                    )}
                  </div>

                  {/* Every payment exactly as it was handed over, with where it went. This list is
                      the answer to "she paid 200, why does it say 140?" — it says 200, always. */}
                  {line.payments.length > 0 && (
                    <ul className="w-full space-y-1">
                      {line.payments.map((payment) => (
                        <li
                          key={payment.id}
                          className="flex items-start gap-2 rounded bg-surface-900/40 px-2 py-1 text-[11px] text-surface-300"
                        >
                          <span className="flex-1">
                            <span className="font-medium text-surface-200">
                              {t('settlements.payment.entry', {
                                date: day(payment.receivedOn),
                                amount: money(payment.amount),
                              })}
                            </span>
                            {!payment.enteredHere && (
                              <span className="text-surface-500">
                                {' '}{t('settlements.payment.elsewhere')}
                              </span>
                            )}
                            {(payment.shares.length > 0 || payment.unallocated > 0) && (
                              <span className="text-surface-400">
                                {' → '}
                                {[
                                  ...payment.shares.map(describeShare),
                                  ...(payment.unallocated > 0
                                    ? [t('settlements.payment.shareCredit', {
                                        amount: money(payment.unallocated),
                                      })]
                                    : []),
                                ].join(' · ')}
                              </span>
                            )}
                          </span>
                          <button
                            type="button"
                            onClick={() => setDeleting({ line, payment })}
                            aria-label={t('settlements.payment.delete', {
                              amount: money(payment.amount),
                              date: day(payment.receivedOn),
                            })}
                            className="shrink-0 rounded p-0.5 text-rose-400/70 hover:text-rose-400 transition-colors"
                          >
                            <X className="w-3 h-3" />
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              )
            })}
          </ul>

          {/* `role="alert"`: this text appears in response to pressing Save, and a screen reader
              otherwise gets nothing back from a button that refused. */}
          {saveMutation.isError && (
            <p role="alert" className="text-sm text-rose-400/80">
              {getErrorMessage(saveMutation.error)}
            </p>
          )}

          <div className="flex items-center justify-between gap-2">
            <Button size="sm" variant="ghost" onClick={() => setPicking(true)}>
              {t('settlements.section.markBulk')}
            </Button>
            <Button
              size="sm"
              variant="primary"
              onClick={() => saveMutation.mutate()}
              loading={saveMutation.isPending}
              disabled={dirtyLines.length === 0 || saveMutation.isPending}
            >
              {t('settlements.actions.save')}
            </Button>
          </div>
        </>
      )}

      <ConfirmModal
        isOpen={deleting !== null}
        onClose={() => setDeleting(null)}
        onConfirm={() => {
          if (deleting) deletePayment.mutate(deleting.payment.id)
        }}
        title={t('settlements.deletePayment.title')}
        message={t('settlements.deletePayment.message', {
          name: deleting?.line.name ?? '',
          amount: money(deleting?.payment.amount ?? 0),
          date: deleting ? day(deleting.payment.receivedOn) : '',
        })}
        variant="danger"
      />
      {deletePayment.isError && (
        <p role="alert" className="text-sm text-rose-400/80">
          {getErrorMessage(deletePayment.error)}
        </p>
      )}
    </div>
  )
}

/**
 * Picks which bulk payer a session belongs to.
 *
 * Archived payers are left out: they exist so old money keeps a name, not so new work can be filed
 * under a collaboration that ended.
 */
function SourcePicker({
  sources,
  participants,
  current,
  pending,
  error,
  onPick,
  onCancel,
}: {
  sources: PayoutSource[]
  participants: SettlementLine[]
  current: string | null
  pending: boolean
  /** Why the server refused. Shown here, under the dropdown that caused it. */
  error?: unknown
  /** `name` never reaches the server — it is what the toast says was written down. */
  onPick: (choice: { sourceId: string | null; subscriberId: string | null; name?: string }) => void
  onCancel: () => void
}) {
  const { t } = useTranslation('admin')
  const active = sources.filter((source) => !source.archived || source.id === current)
  // Guests have no account and no continuity, so no subscription can cover them — and neither can
  // an orphaned row, which is an amount belonging to somebody whose booking is gone. The server's
  // first check is whether the person is actually on the session, so listing them here would be
  // another choice that can only be refused after the click.
  const subscribers = participants.filter((line) => line.payerType === 'user' && !line.orphaned)

  // ⚠️ Mirrors the server's countPayers, orphaned rows excluded: those are amounts belonging to
  // somebody whose booking is gone, so they do not make the session shared. Getting this count
  // wrong in either direction is worse than not having it — too high greys out a legal choice,
  // too low offers one the server then refuses, which is the state this replaced.
  const payerCount = participants.filter((line) => !line.orphaned).length
  // A retainer covers a PERSON while the mark covers the SESSION, so on a session somebody else
  // is also on there is no way to spend it: marking it takes the whole session out of
  // per-participant pricing and the other payer's cash has nowhere to go. The server refuses it;
  // offering it here and failing afterwards just moves the refusal somewhere less useful.
  const subscriptionBlocked = payerCount > 1

  const [choice, setChoice] = useState(current ?? '')

  return (
    <div className="flex flex-wrap items-center gap-2">
      <select
        value={choice}
        onChange={(e) => setChoice(e.target.value)}
        aria-label={t('settlements.section.bulkPayer')}
        className="bg-surface-800 border border-surface-600 rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500"
      >
        <option value="">{t('settlements.section.choosePayer')}</option>
        {/* Two groups, because they are two different relationships — an institution that pays for
            a batch, and a client whose retainer already covers this. */}
        {subscribers.length > 0 && (
          // `disabled` on the optgroup rather than on each option: it greys the whole group and
          // takes every name in it out of reach in one attribute, and the label carries the reason
          // where the eye already is.
          <optgroup
            label={
              subscriptionBlocked
                ? t('settlements.section.groupSubscriptionShared')
                : t('settlements.section.groupSubscription')
            }
            disabled={subscriptionBlocked}
          >
            {subscribers.map((line) => (
              <option key={line.payerId} value={`user:${line.payerId}`}>{line.name}</option>
            ))}
          </optgroup>
        )}
        {active.length > 0 && (
          <optgroup label={t('settlements.section.groupSource')}>
            {active.map((source) => (
              <option key={source.id} value={source.id}>{source.name}</option>
            ))}
          </optgroup>
        )}
      </select>
      <Button
        size="sm"
        variant="primary"
        disabled={choice === '' || choice === current}
        loading={pending}
        onClick={() =>
          onPick(
            choice.startsWith('user:')
              ? {
                  sourceId: null,
                  subscriberId: choice.slice(5),
                  name: subscribers.find((line) => line.payerId === choice.slice(5))?.name,
                }
              : {
                  sourceId: choice,
                  subscriberId: null,
                  name: active.find((source) => source.id === choice)?.name,
                },
          )
        }
      >
        {t('settlements.actions.save')}
      </Button>
      <Button size="sm" variant="ghost" onClick={onCancel}>
        {t('settlements.section.cancel')}
      </Button>
      {/* ⚠️ The server refuses this for five real reasons (amounts already on the session, a payer
          who is not on it, a retainer on a shared session, no retainer for that month, both kinds
          at once) and none of them used to reach the screen: the picker simply stayed open, exactly
          as it looks before the click. Now that a successful pick CLOSES the modal, silence would
          be the only difference between "refused" and "saved". */}
      {error != null && (
        <p role="alert" className="w-full text-sm text-rose-400/80">{getErrorMessage(error)}</p>
      )}
      {active.length === 0 && subscribers.length === 0 && (
        <span className="text-xs text-surface-500">{t('settlements.section.noPayers')}</span>
      )}
      {/* Greyed-out options with no stated reason read as a bug in the app rather than a rule of
          the domain, and the reason does not fit in an optgroup label on a narrow screen. */}
      {subscriptionBlocked && subscribers.length > 0 && (
        <span className="w-full text-xs text-surface-500">
          {t('settlements.section.subscriptionSharedHint')}
        </span>
      )}
    </div>
  )
}
