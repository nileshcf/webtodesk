import axios from 'axios';
import type {
  AppVersion,
  VersionUpgradeRequest,
  VersionUpgradeResponse,
  VersionHistory,
  UpgradeDialog,
  UpgradeProgress,
  VersionComparison,
  AutoUpgradeSettings,
  RollbackCapability
} from '../types/upgrade';
import { getAccessToken } from './api';

const VERSION_BASE = '/conversion/versions';

export const versionApi = {
  // Get version history for a project
  async getVersionHistory(projectId: string): Promise<VersionHistory> {
    const res = await axios.get<VersionHistory>(`${VERSION_BASE}/history/${projectId}`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Get available updates for a project
  async getAvailableUpdates(projectId: string): Promise<AppVersion[]> {
    const res = await axios.get<AppVersion[]>(`${VERSION_BASE}/updates/${projectId}`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Get upgrade dialog information
  async getUpgradeDialog(projectId: string, targetVersion: string): Promise<UpgradeDialog> {
    const res = await axios.get<UpgradeDialog>(`${VERSION_BASE}/upgrade-dialog/${projectId}?version=${targetVersion}`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Initiate version upgrade
  async initiateUpgrade(request: VersionUpgradeRequest): Promise<VersionUpgradeResponse> {
    const res = await axios.post<VersionUpgradeResponse>(`${VERSION_BASE}/upgrade`, request, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Get upgrade progress
  async getUpgradeProgress(upgradeId: string): Promise<UpgradeProgress> {
    const res = await axios.get<UpgradeProgress>(`${VERSION_BASE}/progress/${upgradeId}`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Subscribe to upgrade progress via SSE
  subscribeToUpgradeProgress(upgradeId: string): EventSource {
    return new EventSource(`${VERSION_BASE}/progress/${upgradeId}?token=${getAccessToken()}`);
  },

  // Compare versions
  async compareVersions(projectId: string, fromVersion: string, toVersion: string): Promise<VersionComparison> {
    const res = await axios.get<VersionComparison>(
      `${VERSION_BASE}/compare/${projectId}?from=${fromVersion}&to=${toVersion}`,
      { headers: { Authorization: `Bearer ${getAccessToken()}` } }
    );
    return res.data;
  },

  // Get auto-upgrade settings
  async getAutoUpgradeSettings(projectId: string): Promise<AutoUpgradeSettings> {
    const res = await axios.get<AutoUpgradeSettings>(`${VERSION_BASE}/auto-upgrade/${projectId}`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Update auto-upgrade settings
  async updateAutoUpgradeSettings(projectId: string, settings: Partial<AutoUpgradeSettings>): Promise<AutoUpgradeSettings> {
    const res = await axios.put<AutoUpgradeSettings>(`${VERSION_BASE}/auto-upgrade/${projectId}`, settings, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Get rollback capability
  async getRollbackCapability(projectId: string): Promise<RollbackCapability> {
    const res = await axios.get<RollbackCapability>(`${VERSION_BASE}/rollback/capability/${projectId}`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Initiate rollback
  async initiateRollback(projectId: string, targetVersion: string): Promise<VersionUpgradeResponse> {
    const res = await axios.post<VersionUpgradeResponse>(
      `${VERSION_BASE}/rollback/${projectId}`,
      { targetVersion },
      { headers: { Authorization: `Bearer ${getAccessToken()}` } }
    );
    return res.data;
  },

  // Get rollback history
  async getRollbackHistory(projectId: string): Promise<Array<{
    fromVersion: string;
    toVersion: string;
    rolledBackAt: string;
    reason: string;
    successful: boolean;
  }>> {
    const res = await axios.get(`${VERSION_BASE}/rollback/history/${projectId}`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Cancel upgrade
  async cancelUpgrade(upgradeId: string): Promise<void> {
    await axios.post(`${VERSION_BASE}/cancel/${upgradeId}`, {}, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
  },

  // Get upgrade changelog
  async getChangelog(fromVersion: string, toVersion: string): Promise<string[]> {
    const res = await axios.get<string[]>(`${VERSION_BASE}/changelog?from=${fromVersion}&to=${toVersion}`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Check if version is compatible with current license
  async checkLicenseCompatibility(projectId: string, version: string): Promise<{
    compatible: boolean;
    requiredTier?: string;
    upgradeRequired?: boolean;
    message?: string;
  }> {
    const res = await axios.get(`${VERSION_BASE}/license-compatibility/${projectId}?version=${version}`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  }
};
