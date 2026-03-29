import { motion } from 'framer-motion';
import { Shield, RefreshCcw, Monitor, Package, Globe, Lock, Timer, Bell, Palette, Keyboard } from 'lucide-react';

const features = [
  {
    icon: Globe,
    title: 'Any Website',
    description: 'WordPress, React, Vue, static HTML — paste any URL and convert it to a native desktop app instantly.',
    span: 'sm:col-span-2',
    gradient: 'from-indigo-500/15',
    iconColor: 'text-indigo-400',
  },
  {
    icon: Shield,
    title: 'Screenshot Protection',
    description: 'OS-level content protection blocks screen recording and snipping tools.',
    span: 'sm:col-span-1',
    gradient: 'from-emerald-500/15',
    iconColor: 'text-emerald-400',
  },
  {
    icon: Lock,
    title: 'DevTools Disabled',
    description: 'DevTools are intercepted and blocked to protect your proprietary content.',
    span: 'sm:col-span-1',
    gradient: 'from-amber-500/15',
    iconColor: 'text-amber-400',
  },
  {
    icon: Package,
    title: 'Cross-Platform',
    description: 'Generate .exe for Windows, .dmg for Mac, and AppImage for Linux — from a single config.',
    span: 'sm:col-span-1',
    gradient: 'from-violet-500/15',
    iconColor: 'text-violet-400',
  },
  {
    icon: RefreshCcw,
    title: 'Auto Updates',
    description: 'Push updates seamlessly. Your users always get the latest version automatically.',
    span: 'sm:col-span-1',
    gradient: 'from-cyan-500/15',
    iconColor: 'text-cyan-400',
  },
  {
    icon: Monitor,
    title: 'Native Experience',
    description: 'Custom title bar, system tray, hidden menus — feels truly native, not a browser wrapper.',
    span: 'sm:col-span-2',
    gradient: 'from-pink-500/15',
    iconColor: 'text-pink-400',
  },
  {
    icon: Timer,
    title: 'License & Expiry',
    description: 'Set expiry dates, build limits, and tier-based access control for your desktop apps.',
    span: 'sm:col-span-1',
    gradient: 'from-orange-500/15',
    iconColor: 'text-orange-400',
  },
  {
    icon: Bell,
    title: 'Notifications',
    description: 'Native OS notifications keep your users engaged with real-time alerts.',
    span: 'sm:col-span-1',
    gradient: 'from-blue-500/15',
    iconColor: 'text-blue-400',
  },
  {
    icon: Palette,
    title: 'Dark/Light Sync',
    description: 'Automatically match the user\'s desktop theme for a seamless visual experience.',
    span: 'sm:col-span-1',
    gradient: 'from-fuchsia-500/15',
    iconColor: 'text-fuchsia-400',
  },
  {
    icon: Keyboard,
    title: 'Global Hotkeys',
    description: 'Register system-wide keyboard shortcuts for quick access to your app\'s features.',
    span: 'sm:col-span-1',
    gradient: 'from-teal-500/15',
    iconColor: 'text-teal-400',
  },
];

const container = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.07 } },
};

const item = {
  hidden: { opacity: 0, y: 20 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { type: 'spring', stiffness: 100, damping: 20 },
  },
};

export default function BentoGrid() {
  return (
    <section id="features" className="py-24 sm:py-32 px-6">
      <div className="max-w-6xl mx-auto">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: '-80px' }}
          className="text-center mb-16"
        >
          <p className="text-xs text-indigo-400/80 font-medium tracking-widest uppercase mb-3">Features</p>
          <h2 className="text-3xl sm:text-5xl font-bold tracking-tight gradient-text mb-4">
            Everything you need.
          </h2>
          <p className="text-base text-white/30 max-w-xl mx-auto">
            Enterprise-grade features packed into a beautifully simple interface.
          </p>
        </motion.div>

        <motion.div
          variants={container}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: '-30px' }}
          className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4"
        >
          {features.map((feature) => (
            <motion.div
              key={feature.title}
              variants={item}
              className={`glass-card p-6 sm:p-7 ${feature.span} lg:${feature.span.replace('sm:', '')} group card-glow relative overflow-hidden`}
            >
              {/* Hover gradient */}
              <div className={`absolute inset-0 rounded-[1.25rem] bg-gradient-to-br ${feature.gradient} to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500`} />

              <div className="relative">
                <div className="w-10 h-10 rounded-xl bg-white/[0.04] border border-white/5 flex items-center justify-center mb-4 group-hover:border-white/10 transition-colors">
                  <feature.icon size={18} className={`${feature.iconColor} transition-transform group-hover:scale-110 duration-300`} />
                </div>
                <h3 className="text-base font-semibold mb-1.5 tracking-tight">{feature.title}</h3>
                <p className="text-sm text-white/35 leading-relaxed">{feature.description}</p>
              </div>
            </motion.div>
          ))}
        </motion.div>
      </div>
    </section>
  );
}
