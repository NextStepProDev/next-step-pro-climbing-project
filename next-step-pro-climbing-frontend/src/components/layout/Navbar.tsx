import { Link, useLocation, useNavigate } from "react-router-dom";
import { ChevronDown, LogOut, Menu, Moon, Sun, User, X } from "lucide-react";
import { useState, useRef, useEffect, useLayoutEffect, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { adminApi, reservationApi } from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useTheme } from "../../context/ThemeContext";
import { Button } from "../ui/Button";
import { Avatar } from "../ui/Avatar";
import { SuccessCheckmark } from "../ui/SuccessCheckmark";
import { LanguageSwitcher } from "../ui/LanguageSwitcher";
import clsx from "clsx";
import logoWhite from "../../assets/logo/logo-white.png";
import logoBlack from "../../assets/logo/logo-black.png";

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

  // Powiadomienia admina (oczekujące propozycje + nowe rezerwacje) — czerwona kropka na
  // linku Admin, żeby było widać od progu, że coś czeka. Ten sam cache co panel admina.
  const { data: adminNotifications } = useQuery({
    queryKey: ['admin', 'notifications'],
    queryFn: adminApi.getNotifications,
    enabled: isAdmin,
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
  });
  const adminBadgeCount = isAdmin
    ? (adminNotifications?.pendingRequests ?? 0)
      + (adminNotifications?.newReservations ?? 0)
      + (adminNotifications?.newWaitlistEntries ?? 0)
    : 0;

  // Wiszące zaproszenia klienta (miejsca trzymane "na zaproszenie") — badge na linku
  // Moje rezerwacje. Świeci dopóki user nie zarezerwuje trzymanego miejsca. Ten sam
  // cache co sekcja "Zaproszenia" na MyReservationsPage.
  const { data: myInvitations } = useQuery({
    queryKey: ['invitations', 'my'],
    queryFn: reservationApi.getMyInvitations,
    enabled: isAuthenticated,
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
  });
  const invitationBadgeCount = isAuthenticated ? (myInvitations?.length ?? 0) : 0;

  const mediaLinks = [
    { to: "/galeria", label: t('nav.gallery') },
    { to: "/filmy", label: t('nav.videos') },
  ];

  const teamLinks = [
    { to: "/team/instruktorzy", label: t('team.instructors') },
    { to: "/team/zawodnicy", label: t('team.competitors') },
  ];

  const navLinksBefore: { to: string; label: string; badge?: number }[] = [
    { to: "/", label: t('nav.home') },
    { to: "/calendar", label: t('nav.calendar') },
    ...(isAuthenticated
      ? [{ to: "/my-reservations", label: t('nav.myReservations'), badge: invitationBadgeCount }]
      : []),
    { to: "/aktualnosci", label: t('nav.news') },
    { to: "/kursy", label: t('nav.courses') },
  ];

  const navLinksAfter: { to: string; label: string; badge?: number }[] = [
    { to: "/kontakt", label: t('nav.contact') },
    { to: "/faq", label: t('nav.help') },
    ...(isAdmin ? [{ to: "/admin", label: t('nav.admin'), badge: adminBadgeCount }] : []),
  ];

  const mobileNavLinks: { to: string; label: string; badge?: number }[] =
    [...navLinksBefore, ...teamLinks, ...mediaLinks, ...navLinksAfter];

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
  }


  const navContainerRef = useRef<HTMLDivElement>(null);
  const [indicator, setIndicator] = useState({ left: 0, width: 0 });
  const [hasIndicator, setHasIndicator] = useState(false);

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
      setHasIndicator(true);
    } else {
      setHasIndicator(false);
    }
  }, []);

  useLayoutEffect(() => {
    updateIndicator();
  }, [location.pathname, updateIndicator]);

  // Na stronie głównej u góry navbar „leży" na zdjęciu hero jako szkło (frosted),
  // żeby było widać zdjęcie za logo/hamburgerem. Po zescrollowaniu, przy otwartym
  // menu oraz na desktopie (md:) wraca do normalnego ciemnego tła.
  const heroOverlay = location.pathname === "/" && atTop && !mobileMenuOpen;

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
      <div className={clsx("max-w-7xl mx-auto px-4 sm:px-6 lg:px-8", heroOverlay && "py-2")}>
        {/* W trybie glass (home u góry) navbar staje się pływającą kapsułą (pill) z owalnymi
            końcami, odsuniętą od krawędzi. W zwykłym trybie to pełny pasek (h-18). */}
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
                className="absolute bottom-0 h-0.5 bg-primary-400 rounded-full transition-all duration-300 ease-out"
                style={{ left: indicator.left, width: indicator.width }}
              />
            )}
            {navLinksBefore.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                data-nav-active={isLinkActive(link.to) || undefined}
                className={clsx(
                  "px-3 py-1.5 rounded-lg text-base font-semibold tracking-wide transition-all duration-150 active:scale-95",
                  isLinkActive(link.to)
                    ? "text-surface-100"
                    : "text-surface-400 hover:bg-surface-800/60 hover:text-surface-200",
                )}
              >
                {link.label}
                {(link.badge ?? 0) > 0 && (
                  <span className="ml-1.5 min-w-5 h-5 px-1.5 inline-flex items-center justify-center rounded-full bg-rose-500 text-white text-[11px] font-bold leading-none align-middle">
                    {link.badge}
                  </span>
                )}
              </Link>
            ))}

            {/* Team dropdown */}
            <div className="relative" ref={teamMenuRef}>
              <button
                onClick={() => setTeamMenuOpen(!teamMenuOpen)}
                data-nav-active={isTeamActive || undefined}
                className={clsx(
                  "flex items-center gap-1 px-3 py-1.5 rounded-lg text-base font-semibold tracking-wide transition-all duration-150 active:scale-95",
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
                  "flex items-center gap-1 px-3 py-1.5 rounded-lg text-base font-semibold tracking-wide transition-all duration-150 active:scale-95",
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

            {navLinksAfter.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                data-nav-active={isLinkActive(link.to) || undefined}
                className={clsx(
                  "px-3 py-1.5 rounded-lg text-base font-semibold tracking-wide transition-all duration-150 active:scale-95",
                  isLinkActive(link.to)
                    ? "text-surface-100"
                    : "text-surface-400 hover:bg-surface-800/60 hover:text-surface-200",
                )}
              >
                {link.label}
                {(link.badge ?? 0) > 0 && (
                  <span className="ml-1.5 min-w-5 h-5 px-1.5 inline-flex items-center justify-center rounded-full bg-rose-500 text-white text-[11px] font-bold leading-none align-middle">
                    {link.badge}
                  </span>
                )}
              </Link>
            ))}
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
                  <Button size="sm" className="px-4">
                    {t('nav.login')}
                  </Button>
                </Link>
              </div>
            )}
          </div>

          {/* Mobile menu button (kropka = powiadomienia admina ukryte w zamkniętym menu) */}
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
            {!mobileMenuOpen && (adminBadgeCount + invitationBadgeCount) > 0 && (
              <span className="absolute -top-1 -right-1 w-2.5 h-2.5 rounded-full bg-rose-500" />
            )}
          </button>
        </div>
      </div>

      {/* Mobile menu */}
      {mobileMenuOpen && (
        <div className="md:hidden bg-surface-900 border-t border-surface-800 max-h-[calc(100dvh-4.5rem)] overflow-y-auto">
          <div className="px-4 py-4 space-y-3">
            {mobileNavLinks.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                onClick={() => setMobileMenuOpen(false)}
                className={clsx(
                  "block py-2 text-base font-semibold tracking-wide",
                  isLinkActive(link.to)
                    ? "text-primary-400"
                    : "text-surface-300",
                )}
              >
                {link.label}
                {(link.badge ?? 0) > 0 && (
                  <span className="ml-2 min-w-5 h-5 px-1.5 inline-flex items-center justify-center rounded-full bg-rose-500 text-white text-[11px] font-bold leading-none align-middle">
                    {link.badge}
                  </span>
                )}
              </Link>
            ))}
            <div className="pt-4 border-t border-surface-800">
              <div className="mb-3 flex items-center gap-2">
                <button
                  onClick={toggleTheme}
                  className="p-2 rounded-lg text-surface-300 hover:text-surface-100 hover:bg-surface-800 transition-all duration-150 active:scale-95"
                  aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
                >
                  {theme === 'dark' ? <Moon className="w-4.5 h-4.5" /> : <Sun className="w-4.5 h-4.5" />}
                </button>
                <LanguageSwitcher />
              </div>
              {isAuthenticated ? (
                <div className="space-y-1">
                  <div className="flex items-center gap-3 px-1 py-2">
                    <Avatar src={user?.avatarUrl} name={user?.firstName} className="w-9 h-9" />
                    <div>
                      <p className="text-sm font-medium text-surface-200">
                        {user?.firstName} {user?.lastName}
                      </p>
                      <p className="text-xs text-surface-500">{user?.email}</p>
                    </div>
                  </div>
                  <Link
                    to="/settings"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center gap-3 px-1 py-2 text-surface-300 text-sm"
                  >
                    <User className="w-4 h-4" />
                    {t('nav.profile')}
                  </Link>
                  <button
                    onClick={() => {
                      setMobileMenuOpen(false);
                      setShowLogoutSuccess(true);
                    }}
                    className="flex items-center gap-3 px-1 py-2 text-rose-400/70 text-sm"
                  >
                    <LogOut className="w-4 h-4" />
                    {t('nav.logout')}
                  </button>
                </div>
              ) : (
                <Link to="/login" onClick={() => setMobileMenuOpen(false)}>
                  <Button variant="primary" size="sm" className="w-full">
                    {t('nav.login')}
                  </Button>
                </Link>
              )}
            </div>
          </div>
        </div>
      )}
    </nav>
    </>
  );
}
