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
      {/* ⚠️ First in the DOM on purpose: measured with a driven browser, the content of every page
          sits 14 to 15 Tab presses behind the navbar, and a keyboard user pays that on every single
          navigation. A screen reader can jump by landmark to <main>, so this is for the people
          landmarks do not help — sighted, keyboard-only.

          Hidden until focused. `sr-only` is `position: absolute`, which this codebase has been
          bitten by before (inside a 940px-wide scroll container it resolved against the document
          and widened the page); here it sits at the top of the layout with nothing wide around it,
          so there is no track for it to stretch. */}
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-3 focus:top-3 focus:z-50 focus:rounded-lg focus:bg-primary-500 focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-black"
      >
        {t('skipToContent')}
      </a>
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
      {/* tabIndex -1 so the jump actually moves focus, not just the scroll position. */}
      <main id="main" tabIndex={-1} className="flex-1 focus:outline-none">
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
