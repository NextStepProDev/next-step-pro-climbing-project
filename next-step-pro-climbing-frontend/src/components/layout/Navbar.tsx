import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ChevronDown, LogOut, Menu, User, X } from 'lucide-react'
import { useState, useRef, useEffect } from 'react'
import { useAuth } from '../../context/AuthContext'
import { Button } from '../ui/Button'
import clsx from 'clsx'
import logoWhite from '../../assets/logo/logo-white.png'

export function Navbar() {
  const { user, isAuthenticated, isAdmin, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const userMenuRef = useRef<HTMLDivElement>(null)

  const navLinks = [
    { to: '/', label: 'Start' },
    { to: '/calendar', label: 'Kalendarz' },
    ...(isAuthenticated ? [
      { to: '/my-reservations', label: 'Moje rezerwacje' },
    ] : []),
    ...(isAdmin ? [{ to: '/admin', label: 'Admin' }] : []),
  ]

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (userMenuRef.current && !userMenuRef.current.contains(e.target as Node)) {
        setUserMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  // Close dropdown on route change
  useEffect(() => {
    setUserMenuOpen(false)
  }, [location.pathname])

  const userInitial = user?.firstName?.charAt(0).toUpperCase() ?? '?'

  return (
    <nav className="bg-dark-900/80 backdrop-blur-sm border-b border-dark-800 sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-18">
          {/* Logo */}
          <Link to="/" className="flex items-center hover:opacity-80 transition-opacity">
            <img src={logoWhite} alt="Next Step Pro Climbing" className="h-10" />
          </Link>

          {/* Desktop Navigation */}
          <div className="hidden md:flex items-center gap-8">
            {navLinks.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                className={clsx(
                  'text-base font-semibold tracking-wide transition-colors',
                  location.pathname === link.to
                    ? 'text-primary-400'
                    : 'text-dark-300 hover:text-dark-100'
                )}
              >
                {link.label}
              </Link>
            ))}
          </div>

          {/* User Actions */}
          <div className="hidden md:flex items-center">
            {isAuthenticated ? (
              <div className="relative" ref={userMenuRef}>
                <button
                  onClick={() => setUserMenuOpen(!userMenuOpen)}
                  className="flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-dark-800 transition-colors"
                >
                  <div className="w-9 h-9 rounded-full bg-primary-600 flex items-center justify-center">
                    <span className="text-sm font-bold text-white">{userInitial}</span>
                  </div>
                  <span className="text-sm font-medium text-dark-200">
                    {user?.firstName}
                  </span>
                  <ChevronDown className={clsx(
                    'w-4 h-4 text-dark-400 transition-transform',
                    userMenuOpen && 'rotate-180'
                  )} />
                </button>

                {userMenuOpen && (
                  <div className="absolute right-0 mt-2 w-56 bg-dark-900 border border-dark-700 rounded-xl shadow-lg shadow-black/30 overflow-hidden">
                    <div className="px-4 py-3 border-b border-dark-800">
                      <p className="text-sm font-medium text-dark-100">{user?.firstName} {user?.lastName}</p>
                      <p className="text-xs text-dark-500 mt-0.5">{user?.email}</p>
                    </div>
                    <div className="py-1">
                      <button
                        onClick={() => navigate('/settings')}
                        className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-dark-300 hover:bg-dark-800 hover:text-dark-100 transition-colors"
                      >
                        <User className="w-4 h-4" />
                        Profil
                      </button>
                      <button
                        onClick={() => { setUserMenuOpen(false); logout() }}
                        className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-red-400 hover:bg-dark-800 hover:text-red-300 transition-colors"
                      >
                        <LogOut className="w-4 h-4" />
                        Wyloguj się
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <Link to="/login">
                  <Button variant="ghost" size="sm">
                    Zaloguj się
                  </Button>
                </Link>
              </div>
            )}
          </div>

          {/* Mobile menu button */}
          <button
            className="md:hidden text-dark-300"
            aria-label={mobileMenuOpen ? 'Zamknij menu' : 'Otwórz menu'}
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          >
            {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
          </button>
        </div>
      </div>

      {/* Mobile menu */}
      {mobileMenuOpen && (
        <div className="md:hidden bg-dark-900 border-t border-dark-800">
          <div className="px-4 py-4 space-y-3">
            {navLinks.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                onClick={() => setMobileMenuOpen(false)}
                className={clsx(
                  'block py-2 text-base font-semibold tracking-wide',
                  location.pathname === link.to
                    ? 'text-primary-400'
                    : 'text-dark-300'
                )}
              >
                {link.label}
              </Link>
            ))}
            <div className="pt-4 border-t border-dark-800">
              {isAuthenticated ? (
                <div className="space-y-1">
                  <div className="flex items-center gap-3 px-1 py-2">
                    <div className="w-9 h-9 rounded-full bg-primary-600 flex items-center justify-center">
                      <span className="text-sm font-bold text-white">{userInitial}</span>
                    </div>
                    <div>
                      <p className="text-sm font-medium text-dark-200">{user?.firstName} {user?.lastName}</p>
                      <p className="text-xs text-dark-500">{user?.email}</p>
                    </div>
                  </div>
                  <Link
                    to="/settings"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center gap-3 px-1 py-2 text-dark-300 text-sm"
                  >
                    <User className="w-4 h-4" />
                    Profil
                  </Link>
                  <button
                    onClick={() => { logout(); setMobileMenuOpen(false) }}
                    className="flex items-center gap-3 px-1 py-2 text-red-400 text-sm"
                  >
                    <LogOut className="w-4 h-4" />
                    Wyloguj się
                  </button>
                </div>
              ) : (
                <Link to="/login" onClick={() => setMobileMenuOpen(false)}>
                  <Button variant="primary" size="sm" className="w-full">
                    Zaloguj się
                  </Button>
                </Link>
              )}
            </div>
          </div>
        </div>
      )}
    </nav>
  )
}
