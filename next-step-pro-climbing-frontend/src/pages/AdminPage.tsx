import { Routes, Route, Link, useLocation } from 'react-router-dom'
import { Calendar, Users, Clock, ClipboardList, Activity } from 'lucide-react'
import clsx from 'clsx'
import { AdminSlotsPanel } from './admin/AdminSlotsPanel'
import { AdminEventsPanel } from './admin/AdminEventsPanel'
import { AdminUsersPanel } from './admin/AdminUsersPanel'
import { AdminReservationsPanel } from './admin/AdminReservationsPanel'
import { AdminActivityPanel } from './admin/AdminActivityPanel'

const adminTabs = [
  { path: '/admin', label: 'Terminy', icon: Clock },
  { path: '/admin/reservations', label: 'Rezerwacje', icon: ClipboardList },
  { path: '/admin/events', label: 'Wydarzenia', icon: Calendar },
  { path: '/admin/users', label: 'Użytkownicy', icon: Users },
  { path: '/admin/activity', label: 'Aktywność', icon: Activity },
]

export function AdminPage() {
  const location = useLocation()

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-dark-100 mb-2">
          Panel administratora
        </h1>
        <p className="text-dark-400">
          Zarządzaj terminami, wydarzeniami i użytkownikami.
        </p>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 mb-6 border-b border-dark-800 pb-2">
        {adminTabs.map((tab) => {
          const Icon = tab.icon
          const isActive = location.pathname === tab.path
          return (
            <Link
              key={tab.path}
              to={tab.path}
              className={clsx(
                'flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors',
                isActive
                  ? 'bg-primary-500/10 text-primary-400'
                  : 'text-dark-400 hover:text-dark-200 hover:bg-dark-800'
              )}
            >
              <Icon className="w-4 h-4" />
              {tab.label}
            </Link>
          )
        })}
      </div>

      {/* Content */}
      <Routes>
        <Route index element={<AdminSlotsPanel />} />
        <Route path="reservations" element={<AdminReservationsPanel />} />
        <Route path="events" element={<AdminEventsPanel />} />
        <Route path="users" element={<AdminUsersPanel />} />
        <Route path="activity" element={<AdminActivityPanel />} />
      </Routes>
    </div>
  )
}
