import { useRef } from 'react';
import { motion, useScroll, useTransform } from 'framer-motion';

const words = 'Paste a URL. Get a secure, native desktop app with screenshot protection, automatic updates, and cross‑platform builds. No code changes required.'.split(' ');

export default function ScrollText() {
  const ref = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ['start 0.8', 'end 0.4'],
  });

  return (
    <section ref={ref} className="py-32 px-6">
      <div className="max-w-4xl mx-auto">
        <p className="text-3xl sm:text-4xl lg:text-5xl font-semibold leading-snug tracking-tight">
          {words.map((word, i) => {
            const start = i / words.length;
            const end = start + 1 / words.length;
            return <Word key={i} word={word} range={[start, end]} progress={scrollYProgress} />;
          })}
        </p>
      </div>
    </section>
  );
}

function Word({ word, range, progress }: { word: string; range: [number, number]; progress: any }) {
  const opacity = useTransform(progress, range, [0.1, 1]);
  return (
    <motion.span style={{ opacity }} className="inline-block mr-[0.3em] will-change-[opacity]">
      {word}
    </motion.span>
  );
}
