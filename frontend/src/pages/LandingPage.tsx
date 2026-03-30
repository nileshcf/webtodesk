import Hero from '../components/sections/Hero';
import Stats from '../components/sections/Stats';
import BentoGrid from '../components/sections/BentoGrid';
import HowItWorks from '../components/sections/HowItWorks';
import ScrollText from '../components/sections/ScrollText';
import Pricing from '../components/sections/Pricing';
import CTASection from '../components/sections/CTASection';

export default function LandingPage() {
  return (
    <>
      <Hero />
      <Stats />
      <BentoGrid />
      <HowItWorks />
      <ScrollText />
      <Pricing />
      <CTASection />
    </>
  );
}
