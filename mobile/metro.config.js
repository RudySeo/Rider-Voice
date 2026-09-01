const { getDefaultConfig } = require('expo/metro-config');

const config = getDefaultConfig(__dirname);
const apiProfile = process.env.EXPO_PUBLIC_API_PROFILE?.trim() || 'local';
const selectedApiBaseUrl = apiProfile === 'aws'
  ? process.env.EXPO_PUBLIC_AWS_API_BASE_URL?.trim()
  : process.env.EXPO_PUBLIC_LOCAL_API_BASE_URL?.trim();

config.cacheVersion = JSON.stringify({
  metro: config.cacheVersion,
  riderVoiceApiProfile: apiProfile,
  riderVoiceApiBaseUrl: selectedApiBaseUrl || null,
});

module.exports = config;
