import React, { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  AlertTriangle,
  ArrowUpRight,
  CircleDollarSign,
  Download,
  ShoppingCart,
  TrendingUp,
} from "lucide-react";
import {
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Badge, Button } from "../../components/index.js";
import { analytics, catalog, inventory } from "../../lib/services.js";
import { money } from "../../lib/format.js";
import { downloadCsv } from "../../lib/csv.js";

const RANGE_OPTIONS = [
  { id: "today", label: "Today", days: 1 },
  { id: "7d", label: "7 days", days: 7 },
  { id: "30d", label: "30 days", days: 30 },
];

const STATUS_META = {
  PENDING: { label: "Pending", color: "var(--warning)" },
  CONFIRMED: { label: "Confirmed", color: "var(--success)" },
  SHIPPED: { label: "Shipped", color: "var(--info)" },
  DELIVERED: { label: "Delivered", color: "var(--primary)" },
  CANCELLED: { label: "Cancelled", color: "var(--danger)" },
};

const EMPTY_SUMMARY = {
  revenue: 0,
  orderCount: 0,
  averageOrderValue: 0,
  statusBreakdown: {},
};

export default function StudioHome() {
  const [range, setRange] = useState("30d");
  const [summary, setSummary] = useState(EMPTY_SUMMARY);
  const [daily, setDaily] = useState([]);
  const [topProducts, setTopProducts] = useState([]);
  const [products, setProducts] = useState([]);
  const [stock, setStock] = useState([]);
  const [loading, setLoading] = useState(true);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    Promise.all([
      analytics.daily(30),
      analytics.topProducts(10),
      catalog.adminList({ size: 100 }).then((result) => result.items),
      inventory.list(),
    ]).then(([dailyRows, productRows, catalogRows, stockRows]) => {
      if (!active) return;
      setDaily(dailyRows);
      setTopProducts(productRows);
      setProducts(catalogRows);
      setStock(stockRows);
    }).catch((err) => {
      if (active) setError(readApiError(err, "Could not load Studio analytics."));
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let active = true;
    const option = RANGE_OPTIONS.find((item) => item.id === range) || RANGE_OPTIONS[2];
    const to = new Date();
    const from = new Date(to);
    from.setDate(from.getDate() - option.days + 1);
    setSummaryLoading(true);
    analytics.summary({ from: localDate(from), to: localDate(to) })
      .then((result) => {
        if (active) {
          setSummary(result || EMPTY_SUMMARY);
          setError("");
        }
      })
      .catch((err) => {
        if (active) setError(readApiError(err, "Could not refresh sales summary."));
      })
      .finally(() => {
        if (active) setSummaryLoading(false);
      });
    return () => { active = false; };
  }, [range]);

  const rangeLabel = RANGE_OPTIONS.find((item) => item.id === range)?.label || "30 days";
  const productNames = useMemo(
    () => new Map(products.map((product) => [String(product.id), product.title])),
    [products]
  );
  const lowStock = useMemo(() => stock
    .map((row) => ({ ...row, availableQty: Math.max(0, row.totalQty - row.reservedQty) }))
    .filter((row) => row.availableQty <= 5)
    .sort((a, b) => a.availableQty - b.availableQty)
    .slice(0, 8), [stock]);
  const availableUnits = useMemo(
    () => stock.reduce((sum, row) => sum + Math.max(0, row.totalQty - row.reservedQty), 0),
    [stock]
  );
  const statusData = useMemo(() => Object.entries(STATUS_META)
    .map(([status, meta]) => ({
      status,
      name: meta.label,
      value: Number(summary.statusBreakdown?.[status] || 0),
      color: meta.color,
    }))
    .filter((item) => item.value > 0), [summary.statusBreakdown]);
  const chartData = useMemo(() => daily.map((point) => ({
    ...point,
    label: formatChartDate(point.date),
  })), [daily]);

  return (
    <div>
      <div className="studio-page-heading studio-dashboard-heading">
        <div>
          <h1>Overview</h1>
          <p>Sales performance and inventory signals.</p>
        </div>
        <div className="studio-heading-actions">
          {(loading || summaryLoading) && <Badge tone="info">Refreshing</Badge>}
          <Button
            variant="secondary"
            size="sm"
            disabled={!daily.length}
            iconLeft={<Download size={16} />}
            onClick={() => downloadCsv("trove-daily-sales.csv", [
              { label: "Date", value: "date" },
              { label: "Revenue", value: "revenue" },
              { label: "Orders", value: "orderCount" },
            ], daily)}
          >
            Export CSV
          </Button>
          <div className="studio-segmented" aria-label="Analytics date range">
            {RANGE_OPTIONS.map((option) => (
              <button
                key={option.id}
                type="button"
                className={range === option.id ? "is-active" : ""}
                aria-pressed={range === option.id}
                onClick={() => setRange(option.id)}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {error && <div className="studio-error">{error}</div>}

      <section className="studio-metrics" aria-label="Sales summary">
        <Metric icon={CircleDollarSign} label={`Revenue · ${rangeLabel}`} value={money(summary.revenue)} tone="success" />
        <Metric icon={ShoppingCart} label={`Orders · ${rangeLabel}`} value={summary.orderCount || 0} tone="primary" />
        <Metric icon={TrendingUp} label={`Average order · ${rangeLabel}`} value={money(summary.averageOrderValue)} tone="info" />
        <Metric icon={AlertTriangle} label="Low stock rows" value={lowStock.length} tone="warning" />
      </section>

      <section className="studio-dashboard-grid">
        <div className="studio-section studio-chart-panel">
          <div className="studio-section-heading">
            <div>
              <h2>Daily revenue</h2>
              <span>Recognized sales over the last 30 days</span>
            </div>
          </div>
          <div className="studio-line-chart" aria-label="Daily revenue line chart">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData} margin={{ top: 16, right: 16, left: 0, bottom: 2 }}>
                <CartesianGrid stroke="var(--border-faint)" vertical={false} />
                <XAxis
                  dataKey="label"
                  axisLine={false}
                  tickLine={false}
                  minTickGap={24}
                  tick={{ fill: "var(--text-muted)", fontSize: 11 }}
                />
                <YAxis
                  axisLine={false}
                  tickLine={false}
                  width={54}
                  tickFormatter={compactMoney}
                  tick={{ fill: "var(--text-muted)", fontSize: 11 }}
                />
                <Tooltip content={<RevenueTooltip />} />
                <Line
                  type="monotone"
                  dataKey="revenue"
                  stroke="var(--primary)"
                  strokeWidth={2.5}
                  dot={false}
                  activeDot={{ r: 4, fill: "var(--primary)", stroke: "var(--surface)", strokeWidth: 2 }}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="studio-section studio-status-panel">
          <div className="studio-section-heading">
            <div>
              <h2>Order status</h2>
              <span>{rangeLabel} mix</span>
            </div>
          </div>
          {statusData.length ? (
            <div className="studio-status-content">
              <div className="studio-donut" aria-label="Order status donut chart">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={statusData}
                      dataKey="value"
                      nameKey="name"
                      innerRadius="62%"
                      outerRadius="88%"
                      paddingAngle={2}
                      stroke="none"
                    >
                      {statusData.map((item) => <Cell key={item.status} fill={item.color} />)}
                    </Pie>
                    <Tooltip content={<StatusTooltip />} />
                  </PieChart>
                </ResponsiveContainer>
                <div><strong>{summary.orderCount || 0}</strong><span>orders</span></div>
              </div>
              <div className="studio-status-legend">
                {statusData.map((item) => (
                  <div key={item.status}>
                    <span style={{ background: item.color }} />
                    <small>{item.name}</small>
                    <strong>{item.value}</strong>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="studio-chart-empty">No orders in this range.</div>
          )}
        </div>
      </section>

      <section className="studio-dashboard-grid studio-dashboard-lists">
        <div className="studio-section">
          <div className="studio-section-heading">
            <div>
              <h2>Top products</h2>
              <span>Ranked by confirmed units sold</span>
            </div>
          </div>
          <div className="studio-table-wrap">
            <table className="studio-table studio-analytics-table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th>Units</th>
                  <th>Revenue</th>
                </tr>
              </thead>
              <tbody>
                {topProducts.map((product) => (
                  <tr key={product.productId}>
                    <td>
                      <strong className="studio-table-title">{product.productName}</strong>
                      <span className="studio-muted">{shortId(product.productId)}</span>
                    </td>
                    <td className="studio-number">{product.quantity}</td>
                    <td className="studio-number">{money(product.revenue)}</td>
                  </tr>
                ))}
                {!loading && topProducts.length === 0 && (
                  <tr><td colSpan="3" className="studio-empty">No confirmed product sales yet.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        <div className="studio-section">
          <div className="studio-section-heading">
            <div>
              <h2>Low stock</h2>
              <span>{availableUnits.toLocaleString()} units currently available</span>
            </div>
            <Link className="studio-text-link" to="/studio/inventory">View inventory</Link>
          </div>
          <div className="studio-alert-list">
            {lowStock.map((row) => (
              <Link key={`${row.productId}-${row.variantId || "base"}`} to={`/studio/products/${row.productId}`}>
                <span className={`studio-stock-signal${row.availableQty === 0 ? " is-empty" : ""}`}>
                  {row.availableQty}
                </span>
                <div>
                  <strong>{productNames.get(String(row.productId)) || "Catalog product"}</strong>
                  <small>{row.variantId ? `Variant ${shortId(row.variantId)}` : "Base inventory"}</small>
                </div>
                <ArrowUpRight size={17} />
              </Link>
            ))}
            {!loading && lowStock.length === 0 && (
              <div className="studio-chart-empty">Inventory is above the low-stock threshold.</div>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}

function Metric({ icon: Icon, label, value, tone }) {
  return (
    <div className={`studio-metric studio-metric-${tone}`}>
      <span><Icon size={19} /></span>
      <div><small>{label}</small><strong>{value}</strong></div>
    </div>
  );
}

function RevenueTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  return (
    <div className="studio-chart-tooltip">
      <small>{label}</small>
      <strong>{money(point.revenue)}</strong>
      <span>{point.orderCount} {point.orderCount === 1 ? "order" : "orders"}</span>
    </div>
  );
}

function StatusTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="studio-chart-tooltip">
      <small>{payload[0].name}</small>
      <strong>{payload[0].value}</strong>
    </div>
  );
}

function localDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function formatChartDate(value) {
  if (!value) return "";
  return new Date(`${value}T00:00:00`).toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

function compactMoney(value) {
  const number = Number(value) || 0;
  if (Math.abs(number) >= 1000000) return `$${(number / 1000000).toFixed(1)}m`;
  if (Math.abs(number) >= 1000) return `$${Math.round(number / 1000)}k`;
  return `$${Math.round(number)}`;
}

function shortId(value) {
  const text = String(value || "");
  return text.length > 8 ? text.slice(0, 8) : text;
}

function readApiError(error, fallback) {
  if (!error?.body) return fallback;
  try {
    return JSON.parse(error.body).message || fallback;
  } catch {
    return fallback;
  }
}
