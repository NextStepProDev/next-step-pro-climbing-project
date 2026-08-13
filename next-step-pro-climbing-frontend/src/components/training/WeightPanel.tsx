import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format, parseISO, subDays } from 'date-fns'
import clsx from 'clsx'
import { Scale, TrendingDown, TrendingUp, TriangleAlert } from 'lucide-react'
import { Button } from '../ui/Button'
import { ConfirmModal } from '../ui/ConfirmModal'
import { QueryError } from '../ui/QueryError'
import { WeightChart } from './WeightChart'
import { getErrorMessage } from '../../utils/errors'
import { nowInWarsaw, todayInWarsaw } from '../../utils/calendarDate'
import type { TrainingCalendarAdapter } from './trainingCalendarAdapter'
import type { WeightRange } from '../../types'

const RANGES: WeightRange[] = ['RECENT', 'YEAR', 'ALL']

interface WeightPanelProps {
  api: TrainingCalendarAdapter
  // 'me' for the athlete's own tab, athleteId in the coach panel
  scopeKey: string
  isCoachView?: boolean
}

/**
 * Morning body weight: a chart with the 7-day trend, and — for the athlete only — the one
 * field that feeds it.
 *
 * <p>Two things are deliberately asymmetric. The athlete alone can record a weight (recording
 * somebody else's is not the coach's call), and the coach alone sees the rapid-loss warning:
 * an alarming red box on your own dashboard every morning is not a health intervention, a
 * conversation with your coach is.
 */
export function WeightPanel({ api, scopeKey, isCoachView }: WeightPanelProps) {
  const { t, i18n } = useTranslation('training')
  const queryClient = useQueryClient()

  // Default kept at RECENT so nobody's chart changes, or grows, without them asking
  const [range, setRange] = useState<WeightRange>('RECENT')

  // The range is PART OF THE KEY: without it, switching would show the previous range's data
  // from cache while the new request is still in flight
  const queryKey = ['trainingCalendar', 'weight', scopeKey, range]
  const weightQuery = useQuery({
    queryKey,
    // Trimming happens server-side; the client never receives more than it displays
    queryFn: () => api.getWeights(range),
    // Weight only ever changes from this panel, so no polling — but a remount must be fresh
    refetchOnMount: 'always',
    // Keeps the old chart on screen while the new range loads instead of flashing a skeleton
    placeholderData: (previous) => previous,
  })

  const today = todayInWarsaw()
  const [draft, setDraft] = useState('')
  const [measuredOn, setMeasuredOn] = useState(today)
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const mutations = api.weightMutations

  /**
     * Refetch every cached range rather than writing the mutation's response into the current
     * key. The write endpoints always answer with the DEFAULT range, so dropping that payload
     * into a YEAR or ALL cache would quietly replace a year of history with four months.
     */
  const refreshAfterWrite = () => {
    queryClient.invalidateQueries({ queryKey: ['trainingCalendar', 'weight', scopeKey] })
    // A weigh-in can close a weight goal, and deleting moves the trend the cards measure from
    queryClient.invalidateQueries({ queryKey: ['trainingCalendar', 'goals', scopeKey] })
  }

  const saveMutation = useMutation({
    mutationFn: (weightKg: number) => mutations!.save({ measuredOn, weightKg }),
    onSuccess: () => {
      setDraft('')
      // Back to today on purpose: catching up is the exception, and a date left on last
      // Tuesday would overwrite the wrong day at the next morning's weigh-in
      setMeasuredOn(today)
      setError(null)
      refreshAfterWrite()
    },
    onError: (err) => setError(getErrorMessage(err)),
  })

  const deleteMutation = useMutation({
    mutationFn: (day: string) => mutations!.remove(day),
    onSuccess: () => {
      setError(null)
      refreshAfterWrite()
    },
    onError: (err) => setError(getErrorMessage(err)),
  })

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    // Polish keyboards produce a comma; refusing it would be a pointless papercut
    const value = Number.parseFloat(draft.replace(',', '.'))
    if (Number.isNaN(value)) {
      setError(t('weight.invalid'))
      return
    }
    // `max` on the input is a hint, not a guarantee — the server rejects this too
    if (measuredOn > today) {
      setError(t('weight.futureDate'))
      return
    }
    setError(null)
    saveMutation.mutate(value)
  }

  if (weightQuery.isError) {
    return <QueryError error={weightQuery.error} onRetry={() => weightQuery.refetch()} />
  }
  const data = weightQuery.data
  if (!data) return null

  // The panel used to hide itself from the coach when empty. That was a trap: an athlete whose
  // only readings predate the default range left the coach with nothing to click, and so no way
  // to widen the range and find them. One athlete is open at a time, so the panel is not clutter.
  const emptyMessage = isCoachView
    ? 'weight.emptyCoach'
    : range === 'RECENT'
      ? 'weight.emptyAthlete'
      : 'weight.emptyRange'

  const fmt = (n: number) =>
    n.toLocaleString(i18n.language, { minimumFractionDigits: 1, maximumFractionDigits: 1 })

  // Derived from the server's own window width, so the picker can never offer a day the
  // server would refuse (or hide one it would accept)
  const oldestRecordable = format(subDays(nowInWarsaw(), data.backfillDays - 1), 'yyyy-MM-dd')
  const existingForDay = data.entries.find((entry) => entry.measuredOn === measuredOn)

  const change = data.weeklyChangePercent
  const losing = change != null && change < 0

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 p-4 space-y-3">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-2 text-sm font-medium text-surface-300">
          <Scale className="w-4 h-4 text-surface-400" />
          {t('weight.title')}
        </div>
        {data.currentTrendKg != null && (
          <div className="flex items-baseline gap-2">
            <span className="text-lg font-bold text-surface-100 tabular-nums">
              {fmt(data.currentTrendKg)} kg
            </span>
            {change != null && change !== 0 && (
              <span
                className={`inline-flex items-center gap-1 text-xs font-semibold tabular-nums ${
                  losing ? 'text-green-400' : 'text-amber-400'
                }`}
              >
                {losing ? <TrendingDown className="w-3.5 h-3.5" /> : <TrendingUp className="w-3.5 h-3.5" />}
                {change > 0 ? '+' : ''}
                {fmt(change)}%{t('weight.perWeek')}
              </span>
            )}
          </div>
        )}
      </div>

      {isCoachView && data.rapidLoss && (
        <div
          role="alert"
          className="flex items-start gap-2 p-2.5 rounded-lg bg-amber-500/10 border border-amber-500/25 text-sm text-amber-300"
        >
          <TriangleAlert className="w-4 h-4 shrink-0 mt-0.5" />
          {t('weight.rapidLoss')}
        </div>
      )}

      {/* Named windows, not a free date span: the API has no unbounded request shape */}
      <div className="flex gap-1" role="group" aria-label={t('weight.rangeLabel')}>
        {RANGES.map((option) => (
          <button
            key={option}
            type="button"
            onClick={() => setRange(option)}
            aria-pressed={range === option}
            className={clsx(
              'px-2.5 py-1 text-xs font-medium rounded-lg border transition-colors',
              range === option
                ? 'bg-surface-800 border-surface-600 text-surface-100'
                : 'bg-transparent border-surface-800 text-surface-400 hover:text-surface-200',
            )}
          >
            {t(`weight.range.${option.toLowerCase()}`)}
          </button>
        ))}
      </div>

      {data.entries.length === 0 ? (
        <p className="text-sm text-surface-500">{t(emptyMessage)}</p>
      ) : (
        <>
          <WeightChart
            entries={data.entries}
            isStale={weightQuery.isFetching}
            onDelete={!isCoachView && mutations ? setConfirmDelete : undefined}
          />
          {/* Explains why a met target may not have closed its goal yet */}
          {data.currentTrendKg != null && !data.trendConfirmed && (
            <p className="text-xs text-surface-500">
              {t('weight.trendUnconfirmed', { count: data.trendSampleCount })}
            </p>
          )}
        </>
      )}

      {!isCoachView && mutations && (
        <form onSubmit={submit} className="space-y-2">
          <div className="flex items-end gap-2 flex-wrap">
            <div>
              <label htmlFor="weight-input" className="block text-xs text-surface-400 mb-1">
                {t('weight.todayLabel')}
              </label>
              <input
                id="weight-input"
                inputMode="decimal"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                placeholder="71,2"
                className="w-28 bg-surface-800 border border-surface-700 rounded-lg px-3 py-2 text-surface-100 tabular-nums"
              />
            </div>
            <div>
              <label htmlFor="weight-date" className="block text-xs text-surface-400 mb-1">
                {t('weight.dateLabel')}
              </label>
              <input
                id="weight-date"
                type="date"
                value={measuredOn}
                onChange={(e) => setMeasuredOn(e.target.value)}
                // Nothing in the future, and nothing older than the chart can show —
                // an invisible entry is worse than a refusal. The server enforces both.
                max={today}
                min={oldestRecordable}
                className="bg-surface-800 border border-surface-700 rounded-lg px-3 py-2 text-surface-100"
              />
            </div>
            <Button type="submit" variant="primary" loading={saveMutation.isPending} disabled={!draft.trim()}>
              {t('weight.save')}
            </Button>
          </div>

          {/* One weight per day is a correction, not a second reading — make it deliberate */}
          {existingForDay && (
            <p className="text-xs text-amber-400/90">
              {t('weight.dayAlreadyHasEntry', { value: fmt(existingForDay.weightKg) })}
            </p>
          )}
        </form>
      )}

      {error && <p className="text-sm text-rose-400/80">{error}</p>}

      {data.entries.length > 0 && <p className="text-xs text-surface-500">{t('weight.noiseHint')}</p>}

      <ConfirmModal
        isOpen={confirmDelete !== null}
        onClose={() => setConfirmDelete(null)}
        onConfirm={() => {
          if (confirmDelete) deleteMutation.mutate(confirmDelete)
          setConfirmDelete(null)
        }}
        title={t('weight.deleteConfirmTitle')}
        message={t('weight.deleteConfirmMessage', {
          date: confirmDelete ? format(parseISO(confirmDelete), 'dd.MM.yyyy') : '',
        })}
        variant="danger"
      />
    </div>
  )
}
