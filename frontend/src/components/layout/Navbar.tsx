import { useState, useEffect, useRef } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { Menu, X, Monitor, LogOut, LayoutDashboard, Settings, ChevronDown } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';

const SECTION_LINKS = [
  { label: 'Features', href: '#features' },
  { label: 'How It Works', href: '#how-it-works' },
  { label: 'Pricing', href: '#pricing' },
];

export default function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const { isAuthenticated, user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const userMenuRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const handleScroll = () => setScrolled(window.scrollY > 20);
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  useEffect(() => {
    const onPointerDown = (e: PointerEvent) => {
      const el = userMenuRef.current;
      if (!el) return;
      if (el.contains(e.target as Node)) return;
      setUserMenuOpen(false);
    };
    window.addEventListener('pointerdown', onPointerDown);
    return () => window.removeEventListener('pointerdown', onPointerDown);
  }, []);

  const handleLogout = async () => {
    await logout();
    navigate('/');
  };

  const handleSectionClick = (href: string) => {
    setMobileOpen(false);
    if (location.pathname !== '/') {
      navigate('/');
      setTimeout(() => {
        document.querySelector(href)?.scrollIntoView({ behavior: 'smooth' });
      }, 100);
    } else {
      document.querySelector(href)?.scrollIntoView({ behavior: 'smooth' });
    }
  };

  const isLanding = location.pathname === '/';

  return (
    <motion.nav
      initial={{ y: -100 }}
      animate={{ y: 0 }}
      transition={{ type: 'spring', stiffness: 100, damping: 20 }}
      className="fixed top-0 left-0 right-0 z-50 transition-all duration-500"
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6">
        <div
          className={`mt-3 sm:mt-4 flex items-center justify-between rounded-2xl sm:rounded-full px-4 sm:px-5 py-2.5 nav-glass transition-all duration-500 ${
            scrolled ? 'nav-glass--scrolled' : ''
          }`}
        >
          {/* Logo */}
          <Link to="/" className="flex items-center gap-2 group">
            <div className="w-7 h-7 sm:w-8 sm:h-8 rounded-lg bg-gradient-to-br from-indigo-500 to-violet-500 flex items-center justify-center transition-all duration-300 group-hover:scale-110 group-hover:shadow-lg group-hover:shadow-indigo-500/20">
              <Monitor size={14} className="text-white" />
            </div>
            <span className="text-base sm:text-lg font-semibold tracking-tight">WebToDesk</span>
          </Link>

          {/* Desktop Links */}
          <div className="hidden md:flex items-center gap-1">
            {/* Section links (only on landing or always — they navigate to /) */}
            {isLanding && SECTION_LINKS.map(({ label, href }) => (
              <button
                key={href}
                type="button"
                onClick={() => handleSectionClick(href)}
                className="px-3 py-1.5 rounded-lg text-[13px] text-white/40 hover:text-white/80 hover:bg-white/[0.04] transition-all duration-300"
              >
                {label}
              </button>
            ))}

            <div className="w-px h-4 bg-white/[0.08] mx-2" />

            {isAuthenticated ? (
              <>
                <Link
                  to="/dashboard"
                  className="px-3 py-1.5 rounded-lg text-[13px] text-white/50 hover:text-white hover:bg-white/[0.04] transition-all duration-300 flex items-center gap-1.5"
                >
                  <LayoutDashboard size={13} /> Dashboard
                </Link>
                <div className="relative" ref={userMenuRef}>
                  <button
                    type="button"
                    onClick={() => setUserMenuOpen((v) => !v)}
                    className="px-3 py-1.5 rounded-lg text-[13px] text-white/40 hover:text-white/70 transition-all duration-300 flex items-center gap-1.5"
                    aria-haspopup="menu"
                    aria-expanded={userMenuOpen}
                  >
                    <div className="w-5 h-5 rounded-full bg-gradient-to-br from-indigo-500/30 to-violet-500/30 border border-white/10 flex items-center justify-center">
                      <span className="text-[9px] font-bold text-white/80">
                        {user?.username?.charAt(0).toUpperCase()}
                      </span>
                    </div>
                    <span>{user?.username}</span>
                    <ChevronDown size={12} className={`transition-transform duration-300 ${userMenuOpen ? 'rotate-180' : ''}`} />
                  </button>

                  <AnimatePresence>
                    {userMenuOpen && (
                      <motion.div
                        initial={{ opacity: 0, y: -6, scale: 0.96 }}
                        animate={{ opacity: 1, y: 0, scale: 1 }}
                        exit={{ opacity: 0, y: -6, scale: 0.96 }}
                        transition={{ type: 'spring', stiffness: 260, damping: 22 }}
                        className="absolute right-0 mt-3 w-48 rounded-xl glass overflow-hidden shadow-2xl shadow-black/40"
                        role="menu"
                      >
                        <div className="p-1.5">
                          <Link
                            to="/settings"
                            onClick={() => setUserMenuOpen(false)}
                            className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-white/60 hover:text-white hover:bg-white/5 transition-all"
                            role="menuitem"
                          >
                            <Settings size={14} className="text-white/30" />
                            Settings
                          </Link>
                          <button
                            type="button"
                            onClick={async () => {
                              setUserMenuOpen(false);
                              await handleLogout();
                            }}
                            className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-white/60 hover:text-white hover:bg-white/5 transition-all text-left"
                            role="menuitem"
                          >
                            <LogOut size={14} className="text-white/30" />
                            Logout
                          </button>
                        </div>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              </>
            ) : (
              <>
                <Link to="/login" className="px-3 py-1.5 rounded-lg text-[13px] text-white/50 hover:text-white transition-colors duration-300">
                  Sign In
                </Link>
                <Link to="/register" className="ml-1 px-4 py-1.5 rounded-full bg-gradient-to-r from-indigo-500 to-violet-500 text-white text-[13px] font-semibold hover:brightness-110 transition-all duration-300 shadow-md shadow-indigo-500/15">
                  Get Started
                </Link>
              </>
            )}
          </div>

          {/* Mobile Toggle */}
          <button className="md:hidden text-white/60 p-1" onClick={() => setMobileOpen(!mobileOpen)}>
            {mobileOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>

        {/* Mobile Menu */}
        <AnimatePresence>
          {mobileOpen && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              className="md:hidden glass mt-2 rounded-2xl overflow-hidden"
            >
              <div className="p-5 flex flex-col gap-1">
                {isLanding && SECTION_LINKS.map(({ label, href }) => (
                  <button
                    key={href}
                    type="button"
                    onClick={() => handleSectionClick(href)}
                    className="text-sm text-white/50 hover:text-white py-2 px-3 rounded-lg hover:bg-white/[0.04] transition-all text-left"
                  >
                    {label}
                  </button>
                ))}

                <div className="h-px bg-white/[0.06] my-2" />

                {isAuthenticated ? (
                  <>
                    <Link
                      to="/dashboard"
                      onClick={() => setMobileOpen(false)}
                      className="text-sm text-white/60 hover:text-white py-2 px-3 rounded-lg hover:bg-white/[0.04] transition-all flex items-center gap-2"
                    >
                      <LayoutDashboard size={14} /> Dashboard
                    </Link>
                    <Link
                      to="/settings"
                      onClick={() => setMobileOpen(false)}
                      className="text-sm text-white/60 hover:text-white py-2 px-3 rounded-lg hover:bg-white/[0.04] transition-all flex items-center gap-2"
                    >
                      <Settings size={14} /> Settings
                    </Link>
                    <button
                      onClick={() => { setMobileOpen(false); handleLogout(); }}
                      className="text-sm text-white/60 hover:text-white py-2 px-3 rounded-lg hover:bg-white/[0.04] transition-all text-left flex items-center gap-2"
                    >
                      <LogOut size={14} /> Logout
                    </button>
                  </>
                ) : (
                  <>
                    <Link
                      to="/login"
                      onClick={() => setMobileOpen(false)}
                      className="text-sm text-white/60 hover:text-white py-2 px-3 rounded-lg hover:bg-white/[0.04] transition-all"
                    >
                      Sign In
                    </Link>
                    <Link
                      to="/register"
                      onClick={() => setMobileOpen(false)}
                      className="mt-2 btn-accent text-center !py-2.5"
                    >
                      Get Started
                    </Link>
                  </>
                )}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </motion.nav>
  );
}
