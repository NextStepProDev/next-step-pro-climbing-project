import { useMemo } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, ShieldCheck, Dumbbell } from 'lucide-react'
import { adminUserHistoryApi } from '../../api/client'
import { Avatar } from '../../components/ui/Avatar'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { UserOverviewTab } from '../../components/admin/userhistory/UserOverviewTab'
import { UserReservationsTab } from '../../components/admin/userhistory/UserReservationsTab'
import { UserAccountTab } from '../../components/admin/userhistory/UserAccountTab'
import { TrainingCalendarSection } from '../../components/training/TrainingCalendarSection'
import { coachAdapter } from '../../components/training/trainingCalendarAdapter'
import { AscentsSection } from '../../components/ascents/AscentsSection'
import { coachAscentAdapter } from '../../components/ascents/ascentAdapter'

type TabKey = 'overview' | 'reservations' | 'training' | 'account'

/**
 * One user's whole history in the admin panel, read-only.
 *
 * <p>Four tabs rather than one long page: this screen answers four unrelated questions ("what has
 * this person been doing", "what are they booked on", "how is their training going", "what state is
 * the account in"), and stacking them made the last one unreachable without scrolling past a
 * calendar. Same reasoning, and the same URL-backed segmented control, as the coach's athlete panel.
 *
 * <p><b>The Training tab exists only for flagged athletes.</b> Not a display choice: the endpoints
 * behind it (`/api/admin/training-calendar/...`, `/api/admin/ascents/...`) refuse everyone else,
 * because a plain user's logbook is private and being read by a coach has to follow from a decision
 * somebody actually made. Rendering the tab and letting it 400 would advertise data we do not serve.
 */
export function AdminUserDetailPanel() {
  const { t } = useTranslation('admin')
  const { t: tAscents } = useTranslation('ascents')
  const { t: tTraining } = useTranslation('training')
  const { userId } = useParams<{ userId: string }>()
  const [searchParams, setSearchParams] = useSearchParams()

  const userQuery = useQuery({
    queryKey: ['admin', 'userHistory', userId],
    queryFn: () => adminUserHistoryApi.getUser(userId!),
    enabled: !!userId,
  })

  const user = userQuery.data
  const isAthlete = user?.athlete ?? false

  const adapter = useMemo(() => coachAdapter(userId!), [userId])
  const ascentAdapter = useMemo(() => coachAscentAdapter(userId!), [userId])

  // In the URL like every other bit of state in this panel, so a link sent to oneself comes back
  // to the same tab. `overview` is the default and carries no parameter, matching `?view=ascents`
  // in the athlete panel and `?tab=` on the reservations page.
  const requested = searchParams.get('view')
  const activeTab: TabKey =
    requested === 'reservations' || requested === 'account' ? requested
      : requested === 'training' && isAthlete ? 'training'
      : 'overview'

  const switchTab = (tab: TabKey) => {
    const params = new URLSearchParams(searchParams)
    if (tab === 'overview') params.delete('view')
    else params.set('view', tab)
    setSearchParams(params, { replace: true })
  }

  if (!userId) return null

  const tabs: TabKey[] = isAthlete
    ? ['overview', 'reservations', 'training', 'account']
    : ['overview', 'reservations', 'account']

  const tabLabel = (tab: TabKey) =>
    tab === 'training' ? tTraining('tabs.calendar') : t(`users.detail.tabs.${tab}`)

  const fullName = user ? `${user.firstName} ${user.lastName}` : undefined

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3 flex-wrap">
        <Link
          to="/admin/users"
          className="p-2 text-surface-400 hover:text-surface-100 hover:bg-surface-800 rounded-lg transition-colors"
          aria-label={t('users.detail.back')}
        >
          <ArrowLeft className="w-5 h-5" />
        </Link>
        {user && (
          <>
            <Avatar src={user.avatarUrl} name={user.firstName} />
            <div className="min-w-0">
              <h2 className="text-lg font-semibold text-surface-100 truncate">{fullName}</h2>
              <p className="text-sm text-surface-400 truncate">{user.email}</p>
            </div>
            <div className="flex items-center gap-1.5 ml-auto">
              {user.role === 'ADMIN' && (
                <span className="inline-flex items-center gap-1 px-2 py-1 rounded text-xs bg-primary-500/20 text-primary-400">
                  <ShieldCheck className="w-3 h-3" />
                  {t('users.detail.roleAdmin')}
                </span>
              )}
              {user.athlete && (
                <span className="inline-flex items-center gap-1 px-2 py-1 rounded text-xs bg-indigo-500/15 text-indigo-300">
                  <Dumbbell className="w-3 h-3" />
                  {t('users.detail.athleteBadge')}
                </span>
              )}
            </div>
          </>
        )}
      </div>

      {userQuery.isLoading ? (
        <div className="py-16 flex justify-center"><LoadingSpinner /></div>
      ) : userQuery.isError || !user ? (
        <QueryError error={userQuery.error} onRetry={() => userQuery.refetch()} />
      ) : (
        <>
          <div className="flex gap-1.5 p-1 bg-surface-800 border border-surface-700 rounded-lg w-fit max-w-full overflow-x-auto">
            {tabs.map((tab) => (
              <button
                key={tab}
                onClick={() => switchTab(tab)}
                aria-pressed={activeTab === tab}
                className={`px-3 py-1.5 text-sm font-medium rounded-md transition-colors whitespace-nowrap ${
                  activeTab === tab
                    ? 'bg-primary-600 text-white'
                    : 'text-surface-400 hover:text-surface-200'
                }`}
              >
                {tabLabel(tab)}
              </button>
            ))}
          </div>

          {activeTab === 'overview' && <UserOverviewTab userId={userId} user={user} />}
          {activeTab === 'reservations' && <UserReservationsTab userId={userId} />}
          {activeTab === 'account' && <UserAccountTab user={user} />}
          {activeTab === 'training' && (
            // key: the sections are also mounted from the coach roster, and a stale mark-seen
            // effect from another athlete must not survive a switch between people
            <div className="space-y-6">
              <TrainingCalendarSection
                key={`cal-${userId}`}
                api={adapter}
                scopeKey={userId}
                scopeLabel={fullName}
                isCoachView
              />
              <div>
                <h3 className="text-sm font-semibold text-surface-300 mb-2">{tAscents('tab')}</h3>
                <AscentsSection
                  key={`asc-${userId}`}
                  api={ascentAdapter}
                  scopeKey={userId}
                  scopeLabel={fullName}
                  isCoachView
                />
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
