import { useEffect, useState, type ComponentType } from "react";
import { createPortal } from "react-dom";
import { Link } from "react-router-dom";
import { LogOut, Moon, Settings, Sun, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import clsx from "clsx";
import { useAuth } from "../../context/AuthContext";
import { useTheme } from "../../context/ThemeContext";
import { useFocusTrap } from "../../utils/useFocusTrap";
import { Avatar } from "../ui/Avatar";
import { Button } from "../ui/Button";
import { LanguageSwitcher } from "../ui/LanguageSwitcher";

type LucideIcon = ComponentType<{ className?: string }>;

export type MobileNavItem = {
  to: string;
  label: string;
  icon: LucideIcon;
  badge?: number;
  premium?: boolean;
};

export type MobileNavSection = {
  /** Stable key — the visible title is translated and repeats across sections in some languages. */
  key: string;
  title: string;
  items: MobileNavItem[];
};

interface MobileNavPanelProps {
  open: boolean;
  onClose: () => void;
  sections: MobileNavSection[];
  isActive: (path: string) => boolean;
  /** Logout runs through the navbar's success animation, so the panel only reports the intent. */
  onLogout: () => void;
}

/** Keep in sync with `duration-300` on the sheet — the panel stays mounted this long while sliding out. */
const EXIT_MS = 300;

/**
 * The mobile navigation drawer: a sheet sliding in from the right over a dimmed, blurred page.
 *
 * Portalled to `document.body` rather than left inside the `<nav>` on purpose. The navbar hides
 * itself on scroll with `-translate-y-full`, and a transformed ancestor becomes the containing
 * block for `position: fixed` — a sheet nested inside would ride along with that transform during
 * the hide/show transition instead of standing still over the page.
 */
export function MobileNavPanel({ open, onClose, sections, isActive, onLogout }: MobileNavPanelProps) {
  const { t } = useTranslation("common");
  const { user, isAuthenticated } = useAuth();
  const { theme, toggleTheme } = useTheme();

  // `mounted` keeps the sheet in the DOM through the closing animation; `shown` drives the
  // transform. They are separate because a node that mounts already in its final position never
  // transitions — the browser needs to paint the off-screen state first.
  const [mounted, setMounted] = useState(open);
  const [shown, setShown] = useState(false);

  // Render-phase adjustment rather than an effect: mounting has to happen in the same render that
  // sees `open` flip, otherwise the frame the animation is timed against paints without the sheet.
  const [prevOpen, setPrevOpen] = useState(open);
  if (open !== prevOpen) {
    setPrevOpen(open);
    if (open) setMounted(true);
    else setShown(false);
  }

  useEffect(() => {
    if (open) {
      // Two frames, not one: a single rAF still runs before the first paint of the newly
      // mounted node in some engines, and the sheet then jumps into place without sliding.
      let inner = 0;
      const outer = requestAnimationFrame(() => {
        inner = requestAnimationFrame(() => setShown(true));
      });
      return () => {
        cancelAnimationFrame(outer);
        cancelAnimationFrame(inner);
      };
    }
    // ⚠️ Only while something is actually on screen. A timer armed in the closed steady state
    // fires `setMounted(false)` into a state that is already false — React does not always bail
    // out of that, and the queued low-priority update then lands AFTER the discrete click that
    // opened the sheet, unmounting it a frame after it appeared. The symptom is a drawer that
    // never shows up at all, with no error anywhere.
    if (!mounted) return;
    const timer = window.setTimeout(() => setMounted(false), EXIT_MS);
    return () => window.clearTimeout(timer);
  }, [open, mounted]);

  // The trap needs the ref attached, which only happens once `mounted` flips — passing the raw
  // `open` prop would run the effect one render too early, on a null ref, and never focus anything.
  const trapRef = useFocusTrap(open && mounted);

  useEffect(() => {
    if (!mounted) return;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = "";
    };
  }, [mounted]);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open, onClose]);

  if (!mounted) return null;

  const renderItem = (item: MobileNavItem) => {
    const active = isActive(item.to);
    const Icon = item.icon;
    return (
      <Link
        key={item.to}
        to={item.to}
        onClick={onClose}
        aria-current={active ? "page" : undefined}
        className={clsx(
          "relative flex items-center gap-3 rounded-xl pl-4 pr-3 py-2 text-[15px] font-semibold tracking-wide transition-colors duration-150 active:scale-[0.98]",
          active ? "bg-surface-800/70" : "hover:bg-surface-800/40",
          // The gold label paints itself (.nav-shine), so a text colour here would be dead weight.
          !item.premium && (active ? "text-surface-100" : "text-surface-300"),
        )}
      >
        {active && (
          <span
            aria-hidden
            className={clsx(
              "absolute left-0 top-1/2 h-6 w-1 -translate-y-1/2 rounded-r-full",
              item.premium ? "bg-amber-400" : "bg-primary-400",
            )}
          />
        )}
        <Icon
          className={clsx(
            "w-[18px] h-[18px] shrink-0",
            item.premium
              ? "text-amber-400"
              : active
                ? "text-surface-100"
                : "text-surface-500",
          )}
        />
        {item.premium ? (
          <span className={clsx("nav-shine", active && "nav-shine--active")}>{item.label}</span>
        ) : (
          item.label
        )}
        {(item.badge ?? 0) > 0 && (
          <span className="ml-auto min-w-5 h-5 px-1.5 inline-flex items-center justify-center rounded-full bg-rose-500 text-white text-[11px] font-bold leading-none">
            {item.badge}
          </span>
        )}
      </Link>
    );
  };

  return createPortal(
    <div className="md:hidden fixed inset-0 z-[60]">
      <button
        type="button"
        aria-label={t("nav.closeMenu")}
        onClick={onClose}
        className={clsx(
          // Literal black, not a surface token: the scrim has to READ as dim in both themes, and
          // `surface-950` flips to cream in the light one — the page behind would go pale instead
          // of receding. Same choice the app's modals already make.
          "absolute inset-0 w-full bg-black/50 backdrop-blur-sm transition-opacity duration-300 motion-reduce:transition-none",
          shown ? "opacity-100" : "opacity-0",
        )}
      />

      <div
        ref={trapRef}
        role="dialog"
        aria-modal="true"
        aria-label={t("nav.menu")}
        className={clsx(
          "absolute right-0 top-0 h-dvh w-[min(20rem,85vw)] flex flex-col",
          "bg-surface-900 border-l border-surface-800 shadow-[-12px_0_40px_rgba(0,0,0,0.45)]",
          "transition-transform duration-300 ease-out motion-reduce:transition-none",
          shown ? "translate-x-0" : "translate-x-full",
        )}
      >
        {/* Header: who is logged in, or just the menu title for a guest. Above the scroll area
            so the identity never scrolls away from the links it applies to. */}
        <div className="shrink-0 flex items-start gap-3 px-4 pt-[calc(1rem+env(safe-area-inset-top))] pb-4 border-b border-surface-800">
          {isAuthenticated ? (
            <>
              <Avatar src={user?.avatarUrl} name={user?.firstName} className="w-11 h-11 shrink-0" />
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold text-surface-100 truncate">
                  {user?.firstName} {user?.lastName}
                </p>
                <p className="text-xs text-surface-500 truncate">{user?.email}</p>
              </div>
            </>
          ) : (
            <p className="flex-1 text-base font-semibold text-surface-100 font-display">
              {t("nav.menu")}
            </p>
          )}
          <button
            type="button"
            onClick={onClose}
            aria-label={t("nav.closeMenu")}
            className="shrink-0 -mr-1 -mt-1 p-2 rounded-lg text-surface-400 hover:text-surface-100 hover:bg-surface-800 transition-colors active:scale-95"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <nav className="flex-1 overflow-y-auto overscroll-contain px-3 py-3 space-y-4">
          {sections.map((section) => (
            <div key={section.key}>
              <p className="px-4 pb-1.5 text-[11px] font-semibold uppercase tracking-[0.14em] text-surface-500">
                {section.title}
              </p>
              <div className="space-y-0.5">{section.items.map(renderItem)}</div>
            </div>
          ))}

        </nav>

        <div className="shrink-0 border-t border-surface-800 px-4 py-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))] space-y-2">
          {/* Account actions live in the pinned footer, not as a last section in the scroller:
              with a dozen tabs above them they sat below the fold, and "log out" is the one row
              nobody should have to go looking for. */}
          {isAuthenticated && (
            <div className="flex items-center gap-2">
              <Link
                to="/settings"
                onClick={onClose}
                className="flex-1 flex items-center justify-center gap-2 px-3 py-2 rounded-lg border border-surface-800 text-sm font-semibold text-surface-300 hover:text-surface-100 hover:bg-surface-800 transition-colors active:scale-95"
              >
                <Settings className="w-4 h-4 shrink-0" />
                {t("nav.profile")}
              </Link>
              <button
                type="button"
                onClick={() => {
                  onClose();
                  onLogout();
                }}
                className="flex-1 flex items-center justify-center gap-2 px-3 py-2 rounded-lg border border-surface-800 text-sm font-semibold text-rose-400/80 hover:text-rose-300/90 hover:bg-surface-800 transition-colors active:scale-95"
              >
                <LogOut className="w-4 h-4 shrink-0" />
                {t("nav.logout")}
              </button>
            </div>
          )}
          {!isAuthenticated && (
            <Link to="/login" onClick={onClose} className="block">
              <Button variant="primary" size="sm" className="w-full">
                {t("nav.login")}
              </Button>
            </Link>
          )}
          <div className="flex items-center gap-2">
            {/* Segmented, not the desktop dropdown: at the bottom of the sheet a popover would
                open past the edge of the screen, and three languages fit in the row anyway. */}
            <LanguageSwitcher variant="segmented" className="flex-1" />
            <button
              type="button"
              onClick={toggleTheme}
              aria-label={theme === "dark" ? "Switch to light mode" : "Switch to dark mode"}
              className="shrink-0 p-2.5 rounded-lg border border-surface-800 text-surface-300 hover:text-surface-100 hover:bg-surface-800 transition-colors active:scale-95"
            >
              {theme === "dark" ? <Moon className="w-4.5 h-4.5" /> : <Sun className="w-4.5 h-4.5" />}
            </button>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  );
}
