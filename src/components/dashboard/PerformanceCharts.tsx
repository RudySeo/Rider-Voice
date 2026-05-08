"use client";

import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Eye, MessageSquare, ThumbsUp, Trophy } from "lucide-react";
import {
  getAverageViewCount,
  getOutlierVideos,
  getTopVideos,
  getUploadCadenceDays,
} from "@/lib/metrics";
import type { YouTubeCollectResult, YouTubeVideo } from "@/types/youtube";

interface PerformanceChartsProps {
  collectResult: YouTubeCollectResult;
}

export function PerformanceCharts({ collectResult }: PerformanceChartsProps) {
  const videos = collectResult.videos;
  const topVideos = getTopVideos(videos, 5);
  const outliers = getOutlierVideos(videos);
  const averageViews = getAverageViewCount(videos);
  const uploadCadenceDays = getUploadCadenceDays(videos);
  const chartData = toChartData(videos);
  const cadenceData = toCadenceData(videos);

  return (
    <section aria-labelledby="performance-heading" className="space-y-4">
      <div>
        <p className="text-sm font-semibold text-cyan-700">성과 패턴</p>
        <h2 id="performance-heading" className="mt-1 text-2xl font-semibold">
          최근 공개 영상 흐름
        </h2>
      </div>

      <div className="grid gap-4 md:grid-cols-4">
        <MetricCard
          icon={<Eye aria-hidden="true" size={18} />}
          label="평균 조회수"
          value={formatMetric(averageViews)}
        />
        <MetricCard
          icon={<ThumbsUp aria-hidden="true" size={18} />}
          label="총 좋아요"
          value={formatMetric(sumNullable(videos.map((video) => video.likeCount)))}
        />
        <MetricCard
          icon={<MessageSquare aria-hidden="true" size={18} />}
          label="총 댓글"
          value={formatMetric(sumNullable(videos.map((video) => video.commentCount)))}
        />
        <MetricCard
          icon={<Trophy aria-hidden="true" size={18} />}
          label="업로드 리듬"
          value={uploadCadenceDays === null ? "데이터 없음" : `${uploadCadenceDays.toFixed(1)}일`}
        />
      </div>

      <div className="grid gap-4 xl:grid-cols-[380px_1fr]">
        <article className="border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-base font-semibold">상위 영상 랭킹</h3>
          {topVideos.length === 0 ? (
            <p className="mt-4 text-sm text-slate-600">성과 데이터가 부족합니다.</p>
          ) : (
            <ol className="mt-4 space-y-3">
              {topVideos.map((video, index) => (
                <li className="grid grid-cols-[32px_1fr_auto] gap-3 text-sm" key={video.id}>
                  <span className="font-semibold text-cyan-700">{index + 1}</span>
                  <span className="min-w-0 truncate text-slate-800">{video.title}</span>
                  <span className="font-medium text-slate-900">
                    {formatMetric(video.viewCount)}
                  </span>
                </li>
              ))}
            </ol>
          )}
        </article>

        <article className="border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-base font-semibold">최근 영상 성과 추이</h3>
          <div className="mt-4 h-[260px] overflow-x-auto">
            {chartData.length === 0 ? (
              <p className="text-sm text-slate-600">성과 데이터가 부족합니다.</p>
            ) : (
              <LineChart width={760} height={240} data={chartData} margin={chartMargin}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                <YAxis tick={{ fontSize: 12 }} />
                <Tooltip />
                <Line type="monotone" dataKey="views" stroke="#0e7490" strokeWidth={2} />
                <Line type="monotone" dataKey="likes" stroke="#16a34a" strokeWidth={2} />
                <Line type="monotone" dataKey="comments" stroke="#9333ea" strokeWidth={2} />
              </LineChart>
            )}
          </div>
        </article>
      </div>

      <div className="grid gap-4 xl:grid-cols-[1fr_380px]">
        <article className="border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-base font-semibold">업로드 리듬</h3>
          <div className="mt-4 h-[220px] overflow-x-auto">
            {cadenceData.length === 0 ? (
              <p className="text-sm text-slate-600">업로드 간격을 계산할 데이터가 부족합니다.</p>
            ) : (
              <BarChart width={760} height={200} data={cadenceData} margin={chartMargin}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                <YAxis tick={{ fontSize: 12 }} />
                <Tooltip />
                <Bar dataKey="gapDays" fill="#0891b2" />
              </BarChart>
            )}
          </div>
        </article>

        <article className="border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-base font-semibold">평균 대비 튄 영상</h3>
          {outliers.length === 0 ? (
            <p className="mt-4 text-sm leading-6 text-slate-600">
              평균 대비 크게 튄 영상이 아직 없습니다.
            </p>
          ) : (
            <ul className="mt-4 space-y-3 text-sm">
              {outliers.slice(0, 4).map((video) => (
                <li className="leading-6 text-slate-800" key={video.id}>
                  <span className="font-semibold text-slate-950">{video.title}</span>
                  <span className="ml-2 text-slate-600">{formatMetric(video.viewCount)}</span>
                </li>
              ))}
            </ul>
          )}
        </article>
      </div>
    </section>
  );
}

function MetricCard({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <article className="min-h-28 border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center gap-2 text-cyan-700">
        {icon}
        <p className="text-sm font-semibold">{label}</p>
      </div>
      <p className="mt-4 text-2xl font-semibold text-slate-950">{value}</p>
    </article>
  );
}

function toChartData(videos: YouTubeVideo[]) {
  return [...videos]
    .sort((left, right) => Date.parse(left.publishedAt) - Date.parse(right.publishedAt))
    .map((video, index) => ({
      label: `${index + 1}`,
      views: video.viewCount ?? 0,
      likes: video.likeCount ?? 0,
      comments: video.commentCount ?? 0,
    }));
}

function toCadenceData(videos: YouTubeVideo[]) {
  const sortedVideos = [...videos]
    .filter((video) => Number.isFinite(Date.parse(video.publishedAt)))
    .sort((left, right) => Date.parse(left.publishedAt) - Date.parse(right.publishedAt));

  return sortedVideos.slice(1).map((video, index) => {
    const previous = sortedVideos[index];
    const gapDays =
      (Date.parse(video.publishedAt) - Date.parse(previous.publishedAt)) /
      (24 * 60 * 60 * 1000);

    return {
      label: `${index + 2}`,
      gapDays: Number(gapDays.toFixed(1)),
    };
  });
}

function sumNullable(values: Array<number | null>) {
  const knownValues = values.filter((value): value is number => value !== null);

  if (knownValues.length === 0) {
    return null;
  }

  return knownValues.reduce((total, value) => total + value, 0);
}

function formatMetric(value: number | null) {
  return value === null ? "데이터 없음" : new Intl.NumberFormat("ko-KR").format(value);
}

const chartMargin = {
  top: 12,
  right: 20,
  bottom: 8,
  left: 0,
};
