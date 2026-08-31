import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Coins, Lock, Trash2 } from 'lucide-react'
import { Button } from '../ui/Button'
import { DateInput } from '../ui/DateInput'
import { adminSettlementsApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { formatPln, parseAmount } from '../../utils/money'
import type { SettlementLine, SettlementTarget } from '../../types'

interface SettlementSectionProps {
  target: SettlementTarget
  targetId: string
}

/** Local edits, keyed by payer. The amount stays a string until save — see `parseAmount`. */
interface Draft {
  amount: string
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

  const queryKey = ['admin', 'settlements', target, targetId]
  const { data, isLoading } = useQuery({
    queryKey,
    queryFn: () => adminSettlementsApi.getSection(target, targetId),
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
        await adminSettlementsApi.save(
          target,
          targetId,
          line.payerType,
          line.payerId,
          amount,
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

  const clearRow = (line: SettlementLine) => patch(line, { amount: '', settled: false })

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
      if (draft.settled) paid += amount
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

      {lines.length === 0 ? (
        <p className="text-sm text-surface-400">{t('settlements.section.empty')}</p>
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

          <div className="flex justify-end">
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
