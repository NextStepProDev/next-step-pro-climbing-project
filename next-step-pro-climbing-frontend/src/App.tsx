import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'
import { Layout } from './components/layout/Layout'
import { HomePage } from './pages/HomePage'
import { CalendarPage } from './pages/CalendarPage'
import { MyReservationsPage } from './pages/MyReservationsPage'
import { EventPage } from './pages/EventPage'
import { InstructorsPage } from './pages/InstructorsPage'
import { GalleryPage } from './pages/GalleryPage'
import { AlbumPage } from './pages/AlbumPage'
import { VideosPage } from './pages/VideosPage'
import { NewsPage } from './pages/NewsPage'
import { NewsDetailPage } from './pages/NewsDetailPage'
import { CoursesPage } from './pages/CoursesPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { VerifyEmailPage } from './pages/VerifyEmailPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { ResendVerificationPage } from './pages/ResendVerificationPage'
import { OAuthCallbackPage } from './pages/OAuthCallbackPage'
import { ContactPage } from './pages/ContactPage'
import { SettingsPage } from './pages/SettingsPage'
import { ProtectedRoute } from './components/layout/ProtectedRoute'
import { AdminRoute } from './components/layout/AdminRoute'
import { ErrorBoundary } from './components/ui/ErrorBoundary'
import { ScrollToTop } from './components/ScrollToTop'

const AdminPage = lazy(() => import('./pages/AdminPage').then(m => ({ default: m.AdminPage })))

export default function App() {
  return (
    <ErrorBoundary>
      <ScrollToTop />

      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<HomePage />} />
          <Route path="calendar" element={<CalendarPage />} />
          <Route path="events/:eventId" element={<EventPage />} />
          <Route path="instruktorzy" element={<InstructorsPage />} />
          <Route path="galeria" element={<GalleryPage />} />
          <Route path="galeria/:albumId" element={<AlbumPage />} />
          <Route path="filmy" element={<VideosPage />} />
          <Route path="aktualnosci" element={<NewsPage />} />
          <Route path="aktualnosci/:newsId" element={<NewsDetailPage />} />
          <Route path="kursy" element={<CoursesPage />} />
          <Route path="kontakt" element={<ContactPage />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />
          <Route path="verify-email" element={<VerifyEmailPage />} />
          <Route path="forgot-password" element={<ForgotPasswordPage />} />
          <Route path="reset-password" element={<ResetPasswordPage />} />
          <Route path="resend-verification" element={<ResendVerificationPage />} />
          <Route path="oauth-callback" element={<OAuthCallbackPage />} />
          <Route
            path="my-reservations"
            element={
              <ProtectedRoute>
                <MyReservationsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="settings"
            element={
              <ProtectedRoute>
                <SettingsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="admin/*"
            element={
              <AdminRoute>
                <Suspense fallback={null}>
                  <AdminPage />
                </Suspense>
              </AdminRoute>
            }
          />
        </Route>
      </Routes>
    </ErrorBoundary>
  )
}
