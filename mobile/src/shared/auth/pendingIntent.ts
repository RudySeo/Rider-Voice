import { z } from 'zod';

const pendingIntentSchema = z.discriminatedUnion('kind', [
  z.object({ kind: z.literal('activity') }).strict(),
  z.object({
    kind: z.literal('existingReview'),
    restaurantId: z.number().int().positive(),
    place: z.string().trim().min(1).max(255),
  }).strict(),
  z.object({
    kind: z.literal('kakaoReview'),
    query: z.string().trim().min(2).max(100),
    kakaoPlaceId: z.string().trim().min(1).max(255),
    place: z.string().trim().min(1).max(255),
  }).strict(),
  z.object({
    kind: z.literal('manualReview'),
    query: z.string().trim().max(100),
  }).strict(),
]);

export type PendingIntent = z.infer<typeof pendingIntentSchema>;

export function parsePendingIntent(raw: string | null): PendingIntent | null {
  if (!raw) return null;
  try {
    const parsed = pendingIntentSchema.safeParse(JSON.parse(raw));
    return parsed.success ? parsed.data : null;
  } catch {
    return null;
  }
}
