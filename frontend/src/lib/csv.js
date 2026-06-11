function cellValue(column, row) {
  return typeof column.value === "function" ? column.value(row) : row[column.value];
}

function escapeCell(value) {
  if (value === null || value === undefined) return "";

  let text = typeof value === "object" ? JSON.stringify(value) : String(value);
  if (typeof value === "string" && /^[=+\-@]/.test(text)) {
    text = `'${text}`;
  }

  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export function toCsv(columns, rows) {
  const header = columns.map((column) => escapeCell(column.label)).join(",");
  const body = rows.map((row) => columns
    .map((column) => escapeCell(cellValue(column, row)))
    .join(","));
  return [header, ...body].join("\r\n");
}

export function downloadCsv(filename, columns, rows) {
  const blob = new Blob([`\uFEFF${toCsv(columns, rows)}`], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
