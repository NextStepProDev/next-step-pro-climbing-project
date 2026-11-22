import { useId, useState } from 'react'
import { Link } from 'react-router-dom'
import { Trans, useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, MessageSquare } from 'lucide-react'
import { contactApi } from '../../api/client'
import { useAuth } from '../../context/AuthContext'
import { getErrorMessage } from '../../utils/errors'
import { Button } from '../ui/Button'
import { ContextChip } from './ContextChip'
import type { ContactContext } from '../../types'

/** Mirrors ContactMessage.MAX_BODY_LENGTH on the server. */
export const CONTACT_BODY_MAX = 4000

const inputClass =
  'w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 placeholder-surface-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent'

interface ContactFormProps {
  context: ContactContext
  /** Reported during render (see useChildDirty), so a modal shell can ask before closing. */
  onDirtyChange?: (dirty: boolean) => void
  /** Called after a successful send, e.g. to offer "close" in a modal. */
  onSent?: () => void
}

/**
 * "Write to us" — one form for the contact page, a course page and the calendar. Each opening
 * passes its own `context`, which travels with the message so the team knows what it is about.
 *
 * A signed-in person writes into their one conversation, so the form asks only for the message;
 * a guest also gives a name, an address to answer and consent. The confirmation is on the screen
 * only: no "we got your message" mail exists (anyone could make the site mail any address).
 */
export function ContactForm({ context, onDirtyChange, onSent }: ContactFormProps) {
  const { t, i18n } = useTranslation('common')
  const { user, isAuthenticated } = useAuth()
  const queryClient = useQueryClient()
  const id = useId()

  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [body, setBody] = useState('')
  const [consent, setConsent] = useState(false)
  // Honeypot. Never shown to a person; a bot that fills every field fills this one too.
  const [website, setWebsite] = useState('')
  const [sentToConversation, setSentToConversation] = useState<boolean | null>(null)
  // Field errors appear only after a first click on Send, not while the person is still typing
  const [attempted, setAttempted] = useState(false)

  const dirty = sentToConversation === null && (body.trim() !== '' || name.trim() !== '' || email.trim() !== '')
  // Reported during render, not in an effect: the modal's guard reads it in an event handler,
  // and an effect would arrive one render late (see useChildDirty).
  onDirtyChange?.(dirty)

  const mutation = useMutation({
    mutationFn: () => contactApi.send({
      name: isAuthenticated ? undefined : name.trim(),
      email: isAuthenticated ? undefined : email.trim(),
      body: body.trim(),
      consent: isAuthenticated ? false : consent,
      website: website || undefined,
      contextType: context.type,
      courseId: context.type === 'COURSE' ? context.courseId : undefined,
      date: context.type === 'CALENDAR' ? context.date ?? undefined : undefined,
      language: i18n.language,
    }),
    onSuccess: (result) => {
      setSentToConversation(result.inConversation)
      if (result.inConversation) {
        queryClient.invalidateQueries({ queryKey: ['conversation'] })
      }
      onSent?.()
    },
  })

  if (sentToConversation !== null) {
    return (
      <div className="flex flex-col items-center text-center gap-3 py-6" role="status">
        <CheckCircle2 className="w-10 h-10 text-green-400" aria-hidden="true" />
        <p className="text-surface-100 font-semibold">{t('contactForm.sentTitle')}</p>
        <p className="text-sm text-surface-400 max-w-sm">
          {sentToConversation ? t('contactForm.sentInApp') : t('contactForm.sentGuest')}
        </p>
        {sentToConversation && (
          <Link
            to="/my-reservations?tab=messages"
            className="inline-flex items-center gap-2 text-sm text-primary-400 hover:text-primary-300"
          >
            <MessageSquare className="w-4 h-4" aria-hidden="true" />
            {t('contactForm.openConversation')}
          </Link>
        )}
        <button
          type="button"
          onClick={() => {
            setBody('')
            setSentToConversation(null)
            setAttempted(false)
            mutation.reset()
          }}
          className="text-xs text-surface-500 hover:text-surface-300 underline"
        >
          {t('contactForm.writeAnother')}
        </button>
      </div>
    )
  }

  // The button never greys out: a click on an incomplete form says what is missing, next to it.
  const errors = {
    name: !isAuthenticated && name.trim() === '' ? t('contactForm.errors.name') : null,
    email: isAuthenticated ? null
      : email.trim() === '' ? t('contactForm.errors.email')
        : !EMAIL_SHAPE.test(email.trim()) ? t('contactForm.errors.emailInvalid') : null,
    body: body.trim() === '' ? t('contactForm.errors.body') : null,
    consent: !isAuthenticated && !consent ? t('contactForm.errors.consent') : null,
  }
  const shown = (error: string | null) => (attempted ? error : null)

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        setAttempted(true)
        if (Object.values(errors).some(Boolean) || mutation.isPending) return
        mutation.mutate()
      }}
      // `relative` anchors the off-screen honeypot to the form rather than to the document
      className="relative space-y-4"
      noValidate
    >
      <ContextChip context={context} />

      {isAuthenticated && user ? (
        <p className="text-xs text-surface-500">
          {t('contactForm.signedInAs', { name: `${user.firstName} ${user.lastName}`.trim(), email: user.email })}
        </p>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label htmlFor={`${id}-name`} className="block text-sm font-medium text-surface-300 mb-1">
              {t('contactForm.name')}
            </label>
            <input
              id={`${id}-name`}
              type="text"
              autoComplete="name"
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              aria-invalid={shown(errors.name) ? true : undefined}
              aria-describedby={shown(errors.name) ? `${id}-name-error` : undefined}
              className={inputClass}
            />
            <FieldError id={`${id}-name-error`} message={shown(errors.name)} />
          </div>
          <div>
            <label htmlFor={`${id}-email`} className="block text-sm font-medium text-surface-300 mb-1">
              {t('contactForm.email')}
            </label>
            <input
              id={`${id}-email`}
              type="email"
              autoComplete="email"
              maxLength={255}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              aria-invalid={shown(errors.email) ? true : undefined}
              aria-describedby={shown(errors.email) ? `${id}-email-error` : undefined}
              className={inputClass}
            />
            <FieldError id={`${id}-email-error`} message={shown(errors.email)} />
          </div>
        </div>
      )}

      <div>
        <label htmlFor={`${id}-body`} className="block text-sm font-medium text-surface-300 mb-1">
          {t('contactForm.message')}
        </label>
        <textarea
          id={`${id}-body`}
          rows={5}
          maxLength={CONTACT_BODY_MAX}
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder={t('contactForm.messagePlaceholder')}
          aria-invalid={shown(errors.body) ? true : undefined}
          aria-describedby={shown(errors.body) ? `${id}-body-error` : undefined}
          className={`${inputClass} resize-y`}
        />
        <FieldError id={`${id}-body-error`} message={shown(errors.body)} />
      </div>

      {/* Honeypot: off-screen rather than display:none, which some bots skip; out of the tab order
          and hidden from assistive tech, so no person ever meets it. */}
      <div className="absolute -left-[10000px] w-px h-px overflow-hidden" aria-hidden="true">
        <label htmlFor={`${id}-website`}>{t('contactForm.honeypot')}</label>
        <input
          id={`${id}-website`}
          type="text"
          tabIndex={-1}
          autoComplete="off"
          value={website}
          onChange={(e) => setWebsite(e.target.value)}
        />
      </div>

      {!isAuthenticated && (
        <div>
          <label className="flex items-start gap-2 text-xs text-surface-400 leading-relaxed cursor-pointer">
            <input
              type="checkbox"
              checked={consent}
              onChange={(e) => setConsent(e.target.checked)}
              aria-invalid={shown(errors.consent) ? true : undefined}
              aria-describedby={shown(errors.consent) ? `${id}-consent-error` : undefined}
              className="mt-0.5 rounded border-surface-600 bg-surface-800 text-primary-600 focus:ring-primary-500"
            />
            <span>
              <Trans
                i18nKey="contactForm.consent"
                ns="common"
                components={{
                  1: <Link to="/polityka-prywatnosci" target="_blank" className="text-surface-300 hover:text-primary-400 underline" />,
                }}
              />
            </span>
          </label>
          <FieldError id={`${id}-consent-error`} message={shown(errors.consent)} />
        </div>
      )}

      {mutation.isError && (
        <p role="alert" className="text-sm text-rose-400">{getErrorMessage(mutation.error)}</p>
      )}

      <Button type="submit" loading={mutation.isPending} className="w-full sm:w-auto">
        {t('contactForm.send')}
      </Button>
    </form>
  )
}

/** Deliberately loose: the server's @Email decides; this only catches the missing "@" or domain. */
const EMAIL_SHAPE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function FieldError({ id, message }: { id: string; message: string | null }) {
  if (!message) return null
  return <p id={id} className="text-xs text-rose-400/80 mt-1">{message}</p>
}
