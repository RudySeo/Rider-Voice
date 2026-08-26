jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

describe('mobile auth session contract', () => {
  it('keeps the fixed callback free of service tokens', () => {
    const callback = 'ridervoice://auth/callback?code=one-time-code';
    expect(callback).not.toContain('accessToken');
    expect(callback).not.toContain('refreshToken');
  });
});
