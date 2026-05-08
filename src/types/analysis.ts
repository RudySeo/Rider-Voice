export type RecommendationDifficulty = "low" | "medium" | "high";

export type ChecklistPriority = "high" | "medium" | "low";

export type ConfidenceLevel = "high" | "medium" | "low";

export interface RecommendedNextVideo {
  titleDirection: string;
  format: string;
  whyItFits: string;
  evidence: string;
  expectedImpact: string;
  difficulty: RecommendationDifficulty;
}

export interface ActionChecklistItem {
  priority: ChecklistPriority;
  task: string;
  reason: string;
  expectedImpact: string;
}

export interface AnalysisConfidence {
  level: ConfidenceLevel;
  reason: string;
}

export interface ChannelAnalysis {
  overallScore: number;
  executiveSummary: [string, string, string];
  strongSignals: string[];
  growthBottlenecks: string[];
  contentPatterns: string[];
  recommendedNextVideos: RecommendedNextVideo[];
  actionChecklist: ActionChecklistItem[];
  confidence: AnalysisConfidence;
}
