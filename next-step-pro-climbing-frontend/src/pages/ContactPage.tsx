import { useTranslation } from 'react-i18next'
import { Mail, Phone, User, Facebook, Youtube, Instagram, ExternalLink } from 'lucide-react'
import logoWhite from '../assets/logo/logo-white.png'
import { pzaLogo } from '../assets'
import { CONTACT } from '../constants/contact'
import { useState } from 'react'

export function ContactPage() {
  const { t } = useTranslation('common')

  return (
    <div className="min-h-screen bg-dark-950">
      {/* Hero */}
      <div className="relative overflow-hidden bg-gradient-to-b from-dark-900 to-dark-950 border-b border-dark-800">
        <div className="absolute inset-0 opacity-5">
          <div className="absolute top-0 left-1/4 w-96 h-96 bg-primary-500 rounded-full blur-3xl" />
          <div className="absolute bottom-0 right-1/4 w-64 h-64 bg-primary-700 rounded-full blur-3xl" />
        </div>
        <div className="relative max-w-4xl mx-auto px-4 py-16 sm:py-24 text-center">
          <img
            src={logoWhite}
            alt={CONTACT.clubName}
            className="h-20 sm:h-24 mx-auto mb-6 drop-shadow-lg"
          />
          <h1 className="text-3xl sm:text-4xl font-bold text-dark-100 mb-3">
            {t('contact.title')}
          </h1>
          <p className="text-dark-400 text-lg max-w-xl mx-auto">
            {t('contact.subtitle')}
          </p>
        </div>
      </div>

      {/* Content */}
      <div className="max-w-4xl mx-auto px-4 py-12 sm:py-16">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

          {/* Karta: Dane kontaktowe */}
          <div className="bg-dark-900 border border-dark-800 rounded-2xl p-6 sm:p-8 space-y-5">
            <h2 className="text-lg font-semibold text-dark-100 mb-6">
              {t('contact.directContact')}
            </h2>

            {/* Instruktor */}
            <ContactRow
              icon={<User className="w-5 h-5" />}
              label={t('contact.instructor')}
              value={`${CONTACT.instructor} · ${t('contact.pzaInstructor')}`}
              iconColor="text-primary-400"
            />

            {/* Telefon */}
            <ContactRow
              icon={<Phone className="w-5 h-5" />}
              label={t('contact.phone')}
              value={CONTACT.phone}
              href={CONTACT.phoneHref}
              iconColor="text-green-400"
            />

            {/* Email */}
            <ContactRow
              icon={<Mail className="w-5 h-5" />}
              label={t('contact.email')}
              value={CONTACT.email}
              href={CONTACT.emailHref}
              iconColor="text-blue-400"
            />
          </div>

          {/* Karta: Social media */}
          <div className="bg-dark-900 border border-dark-800 rounded-2xl p-6 sm:p-8">
            <h2 className="text-lg font-semibold text-dark-100 mb-6">
              {t('contact.followUs')}
            </h2>

            <div className="space-y-4">
              {/* Facebook */}
              <SocialLink
                href={CONTACT.facebook}
                icon={<Facebook className="w-5 h-5" />}
                label="Facebook"
                handle="ClimbingTeamofPoland"
                color="hover:border-blue-500/50 hover:bg-blue-500/5"
                iconColor="text-blue-400"
              />

              {/* YouTube */}
              <SocialLink
                href={CONTACT.youtube}
                icon={<Youtube className="w-5 h-5" />}
                label="YouTube"
                handle="PureEssentialFilms"
                color="hover:border-red-500/50 hover:bg-red-500/5"
                iconColor="text-red-400"
              />

              {/* Instagram — gdy będzie URL */}
              {CONTACT.instagram && (
                <SocialLink
                  href={CONTACT.instagram}
                  icon={<Instagram className="w-5 h-5" />}
                  label="Instagram"
                  handle="nextsteppro"
                  color="hover:border-pink-500/50 hover:bg-pink-500/5"
                  iconColor="text-pink-400"
                />
              )}
            </div>
          </div>

          {/* Karta: PZA */}
          <div className="md:col-span-2 bg-dark-900 border border-dark-800 rounded-2xl p-6 sm:p-8">
            <PzaSection t={t} />
          </div>
        </div>
      </div>
    </div>
  )
}

// ==================== Sub-komponenty ====================

function ContactRow({
  icon,
  label,
  value,
  href,
  iconColor,
}: {
  icon: React.ReactNode
  label: string
  value: string
  href?: string
  iconColor: string
}) {
  return (
    <div className="flex items-start gap-4 group">
      <div className={`mt-0.5 shrink-0 ${iconColor}`}>{icon}</div>
      <div className="min-w-0">
        <p className="text-xs text-dark-500 uppercase tracking-wider mb-0.5">{label}</p>
        {href ? (
          <a
            href={href}
            className="text-dark-200 font-medium hover:text-primary-400 transition-colors break-all"
          >
            {value}
          </a>
        ) : (
          <p className="text-dark-200 font-medium">{value}</p>
        )}
      </div>
    </div>
  )
}

function SocialLink({
  href,
  icon,
  label,
  handle,
  color,
  iconColor,
}: {
  href: string
  icon: React.ReactNode
  label: string
  handle: string
  color: string
  iconColor: string
}) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className={`flex items-center gap-4 p-4 rounded-xl border border-dark-700 transition-all duration-200 group ${color}`}
    >
      <div className={`shrink-0 ${iconColor}`}>{icon}</div>
      <div className="flex-1 min-w-0">
        <p className="text-dark-200 font-medium">{label}</p>
        <p className="text-dark-500 text-sm truncate">@{handle}</p>
      </div>
      <ExternalLink className="w-4 h-4 text-dark-600 group-hover:text-dark-400 transition-colors shrink-0" />
    </a>
  )
}

function PzaSection({ t }: { t: (key: string) => string }) {
  const [logoHidden, setLogoHidden] = useState(false)

  return (
    <div className="flex flex-col sm:flex-row items-center sm:items-start gap-6">
      {!logoHidden && (
        <div className="shrink-0 w-20 h-20 bg-white rounded-2xl flex items-center justify-center shadow-lg p-2">
          <img
            src={pzaLogo}
            alt="PZA"
            className="w-full h-full object-contain"
            onError={() => setLogoHidden(true)}
          />
        </div>
      )}
      <div>
        <h2 className="text-lg font-semibold text-dark-100 mb-2">
          {t('contact.pzaTitle')}
        </h2>
        <p className="text-dark-400 text-sm leading-relaxed">
          {t('contact.pzaDescription')}
        </p>
      </div>
    </div>
  )
}
