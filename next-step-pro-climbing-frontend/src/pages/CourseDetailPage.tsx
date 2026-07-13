import { useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useParams, useNavigate, useLocation, Link } from 'react-router-dom'
import { Helmet } from 'react-helmet-async'
import { ArrowLeft, BookOpen } from 'lucide-react'
import { coursesApi, calendarApi } from '../api/client'
import { PageHead } from '../components/ui/PageHead'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { QueryError } from '../components/ui/QueryError'
import { ShareButtons } from '../components/ui/ShareButtons'
import { CourseContentBlocks, CourseEventsList } from '../components/courses/CourseContentBlocks'
import { COURSE_CONTENT_LANGUAGES, getDefaultCourseContentLanguage, pickBestTranslation } from '../constants/courseLanguages'
import clsx from 'clsx'

export function CourseDetailPage() {
  const { t, i18n } = useTranslation('common')
  const { courseId } = useParams<{ courseId: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  // When arriving from a calendar event modal, go back to it instead of the
  // course list (the returnTo URL carries `?event=<id>` to re-open the modal).
  const returnTo = (location.state as { returnTo?: string } | null)?.returnTo

  const { data: course, isLoading, error } = useQuery({
    queryKey: ['courses', courseId],
    queryFn: () => coursesApi.getById(courseId!),
    enabled: !!courseId,
    // Detail-by-id: navigating course A -> B must not flash A's content while B
    // loads. Opt out of the global keepPreviousData and show the spinner instead.
    placeholderData: undefined,
  })

  const { data: translations } = useQuery({
    queryKey: ['courseTranslations', course?.translationGroupId],
    queryFn: () => coursesApi.getTranslations(course!.translationGroupId),
    enabled: !!course?.translationGroupId,
    // Re-keys per course; opt out so the language switcher never shows another
    // course's translations during navigation.
    placeholderData: undefined,
  })

  const { data: courseEvents } = useQuery({
    queryKey: ['courseEvents', course?.translationGroupId],
    queryFn: () => calendarApi.getCourseEventsByTranslationGroup(course!.translationGroupId),
    enabled: !!course?.translationGroupId,
    staleTime: 5 * 60 * 1000,
    // Re-keys per course; opt out so the "terminy" list never shows another
    // course's events during navigation.
    placeholderData: undefined,
  })

  // Changing the global language (top bar) switches the open course to that language;
  // if there is no translation in that language — fall back to an available one (EN → PL → ES).
  const currentContentLang = getDefaultCourseContentLanguage(i18n.language)
  const prevLangRef = useRef(currentContentLang)
  useEffect(() => {
    if (prevLangRef.current !== currentContentLang && course && translations) {
      const target = pickBestTranslation(translations, currentContentLang)
      if (target && target.id !== course.id) {
        navigate(`/kursy/${target.id}`, { replace: true, state: returnTo ? { returnTo } : undefined })
      }
    }
    prevLangRef.current = currentContentLang
  }, [currentContentLang, course, translations, navigate, returnTo])

  if (isLoading) {
    return (
      <div className="min-h-[50vh] flex items-center justify-center">
        <LoadingSpinner />
      </div>
    )
  }

  if (error) {
    return (
      <div className="container mx-auto px-4 py-8">
        <QueryError error={error} />
      </div>
    )
  }

  if (!course) {
    return (
      <div className="container mx-auto px-4 py-8">
        <div className="text-center text-surface-400">{t('courses.courseNotFound')}</div>
      </div>
    )
  }

  const metaDescription = course.blocks
    .find(b => b.blockType === 'TEXT' && b.content)
    ?.content?.replace(/<[^>]*>/g, '').slice(0, 160) ?? undefined

  const currency = course.language === 'pl' ? 'PLN' : 'EUR'
  const courseJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Course',
    name: course.title,
    description: metaDescription,
    provider: {
      '@type': 'Organization',
      name: 'Next Step Pro Climbing',
      url: 'https://nextsteppro.pl',
    },
    ...(course.thumbnailUrl && { image: course.thumbnailUrl }),
    ...(course.price && {
      offers: {
        '@type': 'Offer',
        price: course.price.replace(/[^\d.,]/g, '').replace(',', '.'),
        priceCurrency: currency,
        availability: 'https://schema.org/InStock',
        url: `https://nextsteppro.pl/kursy/${courseId}`,
      },
    }),
    inLanguage: course.language,
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-3xl">
      <PageHead title={course.title} description={metaDescription} path={`/kursy/${courseId}`} ogImage={course.thumbnailUrl ?? undefined} />
      <Helmet>
        <script type="application/ld+json">{JSON.stringify(courseJsonLd)}</script>
      </Helmet>
      <div className="flex items-center justify-between mb-6">
        <Link
          to={returnTo ?? '/kursy'}
          className="inline-flex items-center gap-2 text-surface-300 hover:text-surface-100 transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          {returnTo ? t('courses.back') : t('courses.backToCourses')}
        </Link>

        <div className="flex items-center gap-1 bg-surface-800 border border-surface-700 rounded-lg p-1">
          {COURSE_CONTENT_LANGUAGES.map((lang) => {
            const translation = translations?.find(tr => tr.language === lang.code)
            const isActive = course.language === lang.code
            const isAvailable = isActive || !!translation
            return (
              <button
                key={lang.code}
                disabled={!isAvailable}
                onClick={() => {
                  if (!isActive && translation) navigate(`/kursy/${translation.id}`, { state: returnTo ? { returnTo } : undefined })
                }}
                className={clsx(
                  'px-3 py-1.5 rounded-md text-sm font-medium transition-all duration-150 active:scale-95',
                  isActive
                    ? 'bg-primary-500 text-white'
                    : isAvailable
                      ? 'text-surface-400 hover:text-surface-100 hover:bg-surface-700'
                      : 'text-surface-600 cursor-not-allowed opacity-40'
                )}
              >
                {lang.label}
              </button>
            )
          })}
        </div>
      </div>

      {course.thumbnailUrl && (
        <div className="relative mb-6 rounded-lg overflow-hidden bg-surface-800 h-64 sm:h-80 lg:h-[400px]">
          {/* Blurred background from the SAME image — fills the empty space instead of a black bar */}
          <img
            src={course.thumbnailUrl}
            alt=""
            aria-hidden="true"
            className="absolute inset-0 w-full h-full object-cover scale-110 blur-2xl"
            style={course.thumbnailFocalPointX != null && course.thumbnailFocalPointY != null
              ? { objectPosition: `${course.thumbnailFocalPointX * 100}% ${course.thumbnailFocalPointY * 100}%` }
              : undefined
            }
          />
          {/* Full image, centered */}
          <img
            src={course.thumbnailUrl}
            alt={course.title}
            className="relative w-full h-full object-contain"
          />
        </div>
      )}

      <div className="mb-8">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3 min-w-0">
            {!course.thumbnailUrl && (
              <div className="flex-shrink-0 w-10 h-10 bg-surface-700 rounded-lg flex items-center justify-center">
                <BookOpen className="h-5 w-5 text-surface-500" />
              </div>
            )}
            <h1 className="text-3xl font-bold text-surface-100">{course.title}</h1>
          </div>
        </div>

        <div className="flex items-center justify-between gap-4 flex-wrap mt-3">
          <div className="flex items-center gap-3">
            {course.price && (
              <span className="text-sm text-surface-300 bg-surface-700 px-3 py-1 rounded-full">
                {course.price}
              </span>
            )}
          </div>
          <ShareButtons title={course.title} />
        </div>
      </div>

      <hr className="border-surface-700 mb-8" />

      <div className="space-y-8">
        <CourseContentBlocks detail={course} />
        <CourseEventsList events={courseEvents} />
      </div>
    </div>
  )
}
