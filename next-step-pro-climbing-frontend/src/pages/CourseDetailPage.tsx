import { useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { ArrowLeft, BookOpen } from 'lucide-react'
import { coursesApi, calendarApi } from '../api/client'
import { PageHead } from '../components/ui/PageHead'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { QueryError } from '../components/ui/QueryError'
import { ShareButtons } from '../components/ui/ShareButtons'
import { CourseContentBlocks, CourseEventsList } from '../components/courses/CourseContentBlocks'
import { COURSE_CONTENT_LANGUAGES } from '../constants/courseLanguages'
import clsx from 'clsx'

export function CourseDetailPage() {
  const { t } = useTranslation('common')
  const { courseId } = useParams<{ courseId: string }>()
  const navigate = useNavigate()
  const switchLangRef = useRef<{ translationGroupId: string } | null>(null)

  const { data: course, isLoading, error } = useQuery({
    queryKey: ['courses', courseId],
    queryFn: () => coursesApi.getById(courseId!),
    enabled: !!courseId,
  })

  const { data: courseEvents } = useQuery({
    queryKey: ['courseEvents', course?.translationGroupId],
    queryFn: () => calendarApi.getCourseEventsByTranslationGroup(course!.translationGroupId),
    enabled: !!course?.translationGroupId,
    staleTime: 5 * 60 * 1000,
  })

  async function handleLanguageSwitch(lang: string) {
    if (lang === course?.language || !course) return
    switchLangRef.current = { translationGroupId: course.translationGroupId }
    const courses = await coursesApi.getAll(lang)
    const match = courses.find(c => c.translationGroupId === switchLangRef.current?.translationGroupId)
    switchLangRef.current = null
    if (match) {
      navigate(`/kursy/${match.id}`, { replace: true })
    }
  }

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
        <div className="text-center text-dark-400">{t('courses.courseNotFound')}</div>
      </div>
    )
  }

  const metaDescription = course.blocks
    .find(b => b.blockType === 'TEXT' && b.content)
    ?.content?.replace(/<[^>]*>/g, '').slice(0, 160) ?? undefined

  return (
    <div className="container mx-auto px-4 py-8 max-w-3xl">
      <PageHead title={course.title} description={metaDescription} path={`/kursy/${courseId}`} />
      <div className="flex items-center justify-between mb-6">
        <Link
          to="/kursy"
          className="inline-flex items-center gap-2 text-dark-300 hover:text-dark-100 transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          {t('courses.backToCourses')}
        </Link>

        <div className="flex items-center gap-1 bg-dark-800 border border-dark-700 rounded-lg p-1">
          {COURSE_CONTENT_LANGUAGES.map((lang) => (
            <button
              key={lang.code}
              onClick={() => handleLanguageSwitch(lang.code)}
              className={clsx(
                'px-3 py-1.5 rounded-md text-sm font-medium transition-all duration-150 active:scale-95',
                course.language === lang.code
                  ? 'bg-primary-500 text-white'
                  : 'text-dark-400 hover:text-dark-100 hover:bg-dark-700'
              )}
            >
              {lang.label}
            </button>
          ))}
        </div>
      </div>

      {course.thumbnailUrl && (
        <div className="mb-6 rounded-lg overflow-hidden bg-dark-800">
          <img
            src={course.thumbnailUrl}
            alt={course.title}
            className="w-full max-h-[400px] object-contain"
            style={course.thumbnailFocalPointX != null && course.thumbnailFocalPointY != null
              ? { objectPosition: `${course.thumbnailFocalPointX * 100}% ${course.thumbnailFocalPointY * 100}%` }
              : undefined
            }
          />
        </div>
      )}

      <div className="mb-8">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3 min-w-0">
            {!course.thumbnailUrl && (
              <div className="flex-shrink-0 w-10 h-10 bg-dark-700 rounded-lg flex items-center justify-center">
                <BookOpen className="h-5 w-5 text-dark-500" />
              </div>
            )}
            <h1 className="text-3xl font-bold text-dark-100">{course.title}</h1>
          </div>
        </div>

        <div className="flex items-center justify-between gap-4 flex-wrap mt-3">
          <div className="flex items-center gap-3">
            {course.price && (
              <span className="text-sm text-dark-300 bg-dark-700 px-3 py-1 rounded-full">
                {course.price}
              </span>
            )}
          </div>
          <ShareButtons title={course.title} />
        </div>
      </div>

      <hr className="border-dark-700 mb-8" />

      <div className="space-y-8">
        <CourseContentBlocks detail={course} />
        <CourseEventsList events={courseEvents} />
      </div>
    </div>
  )
}
