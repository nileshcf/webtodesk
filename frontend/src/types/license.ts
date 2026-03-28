// License-related TypeScript types for WebToDesk frontend

export enum LicenseTier {
  TRIAL = 'TRIAL',
  STARTER = 'STARTER',
  PRO = 'PRO',
  LIFETIME = 'LIFETIME'
}

export interface LicenseInfo {
  tier: LicenseTier;
  licenseExpiresAt: string;
  buildsUsed: number;
  buildsAllowed: number;
  activeApps: number;
  maxActiveApps: number;
  licenseId: string;
  issuedAt: string;
  lastValidatedAt: string;
  migrationHistory: LicenseMigration[];
}

export interface LicenseMigration {
  fromVersion: string;
  toVersion: string;
  migratedAt: string;
  successful: boolean;
}

export interface UpgradeOption {
  tier: LicenseTier;
  price: number;
  currency: string;
  billingCycle: 'monthly' | 'yearly' | 'lifetime';
  features: string[];
  popular?: boolean;
  current?: boolean;
}

export interface LicenseDashboard {
  currentLicense: LicenseInfo;
  upgradeOptions: UpgradeOption[];
  usageStats: LicenseUsageStats;
  expiryWarning?: {
    daysRemaining: number;
    warningLevel: 'info' | 'warning' | 'critical';
  };
}

export interface LicenseUsageStats {
  buildsThisMonth: number;
  avgBuildTime: number;
  successRate: number;
  queueWaitTime: number;
  activeProjects: number;
}

export interface LicenseValidationResponse {
  valid: boolean;
  tier: LicenseTier;
  restrictions: LicenseRestrictions;
  canBuild: boolean;
  message?: string;
}

export interface LicenseRestrictions {
  maxBuilds: number;
  maxActiveApps: number;
  allowedFeatures: string[];
  blockedFeatures: string[];
  priorityQueue: boolean;
  crossPlatformBuilds: boolean;
}
