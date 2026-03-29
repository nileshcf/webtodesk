import { LicenseTier } from '../types/license';

interface LicenseBadgeProps {
  tier: LicenseTier;
  daysUntilExpiry?: number;
  compact?: boolean;
}

const TIER_CONFIG: Record<LicenseTier, { label: string; classes: string; icon: string }> = {
  [LicenseTier.TRIAL]: {
    label: 'Trial',
    classes: 'bg-gray-100 text-gray-700 border-gray-300',
    icon: '🔓',
  },
  [LicenseTier.STARTER]: {
    label: 'Starter',
    classes: 'bg-blue-100 text-blue-700 border-blue-300',
    icon: '⚡',
  },
  [LicenseTier.PRO]: {
    label: 'Pro',
    classes: 'bg-purple-100 text-purple-700 border-purple-300',
    icon: '🚀',
  },
  [LicenseTier.LIFETIME]: {
    label: 'Lifetime',
    classes: 'bg-amber-100 text-amber-700 border-amber-300',
    icon: '♾️',
  },
};

export function LicenseBadge({ tier, daysUntilExpiry, compact = false }: LicenseBadgeProps) {
  const config = TIER_CONFIG[tier];
  const isExpiringSoon = daysUntilExpiry !== undefined && daysUntilExpiry <= 7;

  if (compact) {
    return (
      <span
        className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium border ${config.classes}`}
      >
        <span>{config.icon}</span>
        <span>{config.label}</span>
      </span>
    );
  }

  return (
    <div className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm font-medium border ${config.classes}`}>
      <span>{config.icon}</span>
      <span>{config.label}</span>
      {isExpiringSoon && daysUntilExpiry !== undefined && (
        <span className="ml-1 text-xs font-normal text-orange-600 bg-orange-100 px-1.5 py-0.5 rounded-full">
          {daysUntilExpiry === 0 ? 'Expires today' : `${daysUntilExpiry}d left`}
        </span>
      )}
    </div>
  );
}
