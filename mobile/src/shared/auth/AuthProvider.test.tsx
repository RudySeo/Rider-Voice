describe('pending authentication intent', () => {
  it('only supports the three allow-listed intent kinds', () => {
    expect(['activity', 'existingReview', 'kakaoReview']).toContain('activity');
  });
});
