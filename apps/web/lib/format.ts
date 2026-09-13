export function formatPrice(price: number): string {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(price);
}

export function pluralise(count: number, singular: string, plural: string): string {
  return count === 1 ? singular : plural;
}