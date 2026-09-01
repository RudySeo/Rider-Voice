import { ApiError, resolveApiConfiguration } from '@/shared/api/clientConfig';

describe('API error', () => {
  it('keeps the stable HTTP status and problem code', () => {
    const error = new ApiError('failed', 409, 'STATE_CONFLICT');
    expect(error.status).toBe(409);
    expect(error.code).toBe('STATE_CONFLICT');
  });
});

describe('API environment profile', () => {
  it('uses local emulator defaults when no profile is selected', () => {
    expect(resolveApiConfiguration({ platform: 'ios', isDevice: false })).toEqual({
      profile: 'local',
      baseUrl: 'http://localhost:8080',
    });
    expect(resolveApiConfiguration({ platform: 'android', isDevice: false })).toEqual({
      profile: 'local',
      baseUrl: 'http://10.0.2.2:8080',
    });
  });

  it('derives a physical device local API from the private Metro host', () => {
    expect(resolveApiConfiguration({
      platform: 'ios',
      isDevice: true,
      hostUri: '192.168.35.93:8081',
    })).toEqual({
      profile: 'local',
      baseUrl: 'http://192.168.35.93:8080',
    });
  });

  it('prefers an explicit local override and normalizes its trailing slash', () => {
    expect(resolveApiConfiguration({
      profile: 'local',
      platform: 'android',
      isDevice: true,
      hostUri: '192.168.35.93:8081',
      localOverride: 'http://10.20.30.40:8080/',
    })).toEqual({
      profile: 'local',
      baseUrl: 'http://10.20.30.40:8080',
    });
  });

  it('uses only a configured HTTPS origin for the aws profile', () => {
    expect(resolveApiConfiguration({
      profile: 'aws',
      platform: 'ios',
      isDevice: true,
      awsBaseUrl: 'https://203-0-113-10.sslip.io/',
    })).toEqual({
      profile: 'aws',
      baseUrl: 'https://203-0-113-10.sslip.io',
    });
  });

  it.each([
    {
      name: 'unknown profile',
      input: { profile: 'production', platform: 'ios' as const, isDevice: false },
      errorCode: 'API_PROFILE_INVALID',
    },
    {
      name: 'missing aws URL',
      input: { profile: 'aws', platform: 'ios' as const, isDevice: true },
      errorCode: 'AWS_API_URL_MISSING',
    },
    {
      name: 'insecure aws URL',
      input: { profile: 'aws', platform: 'ios' as const, isDevice: true, awsBaseUrl: 'http://api.example.com' },
      errorCode: 'AWS_API_URL_INSECURE',
    },
    {
      name: 'public Metro tunnel for a local physical device',
      input: { profile: 'local', platform: 'android' as const, isDevice: true, hostUri: 'random.exp.direct:443' },
      errorCode: 'LOCAL_API_URL_UNRESOLVED',
    },
  ])('reports $name without silently changing profiles', ({ input, errorCode }) => {
    expect(resolveApiConfiguration(input)).toEqual(expect.objectContaining({
      baseUrl: undefined,
      errorCode,
    }));
  });
});
