import { motion } from 'framer-motion';
import { Shield, RefreshCcw, Monitor, Package, Globe, Lock } from 'lucide-react';

const features = [
  {
    icon: Globe,
    title: 'Any Website',
    description: 'Paste any URL — WordPress, React, static HTML — and convert it to a native desktop app instantly.',
    span: 'col-span-2',
  },
  {
    icon: Shield,
    title: 'Screenshot Protection',
    description: 'OS-level content protection blocks screen recording and snipping tools.',
    span: 'col-span-1',
  },
  {
    icon: Lock,
    title: 'No DevTools',
    description: 'DevTools are intercepted and disabled to protect proprietary content.',
    span: 'col-span-1',
  },
  {
    icon: Package,
    title: 'Cross-Platform Builds',
    description: 'Generate .exe for Windows, .dmg for Mac, and AppImage for Linux from a single config.',
    span: 'col-span-1',
  },
  {
    icon: RefreshCcw,
    title: 'Version Management',
    description: 'Track versions, push updates, and manage your desktop apps from one dashboard.',
    span: 'col-span-1',
  },
  {
    icon: Monitor,
    title: 'Native Experience',
    description: 'Custom title bar, hidden menus, and system tray — feels truly native, not a browser wrapper.',
    span: 'col-span-2',
  },
];

const container = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.1 } },
};

const item = {
  hidden: { opacity: 0, y: 30 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { type: 'spring', stiffness: 80, damping: 20 },
  },
};

export default function BentoGrid() {
  return (
    <section className="py-32 px-6">
      <div className="max-w-6xl mx-auto">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: '-100px' }}
          transition={{ duration: 0.6 }}
          className="text-center mb-16"
        >
          <h2 className="text-4xl sm:text-5xl font-bold tracking-tight gradient-text mb-4">
            Everything you need.
          </h2>
          <p className="text-lg text-white/40 max-w-xl mx-auto font-light">
            Enterprise-grade features, beautifully simple interface.
          </p>
        </motion.div>

        <motion.div
          variants={container}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: '-50px' }}
          className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4"
        >
          {features.map((feature) => (
            <motion.div
              key={feature.title}
              variants={item}
              className={`glass-card p-6 sm:p-8 ${feature.span.replace('col-span-2', 'sm:col-span-2').replace('col-span-1', 'sm:col-span-1')} lg:${feature.span}`}
            >
              <div className="w-10 h-10 rounded-xl bg-white/5 flex items-center justify-center mb-5">
                <feature.icon size={20} className="text-accent-blue" />
              </div>
              <h3 className="text-lg font-semibold mb-2">{feature.title}</h3>
              <p className="text-sm text-white/40 leading-relaxed">{feature.description}</p>
            </motion.div>
          ))}
        </motion.div>
      </div>
    </section>
  );
}
