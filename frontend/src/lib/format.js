// Trove — formatting helpers.

export const money = (n, currency = "$") =>
  currency +
  Number(n || 0).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

export const count = (n) => Number(n || 0).toLocaleString();

export const discountPct = (price, compareAt) =>
  compareAt && compareAt > price ? Math.round((1 - price / compareAt) * 100) : 0;
