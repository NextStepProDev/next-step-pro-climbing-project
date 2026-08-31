import { useTranslation } from 'react-i18next'
import { Routes, Route, Link, useLocation, useMatch } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Calendar, CalendarPlus, Users, Clock, ClipboardList, Activity, User, Image, Newspaper, BookOpen, Library, Mail, HardDrive, Video, Home, Dumbbell, Wallet, type LucideIcon } from 'lucide-react'
import clsx from 'clsx'
import { adminApi } from '../api/client'
import { AdminSlotsPanel } from './admin/AdminSlotsPanel'
import { AdminEventsPanel } from './admin/AdminEventsPanel'
import { AdminUsersPanel } from './admin/AdminUsersPanel'
import { AdminUserDetailPanel } from './admin/AdminUserDetailPanel'
import { AdminReservationsPanel } from './admin/AdminReservationsPanel'
import { AdminActivityPanel } from './admin/AdminActivityPanel'
import { AdminInstructorsPanel } from './admin/AdminInstructorsPanel'
import { AdminCompetitorsPanel } from './admin/AdminCompetitorsPanel'
import { AdminGalleryPanel } from './admin/AdminGalleryPanel'
import { AdminVideosPanel } from './admin/AdminVideosPanel'
import { AdminNewsPanel } from './admin/AdminNewsPanel'
import { AdminCoursesPanel } from './admin/AdminCoursesPanel'
import { AdminAssetsPanel } from './admin/AdminAssetsPanel'
import { AdminMailPanel } from './admin/AdminMailPanel'
import { AdminStoragePanel } from './admin/AdminStoragePanel'
import { AdminSitePanel } from './admin/AdminSitePanel'
import { AdminRequestsPanel } from './admin/AdminRequestsPanel'
import { AdminSettlementsPanel } from './admin/AdminSettlementsPanel'
import { AdminTrainingCalendarsPanel } from './admin/AdminTrainingCalendarsPanel'
import { AdminAthleteCalendarPanel } from './admin/AdminAthleteCalendarPanel'

interface AdminTab {
  path: string
  labelKey: string
  icon: LucideIcon
}

interface AdminTabGroup {
  groupKey: string
  tabs: AdminTab[]
}

const adminTabGroups: AdminTabGroup[] = [
  {
    groupKey: 'tabGroups.calendar',
    tabs: [
      { path: '/admin', labelKey: 'tabs.slots', icon: Clock },
      { path: '/admin/reservations', labelKey: 'tabs.reservations', icon: ClipboardList },
      { path: '/admin/events', labelKey: 'tabs.events', icon: Calendar },
      { path: '/admin/requests', labelKey: 'tabs.requests', icon: CalendarPlus },
      { path: '/admin/training-calendars', labelKey: 'tabs.trainingCalendars', icon: Dumbbell },
      { path: '/admin/settlements', labelKey: 'tabs.settlements', icon: Wallet },
    ],
  },
  {
    groupKey: 'tabGroups.content',
    tabs: [
      { path: '/admin/news', labelKey: 'tabs.news', icon: Newspaper },
      { path: '/admin/courses', labelKey: 'tabs.courses', icon: BookOpen },
      { path: '/admin/gallery', labelKey: 'tabs.gallery', icon: Image },
      { path: '/admin/videos', labelKey: 'tabs.videos', icon: Video },
    ],
  },
  {
    groupKey: 'tabGroups.team',
    tabs: [
      { path: '/admin/instructors', labelKey: 'tabs.instructors', icon: User },
      { path: '/admin/competitors', labelKey: 'tabs.competitors', icon: Users },
    ],
  },
  {
    groupKey: 'tabGroups.system',
    tabs: [
      { path: '/admin/users', labelKey: 'tabs.users', icon: Users },
      { path: '/admin/mail', labelKey: 'tabs.mail', icon: Mail },
      { path: '/admin/activity', labelKey: 'tabs.activity', icon: Activity },
      { path: '/admin/assets', labelKey: 'tabs.assets', icon: Library },
      { path: '/admin/storage', labelKey: 'tabs.storage', icon: HardDrive },
      { path: '/admin/site', labelKey: 'tabs.site', icon: Home },
    ],
  },
]

export function AdminPage() {
  const { t } = useTranslation('admin')
  const location = useLocation()

  // The two sub-routes that are about ONE person, not about the panel: on a phone the title plus
  // four wrapped groups of tabs eat most of the first screen, so entering somebody's card opened on
  // panel-wide navigation instead of on that person. Below `sm` both are dropped on these routes —
  // each panel carries its own back arrow, so nothing becomes unreachable, and `sm` and up is
  // untouched. Rendering less beats scrolling past it: a scroll would race the panel's own data
  // loading (a short document clamps the target and the jump lands somewhere else).
  // Both matches are read into locals: `||` between two hook calls short-circuits the second one.
  const userCardMatch = useMatch('/admin/users/:userId')
  const athleteCardMatch = useMatch('/admin/training-calendars/:athleteId')
  const isPersonRoute = !!userCardMatch || !!athleteCardMatch

  // Notification counters: badges on the "Requests" tab (pending) and the "Reservations" tab
  // (new since last read). The same endpoint feeds the dot on the Admin navbar link.
  const { data: notifications } = useQuery({
    queryKey: ['admin', 'notifications'],
    queryFn: adminApi.getNotifications,
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
    // The read markers are server-side and per-admin, so clearing alerts on one
    // device must show up on another. Treat the count as always stale and refetch
    // on mount/focus so returning to this device re-checks the server immediately
    // instead of showing the cached (pre-clear) count until the next 60s poll.
    staleTime: 0,
    refetchOnWindowFocus: true,
  })
  const tabBadges: Record<string, number> = {
    '/admin/requests': notifications?.pendingRequests ?? 0,
    // Waitlist joins count together with new reservations — both views (the "Waitlists"
    // section and the reservation list) live in this tab, and entering it clears both
    '/admin/reservations': (notifications?.newReservations ?? 0) + (notifications?.newWaitlistEntries ?? 0),
    // Unread athlete activity (new trainings/completions/comments) across all athletes
    '/admin/training-calendars': notifications?.athleteActivity ?? 0,
    // Accounts confirmed since this admin last opened the Users list
    '/admin/users': notifications?.newUsers ?? 0,
  }

  return (
    <div
      className={clsx(
        'max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8',
        isPersonRoute && 'max-sm:pt-4'
      )}
    >
      <div className={clsx('mb-8', isPersonRoute && 'max-sm:mb-0')}>
        {/* `sr-only`, not `hidden`: the h1 costs no pixels either way, and dropping it would leave
            the page with an h2 (the person's name) as its highest heading. */}
        <h1
          className={clsx(
            'text-2xl font-bold text-surface-100 mb-2',
            isPersonRoute && 'max-sm:sr-only'
          )}
        >
          {t('title')}
        </h1>
        <p className={clsx('text-surface-400', isPersonRoute && 'max-sm:hidden')}>
          {t('subtitle')}
        </p>
      </div>

      {/* Tabs */}
      <div
        className={clsx(
          'flex flex-wrap gap-x-6 gap-y-3 mb-6 border-b border-surface-800 pb-4',
          isPersonRoute && 'max-sm:hidden'
        )}
      >
        {adminTabGroups.map((group) => (
          <div key={group.groupKey} className="flex flex-col gap-1.5 max-sm:w-full">
            <span className="text-[11px] font-semibold uppercase tracking-wider text-surface-500 px-1">
              {t(group.groupKey)}
            </span>
            <div className="flex flex-wrap gap-1">
              {group.tabs.map((tab) => {
                const Icon = tab.icon
                const isActive = tab.path === '/admin'
                  ? location.pathname === '/admin'
                  : location.pathname.startsWith(tab.path)
                return (
                  <Link
                    key={tab.path}
                    to={tab.path}
                    className={clsx(
                      'flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors whitespace-nowrap',
                      isActive
                        ? 'bg-primary-500/10 text-primary-400'
                        : 'text-surface-400 hover:text-surface-200 hover:bg-surface-800'
                    )}
                  >
                    <Icon className="w-4 h-4" />
                    {t(tab.labelKey)}
                    {(tabBadges[tab.path] ?? 0) > 0 && (
                      <span className="ml-0.5 min-w-5 h-5 px-1.5 inline-flex items-center justify-center rounded-full bg-rose-500 text-white text-[11px] font-bold leading-none">
                        {tabBadges[tab.path]}
                      </span>
                    )}
                  </Link>
                )
              })}
            </div>
          </div>
        ))}
      </div>

      {/* Content */}
      <Routes>
        <Route index element={<AdminSlotsPanel />} />
        <Route path="reservations" element={<AdminReservationsPanel />} />
        <Route path="events" element={<AdminEventsPanel />} />
        <Route path="requests" element={<AdminRequestsPanel />} />
        <Route path="settlements" element={<AdminSettlementsPanel />} />
        <Route path="training-calendars" element={<AdminTrainingCalendarsPanel />} />
        <Route path="training-calendars/:athleteId" element={<AdminAthleteCalendarPanel />} />
        <Route path="instructors" element={<AdminInstructorsPanel />} />
        <Route path="competitors" element={<AdminCompetitorsPanel />} />
        <Route path="gallery" element={<AdminGalleryPanel />} />
        <Route path="videos" element={<AdminVideosPanel />} />
        <Route path="news" element={<AdminNewsPanel />} />
        <Route path="courses" element={<AdminCoursesPanel />} />
        <Route path="assets" element={<AdminAssetsPanel />} />
        <Route path="users" element={<AdminUsersPanel />} />
        <Route path="users/:userId" element={<AdminUserDetailPanel />} />
        <Route path="mail" element={<AdminMailPanel />} />
        <Route path="activity" element={<AdminActivityPanel />} />
        <Route path="storage" element={<AdminStoragePanel />} />
        <Route path="site" element={<AdminSitePanel />} />
      </Routes>
    </div>
  )
}
