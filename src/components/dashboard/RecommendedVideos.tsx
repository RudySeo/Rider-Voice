"use client";

import { Clapperboard } from "lucide-react";
import type { RecommendedNextVideo } from "@/types/analysis";

interface RecommendedVideosProps {
  recommendations: RecommendedNextVideo[];
}

export function RecommendedVideos({ recommendations }: RecommendedVideosProps) {
  return (
    <section aria-labelledby="recommendations-heading" className="space-y-4">
      <div>
        <p className="text-sm font-semibold text-cyan-700">다음에 만들 콘텐츠 추천</p>
        <h2 id="recommendations-heading" className="mt-1 text-2xl font-semibold">
          다음 영상 아이디어
        </h2>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {recommendations.map((recommendation, index) => (
          <article
            className="min-h-72 border border-slate-200 bg-white p-5 shadow-sm"
            key={`${recommendation.titleDirection}-${index}`}
          >
            <div className="flex items-start justify-between gap-4">
              <div className="flex items-center gap-2 text-cyan-700">
                <Clapperboard aria-hidden="true" size={18} />
                <p className="text-sm font-semibold">아이디어 {index + 1}</p>
              </div>
              <span className="shrink-0 bg-slate-950 px-2 py-1 text-xs font-semibold text-white">
                {difficultyLabel[recommendation.difficulty]}
              </span>
            </div>
            <h3 className="mt-4 text-lg font-semibold leading-7 text-slate-950">
              {recommendation.titleDirection}
            </h3>
            <dl className="mt-4 grid gap-3 text-sm leading-6">
              <Field label="추천 포맷" value={recommendation.format} />
              <Field label="맞는 이유" value={recommendation.whyItFits} />
              <Field label="근거" value={recommendation.evidence} />
              <Field label="기대 효과" value={recommendation.expectedImpact} />
            </dl>
          </article>
        ))}
      </div>
    </section>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="font-semibold text-slate-950">{label}</dt>
      <dd className="mt-1 text-slate-700">{value}</dd>
    </div>
  );
}

const difficultyLabel = {
  low: "쉬움",
  medium: "보통",
  high: "어려움",
} as const;
