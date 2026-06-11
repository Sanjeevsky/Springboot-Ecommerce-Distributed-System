import React, { useEffect, useMemo, useState } from "react";
import { Download } from "lucide-react";
import { Badge, Button } from "../../components/index.js";
import { audit } from "../../lib/services.js";
import { downloadCsv } from "../../lib/csv.js";

const SOURCES = [
  { id: "all", label: "All activity" },
  { id: "catalog", label: "Catalog" },
  { id: "inventory", label: "Inventory" },
];

const ACTION_TONE = {
  CREATE: "success",
  UPDATE: "info",
  RETIRE: "danger",
  STOCK_SET: "info",
  RESTOCK: "success",
};

function toDate(value) {
  // Spring serializes LocalDateTime as [year, month(1-based), day, hour, min, sec, nanos].
  if (Array.isArray(value)) {
    const [y, mo = 1, d = 1, h = 0, mi = 0, s = 0] = value;
    return new Date(y, mo - 1, d, h, mi, s);
  }
  return new Date(value);
}

function formatWhen(value) {
  if (!value) return "";
  const d = toDate(value);
  return Number.isNaN(d.getTime())
    ? String(value)
    : d.toLocaleString("en-US", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

export default function StudioActivity() {
  const [source, setSource] = useState("all");
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError("");
    // "all" merges both sources, newest first; otherwise just the chosen one.
    const sources = source === "all" ? ["catalog", "inventory"] : [source];
    Promise.all(sources.map((s) => audit.list({ source: s, size: 100 })))
      .then((results) => {
        if (!alive) return;
        const merged = results.flat().sort((a, b) => toDate(b.createdAt) - toDate(a.createdAt));
        setRows(merged);
      })
      .catch((err) => alive && setError(err.message))
      .finally(() => alive && setLoading(false));
    return () => { alive = false; };
  }, [source]);

  const heading = useMemo(
    () => SOURCES.find((s) => s.id === source)?.label || "Activity",
    [source]
  );

  return (
    <div>
      <div className="studio-page-heading studio-action-heading">
        <div>
          <h1>Activity log</h1>
          <p>{loading ? "Loading…" : `${rows.length} admin changes in this view.`}</p>
        </div>
        <div className="studio-heading-actions">
          <Button
            variant="secondary"
            iconLeft={<Download size={17} />}
            disabled={!rows.length}
            onClick={() => downloadCsv("trove-activity.csv", [
              { label: "When", value: (r) => formatWhen(r.createdAt) },
              { label: "Source", value: "source" },
              { label: "Action", value: "action" },
              { label: "Entity", value: "entityType" },
              { label: "Entity ID", value: "entityId" },
              { label: "Summary", value: "summary" },
              { label: "Actor", value: "actor" },
            ], rows)}
          >
            Export CSV
          </Button>
        </div>
      </div>

      <div className="studio-toolbar">
        <div className="studio-segmented" role="tablist" aria-label="Activity source">
          {SOURCES.map((s) => (
            <button
              key={s.id}
              type="button"
              role="tab"
              aria-selected={source === s.id}
              className={source === s.id ? "is-active" : ""}
              onClick={() => setSource(s.id)}
            >
              {s.label}
            </button>
          ))}
        </div>
      </div>

      {error && <div className="studio-error">{error}</div>}

      <div className="studio-table-wrap">
        <table className="studio-table">
          <thead>
            <tr>
              <th>When</th>
              <th>Source</th>
              <th>Action</th>
              <th>Summary</th>
              <th>Actor</th>
            </tr>
          </thead>
          <tbody>
            {!loading && rows.length === 0 && (
              <tr><td colSpan={5} className="studio-empty">No activity recorded yet.</td></tr>
            )}
            {rows.map((r) => (
              <tr key={`${r.source}-${r.id}`}>
                <td className="studio-muted">{formatWhen(r.createdAt)}</td>
                <td style={{ textTransform: "capitalize" }}>{r.source}</td>
                <td><Badge tone={ACTION_TONE[r.action] || "neutral"} size="sm">{r.action}</Badge></td>
                <td>{r.summary}</td>
                <td className="studio-muted">{r.actor}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
