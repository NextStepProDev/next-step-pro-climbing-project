import { Phone, Mail, MapPin } from 'lucide-react'
import logoWhite from '../../assets/logo/logo-white.png'

export function Footer() {
  return (
    <footer className="bg-dark-900 border-t border-dark-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {/* Brand */}
          <div>
            <img src={logoWhite} alt="Next Step Pro Climbing" className="h-16 mb-4" />
            <p className="text-dark-400 text-sm">
              Szkolenia wspinaczkowe dopasowane do Twojego poziomu i celów. 
              Trenuj pod okiem doświadczonych instruktorów i rozwijaj się świadomie.
            </p>
          </div>

          {/* Contact */}
          <div>
            <h3 className="font-semibold text-dark-100 mb-4">Kontakt</h3>
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
                <MapPin className="w-4 h-4" />
                <span>Mateusz Nawratek instruktor PZA</span>
              </li>
            </ul>
          </div>

          {/* Hours */}
          <div>
            <h3 className="font-semibold text-dark-100 mb-4">Godziny otwarcia</h3>
            <ul className="space-y-2 text-dark-400 text-sm">
              <li className="flex justify-between">
                <span>Poniedziałek - Piątek</span>
                <span>kalendarz</span>
              </li>
              <li className="flex justify-between">
                <span>Sobota</span>
                <span>kalendarz</span>
              </li>
              <li className="flex justify-between">
                <span>Niedziela</span>
                <span>kalendarz</span>
              </li>
            </ul>
          </div>
        </div>

        <div className="mt-8 pt-8 border-t border-dark-800 text-center text-dark-500 text-sm">
          <p>&copy; {new Date().getFullYear()} Next Step Pro Climbing. Wszelkie prawa zastrzeżone.</p>
          <p className="mt-1 text-dark-600 text-xs">v{__APP_VERSION__}</p>
        </div>
      </div>
    </footer>
  )
}
