import { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { Menu, X, Monitor, LogOut, LayoutDashboard, Settings, ChevronDown } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';

export default function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const { isAuthenticated, user, logout } = useAuth();
  const navigate = useNavigate();
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

  return (
    <motion.nav
      initial={{ y: -100 }}
      animate={{ y: 0 }}
      transition={{ type: 'spring', stiffness: 100, damping: 20 }}
      className="fixed top-0 left-0 right-0 z-50 transition-all duration-500"
    >
      <div className="max-w-7xl mx-auto px-6">
        {/* Rounded frosted pill */}
        <div
          className={`mt-4 flex items-center justify-between rounded-full px-5 py-2.5 nav-glass ${
            scrolled ? 'nav-glass--scrolled' : ''
          }`}
        >
          {/* Logo */}
          <Link to="/" className="flex items-center gap-2.5 group">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-accent-blue to-accent-violet flex items-center justify-center transition-transform duration-300 group-hover:scale-110">
              <Monitor size={16} className="text-white" />
            </div>
            <span className="text-lg font-semibold tracking-tight">WebToDesk</span>
          </Link>

          {/* Desktop Links */}
          <div className="hidden md:flex items-center gap-8">
            <Link to="/" className="text-sm text-white/60 hover:text-white transition-colors duration-300">Home</Link>
            {isAuthenticated ? (
              <>
                <Link
                  to="/dashboard"
                  className="text-sm text-white/60 hover:text-white transition-colors duration-300 flex items-center gap-1.5"
                >
                  <LayoutDashboard size={14} /> Dashboard
                </Link>
                <div className="relative" ref={userMenuRef}>
                  <button
                    type="button"
                    onClick={() => setUserMenuOpen((v) => !v)}
                    className="text-sm text-white/60 hover:text-white transition-colors duration-300 flex items-center gap-1.5"
                    aria-haspopup="menu"
                    aria-expanded={userMenuOpen}
                  >
                    <span className="text-white/40 hover:text-white transition-colors duration-300">
                      {user?.username}
                    </span>
                    <ChevronDown size={14} className={`transition-transform duration-300 ${userMenuOpen ? 'rotate-180' : ''}`} />
                  </button>

                  <AnimatePresence>
                    {userMenuOpen && (
                      <motion.div
                        initial={{ opacity: 0, y: -6, scale: 0.98 }}
                        animate={{ opacity: 1, y: 0, scale: 1 }}
                        exit={{ opacity: 0, y: -6, scale: 0.98 }}
                        transition={{ type: 'spring', stiffness: 260, damping: 22 }}
                        className="absolute right-0 mt-3 w-52 rounded-2xl glass overflow-hidden"
                        role="menu"
                      >
                        <div className="p-2">
                          <Link
                            to="/settings"
                            onClick={() => setUserMenuOpen(false)}
                            className="flex items-center gap-2 px-3 py-2 rounded-xl text-sm text-white/70 hover:text-white hover:bg-white/5 transition-all"
                            role="menuitem"
                          >
                            <Settings size={15} className="text-white/30" />
                            Settings
                          </Link>
                          <button
                            type="button"
                            onClick={async () => {
                              setUserMenuOpen(false);
                              await handleLogout();
                            }}
                            className="w-full flex items-center gap-2 px-3 py-2 rounded-xl text-sm text-white/70 hover:text-white hover:bg-white/5 transition-all text-left"
                            role="menuitem"
                          >
                            <LogOut size={15} className="text-white/30" />
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
                <Link to="/login" className="text-sm text-white/60 hover:text-white transition-colors duration-300">Sign In</Link>
                <Link to="/register" className="btn-primary !py-2 !px-5 !text-xs">Get Started</Link>
              </>
            )}
          </div>

          {/* Mobile Toggle */}
          <button className="md:hidden text-white/60" onClick={() => setMobileOpen(!mobileOpen)}>
            {mobileOpen ? <X size={22} /> : <Menu size={22} />}
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
              <div className="p-6 flex flex-col gap-4">
                <Link
                  to="/"
                  onClick={() => setMobileOpen(false)}
                  className="text-sm text-white/70 hover:text-white"
                >
                  Home
                </Link>
                {isAuthenticated ? (
                  <>
                    <Link
                      to="/dashboard"
                      onClick={() => setMobileOpen(false)}
                      className="text-sm text-white/70 hover:text-white"
                    >
                      Dashboard
                    </Link>
                    <div className="pt-2 border-t border-white/10">
                      <p className="text-xs text-white/30 mb-2">{user?.username}</p>
                      <Link
                        to="/settings"
                        onClick={() => setMobileOpen(false)}
                        className="text-sm text-white/70 hover:text-white flex items-center gap-2"
                      >
                        <Settings size={14} className="text-white/30" /> Settings
                      </Link>
                    </div>
                    <button
                      onClick={() => {
                        setMobileOpen(false);
                        handleLogout();
                      }}
                      className="text-sm text-white/70 hover:text-white text-left flex items-center gap-2"
                    >
                      <LogOut size={14} className="text-white/30" /> Logout
                    </button>
                  </>
                ) : (
                  <>
                    <Link
                      to="/login"
                      onClick={() => setMobileOpen(false)}
                      className="text-sm text-white/70 hover:text-white"
                    >
                      Sign In
                    </Link>
                    <Link
                      to="/register"
                      onClick={() => setMobileOpen(false)}
                      className="btn-primary text-center"
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
