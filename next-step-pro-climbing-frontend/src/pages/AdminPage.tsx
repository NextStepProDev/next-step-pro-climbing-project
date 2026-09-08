import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Routes, Route, useMatch } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { adminApi } from '../api/client'
import { AdminNav } from '../components/admin/AdminNav'
import { AdminCommandPalette } from '../components/admin/AdminCommandPalette'
import { canOpenPalette } from '../components/admin/adminPaletteSearch'
import { AdminHubPanel } from './admin/AdminHubPanel'
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

export function AdminPage() {
  const { t } = useTranslation('admin')
  const [paletteOpen, setPaletteOpen] = useState(false)

  // The two sub-routes that are about ONE person, not about the panel: entering somebody's card
  // should open on that person, so below `sm` the panel-wide title and subtitle step aside. The
  // navigation itself stays — it is one row of four buttons now, not the four wrapped groups of
  // pills that used to eat the whole first screen. Each panel still carries its own back arrow.
  // Both matches are read into locals: `||` between two hook calls short-circuits the second one.
  const userCardMatch = useMatch('/admin/users/:userId')
  const athleteCardMatch = useMatch('/admin/training-calendars/:athleteId')
  const isPersonRoute = !!userCardMatch || !!athleteCardMatch

  // Notification counters: badges on the group menus, the tabs inside them and the hub tiles.
  // The same endpoint feeds the dot on the Admin navbar link.
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

  useEffect(() => {
    function handleShortcut(e: KeyboardEvent) {
      // `metaKey || ctrlKey` follows RichTextEditor's own shortcut handling.
      if (!(e.metaKey || e.ctrlKey) || e.key.toLowerCase() !== 'k') return
      // Firefox maps ⌘K to its search bar, so the default has to go either way.
      e.preventDefault()
      // Never open underneath an open modal — see `canOpenPalette`.
      if (!canOpenPalette()) return
      setPaletteOpen(true)
    }
    document.addEventListener('keydown', handleShortcut)
    return () => document.removeEventListener('keydown', handleShortcut)
  }, [])

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

      <AdminNav notifications={notifications} onOpenPalette={() => setPaletteOpen(true)} />

      {/* Mounted only while open: every opening then starts from an empty query and the top of
          the list, with no reset code to forget. */}
      {paletteOpen && <AdminCommandPalette onClose={() => setPaletteOpen(false)} />}

      {/* Content */}
      <Routes>
        <Route index element={<AdminHubPanel />} />
        <Route path="slots" element={<AdminSlotsPanel />} />
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
