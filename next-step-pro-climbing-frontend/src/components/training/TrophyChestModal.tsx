import { useTranslation } from 'react-i18next'
import { Trophy } from 'lucide-react'
import { format } from 'date-fns'
import clsx from 'clsx'
import { Modal } from '../ui/Modal'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import type { AthleteGoal, GoalHorizon } from '../../types'

// Bigger goal, bigger cup: horizon picks the trophy size and shine
const TROPHY_STYLES: Record<GoalHorizon, string> = {
  SHORT: 'w-6 h-6 text-amber-200',
  MEDIUM: 'w-8 h-8 text-amber-300',
  LONG: 'w-10 h-10 text-yellow-400',
}

interface TrophyChestModalProps {
  isOpen: boolean
  onClose: () => void
  achieved: AthleteGoal[]
}

/** All achieved goals, forever: the motivational trophy shelf. Read-only for everyone. */
export function TrophyChestModal({ isOpen, onClose, achieved }: TrophyChestModalProps) {
  const { t } = useTranslation('training')

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('goals.trophiesTitle', { count: achieved.length })}>
      {achieved.length === 0 ? (
        <div className="py-8 text-center space-y-2">
          <Trophy className="w-10 h-10 text-surface-600 mx-auto" />
          <p className="text-sm text-surface-400">{t('goals.trophiesEmpty')}</p>
        </div>
      ) : (
        <ul className="space-y-2 max-h-[60vh] overflow-y-auto pr-1">
          {achieved.map((goal) => (
            <li
              key={goal.id}
              className="flex items-center gap-3 p-3 rounded-lg border border-amber-500/20 bg-amber-500/5"
            >
              <span className="w-10 shrink-0 flex items-center justify-center">
                <Trophy className={clsx('shrink-0', TROPHY_STYLES[goal.horizon])} />
              </span>
              <span className="min-w-0">
                <span className="block text-sm font-medium text-surface-100">
                  {decodeHtmlEntities(goal.content)}
                </span>
                <span className="block text-xs text-surface-400">
                  {t(`goals.horizon.${goal.horizon.toLowerCase()}`)}
                  {goal.achievedAt && (
                    <> · {t('goals.achievedOn', { date: format(new Date(goal.achievedAt), 'dd.MM.yyyy') })}</>
                  )}
                </span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}
