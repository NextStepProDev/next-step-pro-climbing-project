import { useMemo } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft } from 'lucide-react'
import { adminTrainingCalendarApi } from '../../api/client'
import { Avatar } from '../../components/ui/Avatar'
import { TrainingCalendarSection } from '../../components/training/TrainingCalendarSection'
import { coachAdapter } from '../../components/training/trainingCalendarAdapter'
import { AscentsSection } from '../../components/ascents/AscentsSection'
import { coachAscentAdapter } from '../../components/ascents/ascentAdapter'

/**
 * One athlete in the coach view, in two halves: the training calendar (add/edit/delete/comment,
 * completion read-only) and the climbing logbook (read-only throughout — only the athlete logs
 * their own ascents).
 *
 * The two are tabs rather than one long page: the logbook is a table with filters and an export,
 * and stacked under the weight panel it would be the thing nobody scrolls to.
 */
export function AdminAthleteCalendarPanel() {
  const { t } = useTranslation('admin')
  const { t: tAscents } = useTranslation('ascents')
  const { t: tTraining } = useTranslation('training')
  const { athleteId } = useParams<{ athleteId: string }>()
  const [searchParams, setSearchParams] = useSearchParams()

  // Roster is normally already cached from the list screen; used for the header
  const { data: athletes } = useQuery({
    queryKey: ['admin', 'trainingCalendar', 'athletes'],
    queryFn: adminTrainingCalendarApi.getAthletes,
  })
  const athlete = athletes?.find((a) => a.id === athleteId)

  const adapter = useMemo(() => coachAdapter(athleteId!), [athleteId])
  const ascentAdapter = useMemo(() => coachAscentAdapter(athleteId!), [athleteId])

  // In the URL, like every other bit of state in this panel: a link sent to oneself comes back
  // to the same place. Note the calendar section is UNMOUNTED on the logbook tab, so its
  // mark-seen effect does not run there — reading is the calendar's business, not the logbook's.
  const activeTab = searchParams.get('view') === 'ascents' ? 'ascents' : 'calendar'
  const switchTab = (tab: 'calendar' | 'ascents') => {
    const params = new URLSearchParams(searchParams)
    if (tab === 'ascents') params.set('view', 'ascents')
    else params.delete('view')
    setSearchParams(params, { replace: true })
  }

  if (!athleteId) return null

  const athleteName = athlete ? `${athlete.firstName} ${athlete.lastName}` : undefined

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <Link
          to="/admin/training-calendars"
          className="p-2 text-surface-400 hover:text-surface-100 hover:bg-surface-800 rounded-lg transition-colors"
          aria-label={t('trainingCalendars.back')}
        >
          <ArrowLeft className="w-5 h-5" />
        </Link>
        {athlete && (
          <div className="flex items-center gap-2">
            <Avatar src={athlete.avatarUrl} name={athlete.firstName} />
            <h2 className="text-lg font-semibold text-surface-100">{athleteName}</h2>
          </div>
        )}
      </div>

      <div className="flex gap-1.5 p-1 bg-surface-800 border border-surface-700 rounded-lg w-fit">
        {(['calendar', 'ascents'] as const).map((tab) => (
          <button
            key={tab}
            onClick={() => switchTab(tab)}
            aria-pressed={activeTab === tab}
            className={`px-3 py-1.5 text-sm font-medium rounded-md transition-colors ${
              activeTab === tab
                ? 'bg-primary-600 text-white'
                : 'text-surface-400 hover:text-surface-200'
            }`}
          >
            {tab === 'calendar' ? tTraining('tabs.calendar') : tAscents('tab')}
          </button>
        ))}
      </div>

      {/* key: switching athletes must remount the section (fresh mark-seen effect per athlete).
          The clipboard deliberately sits above this remount so a copy can travel between athletes. */}
      {activeTab === 'calendar' ? (
        <TrainingCalendarSection
          key={athleteId}
          api={adapter}
          scopeKey={athleteId}
          scopeLabel={athleteName}
          isCoachView
        />
      ) : (
        <AscentsSection
          key={athleteId}
          api={ascentAdapter}
          scopeKey={athleteId}
          scopeLabel={athleteName}
          isCoachView
        />
      )}
    </div>
  )
}
