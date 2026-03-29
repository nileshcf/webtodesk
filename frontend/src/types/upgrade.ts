// Version upgrade types for WebToDesk frontend

export interface AppVersion {
  version: string;
  releaseDate: string;
  changelog: string[];
  downloadUrl?: string;
  upgradeUrl?: string;
  size: string;
  minLicenseTier: LicenseTier;
  breakingChanges: boolean;
}

export interface VersionUpgradeRequest {
  projectId: string;
  fromVersion: string;
  toVersion: string;
  preserveLicense: boolean;
  createBackup: boolean;
}

export interface VersionUpgradeResponse {
  upgradeId: string;
  status: 'QUEUED' | 'BUILDING' | 'COMPLETED' | 'FAILED';
  estimatedTime: number;
  downloadUrl?: string;
  error?: string;
}

export interface UpgradeDialog {
  currentVersion: string;
  availableVersion: string;
  changelog: string[];
  licensePreservation: boolean;
  upgradeSize: string;
  estimatedTime: string;
  breakingChanges: boolean;
  requiresPayment: boolean;
}

export interface VersionHistory {
  projectId: string;
  versions: AppVersion[];
  currentVersion: string;
  availableUpdates: AppVersion[];
  upgradePath: VersionUpgradePath[];
}

export interface VersionUpgradePath {
  from: string;
  to: string;
  steps: string[];
  estimatedTime: number;
  requiresLicenseUpgrade: boolean;
}

export interface LicenseMigration {
  licenseId: string;
  fromVersion: string;
  toVersion: string;
  migratedAt: string;
  successful: boolean;
  metadataPreserved: string[];
  issues?: string[];
}

export interface AutoUpgradeSettings {
  enabled: boolean;
  channel: 'stable' | 'beta' | 'alpha';
  autoInstall: boolean;
  createBackups: boolean;
  notifyBeforeUpgrade: boolean;
  upgradeWindow: {
    startHour: number;
    endHour: number;
    timezone: string;
  };
}

export interface RollbackCapability {
  available: boolean;
  maxRollbackVersions: number;
  rollbackWindow: string; // e.g., "30 days"
  requiresLicenseTier: LicenseTier;
}

export interface UpgradeProgress {
  upgradeId: string;
  stage: 'PREPARING' | 'BUILDING' | 'TESTING' | 'PACKAGING' | 'UPLOADING' | 'COMPLETED' | 'FAILED';
  progress: number; // 0-100
  message: string;
  estimatedTimeRemaining: number;
  artifacts: UpgradeArtifact[];
}

export interface UpgradeArtifact {
  os: TargetOS;
  fileType: FileType;
  url: string;
  size: string;
  checksum: string;
}

export interface VersionComparison {
  current: AppVersion;
  available: AppVersion;
  differences: VersionDifference[];
  upgradeRequired: boolean;
  upgradeRecommended: boolean;
}

export interface VersionDifference {
  type: 'feature' | 'bugfix' | 'security' | 'breaking' | 'performance';
  description: string;
  impact: 'low' | 'medium' | 'high' | 'critical';
  affectedModules: string[];
}

// Import types from other files
import { LicenseTier } from './license';
import { TargetOS, FileType } from './build';
