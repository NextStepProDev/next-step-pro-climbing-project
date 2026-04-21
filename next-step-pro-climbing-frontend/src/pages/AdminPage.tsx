import { useTranslation } from 'react-i18next'
import { Routes, Route, Link, useLocation } from 'react-router-dom'
import { Calendar, Users, Clock, ClipboardList, Activity, User, Image, Newspaper, BookOpen, Library, Mail, HardDrive, Video } from 'lucide-react'
import clsx from 'clsx'
import { AdminSlotsPanel } from './admin/AdminSlotsPanel'
import { AdminEventsPanel } from './admin/AdminEventsPanel'
import { AdminUsersPanel } from './admin/AdminUsersPanel'
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

const adminTabs = [
  { path: '/admin', labelKey: 'tabs.slots', icon: Clock },
  { path: '/admin/reservations', labelKey: 'tabs.reservations', icon: ClipboardList },
  { path: '/admin/events', labelKey: 'tabs.events', icon: Calendar },
  { path: '/admin/news', labelKey: 'tabs.news', icon: Newspaper },
  { path: '/admin/courses', labelKey: 'tabs.courses', icon: BookOpen },
  { path: '/admin/instructors', labelKey: 'tabs.instructors', icon: User },
  { path: '/admin/competitors', labelKey: 'tabs.competitors', icon: Users },
  { path: '/admin/gallery', labelKey: 'tabs.gallery', icon: Image },
  { path: '/admin/videos', labelKey: 'tabs.videos', icon: Video },
  { path: '/admin/assets', labelKey: 'tabs.assets', icon: Library },
  { path: '/admin/users', labelKey: 'tabs.users', icon: Users },
  { path: '/admin/mail', labelKey: 'tabs.mail', icon: Mail },
  { path: '/admin/activity', labelKey: 'tabs.activity', icon: Activity },
  { path: '/admin/storage', labelKey: 'tabs.storage', icon: HardDrive },
]

export function AdminPage() {
  const { t } = useTranslation('admin')
  const location = useLocation()

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-dark-100 mb-2">
          {t('title')}
        </h1>
        <p className="text-dark-400">
          {t('subtitle')}
        </p>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 mb-6 border-b border-dark-800 pb-2 overflow-x-auto scrollbar-none">
        {adminTabs.map((tab) => {
          const Icon = tab.icon
          const isActive = location.pathname === tab.path
          return (
            <Link
              key={tab.path}
              to={tab.path}
              className={clsx(
                'flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors whitespace-nowrap shrink-0',
                isActive
                  ? 'bg-primary-500/10 text-primary-400'
                  : 'text-dark-400 hover:text-dark-200 hover:bg-dark-800'
              )}
            >
              <Icon className="w-4 h-4" />
              {t(tab.labelKey)}
            </Link>
          )
        })}
      </div>

      {/* Content */}
      <Routes>
        <Route index element={<AdminSlotsPanel />} />
        <Route path="reservations" element={<AdminReservationsPanel />} />
        <Route path="events" element={<AdminEventsPanel />} />
        <Route path="instructors" element={<AdminInstructorsPanel />} />
        <Route path="competitors" element={<AdminCompetitorsPanel />} />
        <Route path="gallery" element={<AdminGalleryPanel />} />
        <Route path="videos" element={<AdminVideosPanel />} />
        <Route path="news" element={<AdminNewsPanel />} />
        <Route path="courses" element={<AdminCoursesPanel />} />
        <Route path="assets" element={<AdminAssetsPanel />} />
        <Route path="users" element={<AdminUsersPanel />} />
        <Route path="mail" element={<AdminMailPanel />} />
        <Route path="activity" element={<AdminActivityPanel />} />
        <Route path="storage" element={<AdminStoragePanel />} />
      </Routes>
    </div>
  )
}
