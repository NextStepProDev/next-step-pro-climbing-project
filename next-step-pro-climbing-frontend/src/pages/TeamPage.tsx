import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useParams, useNavigate } from 'react-router-dom'
import { User, X, ExternalLink } from 'lucide-react'
import { instructorApi } from '../api/client'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { QueryError } from '../components/ui/QueryError'
import { ShareButtons } from '../components/ui/ShareButtons'
import { renderRichText } from '../utils/renderRichText'
import { deserializeBio } from '../components/ui/bioBlocks'
import type { InstructorPublic, InstructorType } from '../types'

function renderBio(bio: string) {
  const blocks = deserializeBio(bio)
  return blocks.map((block, i) => {
    if (block.type === 'image') {
      return <img key={i} src={block.url} alt={block.alt} className="max-w-full rounded-lg my-2" />
    }
    return (
      <div key={i} className="text-dark-200 whitespace-pre-wrap"
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
          <span className="text-dark-200">{line}</span>
        </li>
      ))}
    </ul>
  )
}

// ─── Kafelek ──────────────────────────────────────────────────────────────────

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
      className="group relative aspect-[3/4] w-full overflow-hidden rounded-xl bg-dark-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
    >
      {member.photoUrl ? (
        <img
          src={member.photoUrl}
          alt={`${member.firstName} ${member.lastName}`}
          className="absolute inset-0 h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          style={
            member.focalPointX != null
              ? { objectPosition: `${member.focalPointX * 100}% ${(member.focalPointY ?? 0.5) * 100}%` }
              : undefined
          }
        />
      ) : (
        <div className="absolute inset-0 flex items-center justify-center bg-dark-700">
          <User className="h-20 w-20 text-dark-500" />
        </div>
      )}

      {/* gradient overlay */}
      <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent" />

      {/* badge */}
      {member.badgeUrl && (
        <img
          src={member.badgeUrl}
          alt="badge"
          className="absolute top-3 right-3 w-8 h-8 rounded-full object-contain drop-shadow-lg"
        />
      )}

      {/* name */}
      <div className="absolute bottom-0 left-0 right-0 p-4">
        <p className="text-base font-bold text-white leading-tight">
          {member.firstName} {member.lastName}
        </p>
      </div>
    </button>
  )
}

// ─── Modal ze szczegółami ─────────────────────────────────────────────────────

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
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      onClick={onClose}
    >
      {/* backdrop */}
      <div className="absolute inset-0 bg-black/70 backdrop-blur-sm" />

      <div
        className="relative z-10 w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-2xl bg-dark-900 border border-dark-700 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        {/* close button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 z-20 p-2 rounded-full bg-dark-800 hover:bg-dark-700 text-dark-300 hover:text-dark-100 transition-colors"
        >
          <X className="w-5 h-5" />
        </button>

        {/* photo header */}
        <div className="relative h-[28rem] md:h-[32rem] w-full overflow-hidden rounded-t-2xl bg-dark-800">
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
              <User className="h-24 w-24 text-dark-500" />
            </div>
          )}
          <div className="absolute inset-0 bg-gradient-to-t from-dark-900 via-dark-900/60 to-transparent" />

          {/* badge */}
          {member.badgeUrl && (
            <img
              src={member.badgeUrl}
              alt="badge"
              className="absolute top-4 right-14 w-10 h-10 rounded-full object-contain drop-shadow-lg"
            />
          )}

          <div className="absolute bottom-0 left-0 p-6 space-y-2">
            <h2 className="text-3xl font-bold text-white">
              {member.firstName} {member.lastName}
            </h2>
            <div className="flex items-center gap-3 flex-wrap">
              {member.profile8aUrl && (
                <a
                  href={member.profile8aUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-orange-500/20 border border-orange-500/40 text-orange-300 hover:bg-orange-500/30 transition-colors text-sm font-medium"
                >
                  <ExternalLink className="w-3.5 h-3.5" />
                  {t('team.profile8aLink')}
                </a>
              )}
              <ShareButtons title={`${member.firstName} ${member.lastName}`} />
            </div>
          </div>
        </div>

        {/* details */}
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
              <div className="text-dark-200">{renderBio(member.bio)}</div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

// ─── Główna strona ────────────────────────────────────────────────────────────

export function TeamPage({ memberType }: { memberType: InstructorType }) {
  const { t } = useTranslation('common')
  const { memberId } = useParams<{ memberId?: string }>()
  const navigate = useNavigate()
  const [selected, setSelected] = useState<InstructorPublic | null>(null)

  const { data: allMembers, isLoading, error } = useQuery({
    queryKey: ['instructors'],
    queryFn: instructorApi.getAll,
  })

  // Auto-open modal when URL contains memberId (deep link)
  useEffect(() => {
    if (memberId && allMembers) {
      const found = allMembers.find(m => m.id === memberId)
      if (found) setSelected(found)
    }
  }, [memberId, allMembers])

  const title = memberType === 'INSTRUCTOR' ? t('team.instructors') : t('team.competitors')
  const certificationsLabel = memberType === 'INSTRUCTOR' ? t('team.certifications') : t('team.achievements')
  const aboutLabel = t('team.about')

  const openModal = (m: InstructorPublic) => {
    setSelected(m)
    navigate(m.id)
  }

  const closeModal = () => {
    setSelected(null)
    navigate('..', { relative: 'path' })
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

  const members = (allMembers ?? []).filter((m) => m.memberType === memberType)

  return (
    <>
      <div className="container mx-auto px-4 py-8">
        <h1 className="text-3xl font-bold text-dark-100 mb-8">{title}</h1>

        {members.length === 0 ? (
          <div className="text-center text-dark-400">{t('team.noMembers')}</div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            {members.map((m) => (
              <MemberTile key={m.id} member={m} onClick={() => openModal(m)} />
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
