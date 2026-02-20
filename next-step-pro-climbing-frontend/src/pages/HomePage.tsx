import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import {
  Calendar,
  Users,
  Award,
  ArrowRight,
  Search,
  CalendarCheck,
  UserPlus,
} from "lucide-react";
import { Button } from "../components/ui/Button";
import logoWhite from "../assets/logo/logo-white.png";
import logoBlack from "../assets/logo/logo-black.png";

export function HomePage() {
  const { t } = useTranslation("home");

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
            <h1 className="text-3xl sm:text-4xl lg:text-5xl font-bold text-dark-50 mb-4">
              {t("hero.tagline")}
            </h1>
            <p className="text-base sm:text-lg text-dark-400 mb-8 max-w-2xl mx-auto">
              {t("hero.subtitle")}
            </p>
            <div className="flex flex-col sm:flex-row gap-4 justify-center">
              <Link to="/calendar">
                <Button size="lg" className="w-full sm:w-auto">
                  <Calendar className="w-5 h-5 mr-2" />
                  {t("hero.viewCalendar")}
                </Button>
              </Link>
              <a href="#offer">
                <Button
                  variant="secondary"
                  size="lg"
                  className="w-full sm:w-auto"
                >
                  {t("hero.seeOffer")}
                </Button>
              </a>
            </div>
          </div>
        </div>
      </section>

      {/* Steps Section */}
      <section className="py-16 sm:py-24">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <h2 className="text-3xl font-bold text-dark-100 mb-12 text-center">
            {t("steps.title")}
          </h2>
          <div className="grid md:grid-cols-3 gap-8 md:gap-0">
            {[
              { num: "01", icon: Search, key: "step1" },
              { num: "02", icon: CalendarCheck, key: "step2" },
              { num: "03", icon: UserPlus, key: "step3" },
            ].map((step, i) => (
              <div
                key={step.key}
                className={`flex flex-col items-center text-center px-6 ${i < 2 ? "md:border-r md:border-dark-800" : ""}`}
              >
                <span className="text-sm font-mono text-primary-400 mb-3">
                  {step.num}
                </span>
                <div className="w-12 h-12 bg-primary-500/10 rounded-lg flex items-center justify-center mb-4">
                  <step.icon className="w-6 h-6 text-primary-400" />
                </div>
                <h3 className="text-lg font-semibold text-dark-100 mb-2">
                  {t(`steps.${step.key}.title`)}
                </h3>
                <p className="text-dark-400 text-sm">
                  {t(`steps.${step.key}.description`)}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section id="offer" className="py-16 sm:py-24 bg-dark-900/50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-12">
            <h2 className="text-3xl font-bold text-dark-100 mb-4">
              {t("offer.title")}
            </h2>
            <p className="text-dark-400 max-w-2xl mx-auto">
              {t("offer.subtitle")}
            </p>
          </div>

          <div className="grid md:grid-cols-3 gap-8">
            {/* Course */}
            <div className="bg-dark-900 rounded-xl border border-dark-800 p-6 hover:border-primary-500/50 hover:-translate-y-1 transition-all duration-300">
              <div className="w-12 h-12 bg-primary-500/10 rounded-lg flex items-center justify-center mb-4">
                <Award className="w-6 h-6 text-primary-400" />
              </div>
              <h3 className="text-xl font-semibold text-dark-100 mb-2">
                {t("offer.courses.title")}
              </h3>
              <p className="text-dark-400 mb-4">
                {t("offer.courses.description")}
              </p>
              <ul className="space-y-2 text-sm text-dark-300">
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-primary-400 rounded-full shrink-0" />
                  {t("offer.courses.item1")}
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-primary-400 rounded-full shrink-0" />
                  {t("offer.courses.item2")}
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-primary-400 rounded-full shrink-0" />
                  {t("offer.courses.item3")}
                </li>
              </ul>
            </div>

            {/* Training */}
            <div className="bg-dark-900 rounded-xl border border-dark-800 p-6 hover:border-primary-500/50 hover:-translate-y-1 transition-all duration-300">
              <div className="w-12 h-12 bg-green-500/10 rounded-lg flex items-center justify-center mb-4">
                <Users className="w-6 h-6 text-green-400" />
              </div>
              <h3 className="text-xl font-semibold text-dark-100 mb-2">
                {t("offer.trainings.title")}
              </h3>
              <p className="text-dark-400 mb-4">
                {t("offer.trainings.description")}
              </p>
              <ul className="space-y-2 text-sm text-dark-300">
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-green-400 rounded-full" />
                  {t("offer.trainings.item1")}
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-green-400 rounded-full" />
                  {t("offer.trainings.item2")}
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-green-400 rounded-full" />
                  {t("offer.trainings.item3")}
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-green-400 rounded-full" />
                  {t("offer.trainings.item4")}
                </li>
              </ul>
            </div>

            {/* Workshop */}
            <div className="bg-dark-900 rounded-xl border border-dark-800 p-6 hover:border-primary-500/50 hover:-translate-y-1 transition-all duration-300">
              <div className="w-12 h-12 bg-amber-500/10 rounded-lg flex items-center justify-center mb-4">
                <Calendar className="w-6 h-6 text-amber-400" />
              </div>
              <h3 className="text-xl font-semibold text-dark-100 mb-2">
                {t("offer.workshops.title")}
              </h3>
              <p className="text-dark-400 mb-4">
                {t("offer.workshops.description")}
              </p>
              <ul className="space-y-2 text-sm text-dark-300">
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-amber-400 rounded-full" />
                  {t("offer.workshops.item1")}
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-amber-400 rounded-full" />
                  {t("offer.workshops.item2")}
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 bg-amber-400 rounded-full" />
                  {t("offer.workshops.item3")}
                </li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="py-16 sm:py-24 relative overflow-hidden bg-gradient-to-b from-transparent via-primary-950/10 to-transparent">
        <img
          src={logoBlack}
          alt=""
          aria-hidden="true"
          className="absolute -right-20 top-1/2 -translate-y-1/2 w-[300px] sm:w-[400px] opacity-[0.03] pointer-events-none select-none rotate-12"
        />
        <div className="relative max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <h2 className="text-3xl font-bold text-dark-100 mb-4">
            {t("cta.title")}
          </h2>
          <p className="text-dark-400 mb-8">
            {t("cta.subtitle")}
          </p>
          <Link to="/calendar">
            <Button size="lg">
              {t("cta.button")}
              <ArrowRight className="w-5 h-5 ml-2" />
            </Button>
          </Link>
        </div>
      </section>
    </div>
  );
}
