import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Link } from 'react-router-dom';
import { ArrowRight, Sparkles, Monitor, Apple, Laptop } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';

const TYPED_URL = 'https://your-awesome-app.com';

function TypingDemo() {
  const [charIndex, setCharIndex] = useState(0);
  const [phase, setPhase] = useState<'typing' | 'building' | 'done'>('typing');
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    if (phase === 'typing') {
      if (charIndex < TYPED_URL.length) {
        const t = setTimeout(() => setCharIndex(c => c + 1), 60);
        return () => clearTimeout(t);
      }
      const t = setTimeout(() => setPhase('building'), 500);
      return () => clearTimeout(t);
    }
    if (phase === 'building') {
      if (progress < 100) {
        const t = setTimeout(() => setProgress(p => Math.min(100, p + 2)), 30);
        return () => clearTimeout(t);
      }
      const t = setTimeout(() => setPhase('done'), 400);
      return () => clearTimeout(t);
    }
  }, [phase, charIndex, progress]);

  // Auto restart the animation
  useEffect(() => {
    if (phase === 'done') {
      const t = setTimeout(() => {
        setPhase('typing');
        setCharIndex(0);
        setProgress(0);
      }, 3000);
      return () => clearTimeout(t);
    }
  }, [phase]);

  return (
    <div className="w-full max-w-2xl mx-auto">
      <div className="glass-card overflow-hidden">
        {/* Title bar */}
        <div className="flex items-center gap-2 px-4 py-3 border-b border-white/5">
          <div className="flex items-center gap-1.5">
            <div className="w-2.5 h-2.5 rounded-full bg-red-500/70" />
            <div className="w-2.5 h-2.5 rounded-full bg-yellow-500/70" />
            <div className="w-2.5 h-2.5 rounded-full bg-green-500/70" />
          </div>
          <div className="flex-1 ml-3">
            <div className="flex items-center gap-2 px-3 py-1 rounded-lg bg-white/[0.03] border border-white/5 max-w-md">
              <span className="text-[11px] font-mono text-white/20 select-none">URL:</span>
              <span className="text-[11px] font-mono text-indigo-300/80">
                {TYPED_URL.slice(0, charIndex)}
                {phase === 'typing' && (
                  <span className="inline-block w-[1px] h-3 bg-indigo-400 ml-0.5 animate-pulse" />
                )}
              </span>
            </div>
          </div>
        </div>

        {/* Content area */}
        <div className="p-6 sm:p-8 min-h-[180px] flex items-center justify-center">
          {phase === 'typing' && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="text-center"
            >
              <Monitor size={32} className="mx-auto text-white/10 mb-3" />
              <p className="text-xs text-white/20">Paste any URL to begin conversion...</p>
            </motion.div>
          )}

          {phase === 'building' && (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              className="w-full max-w-xs"
            >
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-white/40 font-medium">Building desktop app...</span>
                <span className="text-xs font-mono text-indigo-400">{progress}%</span>
              </div>
              <div className="h-1.5 rounded-full bg-white/[0.05] overflow-hidden">
                <motion.div
                  className="h-full rounded-full bg-gradient-to-r from-indigo-500 to-violet-500"
                  style={{ width: `${progress}%` }}
                />
              </div>
              <div className="flex items-center gap-4 mt-4 justify-center">
                {['Installing deps', 'Electron build', 'Packaging'].map((step, i) => (
                  <span
                    key={step}
                    className={`text-[10px] ${
                      progress > (i + 1) * 30 ? 'text-indigo-400' : 'text-white/15'
                    } transition-colors duration-500`}
                  >
                    {step}
                  </span>
                ))}
              </div>
            </motion.div>
          )}

          {phase === 'done' && (
            <motion.div
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ type: 'spring', stiffness: 200, damping: 20 }}
              className="text-center"
            >
              <div className="inline-flex items-center gap-3 px-5 py-3 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 mb-3">
                <div className="w-8 h-8 rounded-xl bg-emerald-500/20 flex items-center justify-center">
                  <Sparkles size={16} className="text-emerald-400" />
                </div>
                <div className="text-left">
                  <p className="text-xs font-semibold text-emerald-300">Build Complete</p>
                  <p className="text-[10px] text-emerald-400/60">your-awesome-app.exe — 48.2 MB</p>
                </div>
              </div>
              <div className="flex items-center justify-center gap-3 mt-2">
                {[
                  { icon: Laptop, label: '.exe' },
                  { icon: Apple, label: '.dmg' },
                  { icon: Monitor, label: '.AppImage' },
                ].map(({ icon: Icon, label }) => (
                  <div key={label} className="flex items-center gap-1 px-2 py-1 rounded-lg bg-white/[0.03] border border-white/5">
                    <Icon size={10} className="text-white/30" />
                    <span className="text-[9px] text-white/30">{label}</span>
                  </div>
                ))}
              </div>
            </motion.div>
          )}
        </div>
      </div>
    </div>
  );
}

export default function Hero() {
  const { isAuthenticated, isLoading } = useAuth();

  return (
    <section className="relative min-h-screen flex items-center justify-center overflow-hidden pt-20">
      {/* Background mesh */}
      <div className="absolute inset-0 mesh-gradient pointer-events-none" />
      <div className="absolute inset-0 pointer-events-none">
        <div className="glow-orb w-[600px] h-[400px] bg-indigo-500/[0.06] top-[10%] left-1/2 -translate-x-1/2" />
        <div className="glow-orb w-[300px] h-[300px] bg-violet-500/[0.04] bottom-[20%] right-[15%] animate-float-slow" />
        <div className="glow-orb w-[200px] h-[200px] bg-emerald-500/[0.03] bottom-[30%] left-[10%] animate-float" />
      </div>

      <div className="relative z-10 max-w-5xl mx-auto px-6 text-center">
        {/* Badge */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full border border-white/[0.08] bg-white/[0.03] mb-8"
        >
          <div className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
          <span className="text-[11px] text-white/50 tracking-wide font-medium">Website to Desktop in seconds</span>
        </motion.div>

        {/* Headline */}
        <motion.h1
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ type: 'spring', stiffness: 80, damping: 20, delay: 0.1 }}
          className="text-5xl sm:text-7xl lg:text-[5.5rem] font-extrabold tracking-tight leading-[0.95] mb-6"
        >
          <span className="gradient-text">Convert any website</span>
          <br />
          <span className="gradient-text-blue">into a desktop app.</span>
        </motion.h1>

        {/* Subheadline */}
        <motion.p
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.25 }}
          className="text-base sm:text-lg text-white/35 max-w-xl mx-auto mb-10 leading-relaxed font-light"
        >
          Paste a URL. Get a secure native desktop application with screenshot protection,
          auto-updates, and cross-platform builds — no code changes needed.
        </motion.p>

        {/* CTAs */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.35 }}
          className="flex flex-col sm:flex-row items-center justify-center gap-3"
        >
          {isLoading ? (
            <>
              <button type="button" disabled className="btn-accent opacity-60">Loading...</button>
              <button type="button" disabled className="btn-ghost opacity-60">Please wait</button>
            </>
          ) : isAuthenticated ? (
            <>
              <Link to="/dashboard" className="btn-accent flex items-center gap-2 group">
                Open Dashboard
                <ArrowRight size={15} className="transition-transform group-hover:translate-x-1" />
              </Link>
              <Link to="/settings" className="btn-ghost">Settings</Link>
            </>
          ) : (
            <>
              <Link to="/register" className="btn-accent flex items-center gap-2 group">
                Start Building Free
                <ArrowRight size={15} className="transition-transform group-hover:translate-x-1" />
              </Link>
              <Link to="/login" className="btn-ghost">Sign In</Link>
            </>
          )}
        </motion.div>

        {/* Animated demo */}
        <motion.div
          initial={{ opacity: 0, y: 40 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ type: 'spring', stiffness: 50, damping: 20, delay: 0.5 }}
          className="mt-16 sm:mt-20"
        >
          <TypingDemo />
        </motion.div>

        {/* Social proof */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.8 }}
          className="mt-12 flex items-center justify-center gap-6 text-[11px] text-white/20"
        >
          <span>Windows</span>
          <span className="w-px h-3 bg-white/10" />
          <span>macOS</span>
          <span className="w-px h-3 bg-white/10" />
          <span>Linux</span>
          <span className="w-px h-3 bg-white/10" />
          <span>Cross-platform builds</span>
        </motion.div>
      </div>
    </section>
  );
}
