export function routeSearchQuery(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}
