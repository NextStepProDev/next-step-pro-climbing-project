import { Phone, Mail, User } from 'lucide-react'
import { Facebook, Youtube, Instagram } from '../ui/BrandIcons'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useTheme } from '../../context/ThemeContext'
import logoWhite from '../../assets/logo/logo-white.png'
import logoBlack from '../../assets/logo/logo-black.png'
import { CONTACT } from '../../constants/contact'

export function Footer() {
  const { t } = useTranslation('common')
  const { theme } = useTheme()

  return (
    <footer className="bg-surface-900">
      <div className="h-0.5 bg-gradient-to-r from-transparent via-primary-400 to-transparent" />
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
          {/* Brand */}
          <div>
            <img src={theme === 'dark' ? logoWhite : logoBlack} alt="Next Step Pro Climbing" className="h-12 mb-3" />
            <p className="text-surface-400 text-sm">
              {t('footer.description')}
            </p>
          </div>

          {/* Contact */}
          <div>
            <h3 className="font-semibold mb-2">
              <Link to="/kontakt" className="text-surface-100 hover:text-primary-400 transition-all duration-150 active:scale-95">
                {t('footer.contact')}
              </Link>
            </h3>
            <ul className="space-y-1.5">
              <li className="flex items-center gap-2 text-surface-400 text-sm">
                <User className="w-4 h-4 shrink-0" />
                <span>{t('footer.instructor')}</span>
              </li>
              <li className="flex items-center gap-2 text-surface-400 text-sm">
                <Phone className="w-4 h-4 shrink-0" />
                <a href={CONTACT.phoneHref} className="hover:text-primary-400 hover:translate-x-1 transition-all duration-200">
                  {CONTACT.phone}
                </a>
              </li>
              <li className="flex items-center gap-2 text-surface-400 text-sm">
                <Mail className="w-4 h-4 shrink-0" />
                <a href={CONTACT.emailHref} className="hover:text-primary-400 hover:translate-x-1 transition-all duration-200">
                  {CONTACT.email}
                </a>
              </li>
              <li className="flex items-center gap-2 text-surface-400 text-sm">
                <Facebook className="w-4 h-4 shrink-0" />
                <a
                  href={CONTACT.facebook}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="hover:text-primary-400 hover:translate-x-1 transition-all duration-200"
                >
                  Facebook
                </a>
              </li>
              <li className="flex items-center gap-2 text-surface-400 text-sm">
                <Youtube className="w-4 h-4 shrink-0" />
                <a
                  href={CONTACT.youtube}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="hover:text-primary-400 hover:translate-x-1 transition-all duration-200"
                >
                  YouTube
                </a>
              </li>
              {CONTACT.instagram && (
                <li className="flex items-center gap-2 text-surface-400 text-sm">
                  <Instagram className="w-4 h-4 shrink-0" />
                  <a
                    href={CONTACT.instagram}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="hover:text-primary-400 hover:translate-x-1 transition-all duration-200"
                  >
                    Instagram
                  </a>
                </li>
              )}
            </ul>
          </div>

          {/* Hours */}
          <div>
            <h3 className="font-semibold text-surface-100 mb-2">{t('footer.hours')}</h3>
            <ul className="space-y-1 text-surface-400 text-sm">
              <li className="flex justify-between">
                <span>{t('footer.weekdays')}</span>
                <span>{t('footer.seeCalendar')}</span>
              </li>
              <li className="flex justify-between">
                <span>{t('footer.saturday')}</span>
                <span>{t('footer.seeCalendar')}</span>
              </li>
              <li className="flex justify-between">
                <span>{t('footer.sunday')}</span>
                <span>{t('footer.seeCalendar')}</span>
              </li>
            </ul>
          </div>
        </div>

        <div className="mt-5 pt-4 border-t border-surface-800 text-center text-surface-500 text-sm">
          <p>&copy; {new Date().getFullYear()} Next Step Pro Climbing. {t('footer.copyright')}</p>
          <p className="mt-1 space-x-3">
            <Link to="/polityka-prywatnosci" className="text-surface-600 hover:text-surface-400 transition-colors text-xs">
              Polityka prywatności
            </Link>
            <Link to="/faq" className="text-surface-600 hover:text-surface-400 transition-colors text-xs">
              {t('nav.help')}
            </Link>
          </p>
          <p className="mt-1 text-surface-600 text-xs">v{__APP_VERSION__}</p>
        </div>
      </div>
    </footer>
  )
}
