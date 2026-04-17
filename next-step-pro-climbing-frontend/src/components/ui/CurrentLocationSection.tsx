import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { MapPin, ArrowRight } from "lucide-react";

// === ZMIANA LOKALIZACJI: edytuj tylko tę tablicę ===
const LOCATIONS = ["El Chorro", "Granada", "Motril", "Los Cahorros", "Órgiva"];
// ===================================================

export function CurrentLocationSection() {
  const { t } = useTranslation("home");

  return (
    <section className="border-y border-amber-500/20 bg-amber-500/5">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10 sm:py-12">
        <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-8">
          {/* Left: title + locations */}
          <div className="flex-1">
            <div className="flex items-center gap-2 mb-3">
              <MapPin className="w-5 h-5 text-amber-400 shrink-0" />
              <h2 className="text-xl font-bold text-amber-300">
                {t("location.title")}
              </h2>
            </div>
            <p className="text-dark-300 text-sm mb-4">
              {t("location.subtitle")}
            </p>
            <ul className="space-y-1.5">
              {LOCATIONS.map((place) => (
                <li key={place} className="flex items-center gap-2 text-dark-200 text-sm">
                  <span className="w-1.5 h-1.5 bg-amber-400 rounded-full shrink-0" />
                  {place}
                </li>
              ))}
            </ul>
          </div>

          {/* Right: CTAs */}
          <div className="sm:self-center flex flex-col gap-3">
            <Link
              to="/calendar"
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-amber-500/10 text-amber-400 border border-amber-500/30 rounded-lg hover:bg-amber-500/20 transition-colors font-medium text-sm whitespace-nowrap"
            >
              {t("location.ctaCalendar")}
              <ArrowRight className="w-4 h-4" />
            </Link>
            <Link
              to="/kursy"
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-amber-500/10 text-amber-400 border border-amber-500/30 rounded-lg hover:bg-amber-500/20 transition-colors font-medium text-sm whitespace-nowrap"
            >
              {t("location.ctaCourses")}
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
