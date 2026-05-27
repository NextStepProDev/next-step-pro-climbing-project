import { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Helmet } from "react-helmet-async";
import { PageHead } from "../components/ui/PageHead";
import {
  Calendar,
  Users,
  Award,
  ArrowRight,
  Search,
  CalendarCheck,
  UserPlus,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "../components/ui/Button";
import { AnimatedCounter } from "../components/ui/AnimatedCounter";
import { ShareButtons } from "../components/ui/ShareButtons";
import { CurrentLocationSection } from "../components/ui/CurrentLocationSection";
import { siteSettingsApi } from "../api/client";
import { useTheme } from "../context/ThemeContext";
import logoWhite from "../assets/logo/logo-white.png";
import logoBlack from "../assets/logo/logo-black.png";

function usePreloadImage(url: string | null | undefined) {
  useEffect(() => {
    if (!url) return;
    const link = document.createElement('link');
    link.rel = 'preload';
    link.as = 'image';
    link.href = url;
    document.head.appendChild(link);
    return () => { document.head.removeChild(link); };
  }, [url]);
}

function BadgeImg({ src, href, className }: { src: string; href?: string | null; className?: string }) {
  const img = (
    <img
      src={src}
      alt="Badge"
      className={`${className} object-contain opacity-75 drop-shadow-[0_2px_8px_rgba(0,0,0,0.5)] transition-opacity duration-500`}
    />
  );
  if (href) {
    return (
      <a href={href} target="_blank" rel="noopener noreferrer" className={`${className} block cursor-pointer`}>
        <img
          src={src}
          alt="Badge"
          className="w-full h-full object-contain opacity-75 hover:opacity-100 drop-shadow-[0_2px_8px_rgba(0,0,0,0.5)] transition-opacity duration-300"
        />
      </a>
    );
  }
  return img;
}

export function HomePage() {
  const { t } = useTranslation("home");
  const { theme } = useTheme();
  const [heroImgLoaded, setHeroImgLoaded] = useState(false);

  const { data: homeSettings, isPending } = useQuery({
    queryKey: ["homeSettings"],
    queryFn: siteSettingsApi.getHome,
    staleTime: 30 * 60 * 1000,
  });

  const heroImageUrl = homeSettings?.hero.imageUrl ?? null;
  const badgeImageUrl = homeSettings?.badge.imageUrl ?? null;
  const badgeLeftImageUrl = homeSettings?.badgeLeft.imageUrl ?? null;

  usePreloadImage(heroImageUrl);

  const objectPosition = homeSettings?.hero.focalPointX != null
    ? `${(homeSettings.hero.focalPointX * 100).toFixed(1)}% ${((homeSettings.hero.focalPointY ?? 0.5) * 100).toFixed(1)}%`
    : 'center center';

  const showWatermark = !isPending && !heroImageUrl;

  return (
    <div>
      <PageHead description={t('metaDescription')} path="/" />
      <Helmet>
        <script type="application/ld+json">{JSON.stringify({
          '@context': 'https://schema.org',
          '@type': 'Organization',
          name: 'Next Step Pro Climbing',
          url: 'https://nextsteppro.pl',
          logo: 'https://nextsteppro.pl/logo/logo-white.png',
          contactPoint: {
            '@type': 'ContactPoint',
            telephone: '+34-622-257-683',
            contactType: 'customer service',
            availableLanguage: ['Polish', 'English', 'Spanish'],
          },
          sameAs: [
            'https://www.facebook.com/ClimbingTeamofPoland',
            'https://www.youtube.com/@ZeroGravityLab',
          ],
        })}</script>
      </Helmet>
      {/* Hero Section */}
      <section className="relative overflow-hidden sm:min-h-[70vh] sm:flex sm:flex-col sm:justify-center">
        {/* Base gradient — always rendered as placeholder/fallback */}
        <div className="absolute inset-0 bg-gradient-to-br from-primary-900/20 via-surface-950 to-surface-950" />

        {/* Watermark logo — only when API confirmed no hero image is set */}
        {showWatermark && (
          <img
            src={logoBlack}
            alt=""
            aria-hidden="true"
            className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] sm:w-[800px] lg:w-[1000px] opacity-[0.04] pointer-events-none select-none"
          />
        )}

        {heroImageUrl && (
          <>
            {/* Mobile: image above content with fixed aspect ratio — no cropping */}
            <div className="sm:hidden relative w-full overflow-hidden" style={{ aspectRatio: '3/2' }}>
              <img
                src={heroImageUrl}
                alt=""
                aria-hidden="true"
                fetchPriority="high"
                onLoad={() => setHeroImgLoaded(true)}
                className={`absolute inset-0 w-full h-full object-cover transition-opacity duration-500 ${heroImgLoaded ? 'opacity-100 animation-ken-burns' : 'opacity-0'}`}
                style={{ objectPosition }}
              />
              <div className="absolute inset-0 bg-gradient-to-b from-transparent via-transparent to-surface-950" />
              <div className="absolute bottom-3 right-3 z-20 bg-surface-900/60 backdrop-blur-sm rounded-full px-2.5 py-1 border border-surface-700/40">
                <ShareButtons title="Next Step Pro Climbing" url={window.location.origin} compact />
              </div>
            </div>
            {/* Desktop: full-section background */}
            <img
              src={heroImageUrl}
              alt=""
              aria-hidden="true"
              fetchPriority="high"
              onLoad={() => setHeroImgLoaded(true)}
              className={`hidden sm:block absolute inset-0 w-full h-full object-cover transition-opacity duration-500 ${heroImgLoaded ? 'opacity-100 animation-ken-burns' : 'opacity-0'}`}
              style={{ objectPosition }}
            />
            <div className={`hidden sm:block absolute inset-0 bg-gradient-to-b from-surface-950/15 via-surface-950/25 to-surface-950 transition-opacity duration-700 ${heroImgLoaded ? 'opacity-100' : 'opacity-0'}`} />
          </>
        )}
        {badgeLeftImageUrl && (
          <BadgeImg
            src={badgeLeftImageUrl}
            href={homeSettings?.badgeLeft.linkUrl}
            className="absolute top-3 left-3 sm:top-12 sm:left-36 z-20 w-[52px] h-[52px] sm:w-24 sm:h-24"
          />
        )}
        {badgeImageUrl && (
          <BadgeImg
            src={badgeImageUrl}
            href={homeSettings?.badge.linkUrl}
            className="absolute top-3 right-3 sm:top-12 sm:right-36 z-20 w-[52px] h-[52px] sm:w-24 sm:h-24"
          />
        )}
        <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10 sm:py-32">
          <div className="text-center max-w-3xl mx-auto">
            {/* === ANDALUSIA BADGE — zakomentuj ten blok aby usunąć === */}
            <div className="inline-flex items-center gap-2 px-3 py-1.5 bg-amber-500/10 border border-amber-500/30 rounded-full text-amber-400 text-sm font-medium mb-6">
              <span>📍</span>
              <span>{t("location.badge")}</span>
            </div>
            {/* === END BADGE === */}
            <img
              src={theme === 'dark' ? logoWhite : logoBlack}
              alt="Next Step Pro Climbing"
              className="h-32 sm:h-40 lg:h-48 mx-auto mb-8 drop-shadow-[0_0_30px_rgba(59,130,246,0.3)]"
            />
            <h1 className="text-3xl sm:text-4xl lg:text-5xl font-bold text-surface-50 mb-4">
              {t("hero.tagline")}
            </h1>
            <p className="text-base sm:text-lg text-surface-200 mb-8 max-w-2xl mx-auto">
              {t("hero.subtitle")}
            </p>
            <div className="flex flex-col sm:flex-row gap-4 justify-center">
              <Link to="/calendar">
                <Button size="lg" className="w-full sm:w-auto btn-glow">
                  <Calendar className="w-5 h-5 mr-2" />
                  {t("hero.viewCalendar")}
                </Button>
              </Link>
              <Link to="/kursy">
                <Button
                  variant="secondary"
                  size="lg"
                  className="w-full sm:w-auto btn-glow"
                >
                  {t("hero.seeOffer")}
                </Button>
              </Link>
            </div>
          </div>
        </div>
        <div className="hidden sm:block absolute bottom-6 right-6 z-20 bg-surface-900/60 backdrop-blur-sm rounded-full px-3 py-1.5 border border-surface-700/40">
          <ShareButtons title="Next Step Pro Climbing" url={window.location.origin} compact />
        </div>
      </section>

      {/* === WHERE I AM NOW — zakomentuj <CurrentLocationSection /> aby usunąć całą sekcję === */}
      <CurrentLocationSection />
      {/* === END WHERE I AM NOW === */}

      {/* Steps Section */}
      <section className="py-16 sm:py-24">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <h2 className="text-3xl font-bold text-surface-100 mb-12 text-center">
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
                className={`flex flex-col items-center text-center px-6 ${i < 2 ? "md:border-r md:border-surface-800" : ""}`}
              >
                <span className="text-sm font-mono text-primary-400 mb-3">
                  {step.num}
                </span>
                <div className="w-12 h-12 bg-primary-500/10 rounded-lg flex items-center justify-center mb-4">
                  <step.icon className="w-6 h-6 text-primary-400" />
                </div>
                <h3 className="text-lg font-semibold text-surface-100 mb-2">
                  {t(`steps.${step.key}.title`)}
                </h3>
                <p className="text-surface-400 text-sm">
                  {t(`steps.${step.key}.description`)}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Stats Section */}
      <section className="py-16 sm:py-20 bg-surface-900/50">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8">
            {[
              { target: 200, suffix: '+', label: t('stats.trained') },
              { target: 10, suffix: '+', label: t('stats.experience') },
              { target: 6, suffix: '', label: t('stats.courseTypes') },
              { target: 100, suffix: '%', label: t('stats.passion') },
            ].map((stat) => (
              <div key={stat.label} className="text-center">
                <p className="text-4xl sm:text-5xl font-bold text-primary-400 mb-2">
                  <AnimatedCounter target={stat.target} suffix={stat.suffix} />
                </p>
                <p className="text-sm text-surface-400">{stat.label}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section id="offer" className="py-16 sm:py-24 bg-surface-900/50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-12">
            <h2 className="text-3xl font-bold text-surface-100 mb-4">
              {t("offer.title")}
            </h2>
            <p className="text-surface-400 max-w-2xl mx-auto">
              {t("offer.subtitle")}
            </p>
          </div>

          <div className="grid md:grid-cols-3 gap-8">
            {/* Course */}
            <Link to="/kursy" className="block card-glass rounded-xl border border-surface-700/50 p-6 hover:border-primary-500/50 hover:-translate-y-0.5 transition-all duration-200 animation-stagger" style={{ animationDelay: '0ms' }}>
              <div className="w-12 h-12 bg-primary-500/10 rounded-lg flex items-center justify-center mb-4">
                <Award className="w-6 h-6 text-primary-400" />
              </div>
              <h3 className="text-xl font-semibold text-surface-100 mb-2">
                {t("offer.courses.title")}
              </h3>
              <p className="text-surface-400 mb-4">
                {t("offer.courses.description")}
              </p>
              <ul className="space-y-2 text-sm text-surface-300">
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
            </Link>

            {/* Training */}
            <Link to="/kursy" className="block card-glass rounded-xl border border-surface-700/50 p-6 hover:border-primary-500/50 hover:-translate-y-0.5 transition-all duration-200 animation-stagger" style={{ animationDelay: '100ms' }}>
              <div className="w-12 h-12 bg-green-500/10 rounded-lg flex items-center justify-center mb-4">
                <Users className="w-6 h-6 text-green-400" />
              </div>
              <h3 className="text-xl font-semibold text-surface-100 mb-2">
                {t("offer.trainings.title")}
              </h3>
              <p className="text-surface-400 mb-4">
                {t("offer.trainings.description")}
              </p>
              <ul className="space-y-2 text-sm text-surface-300">
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
            </Link>

            {/* Workshop */}
            <Link to="/kursy" className="block card-glass rounded-xl border border-surface-700/50 p-6 hover:border-primary-500/50 hover:-translate-y-0.5 transition-all duration-200 animation-stagger" style={{ animationDelay: '200ms' }}>
              <div className="w-12 h-12 bg-amber-500/10 rounded-lg flex items-center justify-center mb-4">
                <Calendar className="w-6 h-6 text-amber-400" />
              </div>
              <h3 className="text-xl font-semibold text-surface-100 mb-2">
                {t("offer.workshops.title")}
              </h3>
              <p className="text-surface-400 mb-4">
                {t("offer.workshops.description")}
              </p>
              <ul className="space-y-2 text-sm text-surface-300">
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
            </Link>
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
          <h2 className="text-3xl font-bold text-surface-100 mb-4">
            {t("cta.title")}
          </h2>
          <p className="text-surface-400 mb-8">
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
