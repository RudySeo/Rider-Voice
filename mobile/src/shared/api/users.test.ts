import { verifyRiderCode } from '@/shared/api/users';
import { requestJson } from '@/shared/api/client';

jest.mock('@/shared/api/client', () => ({ requestJson: jest.fn() }));

it('posts the six digit code to the current user rider verification endpoint', async () => {
  jest.mocked(requestJson).mockResolvedValue({ id: 1, status: 'ACTIVE', role: 'RIDER' });

  await verifyRiderCode('012345');

  expect(requestJson).toHaveBeenCalledWith('/api/v1/users/me/rider-verification', {
    method: 'POST',
    body: JSON.stringify({ code: '012345' }),
  });
});
