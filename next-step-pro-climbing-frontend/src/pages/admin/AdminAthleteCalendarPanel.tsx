import { useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft } from 'lucide-react'
import { adminTrainingCalendarApi } from '../../api/client'
import { Avatar } from '../../components/ui/Avatar'
import { TrainingCalendarSection } from '../../components/training/TrainingCalendarSection'
import { coachAdapter } from '../../components/training/trainingCalendarAdapter'

/**
 * One athlete's training calendar in the coach view: the SAME calendar components the
 * athlete sees, with coach permissions (add/edit/delete/comment) and completion read-only.
 * Opening this screen marks the athlete "seen" for this admin (badge clears).
 */
export function AdminAthleteCalendarPanel() {
  const { t } = useTranslation('admin')
  const { athleteId } = useParams<{ athleteId: string }>()

  // Roster is normally already cached from the list screen; used for the header
  const { data: athletes } = useQuery({
    queryKey: ['admin', 'trainingCalendar', 'athletes'],
    queryFn: adminTrainingCalendarApi.getAthletes,
  })
  const athlete = athletes?.find((a) => a.id === athleteId)

  const adapter = useMemo(() => coachAdapter(athleteId!), [athleteId])

  if (!athleteId) return null

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
            <h2 className="text-lg font-semibold text-surface-100">
              {athlete.firstName} {athlete.lastName}
            </h2>
          </div>
        )}
      </div>

      {/* key: switching athletes must remount the section (fresh mark-seen effect per athlete) */}
      <TrainingCalendarSection key={athleteId} api={adapter} scopeKey={athleteId} isCoachView />
    </div>
  )
}
