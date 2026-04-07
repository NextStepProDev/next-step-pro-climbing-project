import { Outlet } from 'react-router-dom'
import { Navbar } from './Navbar'
import { Footer } from './Footer'
import { useAuth } from '../../context/AuthContext'
import { NewsletterChoiceModal } from '../ui/NewsletterChoiceModal'

export function Layout() {
  const { user, refreshUser } = useAuth()
  const showNewsletterModal = !!user && !user.newsletterChoiceMade

  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
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
