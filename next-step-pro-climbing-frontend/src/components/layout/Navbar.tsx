import { Link, useLocation, useNavigate } from "react-router-dom";
import {
  CalendarDays,
  ChevronDown,
  CircleHelp,
  GraduationCap,
  House,
  Images,
  LogOut,
  Mail,
  Menu,
  Moon,
  Newspaper,
  ShieldCheck,
  Star,
  Sun,
  Trophy,
  User,
  Users,
  Video,
  X,
} from "lucide-react";
import {
  useState,
  useRef,
  useEffect,
  useLayoutEffect,
  useCallback,
  type ComponentType,
} from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { adminApi, reservationApi, trainingCalendarApi } from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useTheme } from "../../context/ThemeContext";
import { Button } from "../ui/Button";
import { Avatar } from "../ui/Avatar";
import { SuccessCheckmark } from "../ui/SuccessCheckmark";
import { LanguageSwitcher } from "../ui/LanguageSwitcher";
import { MobileNavPanel, type MobileNavSection } from "./MobileNavPanel";
import clsx from "clsx";
import logoWhite from "../../assets/logo/logo-white.png";
import logoBlack from "../../assets/logo/logo-black.png";

/**
 * `icon` is required even though the desktop bar never draws one: the mobile drawer does, and a
 * link declared without it would only fail once someone opened the menu. Required here means the
 * compiler asks for the icon at the moment the tab is added.
 */
type NavLink = {
  to: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  badge?: number;
  premium?: boolean;
};

export function Navbar() {
  const { t } = useTranslation('common');
  const { user, isAuthenticated, isAdmin, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const location = useLocation();
  const navigate = useNavigate();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [mediaMenuOpen, setMediaMenuOpen] = useState(false);
  const [teamMenuOpen, setTeamMenuOpen] = useState(false);
  const [navHidden, setNavHidden] = useState(false);
  const [atTop, setAtTop] = useState(true);
  const [showLogoutSuccess, setShowLogoutSuccess] = useState(false);
  const lastScrollY = useRef(0);
  const userMenuRef = useRef<HTMLDivElement>(null);
  const mediaMenuRef = useRef<HTMLDivElement>(null);
  const teamMenuRef = useRef<HTMLDivElement>(null);

  const isLinkActive = (path: string) =>
    path === "/" ? location.pathname === "/" : location.pathname.startsWith(path);

  // Admin notifications (pending requests + new reservations) — a red dot on the Admin
  // link so it is visible right away that something is waiting. Same cache as the admin panel.
  const { data: adminNotifications } = useQuery({
    queryKey: ['admin', 'notifications'],
    queryFn: adminApi.getNotifications,
    enabled: isAdmin,
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
    // Server-side per-admin read markers: clearing on one device must reflect on
    // another, so this still refetches on focus — but not on EVERY focus. With
    // staleTime 0 each alt-tab fired all three badge queries at once, which is how an
    // ordinary session walked into the rate limiter; a badge 30 s behind is invisible.
    staleTime: 30_000,
    refetchOnWindowFocus: true,
  });
  const adminBadgeCount = isAdmin
    ? (adminNotifications?.pendingRequests ?? 0)
      + (adminNotifications?.newReservations ?? 0)
      + (adminNotifications?.newWaitlistEntries ?? 0)
      + (adminNotifications?.athleteActivity ?? 0)
    : 0;

  // The client's pending invitations (invitation-held seats) — a badge on the
  // My Reservations link. Lit until the user books the held seat. Same cache
  // as the "Invitations" section on MyReservationsPage.
  const { data: myInvitations } = useQuery({
    queryKey: ['invitations', 'my'],
    queryFn: reservationApi.getMyInvitations,
    enabled: isAuthenticated,
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
    // Same cross-device freshness as the admin badge, same 30 s floor.
    staleTime: 30_000,
    refetchOnWindowFocus: true,
  });
  const invitationBadgeCount = isAuthenticated ? (myInvitations?.length ?? 0) : 0;

  // Athlete's personal training calendar: new coach activity (trainings/comments) —
  // adds to the My Reservations badge. Cache shared with the tab badge on that page.
  const { data: trainingNotifications } = useQuery({
    queryKey: ['trainingCalendar', 'notifications'],
    queryFn: trainingCalendarApi.getNotifications,
    enabled: isAuthenticated && !!user?.isAthlete,
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
    // Same cross-device freshness as the admin badge, same 30 s floor.
    staleTime: 30_000,
    refetchOnWindowFocus: true,
  });
  const trainingBadgeCount = user?.isAthlete ? (trainingNotifications?.newCount ?? 0) : 0;

  const mediaLinks: NavLink[] = [
    { to: "/galeria", label: t('nav.gallery'), icon: Images },
    { to: "/filmy", label: t('nav.videos'), icon: Video },
  ];

  const teamLinks: NavLink[] = [
    { to: "/team/instruktorzy", label: t('team.instructors'), icon: Users },
    { to: "/team/zawodnicy", label: t('team.competitors'), icon: Trophy },
  ];

  // `premium` marks the one tab a guest never sees. It is painted as a gold pill
  // (.nav-premium) in the same amber as the Athlete Zone and the calendar promo,
  // so gold means the same thing in the bar as on the page: this is yours.
  const navLinksBefore: NavLink[] = [
    { to: "/", label: t('nav.home'), icon: House },
    { to: "/calendar", label: t('nav.calendar'), icon: CalendarDays },
    ...(isAuthenticated
      ? [{
          to: "/my-reservations",
          label: t('nav.myReservations'),
          icon: Star,
          badge: invitationBadgeCount + trainingBadgeCount,
          premium: true,
        }]
      : []),
    { to: "/aktualnosci", label: t('nav.news'), icon: Newspaper },
    { to: "/kursy", label: t('nav.courses'), icon: GraduationCap },
  ];

  const navLinksAfter: NavLink[] = [
    { to: "/kontakt", label: t('nav.contact'), icon: Mail },
    { to: "/faq", label: t('nav.help'), icon: CircleHelp },
    ...(isAdmin
      ? [{ to: "/admin", label: t('nav.admin'), icon: ShieldCheck, badge: adminBadgeCount }]
      : []),
  ];

  // The drawer groups what the desktop bar flattens: twelve links in one column read as a dump,
  // and the two dropdowns (Team, Media) already say where the split belongs.
  const mobileSections: MobileNavSection[] = [
    { key: 'main', title: t('nav.sections.main'), items: navLinksBefore },
    { key: 'team', title: t('nav.team'), items: teamLinks },
    { key: 'media', title: t('nav.media'), items: mediaLinks },
    { key: 'more', title: t('nav.sections.more'), items: navLinksAfter },
  ];

  const isMediaActive = mediaLinks.some((l) => isLinkActive(l.to));
  const isTeamActive = teamLinks.some((l) => isLinkActive(l.to));

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (
        userMenuRef.current &&
        !userMenuRef.current.contains(e.target as Node)
      ) {
        setUserMenuOpen(false);
      }
      if (
        mediaMenuRef.current &&
        !mediaMenuRef.current.contains(e.target as Node)
      ) {
        setMediaMenuOpen(false);
      }
      if (
        teamMenuRef.current &&
        !teamMenuRef.current.contains(e.target as Node)
      ) {
        setTeamMenuOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // Mobile Safari/Chrome render this `position: sticky` navbar overlapping the
  // page content on the very first paint (before any scroll), tucking the top
  // of the hero — incl. the admin badge — under the bar until you scroll. A 1px
  // scroll nudge across two frames forces the browser to recompute the sticky
  // offset so the hero is fully visible on load. Runs once, only when at top.
  useEffect(() => {
    if (window.scrollY !== 0) return;
    let raf2 = 0;
    const raf1 = requestAnimationFrame(() => {
      window.scrollTo(0, 1);
      raf2 = requestAnimationFrame(() => window.scrollTo(0, 0));
    });
    return () => {
      cancelAnimationFrame(raf1);
      cancelAnimationFrame(raf2);
    };
  }, []);

  useEffect(() => {
    function onScroll() {
      const y = window.scrollY;
      setAtTop(y < 60);
      if (y < 60) {
        setNavHidden(false);
      } else if (y > lastScrollY.current + 5) {
        setNavHidden(true);
      } else if (y < lastScrollY.current - 5) {
        setNavHidden(false);
      }
      lastScrollY.current = y;
    }
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  // Close dropdowns on route change
  const [prevPathname, setPrevPathname] = useState(location.pathname);
  if (prevPathname !== location.pathname) {
    setPrevPathname(location.pathname);
    setUserMenuOpen(false);
    setMediaMenuOpen(false);
    setTeamMenuOpen(false);
    // Also the drawer: its links close it themselves, but the browser's back button is a route
    // change nobody clicked, and it would otherwise leave the sheet open over the new page.
    setMobileMenuOpen(false);
  }


  const navContainerRef = useRef<HTMLDivElement>(null);
  const [indicator, setIndicator] = useState({ left: 0, width: 0 });
  const [hasIndicator, setHasIndicator] = useState(false);
  // The underline takes the active tab's colour. Slate under the gold pill read as
  // a stray line belonging to the tab next door, so it is read off the same element
  // the indicator is already measuring — no second lookup, no extra render.
  const [indicatorPremium, setIndicatorPremium] = useState(false);

  const updateIndicator = useCallback(() => {
    const container = navContainerRef.current;
    if (!container) return;
    const active = container.querySelector('[data-nav-active="true"]') as HTMLElement | null;
    if (active) {
      const containerRect = container.getBoundingClientRect();
      const activeRect = active.getBoundingClientRect();
      setIndicator({
        left: activeRect.left - containerRect.left,
        width: activeRect.width,
      });
      setIndicatorPremium(active.dataset.navPremium === "true");
      setHasIndicator(true);
    } else {
      setHasIndicator(false);
    }
  }, []);

  useLayoutEffect(() => {
    updateIndicator();
  }, [location.pathname, updateIndicator]);

  // At the top of the homepage the navbar "sits" on the hero image as frosted glass,
  // so the photo shows through behind the logo/hamburger. After scrolling, with the menu
  // open, and on desktop (md:) it returns to the normal dark background.
  const heroOverlay = location.pathname === "/" && atTop && !mobileMenuOpen;

  const renderBadge = (link: NavLink) =>
    (link.badge ?? 0) > 0 ? (
      <span className="ml-1.5 min-w-5 h-5 px-1.5 inline-flex items-center justify-center rounded-full bg-rose-500 text-white text-[11px] font-bold leading-none align-middle">
        {link.badge}
      </span>
    ) : null;

  // The shine lives on a span around the label, never on the <a>: the gradient is
  // clipped to text via -webkit-text-fill-color: transparent, which INHERITS —
  // put it on the link and the red badge inside turns invisible too.
  const renderLabel = (link: NavLink) =>
    link.premium ? (
      <span className={clsx("nav-shine", isLinkActive(link.to) && "nav-shine--active")}>
        {link.label}
      </span>
    ) : (
      link.label
    );

  // One renderer for both desktop groups — the tab would otherwise live in two
  // copies, and the twin that gets forgotten is the one that drifts.
  const renderDesktopLink = (link: NavLink) => {
    const active = isLinkActive(link.to);
    return (
      <Link
        key={link.to}
        to={link.to}
        data-nav-active={active || undefined}
        data-nav-premium={link.premium || undefined}
        className={clsx(
          // ⚠️ `xl:whitespace-nowrap`, nie `whitespace-nowrap`: zakaz łamania jest bezpieczny
          // dopiero tam, gdzie rząd ma się w co rozłożyć. Poniżej xl pełny pasek (10 zakładek
          // + logo + prawy klaster) i tak nie mieści się w oknie — bezwarunkowy nowrap zamieniłby
          // wtedy zawijanie na WYJEŻDŻANIE zakładek pod prawy klaster, czyli nakładanie się
          // elementów zamiast brzydkiego, ale czytelnego łamania.
          "px-2.5 xl:px-3 xl:whitespace-nowrap py-1.5 rounded-lg text-base font-semibold tracking-wide transition-all duration-150 active:scale-95",
          link.premium
            ? "hover:bg-surface-800/60"
            : active
              ? "text-surface-100"
              : "text-surface-400 hover:bg-surface-800/60 hover:text-surface-200",
        )}
      >
        {renderLabel(link)}
        {renderBadge(link)}
      </Link>
    );
  };

  return (
    <>
    {showLogoutSuccess && <SuccessCheckmark onDone={() => { setShowLogoutSuccess(false); logout(); }} />}
    <nav className={clsx(
      "sticky top-0 z-50 transition-[transform,background-color,border-color] duration-300",
      navHidden && !mobileMenuOpen && "-translate-y-full",
      heroOverlay
        ? "bg-transparent border-b border-transparent"
        : "bg-surface-900/80 backdrop-blur-sm border-b border-surface-800",
    )}>
      {/* Szerszy niż max-w-7xl treści stron i to jest świadome: pasek ma dziś do 10 zakładek,
          a zalogowanemu dochodzi jeszcze „Twoja strefa" z plakietką. Przy 80rem etykieta nie
          mieściła się w swoim kaflu i ŁAMAŁA SIĘ NA DWIE LINIE (a za nią „Zaloguj się"),
          co rozpychało pasek w pionie. Zmierzone: zalogowany admin potrzebuje ~1236 px na
          zawartość, 80rem z paddingami dawało ~1166 px. */}
      <div className={clsx("max-w-[88rem] mx-auto px-4 sm:px-6 lg:px-8", heroOverlay && "py-2")}>
        {/* In glass mode (top of home) the navbar becomes a floating pill with rounded
            ends, offset from the edges. In normal mode it is a full bar (h-18). */}
        <div className={clsx(
          "flex items-center justify-between",
          heroOverlay
            ? "h-14 mx-6 sm:mx-0 rounded-full px-5 sm:px-6 border border-white/12 bg-surface-950/40 backdrop-blur-[2px] md:bg-surface-950/45"
            : "h-18",
        )}>
          {/* Logo */}
          <Link
            to="/"
            className="flex items-center shrink-0 hover:opacity-80 transition-opacity"
          >
            <img
              src={theme === 'dark' ? logoWhite : logoBlack}
              alt="Next Step Pro Climbing"
              className="h-10 w-auto cursor-pointer"
            />
          </Link>

          {/* Desktop Navigation */}
          <div ref={navContainerRef} className="hidden md:flex items-center gap-1 relative">
            {hasIndicator && (
              <div
                className={clsx(
                  "absolute bottom-0 h-0.5 rounded-full transition-all duration-300 ease-out",
                  indicatorPremium ? "bg-amber-400" : "bg-primary-400",
                )}
                style={{ left: indicator.left, width: indicator.width }}
              />
            )}
            {navLinksBefore.map(renderDesktopLink)}

            {/* Team dropdown */}
            <div className="relative" ref={teamMenuRef}>
              <button
                onClick={() => setTeamMenuOpen(!teamMenuOpen)}
                data-nav-active={isTeamActive || undefined}
                className={clsx(
                  "flex items-center gap-1 px-2.5 xl:px-3 xl:whitespace-nowrap py-1.5 rounded-lg text-base font-semibold tracking-wide transition-all duration-150 active:scale-95",
                  isTeamActive
                    ? "text-surface-100"
                    : "text-surface-400 hover:bg-surface-800/60 hover:text-surface-200",
                )}
              >
                {t('nav.team')}
                <ChevronDown
                  className={clsx(
                    "w-3.5 h-3.5 transition-transform",
                    teamMenuOpen && "rotate-180",
                  )}
                />
              </button>

              {teamMenuOpen && (
                <div className="absolute left-0 mt-2 w-40 bg-surface-900 border border-surface-700 rounded-xl shadow-lg shadow-black/30 overflow-hidden">
                  <div className="py-1">
                    {teamLinks.map((link) => (
                      <Link
                        key={link.to}
                        to={link.to}
                        onClick={() => setTeamMenuOpen(false)}
                        className={clsx(
                          "block px-4 py-2.5 text-sm transition-all duration-150 active:scale-95",
                          isLinkActive(link.to)
                            ? "text-surface-100 bg-surface-800"
                            : "text-surface-300 hover:bg-surface-800 hover:text-surface-100",
                        )}
                      >
                        {link.label}
                      </Link>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Media dropdown */}
            <div className="relative" ref={mediaMenuRef}>
              <button
                onClick={() => setMediaMenuOpen(!mediaMenuOpen)}
                data-nav-active={isMediaActive || undefined}
                className={clsx(
                  "flex items-center gap-1 px-2.5 xl:px-3 xl:whitespace-nowrap py-1.5 rounded-lg text-base font-semibold tracking-wide transition-all duration-150 active:scale-95",
                  isMediaActive
                    ? "text-surface-100"
                    : "text-surface-400 hover:bg-surface-800/60 hover:text-surface-200",
                )}
              >
                {t('nav.media')}
                <ChevronDown
                  className={clsx(
                    "w-3.5 h-3.5 transition-transform",
                    mediaMenuOpen && "rotate-180",
                  )}
                />
              </button>

              {mediaMenuOpen && (
                <div className="absolute left-0 mt-2 w-40 bg-surface-900 border border-surface-700 rounded-xl shadow-lg shadow-black/30 overflow-hidden">
                  <div className="py-1">
                    {mediaLinks.map((link) => (
                      <Link
                        key={link.to}
                        to={link.to}
                        onClick={() => setMediaMenuOpen(false)}
                        className={clsx(
                          "block px-4 py-2.5 text-sm transition-all duration-150 active:scale-95",
                          isLinkActive(link.to)
                            ? "text-surface-100 bg-surface-800"
                            : "text-surface-300 hover:bg-surface-800 hover:text-surface-100",
                        )}
                      >
                        {link.label}
                      </Link>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {navLinksAfter.map(renderDesktopLink)}
          </div>

          {/* User Actions */}
          <div className="hidden md:flex items-center gap-2">
            <button
              onClick={toggleTheme}
              className="p-2 rounded-lg text-surface-300 hover:text-surface-100 hover:bg-surface-800 transition-all duration-150 active:scale-95"
              aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
            >
              {theme === 'dark' ? <Moon className="w-4.5 h-4.5" /> : <Sun className="w-4.5 h-4.5" />}
            </button>
            <LanguageSwitcher />
            {isAuthenticated ? (
              <div className="relative" ref={userMenuRef}>
                <button
                  onClick={() => setUserMenuOpen(!userMenuOpen)}
                  className="flex items-center gap-2 px-2 py-2 rounded-lg hover:bg-surface-800 transition-colors"
                >
                  <Avatar src={user?.avatarUrl} name={user?.firstName} className="w-9 h-9" />
                  <ChevronDown
                    className={clsx(
                      "w-4 h-4 text-surface-400 transition-transform",
                      userMenuOpen && "rotate-180",
                    )}
                  />
                </button>

                {userMenuOpen && (
                  <div className="absolute right-0 mt-2 w-56 bg-surface-900 border border-surface-700 rounded-xl shadow-lg shadow-black/30 overflow-hidden">
                    <div className="px-4 py-3 border-b border-surface-800">
                      <p className="text-sm font-medium text-surface-100">
                        {user?.firstName} {user?.lastName}
                      </p>
                      <p className="text-xs text-surface-500 mt-0.5">
                        {user?.email}
                      </p>
                    </div>
                    <div className="py-1">
                      <button
                        onClick={() => navigate("/settings")}
                        className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-surface-300 hover:bg-surface-800 hover:text-surface-100 transition-colors"
                      >
                        <User className="w-4 h-4" />
                        {t('nav.profile')}
                      </button>
                      <button
                        onClick={() => {
                          setUserMenuOpen(false);
                          setShowLogoutSuccess(true);
                        }}
                        className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-rose-400/70 hover:bg-surface-800 hover:text-rose-300/80 transition-colors"
                      >
                        <LogOut className="w-4 h-4" />
                        {t('nav.logout')}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <Link to="/login">
                  {/* Bezwarunkowy nowrap, w odróżnieniu od zakładek: „Zaloguj się" łamało się
                      na dwie linie razem z nimi, a to jest przycisk-CTA o stałej treści —
                      niech raczej ścisną się zakładki obok, które i tak są tylko etykietami. */}
                  <Button size="sm" className="px-4 whitespace-nowrap">
                    {t('nav.login')}
                  </Button>
                </Link>
              </div>
            )}
          </div>

          {/* Mobile menu button (dot = admin notifications hidden in the closed menu) */}
          <button
            className="md:hidden relative text-surface-300"
            aria-label={mobileMenuOpen ? t('nav.closeMenu') : t('nav.openMenu')}
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          >
            {mobileMenuOpen ? (
              <X className="w-6 h-6" />
            ) : (
              <Menu className="w-6 h-6" />
            )}
            {!mobileMenuOpen && (adminBadgeCount + invitationBadgeCount + trainingBadgeCount) > 0 && (
              <span className="absolute -top-1 -right-1 w-2.5 h-2.5 rounded-full bg-rose-500" />
            )}
          </button>
        </div>
      </div>

    </nav>

    <MobileNavPanel
      open={mobileMenuOpen}
      onClose={() => setMobileMenuOpen(false)}
      sections={mobileSections}
      isActive={isLinkActive}
      onLogout={() => setShowLogoutSuccess(true)}
    />
    </>
  );
}
