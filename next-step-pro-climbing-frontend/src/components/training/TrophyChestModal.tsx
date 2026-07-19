import { useTranslation } from 'react-i18next'
import { Award, Medal, Trophy } from 'lucide-react'
import { format } from 'date-fns'
import clsx from 'clsx'
import { Modal } from '../ui/Modal'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import { useDateLocale } from '../../utils/dateFnsLocale'
import type { AthleteGoal, GoalHorizon } from '../../types'

// Escalating prestige: short = bronze, medium = silver, long = gold. The tier colour and
// icon reinforce the horizon (which the cup size already hints at) and make the chest read
// like a real trophy cabinet. Full static class strings so Tailwind keeps them.
interface Tier {
  horizon: GoalHorizon
  icon: typeof Trophy
  labelKey: string
  cup: string
  cupGlow: string
  headerGlow: string
  badge: string
  card: string
  cardIcon: string
}

const TIERS: Tier[] = [
  {
    horizon: 'SHORT',
    icon: Award,
    labelKey: 'goals.chest.short',
    cup: 'text-amber-600',
    cupGlow: 'drop-shadow-[0_0_8px_rgba(217,119,6,0.45)]',
    headerGlow: 'from-amber-600/20',
    badge: 'bg-amber-600/15 text-amber-500 border-amber-600/30',
    card: 'border-amber-600/20 from-amber-600/10',
    cardIcon: 'text-amber-600',
  },
  {
    horizon: 'MEDIUM',
    icon: Medal,
    labelKey: 'goals.chest.medium',
    cup: 'text-slate-300',
    cupGlow: 'drop-shadow-[0_0_8px_rgba(203,213,225,0.45)]',
    headerGlow: 'from-slate-300/20',
    badge: 'bg-slate-400/15 text-slate-200 border-slate-300/30',
    card: 'border-slate-300/20 from-slate-400/10',
    cardIcon: 'text-slate-300',
  },
  {
    horizon: 'LONG',
    icon: Trophy,
    labelKey: 'goals.chest.long',
    cup: 'text-yellow-400',
    cupGlow: 'drop-shadow-[0_0_12px_rgba(250,204,21,0.55)]',
    headerGlow: 'from-yellow-400/25',
    badge: 'bg-yellow-400/15 text-yellow-300 border-yellow-400/30',
    card: 'border-yellow-400/25 from-yellow-400/10',
    cardIcon: 'text-yellow-400',
  },
]

interface TrophyChestModalProps {
  isOpen: boolean
  onClose: () => void
  achieved: AthleteGoal[]
}

/**
 * The trophy chest: every achieved goal, forever. Three prestige columns (bronze/silver/gold
 * = short/medium/long-term), each a scrollable shelf of trophies newest-first. The read-only
 * motivational centrepiece of the athlete zone — seen by athlete and coach alike.
 */
export function TrophyChestModal({ isOpen, onClose, achieved }: TrophyChestModalProps) {
  const { t } = useTranslation('training')
  const locale = useDateLocale()

  // Achieved goals arrive newest-first; keep that order within each column
  const byHorizon = (horizon: GoalHorizon) => achieved.filter((g) => g.horizon === horizon)

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('goals.trophiesTitle', { count: achieved.length })} size="xl">
      {achieved.length === 0 && (
        <p className="text-sm text-surface-400 text-center mb-4">{t('goals.trophiesEmpty')}</p>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        {TIERS.map((tier) => {
          const goals = byHorizon(tier.horizon)
          const Icon = tier.icon
          return (
            <section
              key={tier.horizon}
              className="flex flex-col rounded-xl border border-surface-800 bg-surface-950/40 overflow-hidden"
            >
              {/* Column header with a soft radial glow in the tier colour */}
              <div className="relative border-b border-surface-800 px-3 py-4 text-center">
                <div className={clsx('pointer-events-none absolute inset-0 bg-gradient-to-b to-transparent', tier.headerGlow)} />
                <div className="relative flex flex-col items-center gap-1.5">
                  <Icon className={clsx('w-9 h-9', tier.cup, tier.cupGlow)} strokeWidth={1.5} />
                  <span className="text-sm font-semibold text-surface-100">{t(tier.labelKey)}</span>
                  <span className={clsx('px-2 py-0.5 rounded-full text-xs font-medium border tabular-nums', tier.badge)}>
                    {goals.length}
                  </span>
                </div>
              </div>

              {/* Shelf of trophies (independent scroll so headers stay put) */}
              <div className="p-2 space-y-2 overflow-y-auto max-h-[52vh] min-h-[128px]">
                {goals.length === 0 ? (
                  <div className="flex flex-col items-center justify-center gap-2 py-10 text-center text-surface-600">
                    <Icon className="w-6 h-6 opacity-30" strokeWidth={1.5} />
                    <p className="text-xs">{t('goals.chest.emptyColumn')}</p>
                  </div>
                ) : (
                  goals.map((goal) => (
                    <div
                      key={goal.id}
                      className={clsx(
                        'flex gap-2.5 rounded-lg border bg-gradient-to-br to-transparent p-2.5',
                        tier.card,
                      )}
                    >
                      <Icon className={clsx('w-5 h-5 shrink-0 mt-0.5', tier.cardIcon)} strokeWidth={1.5} />
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-surface-100 break-words">
                          {decodeHtmlEntities(goal.content)}
                        </p>
                        {goal.achievedAt && (
                          <p className="text-[11px] text-surface-400 mt-0.5">
                            {t('goals.achievedOn', { date: format(new Date(goal.achievedAt), 'd MMM yyyy', { locale }) })}
                          </p>
                        )}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </section>
          )
        })}
      </div>
    </Modal>
  )
}
