import { useState } from 'react';
import { LicenseTier, UpgradeOption } from '../types/license';
import { licenseApi } from '../services/licenseApi';

interface UpgradeModalProps {
  currentTier: LicenseTier;
  highlightTier?: LicenseTier;
  featureId?: string;
  onClose: () => void;
  onUpgradeComplete?: (newTier: LicenseTier) => void;
}

const TIER_RANK: Record<LicenseTier, number> = {
  [LicenseTier.TRIAL]: 0,
  [LicenseTier.STARTER]: 1,
  [LicenseTier.PRO]: 2,
  [LicenseTier.LIFETIME]: 3,
};

const TIER_COLORS: Record<LicenseTier, string> = {
  [LicenseTier.TRIAL]: 'border-gray-300 bg-gray-50',
  [LicenseTier.STARTER]: 'border-blue-300 bg-blue-50',
  [LicenseTier.PRO]: 'border-purple-400 bg-purple-50',
  [LicenseTier.LIFETIME]: 'border-amber-400 bg-amber-50',
};

const TIER_BUTTON_COLORS: Record<LicenseTier, string> = {
  [LicenseTier.TRIAL]: 'bg-gray-600 hover:bg-gray-700',
  [LicenseTier.STARTER]: 'bg-blue-600 hover:bg-blue-700',
  [LicenseTier.PRO]: 'bg-purple-600 hover:bg-purple-700',
  [LicenseTier.LIFETIME]: 'bg-amber-600 hover:bg-amber-700',
};

const TIER_ICONS: Record<LicenseTier, string> = {
  [LicenseTier.TRIAL]: '🔓',
  [LicenseTier.STARTER]: '⚡',
  [LicenseTier.PRO]: '🚀',
  [LicenseTier.LIFETIME]: '♾️',
};

export function UpgradeModal({
  currentTier,
  highlightTier,
  onClose,
  onUpgradeComplete,
}: UpgradeModalProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const upgradePlans: UpgradeOption[] = [
    {
      tier: LicenseTier.STARTER,
      price: 9.99,
      currency: 'USD',
      billingCycle: 'monthly',
      features: ['120 builds/month', '5 active apps', 'Basic modules', 'Email support'],
      popular: false,
      current: currentTier === LicenseTier.STARTER,
    },
    {
      tier: LicenseTier.PRO,
      price: 29.99,
      currency: 'USD',
      billingCycle: 'monthly',
      features: [
        '3,000 builds/month',
        '25 active apps',
        'All modules',
        'Priority queue',
        'Cross-platform builds',
        'Priority support',
      ],
      popular: true,
      current: currentTier === LicenseTier.PRO,
    },
    {
      tier: LicenseTier.LIFETIME,
      price: 299,
      currency: 'USD',
      billingCycle: 'lifetime',
      features: [
        'Unlimited builds',
        'Unlimited apps',
        'All modules',
        'Priority queue',
        'Cross-platform builds',
        'All future features',
      ],
      popular: false,
      current: currentTier === LicenseTier.LIFETIME,
    },
  ];

  const handleUpgrade = async (plan: UpgradeOption) => {
    if (plan.current || TIER_RANK[plan.tier] <= TIER_RANK[currentTier]) return;
    setLoading(true);
    setError(null);
    try {
      const { upgradeUrl } = await licenseApi.initiateUpgrade(plan.tier, plan.billingCycle);
      window.open(upgradeUrl, '_blank');
      onClose();
    } catch (err: any) {
      setError(err.message ?? 'Failed to initiate upgrade');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
      <div className="relative w-full max-w-3xl bg-white rounded-2xl shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="bg-gradient-to-r from-indigo-600 to-purple-600 px-6 py-5 text-white">
          <button
            onClick={onClose}
            className="absolute top-4 right-4 text-white/70 hover:text-white transition-colors text-2xl leading-none"
          >
            ×
          </button>
          <h2 className="text-2xl font-bold">Upgrade Your Plan</h2>
          <p className="mt-1 text-indigo-100 text-sm">
            Unlock more builds, modules, and features for your desktop apps.
          </p>
        </div>

        {/* Plans */}
        <div className="p-6 grid grid-cols-1 sm:grid-cols-3 gap-4">
          {upgradePlans.map((plan) => {
            const isHighlighted = plan.tier === highlightTier;
            const isCurrent = plan.current;
            const isDowngrade = TIER_RANK[plan.tier] < TIER_RANK[currentTier];
            const colorClasses = TIER_COLORS[plan.tier];
            const buttonClasses = TIER_BUTTON_COLORS[plan.tier];

            return (
              <div
                key={plan.tier}
                className={`relative flex flex-col rounded-xl border-2 p-4 transition-shadow ${colorClasses} ${
                  isHighlighted ? 'ring-2 ring-indigo-500 shadow-lg' : 'shadow-sm'
                }`}
              >
                {plan.popular && (
                  <div className="absolute -top-3 left-1/2 -translate-x-1/2">
                    <span className="bg-indigo-600 text-white text-xs font-bold px-3 py-0.5 rounded-full">
                      Most Popular
                    </span>
                  </div>
                )}

                <div className="mb-3">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-xl">{TIER_ICONS[plan.tier]}</span>
                    <span className="font-bold text-gray-800">{plan.tier}</span>
                  </div>
                  <div className="flex items-baseline gap-1">
                    <span className="text-2xl font-bold text-gray-900">${plan.price}</span>
                    <span className="text-gray-500 text-sm">
                      /{plan.billingCycle === 'lifetime' ? 'one-time' : plan.billingCycle}
                    </span>
                  </div>
                </div>

                <ul className="flex-1 space-y-1.5 mb-4">
                  {plan.features.map((f) => (
                    <li key={f} className="flex items-start gap-1.5 text-sm text-gray-700">
                      <span className="text-green-500 mt-0.5">✓</span>
                      <span>{f}</span>
                    </li>
                  ))}
                </ul>

                <button
                  onClick={() => handleUpgrade(plan)}
                  disabled={loading || isCurrent || isDowngrade}
                  className={`w-full py-2 rounded-lg text-white text-sm font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed ${buttonClasses}`}
                >
                  {isCurrent ? 'Current Plan' : isDowngrade ? 'Downgrade' : loading ? 'Redirecting…' : 'Upgrade Now'}
                </button>
              </div>
            );
          })}
        </div>

        {error && (
          <p className="px-6 pb-4 text-sm text-red-600 text-center">{error}</p>
        )}
      </div>
    </div>
  );
}
