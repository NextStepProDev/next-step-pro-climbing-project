import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { User } from 'lucide-react'
import { instructorApi } from '../api/client'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { QueryError } from '../components/ui/QueryError'

const IMAGE_MD_RE = /^!\[([^\]]*)\]\((.+)\)$/

function renderBio(bio: string) {
  return bio.split('\n').map((line, i) => {
    const match = line.trim().match(IMAGE_MD_RE)
    if (match) {
      return (
        <img
          key={i}
          src={match[2]}
          alt={match[1]}
          className="max-w-full rounded-lg my-2"
        />
      )
    }
    return <span key={i}>{line}{'\n'}</span>
  })
}

export function InstructorsPage() {
  const { t } = useTranslation('common')
  const { data: instructors, isLoading, error } = useQuery({
    queryKey: ['instructors'],
    queryFn: instructorApi.getAll,
  })

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

  if (!instructors || instructors.length === 0) {
    return (
      <div className="container mx-auto px-4 py-8">
        <div className="text-center text-dark-400">
          {t('instructors.noInstructors')}
        </div>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 py-8">
      <h1 className="text-3xl font-bold text-dark-100 mb-8">{t('instructors.title')}</h1>

      <div className="space-y-6">
        {instructors.map((instructor) => (
          <div
            key={instructor.id}
            className="bg-dark-800 rounded-lg p-6 border border-dark-700"
          >
            <div className="flex flex-col md:flex-row gap-6">
              {/* Photo */}
              <div className="flex-shrink-0 relative w-32 h-32">
                {instructor.photoUrl ? (
                  <img
                    src={instructor.photoUrl}
                    alt={`${instructor.firstName} ${instructor.lastName}`}
                    className="w-32 h-32 rounded-full object-cover border-2 border-primary-500/20"
                    style={instructor.focalPointX != null ? { objectPosition: `${instructor.focalPointX * 100}% ${(instructor.focalPointY ?? 0.5) * 100}%` } : undefined}
                  />
                ) : (
                  <div className="w-32 h-32 rounded-full bg-dark-700 border-2 border-dark-600 flex items-center justify-center">
                    <User className="h-16 w-16 text-dark-400" />
                  </div>
                )}
                {instructor.badgeUrl && (
                  <img
                    src={instructor.badgeUrl}
                    alt="badge"
                    className="absolute bottom-0 right-0 w-9 h-9 rounded-full object-contain bg-white border border-dark-600 shadow p-0.5"
                  />
                )}
              </div>

              {/* Info */}
              <div className="flex-1 space-y-4">
                <h2 className="text-2xl font-bold text-dark-100">
                  {instructor.firstName} {instructor.lastName}
                </h2>

                {instructor.certifications && (
                  <div className="space-y-1">
                    <h3 className="text-xs font-bold tracking-widest uppercase text-primary-400">{t('instructors.certifications')}</h3>
                    <p className="text-dark-200 whitespace-pre-line">{instructor.certifications}</p>
                  </div>
                )}

                {instructor.bio && (
                  <div className="space-y-1">
                    <h3 className="text-xs font-bold tracking-widest uppercase text-primary-400">{t('instructors.about')}</h3>
                    <div className="text-dark-200 whitespace-pre-line">{renderBio(instructor.bio)}</div>
                  </div>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
