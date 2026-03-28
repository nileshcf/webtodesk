import { ReactNode, useState } from 'react';
import { LicenseTier } from '../types/license';
import { UpgradeModal } from './UpgradeModal';

interface FeatureToggleProps {
  featureId: string;
  requiredTier: LicenseTier;
  currentTier: LicenseTier;
  children: ReactNode;
  upgradeMessage?: string;
}

const TIER_RANK: Record<LicenseTier, number> = {
  [LicenseTier.TRIAL]: 0,
  [LicenseTier.STARTER]: 1,
  [LicenseTier.PRO]: 2,
  [LicenseTier.LIFETIME]: 3,
};

const TIER_LABELS: Record<LicenseTier, string> = {
  [LicenseTier.TRIAL]: 'Trial',
  [LicenseTier.STARTER]: 'Starter',
  [LicenseTier.PRO]: 'Pro',
  [LicenseTier.LIFETIME]: 'Lifetime',
};

export function FeatureToggle({
  featureId,
  requiredTier,
  currentTier,
  children,
  upgradeMessage,
}: FeatureToggleProps) {
  const [showUpgrade, setShowUpgrade] = useState(false);
  const hasAccess = TIER_RANK[currentTier] >= TIER_RANK[requiredTier];

  if (hasAccess) {
    return <>{children}</>;
  }

  return (
    <>
      <div
        className="relative cursor-pointer group"
        onClick={() => setShowUpgrade(true)}
        title={`Requires ${TIER_LABELS[requiredTier]} plan`}
      >
        {/* Blurred/dimmed locked content */}
        <div className="pointer-events-none select-none opacity-40 blur-[1px]">{children}</div>

        {/* Overlay badge */}
        <div className="absolute inset-0 flex items-center justify-center">
          <div className="flex items-center gap-2 rounded-lg bg-white/90 backdrop-blur-sm border border-gray-200 px-3 py-2 shadow-sm group-hover:shadow-md transition-shadow">
            <span className="text-amber-500">🔒</span>
            <span className="text-sm font-medium text-gray-700">
              {upgradeMessage ?? `Requires ${TIER_LABELS[requiredTier]}`}
            </span>
            <span className="text-xs text-indigo-600 font-semibold underline">Upgrade</span>
          </div>
        </div>
      </div>

      {showUpgrade && (
        <UpgradeModal
          currentTier={currentTier}
          highlightTier={requiredTier}
          featureId={featureId}
          onClose={() => setShowUpgrade(false)}
        />
      )}
    </>
  );
}
