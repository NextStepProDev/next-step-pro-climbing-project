import { useState, useEffect, type CSSProperties } from "react";
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
  Dumbbell,
  MessageSquare,
  CheckCircle2,
  Bell,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "../context/AuthContext";
import { trainingCalendarApi } from "../api/client";
import { Button } from "../components/ui/Button";
import { AnimatedCounter } from "../components/ui/AnimatedCounter";
import { ShareButtons } from "../components/ui/ShareButtons";
import { Typewriter } from "../components/ui/Typewriter";
import { CurrentLocationSection } from "../components/ui/CurrentLocationSection";
import { useLocationContent } from "../hooks/useLocationContent";
import { useInView } from "../hooks/useInView";
import { siteSettingsApi } from "../api/client";
import logoWhite from "../assets/logo/logo-white.png";
import logoBlack from "../assets/logo/logo-black.png";
import heroDefault from "../assets/hero-default.jpg";
import heroDefaultMobile from "../assets/hero-default-mobile.jpg";

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
      className={`${className} object-contain opacity-90 drop-shadow-[0_2px_8px_rgba(0,0,0,0.5)] transition-opacity duration-500`}
    />
  );
  if (href) {
    return (
      <a href={href} target="_blank" rel="noopener noreferrer" className={`${className} block cursor-pointer`}>
        <img
          src={src}
          alt="Badge"
          className="w-full h-full object-contain opacity-90 hover:opacity-100 drop-shadow-[0_2px_8px_rgba(0,0,0,0.5)] transition-opacity duration-300"
        />
      </a>
    );
  }
  return img;
}

export function HomePage() {
  const { t } = useTranslation("home");
  const { isAuthenticated, user } = useAuth();
  const isAthlete = !!user?.isAthlete;

  // Unread badge on the hero "Athlete zone" button — same cache as the navbar poll
  const { data: trainingNotifications } = useQuery({
    queryKey: ["trainingCalendar", "notifications"],
    queryFn: trainingCalendarApi.getNotifications,
    enabled: isAthlete,
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
  });
  const athleteBadge = trainingNotifications?.newCount ?? 0;
  const { enabled: locationEnabled, badge: locationBadge } = useLocationContent();
  const [heroImgLoaded, setHeroImgLoaded] = useState(false);
  const [heroRevealTimedOut, setHeroRevealTimedOut] = useState(false);
  const { ref: stepsRef, inView: stepsInView } = useInView<HTMLDivElement>();

  // typewriter: the title types out first, the paragraph only after it.
  // reset on language change (the title text changes → the effect restarts)
  // — the "reset state during render" pattern from the React docs, no effect needed.
  const heroTagline = t("hero.tagline");
  // the keyword (wspinaczce/climbing/escalada) gets its gradient highlight only AFTER
  // the heading is typed out — then it "lights up". We split the tagline around the word.
  const heroKeyword = t("hero.taglineHighlight");
  const [titleTyped, setTitleTyped] = useState(false);
  const [prevTagline, setPrevTagline] = useState(heroTagline);
  if (prevTagline !== heroTagline) {
    setPrevTagline(heroTagline);
    setTitleTyped(false);
  }

  const { data: homeSettings } = useQuery({
    queryKey: ["homeSettings"],
    queryFn: siteSettingsApi.getHome,
    staleTime: 30 * 60 * 1000,
  });

  // The admin can upload a custom hero (imageUrl set) or have none — then we show the
  // hardcoded default image from the bundle. The default is instant (loads in parallel with
  // the JS, no round-trip to /api/settings/home and no separate file fetch from the server
  // disk), so there is no delay anymore. The admin's custom photo overrides the default once
  // the settings arrive.
  const customHeroUrl = homeSettings?.hero.imageUrl ?? null;
  const isDefaultHero = !customHeroUrl;
  const heroImageUrl = customHeroUrl ?? heroDefault;
  // Mobile has its own independent (vertical) photo. The admin can override it; when there is
  // none, we show the built-in vertical default cropped the same as before (54% 30%).
  const customMobileHeroUrl = homeSettings?.heroMobile?.imageUrl ?? null;
  const isDefaultMobileHero = !customMobileHeroUrl;
  const mobileHeroImageUrl = customMobileHeroUrl ?? heroDefaultMobile;
  const badgeImageUrl = homeSettings?.badge.imageUrl ?? null;
  const badgeLeftImageUrl = homeSettings?.badgeLeft.imageUrl ?? null;

  usePreloadImage(heroImageUrl);

  // The heading (typewriter) starts only once the visible hero image has loaded — text and
  // image reveal together instead of text-first-image-later. The default image loads
  // instantly, so the start is immediate; a 1.5 s failsafe in case the (admin's custom) photo
  // is slow. While waiting, hero-loading-glow plays.
  useEffect(() => {
    if (heroImgLoaded) return;
    const timer = setTimeout(() => setHeroRevealTimedOut(true), 1500);
    return () => clearTimeout(timer);
  }, [heroImgLoaded]);

  const heroReady = heroImgLoaded || heroRevealTimedOut;

  const objectPosition = isDefaultHero
    ? '52% 20%' // crop: more rock above the climber (climber lower → gap from the logo) + slightly left
    : homeSettings?.hero.focalPointX != null
      ? `${(homeSettings.hero.focalPointX * 100).toFixed(1)}% ${((homeSettings.hero.focalPointY ?? 0.5) * 100).toFixed(1)}%`
      : 'center center';

  const mobileObjectPosition = isDefaultMobileHero
    ? '54% 30%' // crop of the built-in vertical default (climber higher)
    : homeSettings?.heroMobile?.focalPointX != null
      ? `${(homeSettings.heroMobile.focalPointX * 100).toFixed(1)}% ${((homeSettings.heroMobile.focalPointY ?? 0.5) * 100).toFixed(1)}%`
      : 'center center';

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
      <section className="relative overflow-hidden min-h-[100svh] -mt-18 flex flex-col sm:min-h-screen sm:justify-center">
        {/* Base gradient — always rendered as placeholder/fallback */}
        <div className="absolute inset-0 bg-gradient-to-br from-primary-900/20 via-surface-950 to-surface-950" />

        {/* Loading spotlight — waiting for the photo from disk dressed up as stage light
            warming up; disappears once the photo comes in and takes over */}
        {heroImageUrl && !heroImgLoaded && (
          <div className="hero-loading-glow absolute inset-0 z-[1] pointer-events-none" />
        )}

        {heroImageUrl && (
          <>
            {/* Mobile: a separate VERTICAL photo filling the whole phone screen (object-cover).
                Desktop has its own independent photo below — the desktop version untouched. */}
            <img
              src={mobileHeroImageUrl}
              alt=""
              aria-hidden="true"
              fetchPriority="high"
              onLoad={() => setHeroImgLoaded(true)}
              className={`sm:hidden absolute inset-0 w-full h-full object-cover ${heroImgLoaded ? 'hero-cinematic' : 'opacity-0'}`}
              style={{ objectPosition: mobileObjectPosition }}
            />
            {/* Desktop: the existing section background (admin's custom or the horizontal default) — unchanged. */}
            <img
              src={heroImageUrl}
              alt=""
              aria-hidden="true"
              fetchPriority="high"
              onLoad={() => setHeroImgLoaded(true)}
              className={`hidden sm:block absolute inset-0 w-full h-full object-cover ${heroImgLoaded ? 'hero-cinematic' : 'opacity-0'}`}
              style={{ objectPosition }}
            />
            {/* Gradient under the content: stronger at the bottom on mobile (seats the lower glass
                card), subtle on desktop — so the photo edges stay bright. */}
            <div className={`absolute inset-0 bg-gradient-to-b from-surface-950/30 via-transparent to-surface-950 sm:from-transparent sm:via-surface-950/4 transition-opacity duration-700 ${heroImgLoaded ? 'opacity-100' : 'opacity-0'}`} />
            {/* Desktop: a constant, gentle dark underlay beneath the content (both themes) — mainly under
                the logo, so the light text/logo stay readable on any photo; fades to transparency, so
                the photo edges stay bright. The text itself also gets a halo (.hero-over-photo). */}
            <div className="hidden sm:block absolute inset-0 z-[1] pointer-events-none bg-[radial-gradient(ellipse_75%_70%_at_50%_50%,rgba(0,0,0,0.18),rgba(0,0,0,0.04)_60%,transparent_80%)]" />
          </>
        )}
        {badgeLeftImageUrl && (
          <BadgeImg
            src={badgeLeftImageUrl}
            href={homeSettings?.badgeLeft.linkUrl}
            className="absolute top-20 left-3 sm:top-24 sm:left-36 z-20 w-[52px] h-[52px] sm:w-24 sm:h-24"
          />
        )}
        {badgeImageUrl && (
          <BadgeImg
            src={badgeImageUrl}
            href={homeSettings?.badge.linkUrl}
            className="absolute top-20 right-3 sm:top-24 sm:right-36 z-20 w-[52px] h-[52px] sm:w-24 sm:h-24"
          />
        )}
        {/* Mobile: a fixed photo strip at the top (the card never slides under the PZA badges)
            + a flexible gap that pushes the card down on tall screens and collapses on short
            ones (instead of pushing content under the navbar — that used to break the layout). */}
        <div aria-hidden="true" className="sm:hidden shrink-0 h-16" />
        <div aria-hidden="true" className="sm:hidden flex-1" />
        <div className="relative z-10 w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pb-6 sm:py-8">
          {/* Mobile: content in the lower glass card (text readability on any photo).
              Desktop (sm:): the card fades to transparency — same look as before. */}
          <div className="text-center max-w-3xl mx-auto rounded-2xl border border-white/12 bg-surface-950/30 backdrop-blur-[2px] shadow-2xl shadow-black/40 px-5 py-6 sm:rounded-none sm:border-0 sm:bg-transparent sm:backdrop-blur-none sm:shadow-none sm:p-0">
            {/* === ANDALUSIA BADGE — shown when the section is active and the badge is non-empty === */}
            {locationEnabled && locationBadge && (
              <div className="location-badge inline-flex items-center gap-2 px-3 py-1.5 sm:px-4 sm:py-2 bg-amber-500/15 backdrop-blur-md border border-amber-400/40 rounded-full text-amber-400 text-sm sm:text-base font-medium mb-4 sm:mb-6 shadow-lg">
                <span className="location-badge-pin">📍</span>
                <span>{locationBadge}</span>
              </div>
            )}
            {/* === END BADGE === */}
            {/* The logo is always white — it sits on the photo (desktop) / on the dark glass card (mobile).
                It "pops in" (hero-logo-in) as the FINALE — only after the heading is typed out (titleTyped). */}
            <img
              src={logoWhite}
              alt="Next Step Pro Climbing"
              style={{ animationDelay: '1100ms' }}
              className={`h-20 sm:h-36 lg:h-40 mx-auto mb-4 sm:mb-6 drop-shadow-[0_0_30px_rgba(59,130,246,0.3)] ${titleTyped ? 'hero-logo-in' : 'opacity-0'}`}
            />
            <h1 style={{ '--hero-delay': '240ms' } as CSSProperties} className="hero-rise text-3xl sm:text-4xl lg:text-5xl font-bold mb-3 sm:mb-4 text-white sm:hero-over-photo">
              <Typewriter
                text={heroTagline}
                active={heroReady}
                speed={28}
                startDelay={300}
                highlight={heroKeyword}
                highlightClassName="hero-keyword"
                onDone={() => setTitleTyped(true)}
              />
            </h1>
            {/* the paragraph appears (hero-rise) only once the heading has been typed out;
                until then opacity-0 reserves the height → the buttons below do not jump */}
            {/* Mobile: a short, punchy subtitle (the whole glass card must fit on 1 screen). */}
            <p style={{ '--hero-delay': '120ms' } as CSSProperties} className={`sm:hidden text-[15px] leading-snug mb-6 max-w-md mx-auto text-white/95 ${titleTyped ? 'hero-rise' : 'opacity-0'}`}>
              {t("hero.subtitleMobile")}
            </p>
            {/* Desktop: the full description (untouched). */}
            <p style={{ '--hero-delay': '120ms' } as CSSProperties} className={`hidden sm:block text-lg mb-6 max-w-2xl mx-auto text-white/90 hero-over-photo ${titleTyped ? 'hero-rise' : 'opacity-0'}`}>
              {t("hero.subtitle")}
            </p>
            {/* the buttons slide in right after the paragraph — a top-down cascade, only after the heading is typed */}
            <div style={{ '--hero-delay': '320ms' } as CSSProperties} className={`flex flex-col sm:flex-row gap-4 justify-center ${titleTyped ? 'hero-rise' : 'opacity-0'}`}>
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
              {/* Quick entry for coach-designated athletes — named "Strefa zawodnika"
                  so it never clashes with the public "Kalendarz" button */}
              {isAthlete && (
                <Link to="/my-reservations?tab=calendar" className="relative">
                  <Button
                    variant="secondary"
                    size="lg"
                    className="w-full sm:w-auto btn-glow !border-amber-500/60 !text-amber-300 hover:!bg-amber-500/10"
                  >
                    <Dumbbell className="w-5 h-5 mr-2" />
                    {t("hero.athleteZone")}
                  </Button>
                  {athleteBadge > 0 && (
                    <span className="absolute -top-2 -right-2 min-w-5 h-5 px-1 flex items-center justify-center text-[11px] font-semibold text-white bg-rose-500 rounded-full">
                      {athleteBadge}
                    </span>
                  )}
                </Link>
              )}
            </div>
          </div>
        </div>
        <div className="hidden sm:block absolute bottom-6 right-6 z-20 bg-surface-900/60 backdrop-blur-sm rounded-full px-3 py-1.5 border border-surface-700/40">
          <ShareButtons title="Next Step Pro Climbing" url={window.location.origin} compact />
        </div>
      </section>

      {/* === WHERE I AM NOW — comment out <CurrentLocationSection /> to remove the whole section === */}
      <CurrentLocationSection />
      {/* === END WHERE I AM NOW === */}

      {/* Steps Section */}
      <section className="py-16 sm:py-24">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <h2 className="text-3xl font-bold text-surface-100 mb-12 text-center">
            {t("steps.title")}
          </h2>
          <div
            ref={stepsRef}
            className={`steps-grid grid md:grid-cols-3 gap-8 md:gap-0 ${stepsInView ? "is-visible" : ""}`}
          >
            {[
              { num: "01", icon: Search, key: "step1" },
              { num: "02", icon: CalendarCheck, key: "step2" },
              { num: "03", icon: UserPlus, key: "step3" },
            ].map((step, i) => (
              <div
                key={step.key}
                style={{ "--step-delay": `${i * 280}ms` } as CSSProperties}
                className={`step-card flex flex-col items-center text-center px-6 ${i < 2 ? "md:border-r md:border-surface-800" : ""}`}
              >
                <span className="step-num text-sm font-mono text-primary-400 mb-3">
                  {step.num}
                </span>
                <div className="step-icon w-12 h-12 bg-primary-500/10 rounded-lg flex items-center justify-center mb-4">
                  <step.icon className="w-6 h-6 text-primary-400" />
                </div>
                <h3 className="step-title text-lg font-semibold text-surface-100 mb-2">
                  {t(`steps.${step.key}.title`)}
                </h3>
                <p className="step-desc text-surface-400 text-sm">
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
                <p className="font-display text-4xl sm:text-5xl font-bold text-primary-400 mb-2">
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

      {/* Athlete Zone — personal training calendar pitch. CTA adapts to the viewer:
          athlete -> straight into their calendar, logged-in -> "coach unlocks it",
          guest -> create an account first */}
      <section className="py-16 sm:py-24">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="card-glass rounded-2xl border border-amber-500/25 p-8 sm:p-12 relative overflow-hidden">
            <Dumbbell
              aria-hidden="true"
              className="absolute -right-8 -bottom-8 w-48 h-48 text-amber-500/5 rotate-12 pointer-events-none"
            />
            <div className="text-center mb-10">
              <span className="inline-flex items-center gap-2 px-3 py-1 mb-4 text-xs font-semibold uppercase tracking-wider text-amber-300 bg-amber-500/10 border border-amber-500/30 rounded-full">
                <Dumbbell className="w-3.5 h-3.5" />
                {t("athleteZone.badge")}
              </span>
              <h2 className="text-3xl font-bold text-surface-100 mb-3">
                {t("athleteZone.title")}
              </h2>
              <p className="text-surface-400 max-w-2xl mx-auto">
                {t("athleteZone.subtitle")}
              </p>
            </div>

            <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-6 mb-10">
              {[
                { icon: Calendar, key: "feat1" },
                { icon: CheckCircle2, key: "feat2" },
                { icon: MessageSquare, key: "feat3" },
                { icon: Bell, key: "feat4" },
              ].map(({ icon: Icon, key }) => (
                <div key={key} className="text-center">
                  <div className="w-11 h-11 mx-auto bg-amber-500/10 rounded-lg flex items-center justify-center mb-3">
                    <Icon className="w-5 h-5 text-amber-400" />
                  </div>
                  <h3 className="text-sm font-semibold text-surface-100 mb-1">
                    {t(`athleteZone.${key}.title`)}
                  </h3>
                  <p className="text-xs text-surface-400">
                    {t(`athleteZone.${key}.description`)}
                  </p>
                </div>
              ))}
            </div>

            <div className="text-center">
              {isAthlete ? (
                <Link to="/my-reservations?tab=calendar">
                  <Button size="lg" className="btn-glow">
                    <Dumbbell className="w-5 h-5 mr-2" />
                    {t("athleteZone.ctaAthlete")}
                  </Button>
                </Link>
              ) : isAuthenticated ? (
                <>
                  <p className="text-sm text-surface-400 mb-4">{t("athleteZone.accessNote")}</p>
                  <Link to="/kontakt">
                    <Button variant="secondary" size="lg">
                      {t("athleteZone.ctaAskCoach")}
                    </Button>
                  </Link>
                </>
              ) : (
                <>
                  <p className="text-sm text-surface-400 mb-4">{t("athleteZone.accessNote")}</p>
                  <Link to="/register">
                    <Button size="lg" className="btn-glow">
                      {t("athleteZone.ctaGuest")}
                      <ArrowRight className="w-5 h-5 ml-2" />
                    </Button>
                  </Link>
                </>
              )}
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
