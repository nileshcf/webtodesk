import { motion } from 'framer-motion';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';

export default function CTASection() {
  const { isAuthenticated } = useAuth();

  return (
    <section className="py-24 sm:py-32 px-6">
      <div className="max-w-4xl mx-auto text-center relative">
        {/* Background glow */}
        <div className="absolute inset-0 pointer-events-none">
          <div className="glow-orb w-[500px] h-[300px] bg-indigo-500/[0.08] top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2" />
        </div>

        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: '-80px' }}
          className="relative"
        >
          <h2 className="text-3xl sm:text-5xl lg:text-6xl font-bold tracking-tight mb-6">
            <span className="gradient-text">Ready to convert</span>
            <br />
            <span className="gradient-text-blue">your website?</span>
          </h2>

          <p className="text-base text-white/30 max-w-lg mx-auto mb-10 leading-relaxed">
            Join hundreds of creators who've turned their websites into secure,
            native desktop applications — in under 60 seconds.
          </p>

          <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
            <Link
              to={isAuthenticated ? '/dashboard' : '/register'}
              className="btn-accent flex items-center gap-2 group"
            >
              {isAuthenticated ? 'Go to Dashboard' : 'Start Building Free'}
              <ArrowRight size={15} className="transition-transform group-hover:translate-x-1" />
            </Link>
            <Link to={isAuthenticated ? '/settings' : '/login'} className="btn-ghost">
              {isAuthenticated ? 'Settings' : 'Sign In'}
            </Link>
          </div>
        </motion.div>
      </div>
    </section>
  );
}
