import { Link, useLocation, useNavigate } from "react-router-dom";
import { ChevronDown, LogOut, Menu, User, X } from "lucide-react";
import { useState, useRef, useEffect, useLayoutEffect, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useAuth } from "../../context/AuthContext";
import { Button } from "../ui/Button";
import { SuccessCheckmark } from "../ui/SuccessCheckmark";
import { LanguageSwitcher } from "../ui/LanguageSwitcher";
import clsx from "clsx";
import logoWhite from "../../assets/logo/logo-white.png";

export function Navbar() {
  const { t } = useTranslation('common');
  const { user, isAuthenticated, isAdmin, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [mediaMenuOpen, setMediaMenuOpen] = useState(false);
  const [teamMenuOpen, setTeamMenuOpen] = useState(false);
  const [navHidden, setNavHidden] = useState(false);
  const [showLogoutSuccess, setShowLogoutSuccess] = useState(false);
  const lastScrollY = useRef(0);
  const userMenuRef = useRef<HTMLDivElement>(null);
  const mediaMenuRef = useRef<HTMLDivElement>(null);
  const teamMenuRef = useRef<HTMLDivElement>(null);

  const isLinkActive = (path: string) =>
    path === "/" ? location.pathname === "/" : location.pathname.startsWith(path);

  const mediaLinks = [
    { to: "/galeria", label: t('nav.gallery') },
    { to: "/filmy", label: t('nav.videos') },
  ];

  const teamLinks = [
    { to: "/team/instruktorzy", label: t('team.instructors') },
    { to: "/team/zawodnicy", label: t('team.competitors') },
  ];

  const navLinksBefore = [
    { to: "/", label: t('nav.home') },
    { to: "/calendar", label: t('nav.calendar') },
    ...(isAuthenticated
      ? [{ to: "/my-reservations", label: t('nav.myReservations') }]
      : []),
    { to: "/aktualnosci", label: t('nav.news') },
    { to: "/kursy", label: t('nav.courses') },
  ];

  const navLinksAfter = [
    { to: "/kontakt", label: t('nav.contact') },
    { to: "/faq", label: t('nav.help') },
    ...(isAdmin ? [{ to: "/admin", label: t('nav.admin') }] : []),
  ];

  const mobileNavLinks = [...navLinksBefore, ...teamLinks, ...mediaLinks, ...navLinksAfter];

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

  useEffect(() => {
    function onScroll() {
      const y = window.scrollY;
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

  const userInitial = user?.firstName?.charAt(0).toUpperCase() ?? "?";

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

  return (
    <>
    {showLogoutSuccess && <SuccessCheckmark onDone={() => { setShowLogoutSuccess(false); logout(); }} />}
    <nav className={clsx("bg-dark-900/80 backdrop-blur-sm border-b border-dark-800 sticky top-0 z-50 transition-transform duration-300", navHidden && !mobileMenuOpen && "-translate-y-full")}>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-18">
          {/* Logo */}
          <Link
            to="/"
            className="flex items-center hover:opacity-80 transition-opacity"
          >
            <img
              src={logoWhite}
              alt="Next Step Pro Climbing"
              className="h-10 cursor-pointer"
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
                    ? "text-dark-100"
                    : "text-dark-400 hover:bg-dark-800/60 hover:text-dark-200",
                )}
              >
                {link.label}
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
                    ? "text-dark-100"
                    : "text-dark-400 hover:bg-dark-800/60 hover:text-dark-200",
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
                <div className="absolute left-0 mt-2 w-40 bg-dark-900 border border-dark-700 rounded-xl shadow-lg shadow-black/30 overflow-hidden">
                  <div className="py-1">
                    {teamLinks.map((link) => (
                      <Link
                        key={link.to}
                        to={link.to}
                        onClick={() => setTeamMenuOpen(false)}
                        className={clsx(
                          "block px-4 py-2.5 text-sm transition-all duration-150 active:scale-95",
                          isLinkActive(link.to)
                            ? "text-dark-100 bg-dark-800"
                            : "text-dark-300 hover:bg-dark-800 hover:text-dark-100",
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
                    ? "text-dark-100"
                    : "text-dark-400 hover:bg-dark-800/60 hover:text-dark-200",
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
                <div className="absolute left-0 mt-2 w-40 bg-dark-900 border border-dark-700 rounded-xl shadow-lg shadow-black/30 overflow-hidden">
                  <div className="py-1">
                    {mediaLinks.map((link) => (
                      <Link
                        key={link.to}
                        to={link.to}
                        onClick={() => setMediaMenuOpen(false)}
                        className={clsx(
                          "block px-4 py-2.5 text-sm transition-all duration-150 active:scale-95",
                          isLinkActive(link.to)
                            ? "text-dark-100 bg-dark-800"
                            : "text-dark-300 hover:bg-dark-800 hover:text-dark-100",
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
                    ? "text-dark-100"
                    : "text-dark-400 hover:bg-dark-800/60 hover:text-dark-200",
                )}
              >
                {link.label}
              </Link>
            ))}
          </div>

          {/* User Actions */}
          <div className="hidden md:flex items-center gap-2">
            <LanguageSwitcher />
            {isAuthenticated ? (
              <div className="relative" ref={userMenuRef}>
                <button
                  onClick={() => setUserMenuOpen(!userMenuOpen)}
                  className="flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-dark-800 transition-colors"
                >
                  <div className="w-9 h-9 rounded-full bg-primary-600 flex items-center justify-center">
                    <span className="text-sm font-bold text-white">
                      {userInitial}
                    </span>
                  </div>
                  <span className="text-sm font-medium text-dark-200">
                    {user?.firstName}
                  </span>
                  <ChevronDown
                    className={clsx(
                      "w-4 h-4 text-dark-400 transition-transform",
                      userMenuOpen && "rotate-180",
                    )}
                  />
                </button>

                {userMenuOpen && (
                  <div className="absolute right-0 mt-2 w-56 bg-dark-900 border border-dark-700 rounded-xl shadow-lg shadow-black/30 overflow-hidden">
                    <div className="px-4 py-3 border-b border-dark-800">
                      <p className="text-sm font-medium text-dark-100">
                        {user?.firstName} {user?.lastName}
                      </p>
                      <p className="text-xs text-dark-500 mt-0.5">
                        {user?.email}
                      </p>
                    </div>
                    <div className="py-1">
                      <button
                        onClick={() => navigate("/settings")}
                        className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-dark-300 hover:bg-dark-800 hover:text-dark-100 transition-colors"
                      >
                        <User className="w-4 h-4" />
                        {t('nav.profile')}
                      </button>
                      <button
                        onClick={() => {
                          setUserMenuOpen(false);
                          setShowLogoutSuccess(true);
                        }}
                        className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-rose-400/70 hover:bg-dark-800 hover:text-rose-300/80 transition-colors"
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

          {/* Mobile menu button */}
          <button
            className="md:hidden text-dark-300"
            aria-label={mobileMenuOpen ? t('nav.closeMenu') : t('nav.openMenu')}
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          >
            {mobileMenuOpen ? (
              <X className="w-6 h-6" />
            ) : (
              <Menu className="w-6 h-6" />
            )}
          </button>
        </div>
      </div>

      {/* Mobile menu */}
      {mobileMenuOpen && (
        <div className="md:hidden bg-dark-900 border-t border-dark-800 max-h-[calc(100dvh-4.5rem)] overflow-y-auto">
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
                    : "text-dark-300",
                )}
              >
                {link.label}
              </Link>
            ))}
            <div className="pt-4 border-t border-dark-800">
              <div className="mb-3">
                <LanguageSwitcher />
              </div>
              {isAuthenticated ? (
                <div className="space-y-1">
                  <div className="flex items-center gap-3 px-1 py-2">
                    <div className="w-9 h-9 rounded-full bg-primary-600 flex items-center justify-center">
                      <span className="text-sm font-bold text-white">
                        {userInitial}
                      </span>
                    </div>
                    <div>
                      <p className="text-sm font-medium text-dark-200">
                        {user?.firstName} {user?.lastName}
                      </p>
                      <p className="text-xs text-dark-500">{user?.email}</p>
                    </div>
                  </div>
                  <Link
                    to="/settings"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center gap-3 px-1 py-2 text-dark-300 text-sm"
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
