import { useState, useEffect, useCallback } from 'react';
import { licenseApi } from '../services/licenseApi';
import type { 
  LicenseInfo, 
  LicenseDashboard, 
  LicenseValidationResponse
} from '../types/license';
import { LicenseTier } from '../types/license';

export function useLicense() {
  const [currentLicense, setCurrentLicense] = useState<LicenseInfo | null>(null);
  const [dashboard, setDashboard] = useState<LicenseDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Load current license
  const loadLicense = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const license = await licenseApi.getCurrentLicense();
      setCurrentLicense(license);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load license information');
    } finally {
      setLoading(false);
    }
  }, []);

  // Load license dashboard
  const loadDashboard = useCallback(async () => {
    try {
      setError(null);
      const dashboardData = await licenseApi.getLicenseDashboard();
      setDashboard(dashboardData);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load license dashboard');
    }
  }, []);

  // Validate license for specific operation
  const validateLicense = useCallback(async (operation: 'build' | 'download' | 'update'): Promise<LicenseValidationResponse> => {
    try {
      return await licenseApi.validateLicense(operation);
    } catch (err: any) {
      throw new Error(err.response?.data?.message || 'License validation failed');
    }
  }, []);

  // Check if feature is available
  const isFeatureAvailable = useCallback(async (featureId: string): Promise<boolean> => {
    try {
      return await licenseApi.checkFeatureAvailability(featureId);
    } catch {
      return false;
    }
  }, []);

  // Get upgrade options
  const getUpgradeOptions = useCallback(async () => {
    try {
      return await licenseApi.getUpgradeOptions();
    } catch (err: any) {
      throw new Error(err.response?.data?.message || 'Failed to get upgrade options');
    }
  }, []);

  // Initiate upgrade
  const initiateUpgrade = useCallback(async (tier: string, billingCycle: 'monthly' | 'yearly' | 'lifetime') => {
    try {
      return await licenseApi.initiateUpgrade(tier, billingCycle);
    } catch (err: any) {
      throw new Error(err.response?.data?.message || 'Failed to initiate upgrade');
    }
  }, []);

  // Complete upgrade after payment
  const completeUpgrade = useCallback(async (sessionId: string): Promise<LicenseInfo> => {
    try {
      const newLicense = await licenseApi.completeUpgrade(sessionId);
      setCurrentLicense(newLicense);
      await loadDashboard(); // Refresh dashboard
      return newLicense;
    } catch (err: any) {
      throw new Error(err.response?.data?.message || 'Failed to complete upgrade');
    }
  }, [loadDashboard]);

  // Refresh license cache
  const refreshLicense = useCallback(async (): Promise<LicenseInfo> => {
    try {
      const refreshedLicense = await licenseApi.refreshLicense();
      setCurrentLicense(refreshedLicense);
      return refreshedLicense;
    } catch (err: any) {
      throw new Error(err.response?.data?.message || 'Failed to refresh license');
    }
  }, []);

  // Computed properties
  const isTrial = currentLicense?.tier === LicenseTier.TRIAL;
  const isStarter = currentLicense?.tier === LicenseTier.STARTER;
  const isPro = currentLicense?.tier === LicenseTier.PRO;
  const isLifetime = currentLicense?.tier === LicenseTier.LIFETIME;
  const isExpired = currentLicense ? new Date(currentLicense.licenseExpiresAt) < new Date() : false;
  const daysUntilExpiry = currentLicense ? Math.max(0, Math.floor((new Date(currentLicense.licenseExpiresAt).getTime() - new Date().getTime()) / (1000 * 60 * 60 * 24))) : 0;
  const buildsRemaining = currentLicense ? Math.max(0, currentLicense.buildsAllowed - currentLicense.buildsUsed) : 0;
  const buildUsagePercentage = currentLicense ? (currentLicense.buildsUsed / currentLicense.buildsAllowed) * 100 : 0;

  // Load data on mount
  useEffect(() => {
    loadLicense();
    loadDashboard();
  }, [loadLicense, loadDashboard]);

  return {
    // State
    currentLicense,
    dashboard,
    loading,
    error,
    
    // Computed
    isTrial,
    isStarter,
    isPro,
    isLifetime,
    isExpired,
    daysUntilExpiry,
    buildsRemaining,
    buildUsagePercentage,
    
    // Actions
    loadLicense,
    loadDashboard,
    validateLicense,
    isFeatureAvailable,
    getUpgradeOptions,
    initiateUpgrade,
    completeUpgrade,
    refreshLicense,
    
    // Utility
    clearError: () => setError(null)
  };
}
