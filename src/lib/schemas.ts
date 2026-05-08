import { z } from "zod";
import { APP_ERROR_CODES } from "./app-errors";

const nullableCountSchema = z.number().int().nonnegative().nullable();
const textSchema = z.string().min(1);

export const collectRequestSchema = z.object({
  channelUrl: textSchema,
});

export const youtubeChannelSchema = z.object({
  id: textSchema,
  title: textSchema,
  description: z.string(),
  thumbnailUrl: z.string().url().nullable(),
  subscriberCount: nullableCountSchema,
  viewCount: nullableCountSchema,
  videoCount: nullableCountSchema,
});

export const youtubeVideoSchema = z.object({
  id: textSchema,
  title: textSchema,
  description: z.string(),
  publishedAt: textSchema,
  thumbnailUrl: z.string().url().nullable(),
  viewCount: nullableCountSchema,
  likeCount: nullableCountSchema,
  commentCount: nullableCountSchema,
  duration: textSchema,
});

export const collectSuccessSchema = z.object({
  channel: youtubeChannelSchema,
  videos: z.array(youtubeVideoSchema).max(50),
  sampleSize: z.number().int().nonnegative().max(50),
  collectedAt: textSchema,
});

export const analyzeRequestSchema = collectSuccessSchema;

export const recommendationDifficultySchema = z.enum(["low", "medium", "high"]);
export const checklistPrioritySchema = z.enum(["high", "medium", "low"]);
export const confidenceLevelSchema = z.enum(["high", "medium", "low"]);

export const recommendedNextVideoSchema = z.object({
  titleDirection: textSchema,
  format: textSchema,
  whyItFits: textSchema,
  evidence: textSchema,
  expectedImpact: textSchema,
  difficulty: recommendationDifficultySchema,
});

export const actionChecklistItemSchema = z.object({
  priority: checklistPrioritySchema,
  task: textSchema,
  reason: textSchema,
  expectedImpact: textSchema,
});

export const analysisConfidenceSchema = z.object({
  level: confidenceLevelSchema,
  reason: textSchema,
});

export const analyzeSuccessSchema = z.object({
  overallScore: z.number().int().min(0).max(100),
  executiveSummary: z.tuple([textSchema, textSchema, textSchema]),
  strongSignals: z.array(textSchema),
  growthBottlenecks: z.array(textSchema),
  contentPatterns: z.array(textSchema),
  recommendedNextVideos: z.array(recommendedNextVideoSchema).min(1),
  actionChecklist: z.array(actionChecklistItemSchema).max(7),
  confidence: analysisConfidenceSchema,
});

export const apiErrorSchema = z.object({
  error: z.object({
    code: z.enum(APP_ERROR_CODES),
    message: textSchema,
  }),
});

export type CollectRequestInput = z.infer<typeof collectRequestSchema>;
export type CollectSuccessPayload = z.infer<typeof collectSuccessSchema>;
export type AnalyzeRequestPayload = z.infer<typeof analyzeRequestSchema>;
export type AnalyzeSuccessPayload = z.infer<typeof analyzeSuccessSchema>;
export type ApiErrorPayload = z.infer<typeof apiErrorSchema>;
