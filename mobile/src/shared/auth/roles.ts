import type { User } from '@/shared/api/types';

export const canWriteReview = (user: User | null) => user?.role === 'RIDER' || user?.role === 'ADMIN';
