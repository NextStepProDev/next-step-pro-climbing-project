import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Building2, Coins, Lock, Trash2 } from 'lucide-react'
import { Button } from '../ui/Button'
import { ConfirmModal } from '../ui/ConfirmModal'
import { DateInput } from '../ui/DateInput'
import { useModalClose } from '../ui/modalClose'
import { useToast } from '../../context/ToastContext'
import { adminSettlementsApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { formatPln, parseAmount } from '../../utils/money'
import type { PayoutSource, SettlementLine, SettlementTarget } from '../../types'

interface SettlementSectionProps {
  target: SettlementTarget
  targetId: string
  /**
   * Reports whether any amount has been typed and not yet saved, so the surrounding modal can ask
   * before the backdrop, the X or Escape throws it away. Called during render, never from an
   * effect — the guard is read by an event handler, and a report that costs a render arrives one
   * tick too late (see {@link useChildDirty}).
   */
  onDirtyChange?: (dirty: boolean) => void
}

/** Local edits, keyed by payer. The amount stays a string until save — see `parseAmount`. */
interface Draft {
  /** What it costs. */
  amount: string
  /** What actually arrived. Kept apart from the charge, because cash rarely makes them equal. */
  received: string
  settled: boolean
  settledOn: string
}

const payerKey = (line: SettlementLine) => `${line.payerType}:${line.payerId}`

/**
 * What each participant owes for one session, and whether they have paid. Admin-only.
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
 * Saving is batched. A per-row Save button would be the obvious build and is wrong for the shape
 * of the work: pricing a course means typing the same number three times, so one button that
 * writes every changed row is the difference between one click and six.
 */
export function SettlementSection({ target, targetId, onDirtyChange }: SettlementSectionProps) {
  const { t, i18n } = useTranslation('admin')
  const queryClient = useQueryClient()
  /**
   * Saving the amounts is the last thing anybody does on a session, so the modal gets out of the
   * way — and when the admin arrived from the Settlements tab, the host modal's close is also what
   * navigates back to the list they came from (`deepLinkReturnTo` in `CalendarPage`), which is why
   * this goes through the modal rather than closing anything itself.
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
  const [picking, setPicking] = useState(false)
  /**
   * What came of the last "pay from credit", so the row can report it.
   *
   * ⚠️ `settled` is carried, not just the balance: the endpoint reaches nothing when the credit has
   * been spent from another tab since this section loaded, and a row that announced "credit spent"
   * on a request that moved no money would be a lie about money — the one kind this feature cannot
   * afford. The stale figure is on screen for as long as the query cache holds it, so the race is
   * ordinary rather than exotic.
   */
  const [creditResult, setCreditResult] =
    useState<{ key: string; settled: number; balance: number } | null>(null)

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
      // Naming a payer ENDS the work on this session, exactly like saving the amounts does, so it
      // leaves the same way — one click back to wherever the admin came from, and a toast that
      // says what was written, because a modal that simply vanishes confirms nothing.
      //
      // ⚠️ Only when a payer was named. Clearing one puts the session back into per-participant
      // pricing, which is done HERE — closing then would take away the fields the admin just
      // asked for. The credit button still keeps the modal open too: its answer (the money may
      // have gone to an older session) has to be read on this screen.
      if (choice.sourceId || choice.subscriberId) {
        showToast(t('settlements.section.bulkSaved', { name: choice.name ?? '' }))
        // ⚠️ Pushed before closing, not left to the next render. This section's close is now the
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
   * The saved state of one row expressed as a draft, so that "changed" is one comparison instead of
   * three nullable ones. The payment date falls back to the SESSION's date, not today: money then
   * lands in the month the session happened rather than the month somebody got round to ticking it.
   */
  const saved = useMemo(() => {
    const map: Record<string, Draft> = {}
    for (const line of lines) {
      map[payerKey(line)] = {
        amount: line.amount === null ? '' : String(line.amount),
        received: line.paidAmount === 0 ? '' : String(line.paidAmount),
        settled: line.settledOn !== null,
        settledOn: line.settledOn ?? targetDate,
      }
    }
    return map
  }, [lines, targetDate])

  const draftFor = (line: SettlementLine): Draft => drafts[payerKey(line)] ?? saved[payerKey(line)]

  const patch = (line: SettlementLine, change: Partial<Draft>) => {
    const key = payerKey(line)
    setInvalidKeys((keys) => keys.filter((k) => k !== key))
    // The report of a spent credit is about the figure that was there when it was spent; leaving it
    // above a row somebody is now retyping would make it a claim about the new one.
    setCreditResult((result) => (result?.key === key ? null : result))
    setDrafts((prev) => ({ ...prev, [key]: { ...(prev[key] ?? saved[key]), ...change } }))
  }

  const isRowDirty = (line: SettlementLine) => {
    const key = payerKey(line)
    const draft = drafts[key]
    if (!draft) return false
    const base = saved[key]
    if (draft.amount.trim() !== base.amount) return true
    if (draft.received.trim() !== base.received) return true
    if (draft.settled !== base.settled) return true
    // The date only counts while the row claims to be settled; an untouched picker behind an
    // unchecked box is not a change anybody made.
    return draft.settled && draft.settledOn !== base.settledOn
  }

  const dirtyLines = lines.filter(isRowDirty)

  /**
   * Rows where "Save and settle from credit" can do its job in one click: a person holding a credit
   * is being charged, and nothing has been received on this row yet.
   *
   * ⚠️ `paidAmount === 0` is load-bearing. That path writes the charge FIRST with nothing received
   * and only then pays it from the pool — so on a row already holding a payment, the first write
   * would erase money that had arrived. Such a row keeps the ordinary save and the per-row button.
   */
  const spendableLines = dirtyLines.filter((line) => {
    const amount = parseAmount(draftFor(line).amount.trim())
    return line.credit > 0 && line.paidAmount === 0 && amount !== null && amount > 0
  })

  /**
   * Writes every changed row. Rows in `spendFor` are written as the charge alone and then settled
   * through the same pool endpoint as the per-row "settle from credit" button, with whatever the
   * admin typed as received: 10 zł handed over plus a 90 zł credit pays a 100 zł session, where the
   * ordinary save would record 10 of 100 and leave the 90 parked on an older session — the screen
   * would then say "owes 90" and "holds 90" about the same person at once.
   *
   * Returns one sentence per settled person, for the toast.
   */
  const writeDrafts = async (spendFor: Set<string>): Promise<string[]> => {
    const invalid: string[] = []
    // ⚠️ Ticking "settled" and typing a zero cannot be saved, and used to be swallowed. The server
    // drops the payment date whenever nothing arrived — a date with no money behind it would read
    // as paid on every screen while contributing nothing to revenue — so the row came back
    // unsettled with the box unticked and nothing said why. The case behind it is real and has
    // its own answer: the client owes nothing because he is spending an overpayment, and that is
    // the "spend credit" button, which actually moves the money. On the spending path a zero is
    // exactly that case, so it is allowed there.
    const zeroed = dirtyLines.filter((line) => {
      if (spendFor.has(payerKey(line))) return false
      const draft = draftFor(line)
      const amount = parseAmount(draft.amount.trim())
      // A zero-amount row is free of charge, where a zero received is the honest figure.
      return draft.settled && amount !== null && amount > 0 && parseAmount(draft.received) === 0
    })
    if (zeroed.length > 0) {
      setInvalidKeys(zeroed.map(payerKey))
      throw new Error(t('settlements.errors.zeroReceived'))
    }
    // ⚠️ On the spending path an empty "received" under a ticked box is refused, not read as "paid
    // in full" the way the ordinary save reads it. With a credit in play "in full" is ambiguous —
    // the whole charge again on top of the credit hands the person a fresh overpayment of exactly
    // the credit — and money is the one place this section must not guess.
    const unstated = dirtyLines.filter((line) => {
      const draft = draftFor(line)
      return spendFor.has(payerKey(line)) && draft.settled && draft.received.trim() === ''
    })
    if (unstated.length > 0) {
      setInvalidKeys(unstated.map(payerKey))
      throw new Error(t('settlements.errors.receivedRequired'))
    }
    for (const line of dirtyLines) {
      const draft = draftFor(line)
      const key = payerKey(line)
      const raw = draft.amount.trim()
      if (raw === '') {
        // Clearing the field removes the row: back to "not priced", which is a different state
        // from priced at zero.
        if (saved[key].amount !== '') {
          await adminSettlementsApi.remove(target, targetId, line.payerType, line.payerId)
        }
        continue
      }
      const amount = parseAmount(raw)
      if (amount === null) {
        invalid.push(key)
        continue
      }
      // The charge alone when the pool pays it below; the payment would otherwise be counted twice.
      const deferred = spendFor.has(key)
      // Ticking "settled" without touching the received field means the charge arrived in full,
      // which is the ordinary case and must stay a single click.
      const received = draft.settled && !deferred
        ? (parseAmount(draft.received) ?? amount)
        : null
      await adminSettlementsApi.save(
        target,
        targetId,
        line.payerType,
        line.payerId,
        amount,
        received,
        draft.settled && !deferred ? draft.settledOn : null,
      )
    }
    if (invalid.length > 0) {
      setInvalidKeys(invalid)
      throw new Error(t('settlements.errors.invalidAmount'))
    }

    const reports: string[] = []
    for (const line of dirtyLines) {
      const key = payerKey(line)
      if (!spendFor.has(key)) continue
      const draft = draftFor(line)
      const result = await adminSettlementsApi.settleOutstanding(
        line.payerType,
        line.payerId,
        // The date the admin picked when money changed hands; otherwise the session's own day,
        // the same default the per-row button uses.
        draft.settled && draft.settledOn !== '' ? draft.settledOn : targetDate,
        draft.settled ? (parseAmount(draft.received) ?? 0) : 0,
      )
      // ⚠️ Dropped from the drafts the moment its money moved. Should a LATER person's call fail,
      // retrying would otherwise write this row again as "charge, nothing received" — wiping the
      // payment the pool just recorded on it.
      setDrafts((prev) => {
        const next = { ...prev }
        delete next[key]
        return next
      })
      reports.push(`${line.name}: ${
        result.balance < 0
          ? t('settlements.line.creditSpentOwing', {
              amount: formatPln(-result.balance, i18n.language),
            })
          : t('settlements.line.creditSpent', {
              balance: formatPln(result.balance, i18n.language),
            })
      }`)
    }
    return reports
  }

  const saveMutation = useMutation({
    mutationFn: (spend: boolean) =>
      writeDrafts(new Set(spend ? spendableLines.map(payerKey) : [])),
    // Money may have moved before a failure (the charge written, an earlier person settled), so the
    // figures on screen are refreshed either way.
    onError: () => queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] }),
    onSuccess: (reports) => {
      setDrafts({})
      setInvalidKeys([])
      // The whole ['admin','settlements'] prefix, not just this session's key: the Settlements tab
      // lives under it too, so a figure written here shows up there without a manual refresh.
      // ⚠️ This has to happen even though the modal is about to close — the tab is unmounted at
      // this moment, so the invalidation is what marks its cached page stale and makes React Query
      // refetch it when it mounts again a tick later.
      queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] })
      // What the credit did travels in the toast, because the modal is about to close — and it is
      // the balance, not "paid": the pool pays the OLDEST debt first, which need not be this one.
      showToast([t('settlements.actions.saved'), ...reports].join(' '))
      // Naming a bulk payer leaves the same way, for the same reason — both end the work on this
      // session. What still stays open is the credit button, which reports a result the admin has
      // to read here (the money may have gone to an older session), and clearing a payer, which
      // hands the session back to the fields in this very section.
      // ⚠️ The report is pushed first: `setDrafts({})` above has not rendered yet, so the guard
      // behind that close would still be holding `true` and would ask whether to discard the very
      // thing just written.
      onDirtyChange?.(false)
      closeModal?.()
    },
  })

  /**
   * Spends what this person already left with you on what they still owe.
   *
   * The same endpoint the Settlements tab uses, called with nothing received: the server pulls the
   * credit back into a pool, resets the rows that were holding it to exact, and pays off the open
   * debts oldest first. Reusing it rather than adding a "pay this one row from credit" route is
   * deliberate — a second route would be a second copy of that pool loop, and it would have to
   * disagree with the oldest-first rule to do anything different.
   *
   * ⚠️ So the money may well land on an OLDER session than the one on screen, which is correct and
   * is why the button reports the resulting balance instead of claiming this row is now paid.
   */
  const spendCredit = useMutation({
    mutationFn: async (line: SettlementLine) => {
      const result = await adminSettlementsApi.settleOutstanding(
        line.payerType,
        line.payerId,
        // The session's own date, matching what this row suggests as a payment date. The tab
        // defaults to today instead, because there one transfer covers a month of sessions.
        targetDate,
        0,
      )
      return { key: payerKey(line), settled: result.settled, balance: result.balance }
    },
    onSuccess: (result) => {
      setCreditResult(result)
      queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] })
    },
  })

  const clearRow = (line: SettlementLine) => patch(line, { amount: '', received: '', settled: false })

  /**
   * Clearing the amount deletes the row on save, and a row can be holding real money: a payment, or
   * the credit somebody's overpayment left behind. That money then leaves revenue and leaves the
   * client's balance with nothing anywhere recording that it ever arrived — the only irreversible
   * thing this section can do, and it used to take one click and no question.
   *
   * <p>Asked only when there is money to lose. A confirmation on an empty row would be noise on the
   * ordinary gesture of correcting a price that was typed by mistake.
   */
  const [clearingMoney, setClearingMoney] = useState<SettlementLine | null>(null)
  const requestClear = (line: SettlementLine) => {
    if (line.paidAmount > 0) {
      setClearingMoney(line)
      return
    }
    clearRow(line)
  }

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
      const draft = drafts[payerKey(line)] ?? saved[payerKey(line)]
      const amount = parseAmount(draft.amount)
      if (amount === null) continue
      total += amount
      // What arrived, not what was charged — the two are the whole point of the second field.
      if (draft.settled) paid += parseAmount(draft.received) ?? amount
    }
    return { total, paid }
  }, [lines, drafts, saved])

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
              total: formatPln(totals.total, i18n.language),
              paid: formatPln(totals.paid, i18n.language),
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
              grow to four lines (a debt, an offer to spend a credit, what spending it did) while
              the row under it is one line, and without a rule it stops being obvious where one
              person's figures end. */}
          <ul className="divide-y divide-surface-800">
            {lines.map((line) => {
              const key = payerKey(line)
              const draft = draftFor(line)
              return (
                /* `items-start`, not `items-center`: the name block is the tall one, and centring
                   the fields against it floated the amount into the middle of somebody's balance
                   notes instead of level with their name. */
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
                    {/* The standing balance, in front of you at the moment you type the next
                        amount — which is the only moment it is any use.

                        ⚠️ The two states are NOT a plus and a minus on one scale, and colouring
                        them as if they were was wrong. A debt is a task: chase it, so it takes
                        amber, which already means "outstanding" in seven other places here. A
                        credit is only something to remember while pricing, so it stays neutral —
                        green in this app means "done", which a credit is not, and red means "broken
                        or destructive", which a debt is not either. The word carries the meaning;
                        the colour only says whether it is work. */}
                    {line.balance !== 0 && (
                      <span
                        className={`block text-xs ${
                          line.balance > 0 ? 'text-surface-300' : 'text-amber-500'
                        }`}
                      >
                        {t(
                          line.balance > 0
                            ? 'settlements.line.credit'
                            : 'settlements.line.debt',
                          { amount: formatPln(Math.abs(line.balance), i18n.language) },
                        )}
                      </span>
                    )}

                    {/* Spending that credit, offered where the pricing happens. Driven by `credit`
                        and NOT by `balance`: once this session is priced the two debts cancel and
                        the net figure reads zero, which is the exact moment the offer is wanted.
                        Only against a SAVED row with something still owed: the endpoint works on
                        rows already in the database, so a figure still sitting in the draft has
                        nothing for the credit to land on — hence the hint rather than a button that
                        would silently pay off some other session instead. */}
                    {/* A row being priced right now points at the combined button below — this is
                        the case that used to have nothing at all: an unsaved row fails the
                        condition under it, so pricing somebody's first session over a credit said
                        nothing about the credit until the modal was saved, closed and reopened. */}
                    {spendableLines.includes(line) ? (
                      <span className="block text-[11px] text-surface-400">
                        {t('settlements.line.spendOnSave')}
                      </span>
                    ) : line.credit > 0 && line.amount !== null && line.paidAmount < line.amount && (
                      isRowDirty(line) ? (
                        <span className="block text-[11px] text-surface-500">
                          {t('settlements.line.saveFirst')}
                        </span>
                      ) : (
                        <button
                          type="button"
                          onClick={() => spendCredit.mutate(line)}
                          disabled={spendCredit.isPending}
                          // Same reasoning as the "settled" checkbox below: the visible text is the
                          // same sentence on every row, so without this a screen reader announces
                          // two identical "pay from credit" buttons and the person they belong to
                          // is only inferable from reading order.
                          aria-label={t('settlements.line.spendCreditLabel', { name: line.name })}
                          className="mt-0.5 block text-left text-[11px] text-primary-400 hover:text-primary-300 disabled:text-surface-500 transition-colors"
                        >
                          {t('settlements.line.spendCredit', {
                            amount: formatPln(
                              Math.min(line.credit, line.amount - line.paidAmount),
                              i18n.language,
                            ),
                          })}
                        </button>
                      )
                    )}

                    {/* What actually happened, because it need not be this row: the credit pays the
                        OLDEST debt first, so the honest report is where the account now stands.

                        Three outcomes, three sentences. A leftover debt is named as a debt rather
                        than shown as a negative balance — "saldo: -150,00 zł" is arithmetic, and the
                        line already has a word for that state two rows up. */}
                    {creditResult?.key === key && (
                      <span className="block text-[11px] text-surface-400">
                        {creditResult.settled === 0
                          ? t('settlements.line.creditGone')
                          : creditResult.balance < 0
                            ? t('settlements.line.creditSpentOwing', {
                                amount: formatPln(-creditResult.balance, i18n.language),
                              })
                            : t('settlements.line.creditSpent', {
                                balance: formatPln(creditResult.balance, i18n.language),
                              })}
                      </span>
                    )}
                  </span>

                  {/* The fields, as a table that is the same table on every row.

                      They used to be siblings of the name in one wrapping flex line, and there is
                      not enough width for them: measured inside this modal (452px of content) a
                      settled row needs 482px, so it wrapped — and it wrapped at a different point
                      on every row, because the number of controls differs per row (a settled row
                      carries two more than an unsettled one, a never-priced row carries no bin).
                      With four people booked, the amount column sat at three different x positions
                      and the payment date dropped onto its own line UNDER somebody's name, where it
                      reads as belonging to the person below.

                      So the controls get their own box with fixed tracks, which makes their total
                      width a constant and therefore the name column a constant too. Every cell is
                      placed explicitly, which is what lets the conditional ones be absent rather
                      than padded with empty boxes: with auto-placement, a row without a bin would
                      slide its "received" field up into the bin's column.

                      Two rows rather than one, because 482 > 452 however the columns are shared
                      out. Amounts sit above each other in one column, and the payment date lands
                      under the tick that reveals it. Below `sm` this is the old wrapping flex —
                      there the modal is narrower than these tracks.

                      ⚠️ The date spans the last two columns rather than sitting in one of its own,
                      and that is what pays for the name. A native date field will not render much
                      under 140px, so a column wide enough to hold it alone is 35px wider than the
                      tick above it needs — width taken straight off the only column with words in
                      it. Spanning puts the bin's column to work and buys the name back 32px, which
                      is the difference between a full surname on one line and a hyphen break. */}
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
                            amount: formatPln(line.suggestedAmount, i18n.language),
                          })}
                        </button>
                      )}
                    </span>

                    {/* `min-h` so the word sits level with the middle of the input beside it rather
                        than at the top of the row, which is where `items-start` would otherwise put
                        a single line of 12px text. */}
                    <label className="flex items-center gap-1.5 text-xs text-surface-300 sm:col-start-2 sm:row-start-1 sm:min-h-[1.875rem]">
                      <input
                        type="checkbox"
                        checked={draft.settled}
                        onChange={(e) => patch(line, { settled: e.target.checked })}
                        // The visible label is the same word on every row, so without this a screen
                        // reader announces four identical "settled" checkboxes and the person they
                        // belong to is only inferable from reading order.
                        aria-label={t('settlements.line.settledLabel', { name: line.name })}
                        className="accent-emerald-500"
                      />
                      {t('settlements.line.settled')}
                    </label>

                    {saved[key].amount !== '' && (
                      <button
                        type="button"
                        onClick={() => requestClear(line)}
                        aria-label={t('settlements.line.clear', { name: line.name })}
                        className="p-1.5 rounded text-rose-400/70 hover:text-rose-400 transition-colors sm:col-start-3 sm:row-start-1 sm:flex sm:items-center sm:justify-center sm:h-[1.875rem] sm:w-7 sm:p-0"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    )}

                    {/* Second row of the same table: what arrived, and when. Ordered after the tick
                        in the DOM as well as on screen, so tabbing follows what the eye does — the
                        two only agree because these cells are placed where the flow would put them
                        anyway. */}
                    {draft.settled && (
                      <>
                        <span className="flex flex-col sm:col-start-1 sm:row-start-2">
                          <input
                            inputMode="decimal"
                            value={draft.received}
                            onChange={(e) => patch(line, { received: e.target.value })}
                            placeholder={draft.amount || t('settlements.line.receivedPlaceholder')}
                            aria-label={t('settlements.line.receivedLabel', { name: line.name })}
                            className="w-24 bg-surface-800 border border-surface-600 rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500"
                          />
                          <span className="mt-0.5 text-[11px] text-surface-500">
                            {t('settlements.line.receivedHint')}
                          </span>
                        </span>

                        <span className="flex flex-col sm:col-start-2 sm:row-start-2 sm:col-span-2">
                          <DateInput
                            value={draft.settledOn}
                            onChange={(value) => patch(line, { settledOn: value })}
                            aria-label={t('settlements.line.settledOnLabel', { name: line.name })}
                            className="bg-surface-800 border border-surface-600 rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500 sm:w-full"
                          />
                          {/* The date used to sit next to the tick that explains it; on its own line
                              it needs to say what it is. */}
                          <span className="mt-0.5 text-[11px] text-surface-500">
                            {t('settlements.line.settledOnHint')}
                          </span>
                        </span>
                      </>
                    )}
                  </div>
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
          {spendCredit.isError && (
            <p role="alert" className="text-sm text-rose-400/80">
              {getErrorMessage(spendCredit.error)}
            </p>
          )}

          <div className="flex items-center justify-between gap-2">
            <Button size="sm" variant="ghost" onClick={() => setPicking(true)}>
              {t('settlements.section.markBulk')}
            </Button>
            <div className="flex flex-wrap items-center justify-end gap-2">
              <Button
                size="sm"
                variant={spendableLines.length > 0 ? 'secondary' : 'primary'}
                onClick={() => saveMutation.mutate(false)}
                loading={saveMutation.isPending && saveMutation.variables === false}
                disabled={dirtyLines.length === 0 || saveMutation.isPending}
              >
                {t('settlements.actions.save')}
              </Button>
              {/* A separate action, not a checkbox beside Save — same reasoning as "Save and send
                  invitations": spending a credit is not a property of saving. Offered only where it
                  can work in one step (see `spendableLines`), and primary when offered, because a
                  priced session for somebody holding a credit is exactly when it is wanted. */}
              {spendableLines.length > 0 && (
                <Button
                  size="sm"
                  variant="primary"
                  onClick={() => saveMutation.mutate(true)}
                  loading={saveMutation.isPending && saveMutation.variables === true}
                  disabled={saveMutation.isPending}
                >
                  {t('settlements.actions.saveAndSpendCredit')}
                </Button>
              )}
            </div>
          </div>
        </>
      )}

      <ConfirmModal
        isOpen={clearingMoney !== null}
        onClose={() => setClearingMoney(null)}
        onConfirm={() => {
          if (clearingMoney) clearRow(clearingMoney)
          setClearingMoney(null)
        }}
        title={t('settlements.clearPaid.title')}
        message={t('settlements.clearPaid.message', {
          name: clearingMoney?.name ?? '',
          amount: formatPln(clearingMoney?.paidAmount ?? 0, i18n.language),
        })}
        variant="danger"
      />
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
