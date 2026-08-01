import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Check, Pencil, Plus, RotateCcw, Scale, Target, Trash2, Trophy } from 'lucide-react'
import { differenceInCalendarDays, format } from 'date-fns'
import clsx from 'clsx'
import { ConfirmModal } from '../ui/ConfirmModal'
import { GoalFormModal } from './GoalFormModal'
import { AchieveGoalModal } from './AchieveGoalModal'
import { TrophyChestModal } from './TrophyChestModal'
import { getErrorMessage } from '../../utils/errors'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import type { TrainingCalendarAdapter } from './trainingCalendarAdapter'
import type { AthleteGoal, GoalHorizon, GoalKind, SaveGoal } from '../../types'

const HORIZONS: GoalHorizon[] = ['SHORT', 'MEDIUM', 'LONG']

// Training goals first, weight goals below — same order as the GoalKind enum on the backend
const SECTIONS: { kind: GoalKind; titleKey: string }[] = [
  { kind: 'GENERAL', titleKey: 'goals.section.general' },
  { kind: 'WEIGHT', titleKey: 'goals.section.weight' },
]

// A freshly achieved goal celebrates in its slot for a while before the slot
// goes back to "add a goal" (coach) / disappears (athlete)
const CELEBRATION_DAYS = 7

/**
 * One card slot is identified by kind AND horizon: an athlete may chase a technique goal and
 * a weight goal on the same horizon, and keying by horizon alone would silently drop one.
 */
type SlotKey = `${GoalKind}:${GoalHorizon}`
const slotKey = (kind: GoalKind, horizon: GoalHorizon): SlotKey => `${kind}:${horizon}`

interface GoalsBannerProps {
  api: TrainingCalendarAdapter
  scopeKey: string
  isCoachView?: boolean
}

/**
 * Personal goals above the training calendar: two rows of up to three cards (short/medium/
 * long-term), one row for training goals and one for weight goals, plus the trophy chest of
 * all achieved goals. The coach manages the cards; the athlete reads them (and admires the chest).
 *
 * <p>Weight goals differ in one way that matters: they close THEMSELVES when a weigh-in brings
 * the confirmed 7-day trend to target, which is why they — and only they — can be reopened.
 */
export function GoalsBanner({ api, scopeKey, isCoachView }: GoalsBannerProps) {
  const { t } = useTranslation('training')
  const queryClient = useQueryClient()

  const goalsQuery = useQuery({
    queryKey: ['trainingCalendar', 'goals', scopeKey],
    queryFn: api.getGoals,
  })

  // Only the current trend is needed here, and that is computed on today's window whatever
  // range is charted — so pin to the default range and share WeightPanel's cache entry
  const weightQuery = useQuery({
    queryKey: ['trainingCalendar', 'weight', scopeKey, 'RECENT'],
    queryFn: () => api.getWeights('RECENT'),
  })

  const [formSlot, setFormSlot] = useState<{ kind: GoalKind; horizon: GoalHorizon } | null>(null)
  const [editedGoal, setEditedGoal] = useState<AthleteGoal | null>(null)
  const [confirmAchieve, setConfirmAchieve] = useState<AthleteGoal | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<AthleteGoal | null>(null)
  const [confirmReopen, setConfirmReopen] = useState<AthleteGoal | null>(null)
  const [chestOpen, setChestOpen] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['trainingCalendar', 'goals', scopeKey] })
  }

  const mutations = api.goalMutations
  const saveMutation = useMutation({
    mutationFn: (data: SaveGoal) =>
      editedGoal ? mutations!.update(editedGoal.id, data) : mutations!.create(data),
    onSuccess: () => {
      setFormSlot(null)
      setEditedGoal(null)
      invalidate()
    },
  })
  const achieveMutation = useMutation({
    mutationFn: ({ goalId, achievedDate }: { goalId: string; achievedDate: string }) =>
      mutations!.achieve(goalId, achievedDate),
    // Error stays inline in the modal (submitError); the modal remains open to retry
    onSuccess: () => { setConfirmAchieve(null); invalidate() },
  })
  const deleteMutation = useMutation({
    mutationFn: (goalId: string) => mutations!.remove(goalId),
    onSuccess: () => { setActionError(null); invalidate() },
    onError: (err) => setActionError(getErrorMessage(err)),
  })
  const reopenMutation = useMutation({
    mutationFn: (goalId: string) => mutations!.reopen(goalId),
    onSuccess: () => { setActionError(null); invalidate() },
    onError: (err) => setActionError(getErrorMessage(err)),
  })

  const goals = goalsQuery.data
  if (!goals) return null

  const activeBySlot = new Map(goals.active.map((g) => [slotKey(g.kind, g.horizon), g]))
  // Slot celebration: latest achieved goal per slot, if fresh and the slot is free
  const celebrating = new Map<SlotKey, AthleteGoal>()
  for (const g of goals.achieved) {
    const key = slotKey(g.kind, g.horizon)
    if (activeBySlot.has(key) || celebrating.has(key)) continue
    if (g.achievedAt && differenceInCalendarDays(new Date(), new Date(g.achievedAt)) <= CELEBRATION_DAYS) {
      celebrating.set(key, g)
    }
  }

  // Athlete with no goals at all: no banner, no empty chest — nothing to show yet
  if (!isCoachView && goals.active.length === 0 && celebrating.size === 0 && goals.achieved.length === 0) {
    return null
  }

  const currentTrendKg = weightQuery.data?.currentTrendKg ?? null
  const trendConfirmed = weightQuery.data?.trendConfirmed ?? false
  const trendSampleCount = weightQuery.data?.trendSampleCount ?? 0

  return (
    <div className="space-y-2">
      {/* Header: title + trophy chest */}
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-sm font-semibold text-surface-200">
          <Target className="w-4 h-4 text-primary-400" />
          {t('goals.title')}
        </div>
        <button
          onClick={() => setChestOpen(true)}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-amber-500/30 bg-amber-500/10 text-amber-300 hover:bg-amber-500/20 transition-colors"
        >
          <Trophy className="w-3.5 h-3.5" />
          {t('goals.trophies')} ({goals.achieved.length})
        </button>
      </div>

      {actionError && <p className="text-sm text-rose-400/80">{actionError}</p>}

      {SECTIONS.map(({ kind, titleKey }) => {
        const slots = HORIZONS.map((horizon) => ({
          horizon,
          goal: activeBySlot.get(slotKey(kind, horizon)),
          celebrated: celebrating.get(slotKey(kind, horizon)),
        }))
        // The athlete only ever sees the weight row once it holds something
        const hasContent = slots.some((s) => s.goal || s.celebrated)
        if (!isCoachView && !hasContent) return null

        return (
          <div key={kind} className="space-y-1.5">
            <div className="flex items-center gap-1.5 text-xs font-medium text-surface-400 uppercase tracking-wide">
              {kind === 'WEIGHT' ? <Scale className="w-3.5 h-3.5" /> : <Target className="w-3.5 h-3.5" />}
              {t(titleKey)}
            </div>
            <div className="grid gap-2 sm:grid-cols-3">
              {slots.map(({ horizon, goal, celebrated }) => (
                <GoalSlot
                  key={horizon}
                  kind={kind}
                  horizon={horizon}
                  goal={goal}
                  celebrated={celebrated}
                  isCoachView={isCoachView}
                  currentTrendKg={currentTrendKg}
                  trendConfirmed={trendConfirmed}
                  trendSampleCount={trendSampleCount}
                  onAchieve={setConfirmAchieve}
                  onEdit={(g) => { setEditedGoal(g); setFormSlot({ kind: g.kind, horizon: g.horizon }) }}
                  onDelete={setConfirmDelete}
                  onReopen={setConfirmReopen}
                  onAdd={() => { setEditedGoal(null); setFormSlot({ kind, horizon }) }}
                />
              ))}
            </div>
          </div>
        )
      })}

      {/* Coach: create/edit form */}
      {isCoachView && mutations && (
        <GoalFormModal
          isOpen={formSlot !== null}
          onClose={() => { setFormSlot(null); setEditedGoal(null); saveMutation.reset() }}
          kind={formSlot?.kind ?? 'GENERAL'}
          horizon={formSlot?.horizon ?? 'SHORT'}
          goal={editedGoal}
          currentTrendKg={currentTrendKg}
          onSubmit={(data) => saveMutation.mutate(data)}
          saving={saveMutation.isPending}
          submitError={saveMutation.isError ? getErrorMessage(saveMutation.error) : null}
        />
      )}

      <TrophyChestModal
        isOpen={chestOpen}
        onClose={() => setChestOpen(false)}
        achieved={goals.achieved}
        isCoachView={isCoachView}
        onReopen={mutations ? (goal) => {
          // Close the chest rather than stacking modals — the goal is about to leave it anyway
          setChestOpen(false)
          setConfirmReopen(goal)
        } : undefined}
        onDelete={mutations ? (goal) => {
          setChestOpen(false)
          setConfirmDelete(goal)
        } : undefined}
      />

      <AchieveGoalModal
        isOpen={confirmAchieve !== null}
        onClose={() => { setConfirmAchieve(null); setActionError(null); achieveMutation.reset() }}
        goal={confirmAchieve}
        onConfirm={(achievedDate) => {
          if (confirmAchieve) achieveMutation.mutate({ goalId: confirmAchieve.id, achievedDate })
        }}
        saving={achieveMutation.isPending}
        submitError={achieveMutation.isError ? getErrorMessage(achieveMutation.error) : null}
      />

      <ConfirmModal
        isOpen={confirmDelete !== null}
        onClose={() => setConfirmDelete(null)}
        onConfirm={() => {
          if (confirmDelete) deleteMutation.mutate(confirmDelete.id)
          setConfirmDelete(null)
        }}
        // Binning a trophy is a different act from dropping an active goal — say so, or the
        // coach reads the active-goal wording and assumes the achievement merely reopens
        title={confirmDelete?.achievedAt ? t('goals.deleteTrophyConfirmTitle') : t('goals.deleteConfirmTitle')}
        message={confirmDelete?.achievedAt ? t('goals.deleteTrophyConfirmMessage') : t('goals.deleteConfirmMessage')}
        variant="danger"
      />

      <ConfirmModal
        isOpen={confirmReopen !== null}
        onClose={() => setConfirmReopen(null)}
        onConfirm={() => {
          if (confirmReopen) reopenMutation.mutate(confirmReopen.id)
          setConfirmReopen(null)
        }}
        title={t('goals.reopenConfirmTitle')}
        message={t('goals.reopenConfirmMessage')}
      />
    </div>
  )
}

/**
 * How far along a weight goal is, measured start → target against the current trend.
 * Null whenever any of the three numbers is missing — a half-known bar would just mislead.
 */
function weightProgress(goal: AthleteGoal, currentTrendKg: number | null) {
  if (goal.targetWeightKg == null || goal.startWeightKg == null || currentTrendKg == null) return null
  const total = Math.abs(goal.targetWeightKg - goal.startWeightKg)
  if (total === 0) return null
  const done = Math.abs(currentTrendKg - goal.startWeightKg)
  return {
    percent: Math.min(100, Math.max(0, Math.round((done / total) * 100))),
    remaining: Math.max(0, Math.abs(goal.targetWeightKg - currentTrendKg)),
  }
}

interface GoalSlotProps {
  kind: GoalKind
  horizon: GoalHorizon
  goal?: AthleteGoal
  celebrated?: AthleteGoal
  isCoachView?: boolean
  currentTrendKg: number | null
  trendConfirmed: boolean
  trendSampleCount: number
  onAchieve: (goal: AthleteGoal) => void
  onEdit: (goal: AthleteGoal) => void
  onDelete: (goal: AthleteGoal) => void
  onReopen: (goal: AthleteGoal) => void
  onAdd: () => void
}

/** One card: an active goal, a fresh trophy, or (coach only) an invitation to set one. */
function GoalSlot({
  kind,
  horizon,
  goal,
  celebrated,
  isCoachView,
  currentTrendKg,
  trendConfirmed,
  trendSampleCount,
  onAchieve,
  onEdit,
  onDelete,
  onReopen,
  onAdd,
}: GoalSlotProps) {
  const { t, i18n } = useTranslation('training')
  const today = new Date()
  const fmtKg = (n: number) =>
    n.toLocaleString(i18n.language, { minimumFractionDigits: 1, maximumFractionDigits: 1 })

  if (goal) {
    const daysLeft = differenceInCalendarDays(new Date(goal.targetDate), today)
    const overdue = daysLeft < 0
    const progress = kind === 'WEIGHT' ? weightProgress(goal, currentTrendKg) : null
    return (
      <div
        className={clsx(
          'p-3 rounded-lg border space-y-1.5',
          overdue
            ? 'bg-surface-900/60 border-surface-800 opacity-70'
            : 'bg-surface-900 border-primary-500/25',
        )}
      >
        <div className="flex items-center justify-between gap-2">
          <span className="px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide rounded-full bg-primary-500/15 text-primary-300 border border-primary-500/30">
            {t(`goals.horizon.${horizon.toLowerCase()}`)}
          </span>
          {isCoachView && (
            <span className="flex gap-0.5">
              {/* A weight goal closes itself — closing it by hand would make the flag lie */}
              {kind !== 'WEIGHT' && (
                <button
                  onClick={() => onAchieve(goal)}
                  className="p-1 rounded text-surface-400 hover:text-green-300 hover:bg-surface-800 transition-colors"
                  title={t('goals.markAchieved')}
                >
                  <Check className="w-3.5 h-3.5" />
                </button>
              )}
              <button
                onClick={() => onEdit(goal)}
                className="p-1 rounded text-surface-400 hover:text-surface-100 hover:bg-surface-800 transition-colors"
                title={t('goals.edit')}
              >
                <Pencil className="w-3.5 h-3.5" />
              </button>
              <button
                onClick={() => onDelete(goal)}
                className="p-1 rounded text-surface-400 hover:text-rose-300 hover:bg-surface-800 transition-colors"
                title={t('goals.delete')}
              >
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </span>
          )}
        </div>
        <p className="text-sm font-medium text-surface-100">{decodeHtmlEntities(goal.content)}</p>

        {kind === 'WEIGHT' && goal.startWeightKg != null && goal.targetWeightKg != null && (
          <div className="space-y-1">
            <p className="text-xs text-surface-400 tabular-nums">
              {fmtKg(goal.startWeightKg)} → {fmtKg(goal.targetWeightKg)} kg
            </p>
            {progress && (
              <>
                <div className="h-1.5 rounded-full bg-surface-800">
                  <div
                    className="h-1.5 rounded-full bg-green-500/70 transition-all"
                    style={{ width: `${Math.max(progress.percent, 2)}%` }}
                  />
                </div>
                <p className="text-xs text-surface-400 tabular-nums">
                  {progress.percent}% · {t('goals.weight.remaining', { value: fmtKg(progress.remaining) })}
                </p>
              </>
            )}
            {/* Without this the athlete cannot tell why a reached target left the goal open */}
            {!trendConfirmed && (
              <p className="text-xs text-surface-500">
                {t('goals.weight.trendUnconfirmed', { count: trendSampleCount })}
              </p>
            )}
          </div>
        )}

        <p className={clsx('text-xs', overdue ? 'text-surface-500' : 'text-surface-400')}>
          {t('goals.targetDate')}: {format(new Date(goal.targetDate), 'dd.MM.yyyy')}
          {' · '}
          {overdue
            ? t('goals.overdue')
            : daysLeft === 0
              ? t('goals.today')
              : t('goals.daysLeft', { count: daysLeft })}
        </p>
      </div>
    )
  }

  if (celebrated) {
    return (
      <div key={horizon} className="p-3 rounded-lg border bg-green-500/10 border-green-500/30 space-y-1.5">
        <div className="flex items-center justify-between gap-2">
          <span className="px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide rounded-full bg-green-500/15 text-green-300 border border-green-500/30">
            {t(`goals.horizon.${horizon.toLowerCase()}`)}
          </span>
          <Trophy className="w-4 h-4 text-yellow-400" />
        </div>
        <p className="text-sm font-medium text-green-200">{decodeHtmlEntities(celebrated.content)}</p>
        <p className="text-xs text-green-300/80 font-medium">{t('goals.achieved')}</p>
        {/* The slot is already free in the DB — let the coach set the next goal now
            instead of waiting out the 7-day celebration window */}
        {isCoachView && (
          <div className="flex flex-wrap items-center gap-3">
            <button
              onClick={onAdd}
              className="inline-flex items-center gap-1 text-xs font-medium text-green-300 hover:text-green-200 transition-colors"
            >
              <Plus className="w-3.5 h-3.5" />
              {t('goals.setNext')}
            </button>
            {/* Undo only for a machine closure: a mistyped weigh-in must not be permanent */}
            {celebrated.achievedAutomatically && (
              <button
                onClick={() => onReopen(celebrated)}
                className="inline-flex items-center gap-1 text-xs font-medium text-surface-400 hover:text-surface-200 transition-colors"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                {t('goals.reopen')}
              </button>
            )}
            {/* A trophy awarded by mistake is spotted right here, on the celebration card —
                make the coach open the chest for it and it just stays */}
            <button
              onClick={() => onDelete(celebrated)}
              className="inline-flex items-center gap-1 text-xs font-medium text-surface-500 hover:text-rose-300 transition-colors"
            >
              <Trash2 className="w-3.5 h-3.5" />
              {t('goals.deleteTrophy')}
            </button>
          </div>
        )}
      </div>
    )
  }

  if (isCoachView) {
    return (
      <button
        onClick={onAdd}
        className="p-3 rounded-lg border border-dashed border-surface-700 text-surface-500 hover:text-surface-300 hover:border-surface-500 transition-colors flex flex-col items-center justify-center gap-1 min-h-[88px]"
      >
        <Plus className="w-4 h-4" />
        <span className="text-xs font-medium">
          {t('goals.add')} — {t(`goals.horizon.${horizon.toLowerCase()}`).toLowerCase()}
        </span>
      </button>
    )
  }

  // Athlete: empty slot renders nothing (the grid just has fewer cards)
  return null
}
