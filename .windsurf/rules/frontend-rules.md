# ─────────────────────────────────────────────────────────────────────────────
# FRONTEND — Local Rules
# Stack: React 18 · TypeScript · Vite · Tailwind CSS
# Port: 7860 (prod via Nginx) | 5173 (local dev)
# ─────────────────────────────────────────────────────────────────────────────

## FRAMEWORK & TOOLING
- React 18 with functional components only — no class components
- TypeScript strict mode — no implicit any, no ts-ignore without comment
- Vite for bundling — never use CRA or webpack configs
- Tailwind CSS for all styling — no inline styles, no CSS modules, no styled-components
- @microsoft/fetch-event-source for SSE streams with Bearer token auth

## DESIGN SYSTEM
- Theme: dark background (#0a0a0a base), glass-morphism panels, animated gradients
- Primary accent: blue-violet gradient (from-blue-500 to-violet-600)
- Glass panels: bg-white/5 backdrop-blur-xl border border-white/10
- Text hierarchy: text-white (primary), text-white/70 (secondary), text-white/40 (muted)
- Micro-animations: transition-all duration-200, hover:scale-[1.02], hover:brightness-110
- Never use light theme — this is a dark SaaS product
- Border radius: rounded-xl for cards, rounded-lg for buttons, rounded-full for badges

## COMPONENT RULES
- One component per file, filename = ComponentName.tsx (PascalCase)
- Export: default export for pages, named export for shared components
- Props: define explicit TypeScript interface for every component's props
- No prop drilling beyond 2 levels — use Context or lift state
- Never use <form> tags — use onClick/onChange event handlers on divs/buttons

## KEY COMPONENTS & THEIR CONTRACT
- ProjectWizard: 3-step wizard (BasicInfo → ModuleSelect → Review), props: userTier, initialData, onSubmit, onCancel, submitLabel
- BuildProgress: SSE-connected progress bar, props: projectId, onComplete
- LicenseBadge: tier badge, props: tier, compact?, expiresAt?
- FeatureToggle: blur locked state + upgrade overlay, props: feature, userTier, children
- UpgradeModal: 3-plan cards (Starter/Pro/Lifetime), props: isOpen, onClose, currentTier
- BuildDashboard: main build management view, connects to SSE stream

## API INTEGRATION RULES
- Base URL pattern: /user/** (user-service), /conversion/** (conversion-service)
- Always send Authorization: Bearer <token> header
- Always send X-User-Email header on conversion service requests
- SSE endpoint: GET /conversion/conversions/{id}/build/stream
- Build trigger: POST /conversion/build/trigger
- Module list: GET /conversion/build/modules?tier={tier}
- License dashboard: GET /conversion/license/dashboard

## STATE MANAGEMENT
- Auth: React Context (AuthContext) — JWT token + user email
- Build state: local useState within BuildDashboard — no global store
- License state: fetched per-page, cached with useMemo (60s TTL)
- Module selection: local state in ProjectWizard steps

## ERROR HANDLING
- 402 responses: show UpgradeModal automatically
- 401 responses: redirect to login, clear auth context
- 404 responses: show inline error message, do not throw
- 500 responses: show generic error toast — never expose raw error message to user
- SSE errors: attempt reconnect 3x with exponential backoff, then show "Build failed" state

## BUILD & LINT
- Build command: npm run build
- Dev command: npm run dev (port 5173 locally, 7860 in production via Nginx)
- TypeScript check: npx tsc --noEmit
- Never skip TypeScript errors with @ts-ignore without a comment explaining why

## TAILWIND PATTERNS
- Card: className="bg-white/5 backdrop-blur-xl border border-white/10 rounded-xl p-6"
- Primary button: className="bg-gradient-to-r from-blue-500 to-violet-600 text-white rounded-lg px-4 py-2 hover:brightness-110 transition-all"
- Ghost button: className="border border-white/20 text-white/70 rounded-lg px-4 py-2 hover:bg-white/5 transition-all"
- Input: className="bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-white placeholder:text-white/30 focus:outline-none focus:ring-2 focus:ring-blue-500"
- Badge (TRIAL): className="bg-yellow-500/20 text-yellow-400 border border-yellow-500/30 rounded-full px-2 py-0.5 text-xs"
- Badge (PRO): className="bg-blue-500/20 text-blue-400 border border-blue-500/30 rounded-full px-2 py-0.5 text-xs"

## WHAT NOT TO DO
- Do NOT add Redux, Zustand, or Jotai — hooks + context are sufficient
- Do NOT use React Router v5 patterns — use v6 (useNavigate, Outlet, etc.)
- Do NOT add CSS animations via keyframes in CSS files — use Tailwind animate-* or inline transition
- Do NOT use axios — use native fetch (or fetch-event-source for SSE)
- Do NOT create separate .css files — Tailwind only
- Do NOT use any of these Tailwind classes that need the compiler: arbitrary values like w-[347px] in new components — use standard scale (w-80, w-96, etc.)