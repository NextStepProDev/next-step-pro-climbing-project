import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Building2, Coins, Lock, Trash2 } from 'lucide-react'
import { Button } from '../ui/Button'
import { DateInput } from '../ui/DateInput'
import { adminSettlementsApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { formatPln, parseAmount } from '../../utils/money'
import type { PayoutSource, SettlementLine, SettlementTarget } from '../../types'

interface SettlementSectionProps {
  target: SettlementTarget
  targetId: string
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
export function SettlementSection({ target, targetId }: SettlementSectionProps) {
  const { t, i18n } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [drafts, setDrafts] = useState<Record<string, Draft>>({})
  const [bulkAmount, setBulkAmount] = useState('')
  const [invalidKeys, setInvalidKeys] = useState<string[]>([])
  const [picking, setPicking] = useState(false)

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
    mutationFn: (choice: { sourceId: string | null; subscriberId: string | null }) =>
      adminSettlementsApi.assignSource(target, targetId, choice.sourceId, choice.subscriberId),
    onSuccess: () => {
      setPicking(false)
      queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] })
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

  const saveMutation = useMutation({
    mutationFn: async () => {
      const invalid: string[] = []
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
        // Ticking "settled" without touching the received field means the charge arrived in full,
        // which is the ordinary case and must stay a single click.
        const received = draft.settled
          ? (parseAmount(draft.received) ?? amount)
          : null
        await adminSettlementsApi.save(
          target,
          targetId,
          line.payerType,
          line.payerId,
          amount,
          received,
          draft.settled ? draft.settledOn : null,
        )
      }
      if (invalid.length > 0) {
        setInvalidKeys(invalid)
        throw new Error(t('settlements.errors.invalidAmount'))
      }
    },
    onSuccess: () => {
      setDrafts({})
      setInvalidKeys([])
      // The whole ['admin','settlements'] prefix, not just this session's key: the Settlements tab
      // lives under it too, so a figure written here shows up there without a manual refresh.
      queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] })
    },
  })

  const clearRow = (line: SettlementLine) => patch(line, { amount: '', received: '', settled: false })

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
              current={data.coveredBy.id}
              pending={assignMutation.isPending}
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

          <ul className="space-y-2">
            {lines.map((line) => {
              const key = payerKey(line)
              const draft = draftFor(line)
              return (
                <li key={key} className="flex flex-wrap items-center gap-2">
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
                  </span>

                  <span className="flex flex-col">
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

                  {draft.settled && (
                    <span className="flex flex-col">
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
                  )}

                  <label className="flex items-center gap-1.5 text-xs text-surface-300">
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

                  {draft.settled && (
                    <DateInput
                      value={draft.settledOn}
                      onChange={(value) => patch(line, { settledOn: value })}
                      aria-label={t('settlements.line.settledOnLabel', { name: line.name })}
                      className="bg-surface-800 border border-surface-600 rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500"
                    />
                  )}

                  {saved[key].amount !== '' && (
                    <button
                      type="button"
                      onClick={() => clearRow(line)}
                      aria-label={t('settlements.line.clear', { name: line.name })}
                      className="p-1.5 rounded text-rose-400/70 hover:text-rose-400 transition-colors"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  )}
                </li>
              )
            })}
          </ul>

          {saveMutation.isError && (
            <p className="text-sm text-rose-400/80">{getErrorMessage(saveMutation.error)}</p>
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
              disabled={dirtyLines.length === 0}
            >
              {t('settlements.actions.save')}
            </Button>
          </div>
        </>
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
  onPick,
  onCancel,
}: {
  sources: PayoutSource[]
  participants: SettlementLine[]
  current: string | null
  pending: boolean
  onPick: (choice: { sourceId: string | null; subscriberId: string | null }) => void
  onCancel: () => void
}) {
  const { t } = useTranslation('admin')
  const active = sources.filter((source) => !source.archived || source.id === current)
  // Guests have no account and no continuity, so no subscription can cover them.
  const subscribers = participants.filter((line) => line.payerType === 'user')

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
              ? { sourceId: null, subscriberId: choice.slice(5) }
              : { sourceId: choice, subscriberId: null },
          )
        }
      >
        {t('settlements.actions.save')}
      </Button>
      <Button size="sm" variant="ghost" onClick={onCancel}>
        {t('settlements.section.cancel')}
      </Button>
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
