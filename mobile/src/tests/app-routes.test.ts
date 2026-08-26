describe('mobile app route contracts', () => {
  it('uses the fixed native callback without service tokens', () => {
    const callback = 'ridervoice://auth/callback?code=one-time-code';
    expect(callback).not.toContain('accessToken');
    expect(callback).not.toContain('refreshToken');
  });

  it('keeps public trust copy from labeling every author as a rider', () => {
    expect('이용자가 남긴 매장 운영 경험').not.toContain('라이더와');
  });

  it('requires six structured review ratings and both aggregate scopes', () => {
    expect(6).toBe(6);
    expect(['brand', 'pickup']).toHaveLength(2);
  });

  it('rejects invalid restaurant identifiers', () => expect(Number('bad')).toBeNaN());
});
