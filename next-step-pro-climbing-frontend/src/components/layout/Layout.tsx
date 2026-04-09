import { Outlet, Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { Clock3 } from 'lucide-react'
import { Navbar } from './Navbar'
import { Footer } from './Footer'
import { useAuth } from '../../context/AuthContext'
import { NewsletterChoiceModal } from '../ui/NewsletterChoiceModal'
import { reservationApi } from '../../api/client'

export function Layout() {
  const { user, refreshUser, isAuthenticated } = useAuth()
  const { t } = useTranslation('reservations')
  const showNewsletterModal = !!user && !user.newsletterChoiceMade

  const { data: waitlistData } = useQuery({
    queryKey: ['reservations', 'waitlist'],
    queryFn: reservationApi.getMyWaitlist,
    enabled: isAuthenticated,
    staleTime: 60_000,
  })

  const { data: eventWaitlistData } = useQuery({
    queryKey: ['reservations', 'event-waitlist'],
    queryFn: reservationApi.getMyEventWaitlist,
    enabled: isAuthenticated,
    staleTime: 60_000,
  })

  const pendingCount =
    (waitlistData?.filter(w => w.status === 'PENDING_CONFIRMATION').length ?? 0) +
    (eventWaitlistData?.filter(w => w.status === 'PENDING_CONFIRMATION').length ?? 0)

  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      {pendingCount > 0 && (
        <Link
          to="/my-reservations"
          className="block w-full bg-amber-500 hover:bg-amber-400 transition-colors text-black text-center py-2.5 px-4 text-sm font-semibold"
        >
          <span className="inline-flex items-center gap-2">
            <Clock3 className="w-4 h-4" />
            {t('waitlist.banner', { count: pendingCount })}
          </span>
        </Link>
      )}
      <main className="flex-1">
        <Outlet />
      </main>
      <Footer />
      {showNewsletterModal && (
        <NewsletterChoiceModal onDone={refreshUser} />
      )}
    </div>
  )
}
