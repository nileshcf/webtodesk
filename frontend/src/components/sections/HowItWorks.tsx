import { motion } from 'framer-motion';
import { Globe, Sliders, Download } from 'lucide-react';

const steps = [
  {
    number: '01',
    icon: Globe,
    title: 'Paste your URL',
    description: 'Enter any website URL — WordPress, React, Vue, static HTML. We handle the rest.',
    color: 'from-indigo-500/20 to-indigo-500/0',
    iconColor: 'text-indigo-400',
  },
  {
    number: '02',
    icon: Sliders,
    title: 'Configure & protect',
    description: 'Enable screenshot protection, custom title bar, domain locking, auto-updates, and more.',
    color: 'from-violet-500/20 to-violet-500/0',
    iconColor: 'text-violet-400',
  },
  {
    number: '03',
    icon: Download,
    title: 'Download your app',
    description: 'Get a production-ready .exe, .dmg, or AppImage. Deploy to your users instantly.',
    color: 'from-emerald-500/20 to-emerald-500/0',
    iconColor: 'text-emerald-400',
  },
];

export default function HowItWorks() {
  return (
    <section id="how-it-works" className="py-24 sm:py-32 px-6">
      <div className="max-w-5xl mx-auto">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: '-80px' }}
          className="text-center mb-16"
        >
          <p className="text-xs text-indigo-400/80 font-medium tracking-widest uppercase mb-3">How it works</p>
          <h2 className="text-3xl sm:text-5xl font-bold tracking-tight gradient-text">
            Three steps. That's it.
          </h2>
        </motion.div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {steps.map((step, i) => (
            <motion.div
              key={step.number}
              initial={{ opacity: 0, y: 30 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: '-50px' }}
              transition={{ delay: i * 0.15 }}
              className="relative group"
            >
              <div className="glass-card p-7 sm:p-8 h-full card-glow">
                {/* Gradient accent */}
                <div className={`absolute inset-0 rounded-[1.25rem] bg-gradient-to-b ${step.color} opacity-0 group-hover:opacity-100 transition-opacity duration-500`} />

                <div className="relative">
                  {/* Step number */}
                  <span className="text-[10px] font-mono text-white/15 tracking-wider">{step.number}</span>

                  {/* Icon */}
                  <div className="w-11 h-11 rounded-xl bg-white/[0.04] border border-white/5 flex items-center justify-center mt-3 mb-5">
                    <step.icon size={20} className={step.iconColor} />
                  </div>

                  <h3 className="text-lg font-semibold mb-2 tracking-tight">{step.title}</h3>
                  <p className="text-sm text-white/35 leading-relaxed">{step.description}</p>
                </div>
              </div>

              {/* Connector line (hidden on mobile) */}
              {i < 2 && (
                <div className="hidden md:block absolute top-1/2 -right-3 w-6 h-px bg-gradient-to-r from-white/10 to-transparent" />
              )}
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
