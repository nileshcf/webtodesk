// Build configuration types for WebToDesk frontend

export enum TargetOS {
  WINDOWS = 'WINDOWS',
  LINUX = 'LINUX',
  MACOS = 'MACOS'
}

export enum FileType {
  WINDOWS_EXE = 'WINDOWS_EXE',
  WINDOWS_MSI = 'WINDOWS_MSI',
  LINUX_APPIMAGE = 'LINUX_APPIMAGE',
  LINUX_DEB = 'LINUX_DEB',
  LINUX_RPM = 'LINUX_RPM',
  MACOS_DMG = 'MACOS_DMG',
  MACOS_ZIP = 'MACOS_ZIP'
}

export enum BuildPriority {
  NORMAL = 'NORMAL',
  PRIORITY = 'PRIORITY'
}

export interface BuildFlags {
  targetOS: TargetOS;
  priority: BuildPriority;
  fileType: FileType;
  crossPlatform: boolean;
  osFileMappings: Record<TargetOS, FileType>;
}

export interface BuildConfigForm {
  targetOS: TargetOS[];
  fileType: Record<TargetOS, FileType>;
  crossPlatform: boolean;
  priorityBuild: boolean;
}

export interface BuildRequest {
  projectId: string;
  buildFlags: BuildFlags;
  featureConfig: FeatureConfig;
}

export interface BuildResult {
  success: boolean;
  targetOS: TargetOS;
  fileType: FileType;
  artifactUrl?: string;
  downloadUrl?: string;
  buildTime: number;
  queuePosition?: number;
  error?: string;
}

export interface BuildStatusResponse {
  projectId: string;
  projectName: string;
  status: 'DRAFT' | 'READY' | 'BUILDING' | 'FAILED' | 'LICENSE_EXPIRED';
  buildError: string | null;
  downloadAvailable: boolean;
  downloadUrl: string | null;
  updatedAt: string | null;
  buildProgress?: BuildProgress;
  queuePosition?: number;
  estimatedTimeRemaining?: number;
}

export interface BuildProgress {
  stage: 'VALIDATING_ENV' | 'PREPARING' | 'WRITING_FILES' | 'INSTALLING' | 'BUILDING' | 'UPLOADING_R2' | 'COMPLETE' | 'FAILED';
  progress: number; // 0-100
  message: string;
  timestamp: string;
  queuePosition?: number;
}

export interface CrossPlatformBuildRequest {
  projectId: string;
  targetOSes: TargetOS[];
  fileTypes: Record<TargetOS, FileType>;
  priority: boolean;
}

export interface CrossPlatformBuildResult {
  results: Record<TargetOS, BuildResult>;
  overallSuccess: boolean;
  totalBuildTime: number;
  successfulBuilds: number;
  failedBuilds: number;
}

export interface BuildMetrics {
  buildsByTier: Record<LicenseTier, BuildMetricsByTier>;
  buildsByOS: Record<TargetOS, BuildMetricsByOS>;
  queueStats: QueueStats;
  monthlyUsage: MonthlyBuildUsage[];
}

export interface BuildMetricsByTier {
  totalBuilds: number;
  successfulBuilds: number;
  averageBuildTime: number;
  averageQueueTime: number;
}

export interface BuildMetricsByOS {
  totalBuilds: number;
  successfulBuilds: number;
  averageBuildTime: number;
  popularFileTypes: Record<FileType, number>;
}

export interface QueueStats {
  normalQueueLength: number;
  priorityQueueLength: number;
  averageWaitTime: Record<BuildPriority, number>;
  maxCapacity: Record<BuildPriority, number>;
}

export interface MonthlyBuildUsage {
  month: string;
  buildsUsed: number;
  buildsAllowed: number;
  successRate: number;
}

export interface VersionInfo {
  currentVersion: string;
  availableVersion?: string;
  changelog?: string[];
  upgradeAvailable: boolean;
  upgradeSize?: string;
  estimatedTime?: string;
}

// Import LicenseTier from license.ts for type compatibility
import { LicenseTier } from './license';
