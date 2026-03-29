import axios from 'axios';
import type {
  LicenseInfo,
  LicenseDashboard,
  UpgradeOption,
  LicenseValidationResponse,
  LicenseUsageStats
} from '../types/license';
import { getAccessToken } from './api';

const LICENSE_BASE = '/conversion/license';

export const licenseApi = {
  // Get current license information
  async getCurrentLicense(): Promise<LicenseInfo> {
    const res = await axios.get<LicenseInfo>(`${LICENSE_BASE}/current`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Get license dashboard with usage stats and upgrade options
  async getLicenseDashboard(): Promise<LicenseDashboard> {
    const res = await axios.get<LicenseDashboard>(`${LICENSE_BASE}/dashboard`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Validate license for specific operations
  async validateLicense(operation: 'build' | 'download' | 'update'): Promise<LicenseValidationResponse> {
    const res = await axios.post<LicenseValidationResponse>(
      `${LICENSE_BASE}/validate`,
      { operation },
      { headers: { Authorization: `Bearer ${getAccessToken()}` } }
    );
    return res.data;
  },

  // Get available upgrade options
  async getUpgradeOptions(): Promise<UpgradeOption[]> {
    const res = await axios.get<UpgradeOption[]>(`${LICENSE_BASE}/upgrade-options`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Initiate license upgrade
  async initiateUpgrade(tier: string, billingCycle: 'monthly' | 'yearly' | 'lifetime'): Promise<{ upgradeUrl: string; sessionId: string }> {
    const res = await axios.post(
      `${LICENSE_BASE}/upgrade`,
      { tier, billingCycle },
      { headers: { Authorization: `Bearer ${getAccessToken()}` } }
    );
    return res.data;
  },

  // Complete license upgrade after payment
  async completeUpgrade(sessionId: string): Promise<LicenseInfo> {
    const res = await axios.post<LicenseInfo>(
      `${LICENSE_BASE}/upgrade/complete`,
      { sessionId },
      { headers: { Authorization: `Bearer ${getAccessToken()}` } }
    );
    return res.data;
  },

  // Get license usage statistics
  async getUsageStats(period: 'current' | 'last30' | 'last90' = 'current'): Promise<LicenseUsageStats> {
    const res = await axios.get<LicenseUsageStats>(`${LICENSE_BASE}/usage?period=${period}`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Check if feature is available for current license
  async checkFeatureAvailability(featureId: string): Promise<boolean> {
    const res = await axios.get<{ available: boolean }>(
      `${LICENSE_BASE}/features/${featureId}/availability`,
      { headers: { Authorization: `Bearer ${getAccessToken()}` } }
    );
    return res.data.available;
  },

  // Get feature restrictions for current license
  async getFeatureRestrictions(): Promise<{
    maxBuilds: number;
    maxActiveApps: number;
    allowedFeatures: string[];
    blockedFeatures: string[];
    priorityQueue: boolean;
    crossPlatformBuilds: boolean;
  }> {
    const res = await axios.get(`${LICENSE_BASE}/restrictions`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` }
    });
    return res.data;
  },

  // Refresh license cache
  async refreshLicense(): Promise<LicenseInfo> {
    const res = await axios.post<LicenseInfo>(
      `${LICENSE_BASE}/refresh`,
      {},
      { headers: { Authorization: `Bearer ${getAccessToken()}` } }
    );
    return res.data;
  }
};
