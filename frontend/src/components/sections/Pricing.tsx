import { motion } from 'framer-motion';
import { Check, X, Zap, Crown, Infinity } from 'lucide-react';

const tiers = [
  {
    name: 'Trial',
    price: 'Free',
    period: '30 days',
    icon: Zap,
    description: 'Perfect for testing the waters',
    highlight: false,
    features: [
      { text: '2 active apps', included: true },
      { text: '4 total builds', included: true },
      { text: 'All platforms (Win/Mac/Linux)', included: true },
      { text: 'Splash screen (branded)', included: true },
      { text: 'File download support', included: true },
      { text: 'Basic domain lock', included: true },
      { text: 'Screenshot protection', included: false },
      { text: 'Custom watermark', included: false },
      { text: 'Auto-updates', included: false },
      { text: 'Priority build queue', included: false },
    ],
  },
  {
    name: 'Pro',
    price: '$29',
    period: '/month',
    icon: Crown,
    description: 'For serious creators and businesses',
    highlight: true,
    features: [
      { text: 'Unlimited apps', included: true },
      { text: '50 builds/month', included: true },
      { text: 'All platforms (Win/Mac/Linux)', included: true },
      { text: 'Custom splash screen', included: true },
      { text: 'Screenshot protection', included: true },
      { text: 'Full domain whitelist/blacklist', included: true },
      { text: 'Custom watermark', included: true },
      { text: 'Auto-updates & notifications', included: true },
      { text: 'System tray integration', included: true },
      { text: 'Priority build queue', included: true },
    ],
  },
  {
    name: 'Lifetime',
    price: '$199',
    period: 'one-time',
    icon: Infinity,
    description: 'Pay once, build forever',
    highlight: false,
    features: [
      { text: 'Unlimited apps', included: true },
      { text: 'Unlimited builds', included: true },
      { text: 'All platforms (Win/Mac/Linux)', included: true },
      { text: 'Everything in Pro', included: true },
      { text: 'Global hotkeys', included: true },
      { text: 'File system access', included: true },
      { text: 'Window polish (blur, AOT)', included: true },
      { text: 'Offline caching', included: true },
      { text: 'Clipboard integration', included: true },
      { text: 'Priority support', included: true },
    ],
  },
];

export default function Pricing() {
  return (
    <section id="pricing" className="py-24 sm:py-32 px-6">
      <div className="max-w-6xl mx-auto">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: '-80px' }}
          className="text-center mb-16"
        >
          <p className="text-xs text-indigo-400/80 font-medium tracking-widest uppercase mb-3">Pricing</p>
          <h2 className="text-3xl sm:text-5xl font-bold tracking-tight gradient-text mb-4">
            Simple, transparent pricing.
          </h2>
          <p className="text-base text-white/30 max-w-md mx-auto">
            Start free. Upgrade when you're ready.
          </p>
        </motion.div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
          {tiers.map((tier, i) => (
            <motion.div
              key={tier.name}
              initial={{ opacity: 0, y: 30 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: '-50px' }}
              transition={{ delay: i * 0.1 }}
              className={`relative flex flex-col rounded-2xl ${
                tier.highlight
                  ? 'pricing-highlight bg-white/[0.03] border border-indigo-500/20'
                  : 'glass-card'
              } p-7 sm:p-8`}
            >
              {tier.highlight && (
                <div className="absolute -top-3 left-1/2 -translate-x-1/2 px-3 py-0.5 rounded-full text-[10px] font-semibold tracking-wider uppercase bg-gradient-to-r from-indigo-500 to-violet-500 text-white">
                  Most Popular
                </div>
              )}

              {/* Header */}
              <div className="mb-6">
                <div className="flex items-center gap-2 mb-3">
                  <div className={`w-8 h-8 rounded-lg ${
                    tier.highlight ? 'bg-indigo-500/20' : 'bg-white/[0.04]'
                  } flex items-center justify-center`}>
                    <tier.icon size={16} className={tier.highlight ? 'text-indigo-400' : 'text-white/40'} />
                  </div>
                  <span className="text-sm font-semibold tracking-tight">{tier.name}</span>
                </div>
                <div className="flex items-baseline gap-1 mb-1">
                  <span className="text-3xl font-extrabold tracking-tight">{tier.price}</span>
                  <span className="text-xs text-white/30">{tier.period}</span>
                </div>
                <p className="text-xs text-white/30">{tier.description}</p>
              </div>

              {/* Features */}
              <div className="flex-1 space-y-2.5 mb-7">
                {tier.features.map((f) => (
                  <div key={f.text} className="flex items-start gap-2.5">
                    {f.included ? (
                      <Check size={14} className="text-emerald-400/80 mt-0.5 flex-shrink-0" />
                    ) : (
                      <X size={14} className="text-white/15 mt-0.5 flex-shrink-0" />
                    )}
                    <span className={`text-xs leading-relaxed ${f.included ? 'text-white/55' : 'text-white/20'}`}>
                      {f.text}
                    </span>
                  </div>
                ))}
              </div>

              {/* CTA */}
              <button
                className={`w-full py-3 rounded-xl text-sm font-semibold transition-all duration-300 ${
                  tier.highlight
                    ? 'bg-gradient-to-r from-indigo-500 to-violet-500 text-white hover:brightness-110 shadow-lg shadow-indigo-500/20'
                    : 'bg-white/[0.04] border border-white/[0.08] text-white/70 hover:bg-white/[0.07] hover:text-white'
                }`}
              >
                {tier.price === 'Free' ? 'Start Free Trial' : `Get ${tier.name}`}
              </button>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
