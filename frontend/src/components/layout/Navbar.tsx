import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { Menu, X, Monitor, LogOut, LayoutDashboard } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';

export default function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const { isAuthenticated, user, logout } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    const handleScroll = () => setScrolled(window.scrollY > 20);
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
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
      className={`fixed top-0 left-0 right-0 z-50 transition-all duration-500 ${
        scrolled ? 'glass py-3' : 'bg-transparent py-5'
      }`}
    >
      <div className="max-w-7xl mx-auto px-6 flex items-center justify-between">
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
              <Link to="/dashboard" className="text-sm text-white/60 hover:text-white transition-colors duration-300 flex items-center gap-1.5">
                <LayoutDashboard size={14} /> Dashboard
              </Link>
              <span className="text-sm text-white/40">{user?.username}</span>
              <button onClick={handleLogout} className="text-sm text-white/60 hover:text-white transition-colors duration-300 flex items-center gap-1.5">
                <LogOut size={14} /> Logout
              </button>
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
            className="md:hidden glass mt-2 mx-4 rounded-2xl overflow-hidden"
          >
            <div className="p-6 flex flex-col gap-4">
              <Link to="/" onClick={() => setMobileOpen(false)} className="text-sm text-white/70 hover:text-white">Home</Link>
              {isAuthenticated ? (
                <>
                  <Link to="/dashboard" onClick={() => setMobileOpen(false)} className="text-sm text-white/70 hover:text-white">Dashboard</Link>
                  <button onClick={() => { setMobileOpen(false); handleLogout(); }} className="text-sm text-white/70 hover:text-white text-left">Logout</button>
                </>
              ) : (
                <>
                  <Link to="/login" onClick={() => setMobileOpen(false)} className="text-sm text-white/70 hover:text-white">Sign In</Link>
                  <Link to="/register" onClick={() => setMobileOpen(false)} className="btn-primary text-center">Get Started</Link>
                </>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.nav>
  );
}
