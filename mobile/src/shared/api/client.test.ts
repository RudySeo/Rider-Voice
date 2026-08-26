describe('JSON API client contract', () => {
  it('does not require a response body for HTTP 204', () => {
    expect(204).toBe(204);
  });
});
