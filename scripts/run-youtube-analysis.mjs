#!/usr/bin/env node

import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const DEFAULT_BASE_URL = "http://localhost:3000";
const DEFAULT_OUT_DIR = "reports/youtube-analysis";
const DEFAULT_TIMEOUT_MS = 120_000;

const STATUS_EXIT_CODE = {
  success: 0,
  collection_failed: 1,
  analysis_failed: 1,
};

export async function runYoutubeAnalysis(options = {}) {
  const argv = options.argv ?? process.argv.slice(2);
  const env = options.env ?? process.env;
  const logger = options.logger ?? console;
  const now = options.now ?? (() => new Date());
  const fetchImpl = options.fetchImpl ?? globalThis.fetch;
  const cwd = options.cwd ?? process.cwd();

  const parsed = parseArgs(argv, env);

  if (!parsed.ok) {
    logger.error(parsed.error);
    logger.error(usage());
    return {
      exitCode: 1,
      status: "invalid_arguments",
      error: parsed.error,
    };
  }

  if (parsed.help) {
    logger.log(usage());
    return {
      exitCode: 0,
      status: "help",
    };
  }

  if (typeof fetchImpl !== "function") {
    const error = "This Node.js runtime does not provide fetch.";
    logger.error(error);
    return {
      exitCode: 1,
      status: "invalid_runtime",
      error,
    };
  }

  const config = parsed.config;
  const generatedAt = now().toISOString();
  const outDir = path.resolve(cwd, config.outDir);

  logger.log(`Collecting YouTube data for ${config.channelUrl}`);

  const collectResult = await postJson({
    baseUrl: config.baseUrl,
    pathname: "/api/channel/collect",
    body: {
      channelUrl: config.channelUrl,
    },
    fetchImpl,
    timeoutMs: config.timeoutMs,
    stage: "collect",
  });

  if (!collectResult.ok) {
    const report = {
      status: "collection_failed",
      channelUrl: config.channelUrl,
      baseUrl: config.baseUrl,
      generatedAt,
      error: collectResult.error,
    };

    const paths = await writeReports(outDir, report);
    logger.error(`[collection_failed] ${collectResult.error.message}`);

    return {
      exitCode: STATUS_EXIT_CODE.collection_failed,
      status: "collection_failed",
      report,
      ...paths,
    };
  }

  logger.log(`Analyzing ${collectResult.data.sampleSize} collected videos`);

  const analyzeResult = await postJson({
    baseUrl: config.baseUrl,
    pathname: "/api/channel/analyze",
    body: collectResult.data,
    fetchImpl,
    timeoutMs: config.timeoutMs,
    stage: "analyze",
  });

  if (!analyzeResult.ok) {
    const report = {
      status: "analysis_failed",
      channelUrl: config.channelUrl,
      baseUrl: config.baseUrl,
      generatedAt,
      collect: collectResult.data,
      error: analyzeResult.error,
    };

    const paths = await writeReports(outDir, report);
    logger.error(`[analysis_failed] ${analyzeResult.error.message}`);

    return {
      exitCode: STATUS_EXIT_CODE.analysis_failed,
      status: "analysis_failed",
      report,
      ...paths,
    };
  }

  const report = {
    status: "success",
    channelUrl: config.channelUrl,
    baseUrl: config.baseUrl,
    generatedAt,
    collect: collectResult.data,
    analysis: analyzeResult.data,
  };

  const paths = await writeReports(outDir, report);
  logger.log(`[success] Wrote ${paths.jsonPath}`);
  logger.log(`[success] Wrote ${paths.markdownPath}`);

  return {
    exitCode: STATUS_EXIT_CODE.success,
    status: "success",
    report,
    ...paths,
  };
}

export function parseArgs(argv, env = process.env) {
  const config = {
    channelUrl: env.YOUTUBE_CHANNEL_URL,
    baseUrl: env.YOUTUBE_ANALYSIS_BASE_URL ?? DEFAULT_BASE_URL,
    outDir: env.YOUTUBE_ANALYSIS_OUT_DIR ?? DEFAULT_OUT_DIR,
    timeoutMs: env.YOUTUBE_ANALYSIS_TIMEOUT_MS ?? String(DEFAULT_TIMEOUT_MS),
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];

    if (arg === "--help" || arg === "-h") {
      return {
        ok: true,
        help: true,
      };
    }

    if (!arg.startsWith("--")) {
      return {
        ok: false,
        error: `Unexpected positional argument: ${arg}`,
      };
    }

    const { flag, inlineValue } = splitFlag(arg);
    const valueResult = readFlagValue(argv, index, inlineValue);

    if (!valueResult.ok) {
      return {
        ok: false,
        error: `${flag} requires a value.`,
      };
    }

    index = valueResult.index;

    switch (flag) {
      case "--channel":
        config.channelUrl = valueResult.value;
        break;
      case "--base-url":
        config.baseUrl = valueResult.value;
        break;
      case "--out-dir":
        config.outDir = valueResult.value;
        break;
      case "--timeout-ms":
        config.timeoutMs = valueResult.value;
        break;
      default:
        return {
          ok: false,
          error: `Unknown option: ${flag}`,
        };
    }
  }

  const channelUrl = config.channelUrl?.trim();

  if (!channelUrl) {
    return {
      ok: false,
      error: "Missing required --channel value or YOUTUBE_CHANNEL_URL env var.",
    };
  }

  const timeoutMs = Number(config.timeoutMs);

  if (!Number.isSafeInteger(timeoutMs) || timeoutMs <= 0) {
    return {
      ok: false,
      error: "--timeout-ms must be a positive integer.",
    };
  }

  const baseUrlResult = normalizeBaseUrl(config.baseUrl);

  if (!baseUrlResult.ok) {
    return {
      ok: false,
      error: "--base-url must be a valid absolute URL.",
    };
  }

  return {
    ok: true,
    help: false,
    config: {
      channelUrl,
      baseUrl: baseUrlResult.value,
      outDir: config.outDir,
      timeoutMs,
    },
  };
}

function splitFlag(arg) {
  const separatorIndex = arg.indexOf("=");

  if (separatorIndex === -1) {
    return {
      flag: arg,
      inlineValue: undefined,
    };
  }

  return {
    flag: arg.slice(0, separatorIndex),
    inlineValue: arg.slice(separatorIndex + 1),
  };
}

function readFlagValue(argv, index, inlineValue) {
  if (inlineValue !== undefined) {
    if (inlineValue === "") {
      return {
        ok: false,
      };
    }

    return {
      ok: true,
      index,
      value: inlineValue,
    };
  }

  const value = argv[index + 1];

  if (!value || value.startsWith("--")) {
    return {
      ok: false,
    };
  }

  return {
    ok: true,
    index: index + 1,
    value,
  };
}

function normalizeBaseUrl(value) {
  try {
    const url = new URL(value);
    return {
      ok: true,
      value: url.toString().replace(/\/$/, ""),
    };
  } catch {
    return {
      ok: false,
    };
  }
}

async function postJson({ baseUrl, pathname, body, fetchImpl, timeoutMs, stage }) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetchImpl(new URL(pathname, baseUrl).toString(), {
      method: "POST",
      headers: {
        "content-type": "application/json",
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    const payload = await safeJson(response);

    if (!response.ok || isApiError(payload)) {
      return {
        ok: false,
        error: toReportError(stage, response.status, payload),
      };
    }

    return {
      ok: true,
      data: payload,
    };
  } catch (error) {
    return {
      ok: false,
      error: {
        stage,
        status: 0,
        code: error?.name === "AbortError" ? "REQUEST_TIMEOUT" : "REQUEST_FAILED",
        message:
          error?.name === "AbortError"
            ? `Request timed out after ${timeoutMs}ms.`
            : error?.message ?? "Request failed before the API returned a response.",
      },
    };
  } finally {
    clearTimeout(timeout);
  }
}

async function safeJson(response) {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function isApiError(payload) {
  return Boolean(payload && typeof payload === "object" && "error" in payload);
}

function toReportError(stage, status, payload) {
  if (isApiError(payload)) {
    return {
      stage,
      status,
      code: payload.error.code ?? "API_ERROR",
      message: payload.error.message ?? "The API returned an error.",
    };
  }

  return {
    stage,
    status,
    code: "INVALID_API_RESPONSE",
    message: "The API returned a response the automation script could not use.",
  };
}

async function writeReports(outDir, report) {
  await mkdir(outDir, {
    recursive: true,
  });

  const jsonPath = path.join(outDir, "latest.json");
  const markdownPath = path.join(outDir, "latest.md");

  await Promise.all([
    writeFile(jsonPath, `${JSON.stringify(report, null, 2)}\n`, "utf8"),
    writeFile(markdownPath, formatMarkdown(report), "utf8"),
  ]);

  return {
    jsonPath,
    markdownPath,
  };
}

export function formatMarkdown(report) {
  const lines = [
    "# YouTube Channel Analysis",
    "",
    `Status: ${report.status}`,
    `Generated: ${report.generatedAt}`,
    `Channel input: ${report.channelUrl}`,
    "",
  ];

  if (report.status !== "success") {
    lines.push(
      "## Failure",
      "",
      `Stage: ${report.error.stage}`,
      `Code: ${report.error.code}`,
      `Message: ${report.error.message}`,
      "",
    );

    if (report.collect) {
      lines.push(
        "## Preserved Collection",
        "",
        `Channel: ${report.collect.channel.title}`,
        `Sample size: ${report.collect.sampleSize}`,
        "",
      );
    }

    return `${lines.join("\n")}\n`;
  }

  const { collect, analysis } = report;

  lines.push(
    "## Channel",
    "",
    `Name: ${collect.channel.title}`,
    `Sample size: ${collect.sampleSize}`,
    `Collected at: ${collect.collectedAt}`,
    "",
    "## Score",
    "",
    `Score: ${analysis.overallScore}/100`,
    `Confidence: ${analysis.confidence.level} - ${analysis.confidence.reason}`,
    "",
    "## Executive Summary",
    "",
    bulletList(analysis.executiveSummary),
    "",
    "## Strong Signals",
    "",
    bulletList(analysis.strongSignals),
    "",
    "## Growth Bottlenecks",
    "",
    bulletList(analysis.growthBottlenecks),
    "",
    "## Content Patterns",
    "",
    bulletList(analysis.contentPatterns),
    "",
    "## Recommended Next Videos",
    "",
    numberedRecommendations(analysis.recommendedNextVideos),
    "",
    "## Action Checklist",
    "",
    checklist(analysis.actionChecklist),
    "",
  );

  return `${lines.join("\n")}\n`;
}

function bulletList(items) {
  if (!items?.length) {
    return "- No items returned.";
  }

  return items.map((item) => `- ${item}`).join("\n");
}

function numberedRecommendations(items) {
  if (!items?.length) {
    return "No recommendations returned.";
  }

  return items
    .map(
      (item, index) =>
        `${index + 1}. ${item.titleDirection}\n` +
        `   - Format: ${item.format}\n` +
        `   - Difficulty: ${item.difficulty}\n` +
        `   - Why: ${item.whyItFits}\n` +
        `   - Evidence: ${item.evidence}\n` +
        `   - Expected impact: ${item.expectedImpact}`,
    )
    .join("\n\n");
}

function checklist(items) {
  if (!items?.length) {
    return "- No checklist items returned.";
  }

  return items
    .map(
      (item) =>
        `- [${item.priority}] ${item.task}\n` +
        `  - Reason: ${item.reason}\n` +
        `  - Expected impact: ${item.expectedImpact}`,
    )
    .join("\n");
}

function usage() {
  return [
    "Usage:",
    "  npm run analyze:youtube -- --channel \"@handle\"",
    "",
    "Options:",
    `  --channel <value>      YouTube channel URL, @handle, or UC... id. Env: YOUTUBE_CHANNEL_URL`,
    `  --base-url <url>       App base URL. Default: ${DEFAULT_BASE_URL}`,
    `  --out-dir <path>       Output directory. Default: ${DEFAULT_OUT_DIR}`,
    `  --timeout-ms <number>  Request timeout. Default: ${DEFAULT_TIMEOUT_MS}`,
  ].join("\n");
}

const currentFile = fileURLToPath(import.meta.url);
const invokedFile = process.argv[1] ? path.resolve(process.argv[1]) : "";

if (invokedFile === currentFile) {
  const result = await runYoutubeAnalysis();
  process.exitCode = result.exitCode;
}
