import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Compass, Home } from 'lucide-react'

/**
 * The route that catches everything the router does not know.
 *
 * Without it an unknown address rendered an entirely blank page: nginx answers `try_files … /index.html`,
 * React matched no route, and the layout drew a header and footer around nothing. Two people lose out —
 * a visitor holding a stale link sees a broken site with no way back, and a crawler is handed a soft 404
 * (HTTP 200 for a page that does not exist), which is the shape Search Console reports as an error.
 *
 * We cannot send a real 404 status from a static SPA shell, so the next best thing is a page that says
 * plainly what happened and offers the way out.
 */
export function NotFoundPage() {
  const { t } = useTranslation('common')

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 lg:px-8 py-20 text-center">
      <Compass className="w-12 h-12 mx-auto mb-6 text-surface-500" aria-hidden="true" />
      <p className="text-sm font-semibold tracking-widest text-surface-400 mb-3">404</p>
      <h1 className="text-2xl sm:text-3xl font-bold text-surface-100 mb-3">{t('notFound.title')}</h1>
      <p className="text-surface-300 mb-8">{t('notFound.description')}</p>
      <div className="flex flex-wrap items-center justify-center gap-3">
        <Link
          to="/"
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-primary-600 text-white font-medium hover:bg-primary-500 transition-colors"
        >
          <Home className="w-4 h-4" aria-hidden="true" />
          {t('notFound.home')}
        </Link>
        <Link
          to="/calendar"
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-surface-700 text-surface-200 font-medium hover:bg-surface-800 transition-colors"
        >
          {t('notFound.calendar')}
        </Link>
      </div>
    </div>
  )
}
