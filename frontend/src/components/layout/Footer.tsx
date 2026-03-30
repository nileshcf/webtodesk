import { Link } from 'react-router-dom';
import { Monitor, Github, Twitter } from 'lucide-react';

const footerLinks = {
  Product: [
    { label: 'Features', href: '#features' },
    { label: 'Pricing', href: '#pricing' },
    { label: 'How It Works', href: '#how-it-works' },
    { label: 'Dashboard', href: '/dashboard' },
  ],
  Company: [
    { label: 'About', href: '#' },
    { label: 'Blog', href: '#' },
    { label: 'Contact', href: '#' },
  ],
  Legal: [
    { label: 'Privacy Policy', href: '#' },
    { label: 'Terms of Service', href: '#' },
  ],
};

export default function Footer() {
  return (
    <footer className="border-t border-white/[0.04] pt-16 pb-8 px-6">
      <div className="max-w-6xl mx-auto">
        {/* Top section */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-10 sm:gap-8 mb-14">
          {/* Brand column */}
          <div className="col-span-2 sm:col-span-1">
            <Link to="/" className="flex items-center gap-2 mb-4">
              <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-indigo-500 to-violet-500 flex items-center justify-center">
                <Monitor size={13} className="text-white" />
              </div>
              <span className="text-sm font-semibold tracking-tight">WebToDesk</span>
            </Link>
            <p className="text-xs text-white/25 leading-relaxed max-w-[200px]">
              Transform any website into a secure, native desktop application.
            </p>
            <div className="flex items-center gap-3 mt-5">
              <a
                href="https://github.com"
                target="_blank"
                rel="noopener noreferrer"
                className="w-8 h-8 rounded-lg bg-white/[0.03] border border-white/5 flex items-center justify-center text-white/30 hover:text-white/60 hover:bg-white/[0.06] transition-all"
              >
                <Github size={14} />
              </a>
              <a
                href="https://twitter.com"
                target="_blank"
                rel="noopener noreferrer"
                className="w-8 h-8 rounded-lg bg-white/[0.03] border border-white/5 flex items-center justify-center text-white/30 hover:text-white/60 hover:bg-white/[0.06] transition-all"
              >
                <Twitter size={14} />
              </a>
            </div>
          </div>

          {/* Link columns */}
          {Object.entries(footerLinks).map(([title, links]) => (
            <div key={title}>
              <h4 className="text-xs font-semibold text-white/50 tracking-wider uppercase mb-4">{title}</h4>
              <ul className="space-y-2.5">
                {links.map(({ label, href }) => (
                  <li key={label}>
                    {href.startsWith('/') ? (
                      <Link
                        to={href}
                        className="text-xs text-white/25 hover:text-white/50 transition-colors duration-300"
                      >
                        {label}
                      </Link>
                    ) : (
                      <a
                        href={href}
                        className="text-xs text-white/25 hover:text-white/50 transition-colors duration-300"
                      >
                        {label}
                      </a>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        {/* Bottom bar */}
        <div className="section-divider mb-6" />
        <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
          <p className="text-[11px] text-white/15">
            © {new Date().getFullYear()} WebToDesk. All rights reserved.
          </p>
          <p className="text-[11px] text-white/15">
            Built with precision for creators everywhere.
          </p>
        </div>
      </div>
    </footer>
  );
}
