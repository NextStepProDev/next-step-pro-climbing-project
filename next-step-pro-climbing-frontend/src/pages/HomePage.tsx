import { Link } from "react-router-dom";
import { Calendar, Users, Award, ArrowRight } from "lucide-react";
import { Button } from "../components/ui/Button";
import logoWhite from "../assets/logo/logo-white.png";
import logoBlack from "../assets/logo/logo-black.png";

export function HomePage() {
  return (
    <div>
      {/* Hero Section */}
      <section className="relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-primary-900/20 via-dark-950 to-dark-950" />
        <img
          src={logoBlack}
          alt=""
          aria-hidden="true"
          className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] sm:w-[800px] lg:w-[1000px] opacity-[0.04] pointer-events-none select-none"
        />
        <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-24 sm:py-32">
          <div className="text-center max-w-3xl mx-auto">
            <img
              src={logoWhite}
              alt="Next Step Pro Climbing"
              className="h-32 sm:h-40 lg:h-48 mx-auto mb-8 drop-shadow-[0_0_30px_rgba(59,130,246,0.3)]"
            />
            <p className="text-lg sm:text-xl text-dark-400 mb-8">
              Profesjonalna szkoła wspinaczkowa. Kursy, treningi i warsztaty dla
              każdego poziomu zaawansowania.
            </p>
            <div className="flex flex-col sm:flex-row gap-4 justify-center">
              <Link to="/calendar">
                <Button size="lg" className="w-full sm:w-auto">
                  <Calendar className="w-5 h-5 mr-2" />
                  Zobacz kalendarz
                </Button>
              </Link>
              <a href="#offer">
                <Button
                  variant="secondary"
                  size="lg"
                  className="w-full sm:w-auto"
                >
                  Poznaj ofertę
                </Button>
              </a>
            </div>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section id="offer" className="py-16 sm:py-24 bg-dark-900/50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-12">
            <h2 className="text-3xl font-bold text-dark-100 mb-4">
              Nasza oferta
            </h2>
            <p className="text-dark-400 max-w-2xl mx-auto">
              Wybierz formę treningu dopasowaną do Twoich potrzeb i poziomu
              zaawansowania
            </p>
          </div>

          <div className="grid md:grid-cols-3 gap-8">
            {/* Course */}
            <div className="bg-dark-900 rounded-xl border border-dark-800 p-6 hover:border-primary-500/50 transition-colors">
              <div className="w-12 h-12 bg-primary-500/10 rounded-lg flex items-center justify-center mb-4">
                <Award className="w-6 h-6 text-primary-400" />
              </div>
              <h3 className="text-xl font-semibold text-dark-100 mb-2">
                Kursy
              </h3>
              <p className="text-dark-400 mb-4">
                Kursy wspinaczkowe przygotowujące do samodzielnego i
                bezpiecznego wspinania w skałach oraz na sztucznej ścianie.
                Szkolenia prowadzone są przez doświadczonych instruktorów PZA.
              </p>
              <ul className="space-y-2 text-sm text-dark-300">
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-primary-400 rounded-full shrink-0" />
                  Kurs wspinaczkowy po drogach ubezpieczonych wg programu PZA -
                  900zł
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-primary-400 rounded-full shrink-0" />
                  Wspinaczka wielowyciągowa po drogach ubezpieczonych 2 dni -
                  800zł
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-primary-400 rounded-full shrink-0" />
                  Kurs autoratownictwa 1 - 2 dni - 700zł
                </li>
              </ul>
            </div>

            {/* Training */}
            <div className="bg-dark-900 rounded-xl border border-dark-800 p-6 hover:border-primary-500/50 transition-colors">
              <div className="w-12 h-12 bg-green-500/10 rounded-lg flex items-center justify-center mb-4">
                <Users className="w-6 h-6 text-green-400" />
              </div>
              <h3 className="text-xl font-semibold text-dark-100 mb-2">
                Treningi
              </h3>
              <p className="text-dark-400 mb-4">
                Regularne sesje treningowe w małych grupach lub indywidualnie.
                Rozwijaj siłę, technikę i wytrzymałość pod okiem trenerów Next
                Step Pro Climbing.
              </p>
              <ul className="space-y-2 text-sm text-dark-300">
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-green-400 rounded-full" />
                  Treningi indywidualne
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-green-400 rounded-full" />
                  Sekcje wspinaczkowe
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-green-400 rounded-full" />
                  Wspinanie w skałach
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-green-400 rounded-full" />
                  Treningi klasy mistrzowskiej
                </li>
              </ul>
            </div>

            {/* Workshop */}
            <div className="bg-dark-900 rounded-xl border border-dark-800 p-6 hover:border-primary-500/50 transition-colors">
              <div className="w-12 h-12 bg-amber-500/10 rounded-lg flex items-center justify-center mb-4">
                <Calendar className="w-6 h-6 text-amber-400" />
              </div>
              <h3 className="text-xl font-semibold text-dark-100 mb-2">
                Warsztaty
              </h3>
              <p className="text-dark-400 mb-4">
                Specjalistyczne zajęcia tematyczne. Skup się na konkretnych
                aspektach wspinaczki w intensywnej formie.
              </p>
              <ul className="space-y-2 text-sm text-dark-300">
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-amber-400 rounded-full" />
                  Doskonalenie techniki wspinaczkowej
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-amber-400 rounded-full" />
                  Mastermind
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-amber-400 rounded-full" />
                  Wybrane techniki linowe i asekuracyjne
                </li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="py-16 sm:py-24 relative overflow-hidden">
        <img
          src={logoBlack}
          alt=""
          aria-hidden="true"
          className="absolute -right-20 top-1/2 -translate-y-1/2 w-[300px] sm:w-[400px] opacity-[0.03] pointer-events-none select-none rotate-12"
        />
        <div className="relative max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <h2 className="text-3xl font-bold text-dark-100 mb-4">
            Gotowy na pierwszy krok?
          </h2>
          <p className="text-dark-400 mb-8">
            Sprawdź dostępne terminy i zapisz się na wybrane zajęcia. Czekamy na
            Ciebie!
          </p>
          <Link to="/calendar">
            <Button size="lg">
              Przejdź do kalendarza
              <ArrowRight className="w-5 h-5 ml-2" />
            </Button>
          </Link>
        </div>
      </section>
    </div>
  );
}
