import { useState, useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router-dom'
import { PageHead } from '../components/ui/PageHead'
import { ChevronDown, BookOpen, ArrowRight } from 'lucide-react'
import { coursesApi, calendarApi } from '../api/client'
import type { CourseSummary } from '../types'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { QueryError } from '../components/ui/QueryError'
import { CourseContentBlocks, CourseEventsList } from '../components/courses/CourseContentBlocks'
import { COURSE_CONTENT_LANGUAGES, getDefaultCourseContentLanguage } from '../constants/courseLanguages'
import clsx from 'clsx'

export function CoursesPage() {
  const { t, i18n } = useTranslation('common')
  const { hash } = useLocation()
  const scrolledRef = useRef('')
  const [contentLanguage, setContentLanguage] = useState(() =>
    getDefaultCourseContentLanguage(i18n.language)
  )

  const { data: courses, isLoading, error } = useQuery({
    queryKey: ['courses', contentLanguage],
    queryFn: () => coursesApi.getAll(contentLanguage),
    staleTime: 5 * 60 * 1000,
  })

  useEffect(() => {
    if (!courses || !hash.startsWith('#course-') || scrolledRef.current === hash) return
    scrolledRef.current = hash
    // Delay to let ScrollToTop (window.scrollTo(0,0)) finish first
    setTimeout(() => {
      const el = document.getElementById(hash.slice(1))
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }, 200)
  }, [courses, hash])

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-24">
        <LoadingSpinner />
      </div>
    )
  }

  if (error) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-12">
        <QueryError error={error} />
      </div>
    )
  }

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <PageHead title={t('courses.title')} description={t('courses.metaDescription')} path="/kursy" availableLanguages={['pl', 'en', 'es']} currentLanguage={contentLanguage} />
      <div className="flex items-center justify-between mb-8">
        <h1 className="text-3xl font-bold text-dark-100">{t('courses.title')}</h1>
        <div className="flex items-center gap-1 bg-dark-800 border border-dark-700 rounded-lg p-1">
          {COURSE_CONTENT_LANGUAGES.map((lang) => (
            <button
              key={lang.code}
              onClick={() => setContentLanguage(lang.code)}
              className={clsx(
                'px-3 py-1.5 rounded-md text-sm font-medium transition-colors',
                contentLanguage === lang.code
                  ? 'bg-primary-500 text-white'
                  : 'text-dark-400 hover:text-dark-100 hover:bg-dark-700'
              )}
            >
              {lang.label}
            </button>
          ))}
        </div>
      </div>

      {!courses || courses.length === 0 ? (
        <p className="text-dark-400 text-center py-12">{t('courses.noCourses')}</p>
      ) : (
        <div className="space-y-3">
          {courses.map((course, i) => (
            <div key={course.id} className="animation-stagger" style={{ animationDelay: `${i * 120}ms` }}>
              <CourseAccordionItem
                course={course}
                defaultOpen={hash === `#course-${course.id}`}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function CourseAccordionItem({ course, defaultOpen = false }: { course: CourseSummary; defaultOpen?: boolean }) {
  const { t } = useTranslation('common')
  const [isOpen, setIsOpen] = useState(defaultOpen)

  const { data: detail, isLoading } = useQuery({
    queryKey: ['courses', course.id],
    queryFn: () => coursesApi.getById(course.id),
    enabled: isOpen,
    staleTime: 10 * 60 * 1000,
  })

  const { data: courseEvents } = useQuery({
    queryKey: ['courseEvents', course.translationGroupId],
    queryFn: () => calendarApi.getCourseEventsByTranslationGroup(course.translationGroupId),
    enabled: isOpen,
    staleTime: 5 * 60 * 1000,
  })

  return (
    <div id={`course-${course.id}`} className="card-glass hover-gradient-border border border-dark-700/50 rounded-lg overflow-hidden scroll-mt-24 hover:-translate-y-0.5 transition-all duration-200">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="w-full flex items-center gap-4 p-4 text-left hover:bg-dark-750 transition-colors"
        aria-expanded={isOpen}
      >
        {/* Miniaturka */}
        <div className="flex-shrink-0 w-20 h-20 bg-dark-700 rounded-lg overflow-hidden">
          {course.thumbnailUrl ? (
            <img
              src={course.thumbnailUrl}
              alt={course.title}
              className="w-full h-full object-contain"
              onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = 'none' }}
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center">
              <BookOpen className="h-8 w-8 text-dark-500" />
            </div>
          )}
        </div>

        {/* Tytuł + cena */}
        <div className="flex-1 min-w-0">
          <p className="font-semibold text-dark-100 text-lg leading-snug">{course.title}</p>
          {course.price && (
            <p className="text-sm text-dark-400 mt-1 line-clamp-2">{course.price}</p>
          )}
        </div>

        {/* Chevron */}
        <ChevronDown
          className={clsx(
            'flex-shrink-0 h-5 w-5 text-dark-400 transition-transform duration-200',
            isOpen && 'rotate-180'
          )}
        />
      </button>

      {/* Rozwinięta treść */}
      {isOpen && (
        <div className="border-t border-dark-700 px-6 py-6 space-y-8">
          {isLoading ? (
            <div className="flex justify-center py-8">
              <LoadingSpinner />
            </div>
          ) : detail ? (
            <CourseContentBlocks detail={detail} />
          ) : null}

          <CourseEventsList events={courseEvents} />

          <Link
            to={`/kursy/${course.id}`}
            className="inline-flex items-center gap-2 text-primary-400 hover:text-primary-300 transition-colors text-sm font-medium"
          >
            {t('courses.viewDetails')}
            <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      )}
    </div>
  )
}

