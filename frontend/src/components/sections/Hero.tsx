import { motion } from 'framer-motion';
import { Link } from 'react-router-dom';
import { ArrowRight, Sparkles } from 'lucide-react';

export default function Hero() {
  return (
    <section className="relative min-h-screen flex items-center justify-center overflow-hidden pt-20">
      {/* Background glow */}
      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute top-1/3 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[600px] bg-accent-blue/8 rounded-full blur-[120px]" />
        <div className="absolute bottom-1/4 right-1/4 w-[400px] h-[400px] bg-accent-violet/5 rounded-full blur-[100px]" />
      </div>

      <div className="relative z-10 max-w-5xl mx-auto px-6 text-center">
        {/* Badge */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.1 }}
          className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full border border-white/10 bg-white/5 mb-8"
        >
          <Sparkles size={14} className="text-accent-blue" />
          <span className="text-xs text-white/60 tracking-wide">Instant Website-to-Desktop Conversion</span>
        </motion.div>

        {/* Headline */}
        <motion.h1
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ type: 'spring', stiffness: 80, damping: 20, delay: 0.2 }}
          className="text-5xl sm:text-7xl lg:text-8xl font-bold tracking-tight leading-[0.95] mb-6"
        >
          <span className="gradient-text">Any Website.</span>
          <br />
          <span className="text-white">One Desktop App.</span>
        </motion.h1>

        {/* Subheadline */}
        <motion.p
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.4 }}
          className="text-lg sm:text-xl text-white/40 max-w-2xl mx-auto mb-10 leading-relaxed font-light"
        >
          Paste a URL. Get a native desktop application with screenshot protection,
          auto-updates, and cross-platform builds — in seconds.
        </motion.p>

        {/* CTAs */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.55 }}
          className="flex flex-col sm:flex-row items-center justify-center gap-4"
        >
          <Link to="/register" className="btn-primary flex items-center gap-2 group">
            Start Converting
            <ArrowRight size={16} className="transition-transform group-hover:translate-x-1" />
          </Link>
          <Link to="/login" className="btn-ghost">
            Sign In
          </Link>
        </motion.div>

        {/* Floating mockup hint */}
        <motion.div
          initial={{ opacity: 0, y: 40 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ type: 'spring', stiffness: 60, delay: 0.7 }}
          className="mt-20 glass-card p-1 max-w-3xl mx-auto"
        >
          <div className="bg-surface-secondary rounded-2xl p-6 sm:p-10">
            <div className="flex items-center gap-2 mb-4">
              <div className="w-3 h-3 rounded-full bg-red-500/60" />
              <div className="w-3 h-3 rounded-full bg-yellow-500/60" />
              <div className="w-3 h-3 rounded-full bg-green-500/60" />
              <div className="ml-4 flex-1 h-6 rounded-lg bg-white/5 flex items-center px-3">
                <span className="text-xs text-white/20 font-mono">https://your-website.com</span>
              </div>
            </div>
            <div className="h-40 sm:h-56 rounded-xl bg-gradient-to-br from-white/[0.03] to-transparent border border-white/5 flex items-center justify-center">
              <p className="text-white/10 text-sm">Your website renders here as a desktop app</p>
            </div>
          </div>
        </motion.div>
      </div>
    </section>
  );
}
