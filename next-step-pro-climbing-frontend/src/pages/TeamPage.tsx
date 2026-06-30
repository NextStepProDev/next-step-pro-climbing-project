import { useEffect, useRef } from 'react'
import { useFocusTrap } from '../utils/useFocusTrap'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import { PageHead } from '../components/ui/PageHead'
import { User, X, ExternalLink } from 'lucide-react'
import clsx from 'clsx'
import { instructorApi } from '../api/client'
import { TileSkeleton } from '../components/ui/CardSkeleton'
import { QueryError } from '../components/ui/QueryError'
import { ShareButtons } from '../components/ui/ShareButtons'
import { renderRichText } from '../utils/renderRichText'
import { deserializeBio } from '../components/ui/bioBlocks'
import { COURSE_CONTENT_LANGUAGES, getDefaultCourseContentLanguage } from '../constants/courseLanguages'
import type { InstructorPublic, InstructorType } from '../types'

function renderBio(bio: string) {
  const blocks = deserializeBio(bio)
  return blocks.map((block, i) => {
    if (block.type === 'image') {
      return <img key={i} src={block.url} alt={block.alt} className="max-w-full rounded-lg my-2" />
    }
    return (
      <div key={i} className="text-surface-200 whitespace-pre-wrap"
        dangerouslySetInnerHTML={{ __html: renderRichText(block.content) }} />
    )
  })
}

function renderCertifications(text: string) {
  const lines = text.split('\n').filter(l => l.trim())
  return (
    <ul className="space-y-1.5">
      {lines.map((line, i) => (
        <li key={i} className="flex items-start gap-2.5">
          <span className="w-1.5 h-1.5 rounded-full bg-primary-400 mt-2 shrink-0" />
          <span className="text-surface-200">{line}</span>
        </li>
      ))}
    </ul>
  )
}

function MemberTile({
  member,
  onClick,
}: {
  member: InstructorPublic
  onClick: () => void
}) {
  return (
    <button
      onClick={onClick}
      className="group relative aspect-[3/4] w-full overflow-hidden rounded-xl bg-surface-800 border border-transparent hover:border-primary-500/50 hover:-translate-y-0.5 transition-all duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
    >
      {member.photoUrl ? (
        <img
          src={member.photoUrl}
          alt={`${member.firstName} ${member.lastName}`}
          loading="lazy"
          className="absolute inset-0 h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          style={
            member.focalPointX != null
              ? { objectPosition: `${member.focalPointX * 100}% ${(member.focalPointY ?? 0.5) * 100}%` }
              : undefined
          }
        />
      ) : (
        <div className="absolute inset-0 flex items-center justify-center bg-surface-700">
          <User className="h-20 w-20 text-surface-500" />
        </div>
      )}

      <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent" />

      {member.badgeUrl && (
        <img
          src={member.badgeUrl}
          alt="badge"
          className="absolute top-3 right-3 w-14 h-14 rounded-full object-contain drop-shadow-lg"
        />
      )}

      <div className="absolute bottom-0 left-0 right-0 p-4">
        <p className="text-base font-bold text-white leading-tight">
          {member.firstName} {member.lastName}
        </p>
      </div>
    </button>
  )
}

function MemberModal({
  member,
  certificationsLabel,
  aboutLabel,
  onClose,
}: {
  member: InstructorPublic
  certificationsLabel: string
  aboutLabel: string
  onClose: () => void
}) {
  const { t } = useTranslation('common')
  const trapRef = useFocusTrap(true)
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      onClick={onClose}
    >
      <div className="absolute inset-0 bg-black/70 backdrop-blur-sm" />

      <div
        ref={trapRef}
        role="dialog"
        aria-modal="true"
        className="relative z-10 w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-2xl bg-surface-900 border border-surface-700 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          aria-label={t('close')}
          onClick={onClose}
          className="absolute top-4 right-4 z-20 p-2 rounded-full bg-surface-800 hover:bg-surface-700 text-surface-300 hover:text-surface-100 transition-colors"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="relative h-[28rem] md:h-[32rem] w-full overflow-hidden rounded-t-2xl bg-surface-800">
          {member.photoUrl ? (
            <img
              src={member.photoUrl}
              alt={`${member.firstName} ${member.lastName}`}
              className="h-full w-full object-cover"
              style={
                member.focalPointX != null
                  ? { objectPosition: `${member.focalPointX * 100}% ${(member.focalPointY ?? 0.5) * 100}%` }
                  : undefined
              }
            />
          ) : (
            <div className="flex h-full items-center justify-center">
              <User className="h-24 w-24 text-surface-500" />
            </div>
          )}
          <div className="absolute inset-0 bg-gradient-to-t from-surface-900 via-surface-900/30 to-transparent" />

          {member.badgeUrl && (
            <img
              src={member.badgeUrl}
              alt="badge"
              className="absolute top-4 right-14 w-16 h-16 rounded-full object-contain drop-shadow-lg"
            />
          )}

          <div className="absolute bottom-0 left-0 p-6 space-y-2">
            <h2 className="text-3xl font-bold text-surface-50">
              {member.firstName} {member.lastName}
            </h2>
            <div className="flex items-center gap-3 flex-wrap">
              {member.profile8aUrl && (
                <a
                  href={member.profile8aUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-500/20 border border-amber-500/40 text-amber-400 hover:bg-amber-500/30 transition-colors text-sm font-medium"
                >
                  <ExternalLink className="w-3.5 h-3.5" />
                  {t('team.profile8aLink')}
                </a>
              )}
              <ShareButtons title={`${member.firstName} ${member.lastName}`} />
            </div>
          </div>
        </div>

        <div className="p-6 space-y-5">
          {member.certifications && (
            <div className="space-y-2">
              <h3 className="text-xs font-bold tracking-widest uppercase text-primary-400">
                {certificationsLabel}
              </h3>
              {renderCertifications(member.certifications)}
            </div>
          )}
          {member.bio && (
            <div className="space-y-2">
              <h3 className="text-xs font-bold tracking-widest uppercase text-primary-400">
                {aboutLabel}
              </h3>
              <div className="text-surface-200">{renderBio(member.bio)}</div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export function TeamPage({ memberType }: { memberType: InstructorType }) {
  const { t, i18n } = useTranslation('common')
  const { memberId } = useParams<{ memberId?: string }>()
  const navigate = useNavigate()

  const basePath = memberType === 'INSTRUCTOR' ? '/team/instruktorzy' : '/team/zawodnicy'
  const [searchParams, setSearchParams] = useSearchParams()

  // Język treści żyje w URL (?lang=...), więc PRZETRWUJE remount strony, który
  // Layout wymusza przy każdej zmianie ścieżki (`key={location.pathname}`).
  // Wcześniej był to lokalny useState — otwarcie członka (zmiana ścieżki) montowało
  // stronę od nowa i resetowało wybór języka do globalnego, więc modal próbował
  // pokazać rekord w złym języku (id jest zależne od języka) i nic się nie wyświetlało.
  const langParam = searchParams.get('lang')
  const contentLanguage =
    langParam === 'pl' || langParam === 'en' || langParam === 'es'
      ? langParam
      : getDefaultCourseContentLanguage(i18n.language)

  const setContentLanguage = (code: string) =>
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        next.set('lang', code)
        return next
      },
      { replace: true }
    )

  // Trasy zachowują wybrany język w query, żeby przetrwał remount/nawigację.
  const memberUrl = (id: string) => `${basePath}/${id}?lang=${contentLanguage}`
  const listUrl = `${basePath}?lang=${contentLanguage}`

  // Globalny przełącznik języka aktualizuje też treść zespołu.
  useEffect(() => {
    const handler = (lng: string) => {
      const code = getDefaultCourseContentLanguage(lng)
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          next.set('lang', code)
          return next
        },
        { replace: true }
      )
    }
    i18n.on('languageChanged', handler)
    return () => { i18n.off('languageChanged', handler) }
  }, [i18n, setSearchParams])

  const { data: allMembers, isLoading, isFetching, error } = useQuery({
    queryKey: ['instructors', contentLanguage],
    queryFn: () => instructorApi.getAll(contentLanguage),
  })

  const selected = memberId && allMembers
    ? (allMembers.find(m => m.id === memberId) ?? null)
    : null

  // Zmiana języka treści przy OTWARTYM członku (np. globalnym przełącznikiem)
  // przełącza go na ten język. Każda wersja językowa to osobny rekord z innym id,
  // więc mapujemy po translationGroupId na odpowiednik w nowym języku (jak Courses/News).
  const lastOpenedRef = useRef<InstructorPublic | null>(null)
  useEffect(() => {
    if (selected) lastOpenedRef.current = selected
  }, [selected])

  const prevLangRef = useRef(contentLanguage)
  useEffect(() => {
    if (prevLangRef.current === contentLanguage) return
    if (!allMembers) return // poczekaj na listę w nowym języku przed synchronizacją
    prevLangRef.current = contentLanguage
    if (!memberId) return // brak otwartego modala — nic do synchronizacji
    const prev = lastOpenedRef.current
    const target = prev
      ? allMembers.find(m => m.translationGroupId === prev.translationGroupId)
      : undefined
    navigate(target ? memberUrl(target.id) : listUrl, { replace: true })
    // memberUrl/listUrl zależą tylko od basePath+contentLanguage (już w deps)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [contentLanguage, allMembers, memberId, basePath, navigate])

  const title = memberType === 'INSTRUCTOR' ? t('team.instructors') : t('team.competitors')
  const certificationsLabel = memberType === 'INSTRUCTOR' ? t('team.certifications') : t('team.achievements')
  const aboutLabel = t('team.about')

  const openModal = (m: InstructorPublic) => {
    navigate(memberUrl(m.id))
  }

  const closeModal = () => {
    navigate(listUrl)
  }

  if (isLoading) {
    return (
      <div className="container mx-auto px-4 py-8">
        <h1 className="text-3xl font-bold text-surface-100 mb-8">{title}</h1>
        <TileSkeleton count={8} />
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

  const members = (allMembers ?? []).filter((m) => m.memberType === memberType)

  return (
    <>
      <PageHead title={title} description={memberType === 'INSTRUCTOR' ? t('team.instructorsMetaDescription') : t('team.competitorsMetaDescription')} path={memberType === 'INSTRUCTOR' ? '/team/instruktorzy' : '/team/zawodnicy'} availableLanguages={['pl', 'en', 'es']} currentLanguage={contentLanguage} />
      <div className="container mx-auto px-4 py-8">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
          <h1 className="text-3xl font-bold text-surface-100">{title}</h1>
          <div className="flex items-center gap-1 bg-surface-800 border border-surface-700 rounded-lg p-1">
            {COURSE_CONTENT_LANGUAGES.map((lang) => (
              <button
                key={lang.code}
                onClick={() => setContentLanguage(lang.code)}
                className={clsx(
                  'px-3 py-1.5 rounded-md text-sm font-medium transition-all duration-150 active:scale-95',
                  contentLanguage === lang.code
                    ? 'bg-primary-500 text-white'
                    : 'text-surface-400 hover:text-surface-100 hover:bg-surface-700'
                )}
              >
                {lang.label}
              </button>
            ))}
          </div>
        </div>

        {members.length === 0 ? (
          <div className="text-center text-surface-400">{t('team.noMembers')}</div>
        ) : (
          <div className={clsx('grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4 transition-opacity duration-150', isFetching && 'opacity-60')}>
            {members.map((m) => (
              <div key={m.id} className="scroll-reveal">
                <MemberTile member={m} onClick={() => openModal(m)} />
              </div>
            ))}
          </div>
        )}
      </div>

      {selected && (
        <MemberModal
          member={selected}
          certificationsLabel={certificationsLabel}
          aboutLabel={aboutLabel}
          onClose={closeModal}
        />
      )}
    </>
  )
}
