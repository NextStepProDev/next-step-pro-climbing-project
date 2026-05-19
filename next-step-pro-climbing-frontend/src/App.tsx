import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'
import { Layout } from './components/layout/Layout'
import { HomePage } from './pages/HomePage'
import { ProtectedRoute } from './components/layout/ProtectedRoute'
import { AdminRoute } from './components/layout/AdminRoute'
import { ErrorBoundary } from './components/ui/ErrorBoundary'
import { ScrollToTop } from './components/ScrollToTop'
import { LoadingSpinner } from './components/ui/LoadingSpinner'

const CalendarPage = lazy(() => import('./pages/CalendarPage').then(m => ({ default: m.CalendarPage })))
const LoginPage = lazy(() => import('./pages/LoginPage').then(m => ({ default: m.LoginPage })))
const MyReservationsPage = lazy(() => import('./pages/MyReservationsPage').then(m => ({ default: m.MyReservationsPage })))
const EventPage = lazy(() => import('./pages/EventPage').then(m => ({ default: m.EventPage })))
const TeamPage = lazy(() => import('./pages/TeamPage').then(m => ({ default: m.TeamPage })))
const GalleryPage = lazy(() => import('./pages/GalleryPage').then(m => ({ default: m.GalleryPage })))
const AlbumPage = lazy(() => import('./pages/AlbumPage').then(m => ({ default: m.AlbumPage })))
const VideosPage = lazy(() => import('./pages/VideosPage').then(m => ({ default: m.VideosPage })))
const NewsPage = lazy(() => import('./pages/NewsPage').then(m => ({ default: m.NewsPage })))
const NewsDetailPage = lazy(() => import('./pages/NewsDetailPage').then(m => ({ default: m.NewsDetailPage })))
const CoursesPage = lazy(() => import('./pages/CoursesPage').then(m => ({ default: m.CoursesPage })))
const CourseDetailPage = lazy(() => import('./pages/CourseDetailPage').then(m => ({ default: m.CourseDetailPage })))
const RegisterPage = lazy(() => import('./pages/RegisterPage').then(m => ({ default: m.RegisterPage })))
const VerifyEmailPage = lazy(() => import('./pages/VerifyEmailPage').then(m => ({ default: m.VerifyEmailPage })))
const ForgotPasswordPage = lazy(() => import('./pages/ForgotPasswordPage').then(m => ({ default: m.ForgotPasswordPage })))
const ResetPasswordPage = lazy(() => import('./pages/ResetPasswordPage').then(m => ({ default: m.ResetPasswordPage })))
const ResendVerificationPage = lazy(() => import('./pages/ResendVerificationPage').then(m => ({ default: m.ResendVerificationPage })))
const OAuthCallbackPage = lazy(() => import('./pages/OAuthCallbackPage').then(m => ({ default: m.OAuthCallbackPage })))
const ContactPage = lazy(() => import('./pages/ContactPage').then(m => ({ default: m.ContactPage })))
const FAQPage = lazy(() => import('./pages/FAQPage').then(m => ({ default: m.FAQPage })))
const PrivacyPolicyPage = lazy(() => import('./pages/PrivacyPolicyPage').then(m => ({ default: m.PrivacyPolicyPage })))
const SettingsPage = lazy(() => import('./pages/SettingsPage').then(m => ({ default: m.SettingsPage })))
const AdminPage = lazy(() => import('./pages/AdminPage').then(m => ({ default: m.AdminPage })))

const LazyFallback = () => (
  <div className="flex justify-center py-12">
    <LoadingSpinner size="lg" />
  </div>
)

export default function App() {
  return (
    <ErrorBoundary>
      <ScrollToTop />

      <Suspense fallback={<LazyFallback />}>
        <Routes>
          <Route path="/" element={<Layout />}>
            <Route index element={<HomePage />} />
            <Route path="calendar" element={<CalendarPage />} />
            <Route path="events/:eventId" element={<EventPage />} />
            <Route path="team/instruktorzy" element={<TeamPage memberType="INSTRUCTOR" />} />
            <Route path="team/instruktorzy/:memberId" element={<TeamPage memberType="INSTRUCTOR" />} />
            <Route path="team/zawodnicy" element={<TeamPage memberType="COMPETITOR" />} />
            <Route path="team/zawodnicy/:memberId" element={<TeamPage memberType="COMPETITOR" />} />
            <Route path="galeria" element={<GalleryPage />} />
            <Route path="galeria/:albumId" element={<AlbumPage />} />
            <Route path="filmy" element={<VideosPage />} />
            <Route path="aktualnosci" element={<NewsPage />} />
            <Route path="aktualnosci/:newsId" element={<NewsDetailPage />} />
            <Route path="kursy" element={<CoursesPage />} />
            <Route path="kursy/:courseId" element={<CourseDetailPage />} />
            <Route path="kontakt" element={<ContactPage />} />
            <Route path="faq" element={<FAQPage />} />
            <Route path="polityka-prywatnosci" element={<PrivacyPolicyPage />} />
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
                  <AdminPage />
                </AdminRoute>
              }
            />
          </Route>
        </Routes>
      </Suspense>
    </ErrorBoundary>
  )
}
