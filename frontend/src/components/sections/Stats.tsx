import { useRef, useEffect, useState } from 'react';
import { motion, useInView } from 'framer-motion';

const stats = [
  { value: 500, suffix: '+', label: 'Apps Built', description: 'Desktop apps generated' },
  { value: 3, suffix: '', label: 'Platforms', description: 'Windows, macOS, Linux' },
  { value: 60, suffix: 's', prefix: '<', label: 'Build Time', description: 'Average build duration' },
  { value: 99.9, suffix: '%', label: 'Uptime', description: 'Service availability' },
];

function AnimatedCounter({ value, suffix = '', prefix = '' }: { value: number; suffix?: string; prefix?: string }) {
  const [count, setCount] = useState(0);
  const ref = useRef<HTMLSpanElement>(null);
  const isInView = useInView(ref, { once: true, margin: '-100px' });

  useEffect(() => {
    if (!isInView) return;
    const duration = 1500;
    const steps = 40;
    const increment = value / steps;
    let current = 0;
    const timer = setInterval(() => {
      current += increment;
      if (current >= value) {
        current = value;
        clearInterval(timer);
      }
      setCount(Number(current.toFixed(value % 1 !== 0 ? 1 : 0)));
    }, duration / steps);
    return () => clearInterval(timer);
  }, [isInView, value]);

  return (
    <span ref={ref} className="counter-glow">
      {prefix}{count}{suffix}
    </span>
  );
}

export default function Stats() {
  return (
    <section className="py-20 sm:py-24 px-6">
      <div className="max-w-5xl mx-auto">
        <div className="glass-card p-8 sm:p-10">
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-8 sm:gap-12">
            {stats.map((stat, i) => (
              <motion.div
                key={stat.label}
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, margin: '-50px' }}
                transition={{ delay: i * 0.1 }}
                className="text-center"
              >
                <div className="text-3xl sm:text-4xl font-extrabold tracking-tight gradient-text-blue mb-1">
                  <AnimatedCounter value={stat.value} suffix={stat.suffix} prefix={stat.prefix} />
                </div>
                <p className="text-sm font-medium text-white/60 mb-0.5">{stat.label}</p>
                <p className="text-[11px] text-white/25">{stat.description}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
