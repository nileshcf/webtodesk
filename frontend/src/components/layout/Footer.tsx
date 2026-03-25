import { Monitor } from 'lucide-react';

export default function Footer() {
  return (
    <footer className="border-t border-white/5 py-12">
      <div className="max-w-7xl mx-auto px-6">
        <div className="flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <Monitor size={16} className="text-white/40" />
            <span className="text-sm text-white/40">WebToDesk</span>
          </div>
          <p className="text-xs text-white/30">
            © {new Date().getFullYear()} WebToDesk. Transform any website into a desktop experience.
          </p>
        </div>
      </div>
    </footer>
  );
}
