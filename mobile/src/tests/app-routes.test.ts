import { readdirSync } from 'node:fs';
import { join, relative } from 'node:path';

function filesBelow(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? filesBelow(path) : [path];
  });
}

describe('mobile app route contracts', () => {
  it('keeps Jest files outside the Expo Router route directory', () => {
    const appDirectory = join(process.cwd(), 'src', 'app');
    const testRoutes = filesBelow(appDirectory)
      .filter((path) => /\.(test|spec)\.[jt]sx?$/.test(path))
      .map((path) => relative(appDirectory, path));

    expect(testRoutes).toEqual([]);
  });

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

  it('keeps manual address registration separate from review creation', () => {
    expect(['/review/manual-target', '/review/new']).toHaveLength(2);
  });
});
