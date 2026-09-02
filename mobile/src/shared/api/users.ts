import { requestJson } from '@/shared/api/client';
import type { User } from '@/shared/api/types';

export const verifyRiderCode = (code: string) => requestJson<User>('/api/v1/users/me/rider-verification', {
  method: 'POST',
  body: JSON.stringify({ code }),
});
