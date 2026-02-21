import { Phone, Mail, User, Facebook, Youtube } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import logoWhite from '../../assets/logo/logo-white.png'

export function Footer() {
  const { t } = useTranslation('common')

  return (
    <footer className="bg-dark-900 border-t border-dark-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {/* Brand */}
          <div>
            <img src={logoWhite} alt="Next Step Pro Climbing" className="h-16 mb-4" />
            <p className="text-dark-400 text-sm">
              {t('footer.description')}
            </p>
          </div>

          {/* Contact */}
          <div>
            <h3 className="font-semibold text-dark-100 mb-4">{t('footer.contact')}</h3>
            <ul className="space-y-3">
              <li className="flex items-center gap-2 text-dark-400 text-sm">
                <Phone className="w-4 h-4" />
                <span>+48 535 246 673</span>
              </li>
              <li className="flex items-center gap-2 text-dark-400 text-sm">
                <Mail className="w-4 h-4" />
                <span>nextsteppro.team@gmail.com</span>
              </li>
              <li className="flex items-center gap-2 text-dark-400 text-sm">
                <User className="w-4 h-4" />
                <span>{t('footer.instructor')}</span>
              </li>
              <li className="flex items-center gap-2 text-dark-400 text-sm">
                <Facebook className="w-4 h-4" />
                <a
                  href="https://www.facebook.com/ClimbingTeamofPoland"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="hover:text-primary-400 transition-colors"
                >
                  Facebook
                </a>
              </li>
              <li className="flex items-center gap-2 text-dark-400 text-sm">
                <Youtube className="w-4 h-4" />
                <a
                  href="https://www.youtube.com/@PureEssentialFilms-hd1kh"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="hover:text-primary-400 transition-colors"
                >
                  YouTube
                </a>
              </li>
            </ul>
          </div>

          {/* Hours */}
          <div>
            <h3 className="font-semibold text-dark-100 mb-4">{t('footer.hours')}</h3>
            <ul className="space-y-2 text-dark-400 text-sm">
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

        <div className="mt-8 pt-8 border-t border-dark-800 text-center text-dark-500 text-sm">
          <p>&copy; {new Date().getFullYear()} Next Step Pro Climbing. {t('footer.copyright')}</p>
          <p className="mt-1 text-dark-600 text-xs">v{__APP_VERSION__}</p>
        </div>
      </div>
    </footer>
  )
}
