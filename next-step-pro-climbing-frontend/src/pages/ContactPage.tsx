import { useTranslation } from 'react-i18next'
import { PageHead } from '../components/ui/PageHead'
import { Mail, Phone, User, ExternalLink } from 'lucide-react'
import { Facebook, Youtube, Instagram } from '../components/ui/BrandIcons'
import { useTheme } from '../context/ThemeContext'
import logoWhite from '../assets/logo/logo-white.png'
import logoBlack from '../assets/logo/logo-black.png'
import { pzaLogo } from '../assets'
import { CONTACT } from '../constants/contact'
import { useState } from 'react'

export function ContactPage() {
  const { t } = useTranslation('common')
  const { theme } = useTheme()

  return (
    <div className="min-h-screen bg-surface-950">
      <PageHead title={t('contact.title')} description={t('contact.metaDescription')} />
      {/* Hero */}
      <div className="relative overflow-hidden bg-gradient-to-b from-surface-900 to-surface-950 border-b border-surface-800">
        <div className="absolute inset-0 opacity-5">
          <div className="absolute top-0 left-1/4 w-96 h-96 bg-primary-500 rounded-full blur-3xl" />
          <div className="absolute bottom-0 right-1/4 w-64 h-64 bg-primary-700 rounded-full blur-3xl" />
        </div>
        <div className="relative max-w-4xl mx-auto px-4 py-16 sm:py-24 text-center">
          <img
            src={theme === 'dark' ? logoWhite : logoBlack}
            alt={CONTACT.clubName}
            className="h-20 sm:h-24 mx-auto mb-6 drop-shadow-lg"
          />
          <h1 className="text-3xl sm:text-4xl font-bold text-surface-100 mb-3">
            {t('contact.title')}
          </h1>
          <p className="text-surface-400 text-lg max-w-xl mx-auto">
            {t('contact.subtitle')}
          </p>
        </div>
      </div>

      {/* Content */}
      <div className="max-w-4xl mx-auto px-4 py-12 sm:py-16">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

          {/* Karta: Dane kontaktowe */}
          <div className="bg-surface-900 border border-surface-800 rounded-2xl p-6 sm:p-8 space-y-5">
            <h2 className="text-lg font-semibold text-surface-100 mb-6">
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
          <div className="bg-surface-900 border border-surface-800 rounded-2xl p-6 sm:p-8">
            <h2 className="text-lg font-semibold text-surface-100 mb-6">
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
                handle="ZeroGravityLab"
                color="hover:border-red-500/50 hover:bg-red-500/5"
                iconColor="text-red-400"
              />

              {/* Instagram — once there is a URL */}
              {CONTACT.instagram && (
                <SocialLink
                  href={CONTACT.instagram}
                  icon={<Instagram className="w-5 h-5" />}
                  label="Instagram"
                  handle="next.step.pro.climbing"
                  color="hover:border-pink-500/50 hover:bg-pink-500/5"
                  iconColor="text-pink-400"
                />
              )}

              {/* 8a.nu */}
              <SocialLink
                href={CONTACT.eightA}
                icon={<span className="w-5 h-5 flex items-center justify-center font-bold text-sm leading-none">8a</span>}
                label="8a.nu"
                handle="mateusz-nawratek"
                color="hover:border-amber-500/50 hover:bg-amber-500/5"
                iconColor="text-amber-400"
              />

              {/* Strava */}
              <SocialLink
                href={CONTACT.strava}
                icon={<StravaIcon className="w-5 h-5" />}
                label="Strava"
                handle="Mateusz Nawratek"
                color="hover:border-orange-500/50 hover:bg-orange-500/5"
                iconColor="text-orange-400"
              />
            </div>
          </div>

          {/* Karta: PZA */}
          <div className="md:col-span-2 bg-surface-900 border border-surface-800 rounded-2xl p-6 sm:p-8">
            <PzaSection t={t} />
          </div>
        </div>
      </div>
    </div>
  )
}

// ==================== Sub-components ====================

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
        <p className="text-xs text-surface-500 uppercase tracking-wider mb-0.5">{label}</p>
        {href ? (
          <a
            href={href}
            className="inline-block text-surface-200 font-medium hover:text-primary-400 hover:translate-x-1 transition-all duration-200 break-all"
          >
            {value}
          </a>
        ) : (
          <p className="text-surface-200 font-medium">{value}</p>
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
      className={`flex items-center gap-4 p-4 rounded-xl border border-surface-700 transition-all duration-200 active:scale-95 group ${color}`}
    >
      <div className={`shrink-0 ${iconColor}`}>{icon}</div>
      <div className="flex-1 min-w-0">
        <p className="text-surface-200 font-medium">{label}</p>
        <p className="text-surface-500 text-sm truncate">@{handle}</p>
      </div>
      <ExternalLink className="w-4 h-4 text-surface-600 group-hover:text-surface-400 transition-colors shrink-0" />
    </a>
  )
}

function StravaIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
      <path d="M15.387 17.944l-2.089-4.116h-3.065L15.387 24l5.15-10.172h-3.066m-7.008-5.599l2.836 5.598h4.172L10.463 0l-7 13.828h4.169" />
    </svg>
  )
}

function PzaSection({ t }: { t: (key: string) => string }) {
  const [logoHidden, setLogoHidden] = useState(false)

  return (
    <div className="flex flex-col sm:flex-row items-center sm:items-start gap-6">
      {!logoHidden && (
        <div className="shrink-0 w-20 h-20 flex items-center justify-center">
          <img
            src={pzaLogo}
            alt="PZA"
            className="w-full h-full object-contain"
            onError={() => setLogoHidden(true)}
          />
        </div>
      )}
      <div>
        <h2 className="text-lg font-semibold text-surface-100 mb-2">
          {t('contact.pzaTitle')}
        </h2>
        <p className="text-surface-400 text-sm leading-relaxed">
          {t('contact.pzaDescription')}
        </p>
      </div>
    </div>
  )
}
