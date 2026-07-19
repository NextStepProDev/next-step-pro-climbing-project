import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Check, Pencil, Plus, Target, Trash2, Trophy } from 'lucide-react'
import { differenceInCalendarDays, format } from 'date-fns'
import clsx from 'clsx'
import { ConfirmModal } from '../ui/ConfirmModal'
import { GoalFormModal } from './GoalFormModal'
import { AchieveGoalModal } from './AchieveGoalModal'
import { TrophyChestModal } from './TrophyChestModal'
import { getErrorMessage } from '../../utils/errors'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import type { TrainingCalendarAdapter } from './trainingCalendarAdapter'
import type { AthleteGoal, GoalHorizon, SaveGoal } from '../../types'

const HORIZONS: GoalHorizon[] = ['SHORT', 'MEDIUM', 'LONG']

// A freshly achieved goal celebrates in its slot for a while before the slot
// goes back to "add a goal" (coach) / disappears (athlete)
const CELEBRATION_DAYS = 7

interface GoalsBannerProps {
  api: TrainingCalendarAdapter
  scopeKey: string
  isCoachView?: boolean
}

/**
 * Personal goals above the training calendar: up to three cards (short/medium/long-term)
 * with target-date countdowns, plus the trophy chest of all achieved goals. The coach
 * manages the cards; the athlete reads them (and admires the chest).
 */
export function GoalsBanner({ api, scopeKey, isCoachView }: GoalsBannerProps) {
  const { t } = useTranslation('training')
  const queryClient = useQueryClient()

  const goalsQuery = useQuery({
    queryKey: ['trainingCalendar', 'goals', scopeKey],
    queryFn: api.getGoals,
  })

  const [formHorizon, setFormHorizon] = useState<GoalHorizon | null>(null)
  const [editedGoal, setEditedGoal] = useState<AthleteGoal | null>(null)
  const [confirmAchieve, setConfirmAchieve] = useState<AthleteGoal | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<AthleteGoal | null>(null)
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
      setFormHorizon(null)
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

  const goals = goalsQuery.data
  if (!goals) return null

  const activeByHorizon = new Map(goals.active.map((g) => [g.horizon, g]))
  // Slot celebration: latest achieved goal per horizon, if fresh and the slot is free
  const celebrating = new Map<GoalHorizon, AthleteGoal>()
  for (const g of goals.achieved) {
    if (activeByHorizon.has(g.horizon) || celebrating.has(g.horizon)) continue
    if (g.achievedAt && differenceInCalendarDays(new Date(), new Date(g.achievedAt)) <= CELEBRATION_DAYS) {
      celebrating.set(g.horizon, g)
    }
  }

  // Athlete with no goals at all: no banner, no empty chest — nothing to show yet
  if (!isCoachView && goals.active.length === 0 && celebrating.size === 0 && goals.achieved.length === 0) {
    return null
  }

  const today = new Date()

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

      {/* Slot cards: short → medium → long */}
      <div className="grid gap-2 sm:grid-cols-3">
        {HORIZONS.map((horizon) => {
          const goal = activeByHorizon.get(horizon)
          const celebrated = celebrating.get(horizon)

          if (goal) {
            const daysLeft = differenceInCalendarDays(new Date(goal.targetDate), today)
            const overdue = daysLeft < 0
            return (
              <div
                key={horizon}
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
                      <button
                        onClick={() => setConfirmAchieve(goal)}
                        className="p-1 rounded text-surface-400 hover:text-green-300 hover:bg-surface-800 transition-colors"
                        title={t('goals.markAchieved')}
                      >
                        <Check className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => { setEditedGoal(goal); setFormHorizon(goal.horizon) }}
                        className="p-1 rounded text-surface-400 hover:text-surface-100 hover:bg-surface-800 transition-colors"
                        title={t('goals.edit')}
                      >
                        <Pencil className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => setConfirmDelete(goal)}
                        className="p-1 rounded text-surface-400 hover:text-rose-300 hover:bg-surface-800 transition-colors"
                        title={t('goals.delete')}
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </span>
                  )}
                </div>
                <p className="text-sm font-medium text-surface-100">{decodeHtmlEntities(goal.content)}</p>
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
              </div>
            )
          }

          if (isCoachView) {
            return (
              <button
                key={horizon}
                onClick={() => { setEditedGoal(null); setFormHorizon(horizon) }}
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
        })}
      </div>

      {/* Coach: create/edit form */}
      {isCoachView && mutations && (
        <GoalFormModal
          isOpen={formHorizon !== null}
          onClose={() => { setFormHorizon(null); setEditedGoal(null); saveMutation.reset() }}
          horizon={formHorizon ?? 'SHORT'}
          goal={editedGoal}
          onSubmit={(data) => saveMutation.mutate(data)}
          saving={saveMutation.isPending}
          submitError={saveMutation.isError ? getErrorMessage(saveMutation.error) : null}
        />
      )}

      <TrophyChestModal
        isOpen={chestOpen}
        onClose={() => setChestOpen(false)}
        achieved={goals.achieved}
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
        title={t('goals.deleteConfirmTitle')}
        message={t('goals.deleteConfirmMessage')}
        variant="danger"
      />
    </div>
  )
}
