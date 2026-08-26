import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import openapiTS, { astToString } from 'openapi-typescript';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const outputPath = resolve(scriptDirectory, '../src/shared/api/generated.ts');
const schemaUrl = new URL(process.env.OPENAPI_SCHEMA_URL ?? 'http://localhost:8080/v3/api-docs');
const nodes = await openapiTS(schemaUrl, { exportType: true });
await mkdir(dirname(outputPath), { recursive: true });
await writeFile(outputPath, `// @ts-nocheck -- generated discriminator cycles are validated by backend OpenAPI tests\n// Generated from Rider Voice OpenAPI. Do not edit.\n${astToString(nodes)}`, 'utf8');
