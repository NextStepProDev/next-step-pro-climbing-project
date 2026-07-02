import { Outlet, Link, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { Clock3 } from 'lucide-react'
import { Navbar } from './Navbar'
import { Footer } from './Footer'
import { ScrollToTopButton } from '../ui/ScrollToTopButton'
import { GlobalLoadingBar } from '../ui/GlobalLoadingBar'
import { useAuth } from '../../context/AuthContext'
import { NewsletterChoiceModal } from '../ui/NewsletterChoiceModal'
import { reservationApi } from '../../api/client'

export function Layout() {
  const location = useLocation()
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
      <GlobalLoadingBar />
      <Navbar />
      {pendingCount > 0 && (
        <Link
          to="/my-reservations"
          className="relative z-40 block w-full bg-amber-500 hover:bg-amber-400 transition-colors text-black text-center py-2.5 px-4 text-sm font-semibold"
        >
          <span className="inline-flex items-center gap-2">
            <Clock3 className="w-4 h-4" />
            {t('waitlist.banner', { count: pendingCount })}
          </span>
        </Link>
      )}
      <main className="flex-1">
        <div key={location.pathname} className="animation-page-fade">
          <Outlet />
        </div>
      </main>
      <Footer />
      <ScrollToTopButton />
      {showNewsletterModal && (
        <NewsletterChoiceModal onDone={refreshUser} />
      )}
    </div>
  )
}
