import React, { useEffect, useMemo, useState } from "react";
import {
  CheckCircle2,
  CircleDollarSign,
  Download,
  Power,
  PowerOff,
  Search,
  TicketPercent,
} from "lucide-react";
import { Badge, Button, Input, Select } from "../../components/index.js";
import { money } from "../../lib/format.js";
import { coupons } from "../../lib/services.js";
import { downloadCsv } from "../../lib/csv.js";

const EMPTY_FORM = {
  code: "",
  type: "PERCENTAGE",
  value: "",
  minOrderAmount: "0",
  maxUsageCount: "-1",
  expiryDate: "",
};

function isExpired(coupon) {
  return coupon.expiryDate && coupon.expiryDate < new Date().toISOString().slice(0, 10);
}

function couponStatus(coupon) {
  if (!coupon.active) return { label: "Inactive", tone: "neutral" };
  if (isExpired(coupon)) return { label: "Expired", tone: "danger" };
  if (coupon.maxUsageCount >= 0 && coupon.usedCount >= coupon.maxUsageCount) {
    return { label: "Exhausted", tone: "warning" };
  }
  return { label: "Active", tone: "success" };
}

function discountLabel(coupon) {
  return coupon.type === "PERCENTAGE" ? `${coupon.value}%` : money(coupon.value);
}

export default function StudioCoupons() {
  const [rows, setRows] = useState([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = React.useCallback(() => {
    setLoading(true);
    setError("");
    coupons.adminList()
      .then(setRows)
      .catch((err) => setError(err.body || err.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const metrics = useMemo(() => ({
    total: rows.length,
    active: rows.filter((coupon) => couponStatus(coupon).label === "Active").length,
    expired: rows.filter(isExpired).length,
    redemptions: rows.reduce((sum, coupon) => sum + Number(coupon.usedCount || 0), 0),
  }), [rows]);

  const visible = rows.filter((coupon) => {
    const status = couponStatus(coupon).label.toLowerCase();
    return (!query || coupon.code.toLowerCase().includes(query.toLowerCase()))
      && (!statusFilter || status === statusFilter);
  });

  const updateForm = (field) => (event) => {
    const value = event.target.value;
    setForm((current) => ({ ...current, [field]: field === "code" ? value.toUpperCase() : value }));
  };

  const createCoupon = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");
    try {
      await coupons.create({
        code: form.code.trim(),
        type: form.type,
        value: Number(form.value),
        minOrderAmount: Number(form.minOrderAmount),
        maxUsageCount: Number(form.maxUsageCount),
        expiryDate: form.expiryDate || null,
        active: true,
      });
      setForm(EMPTY_FORM);
      setNotice(`Coupon ${form.code.trim()} created.`);
      load();
    } catch (err) {
      setError(err.body || err.message);
    } finally {
      setSaving(false);
    }
  };

  const setActive = async (coupon) => {
    setError("");
    setNotice("");
    try {
      const updated = await coupons.setActive(coupon.id, !coupon.active);
      setRows((current) => current.map((row) => row.id === coupon.id ? updated : row));
      setNotice(`${coupon.code} ${updated.active ? "activated" : "deactivated"}.`);
    } catch (err) {
      setError(err.body || err.message);
    }
  };

  return (
    <div>
      <div className="studio-page-heading studio-action-heading">
        <div>
          <h1>Coupons</h1>
          <p>Create promotions and control which offers can be redeemed.</p>
        </div>
        <div className="studio-heading-actions">
          <Button
            variant="secondary"
            iconLeft={<Download size={17} />}
            disabled={!visible.length}
            onClick={() => downloadCsv("trove-coupons.csv", [
              { label: "Coupon ID", value: "id" },
              { label: "Code", value: "code" },
              { label: "Type", value: "type" },
              { label: "Value", value: "value" },
              { label: "Minimum order", value: "minOrderAmount" },
              { label: "Used", value: "usedCount" },
              { label: "Usage limit", value: (coupon) => coupon.maxUsageCount < 0 ? "Unlimited" : coupon.maxUsageCount },
              { label: "Expiry date", value: (coupon) => coupon.expiryDate || "" },
              { label: "Status", value: (coupon) => couponStatus(coupon).label },
            ], visible)}
          >
            Export CSV
          </Button>
        </div>
      </div>

      <div className="studio-metrics">
        <div className="studio-metric studio-metric-primary">
          <span><TicketPercent size={19} /></span>
          <div><small>Total coupons</small><strong>{metrics.total}</strong></div>
        </div>
        <div className="studio-metric studio-metric-success">
          <span><CheckCircle2 size={19} /></span>
          <div><small>Active</small><strong>{metrics.active}</strong></div>
        </div>
        <div className="studio-metric studio-metric-warning">
          <span><PowerOff size={19} /></span>
          <div><small>Expired</small><strong>{metrics.expired}</strong></div>
        </div>
        <div className="studio-metric studio-metric-info">
          <span><CircleDollarSign size={19} /></span>
          <div><small>Redemptions</small><strong>{metrics.redemptions}</strong></div>
        </div>
      </div>

      <form className="studio-form-section" onSubmit={createCoupon}>
        <div className="studio-section-heading">
          <div><h2>New coupon</h2><span>Usage limit -1 allows unlimited redemptions.</span></div>
        </div>
        <div className="studio-coupon-form-grid">
          <label className="studio-field">
            <span>Code</span>
            <Input required value={form.code} onChange={updateForm("code")} placeholder="SUMMER20" />
          </label>
          <label className="studio-field">
            <span>Discount type</span>
            <Select value={form.type} onChange={updateForm("type")}>
              <option value="PERCENTAGE">Percentage</option>
              <option value="FIXED">Fixed amount</option>
            </Select>
          </label>
          <label className="studio-field">
            <span>Value</span>
            <Input required type="number" min="0.01" step="0.01" value={form.value} onChange={updateForm("value")} />
          </label>
          <label className="studio-field">
            <span>Minimum order</span>
            <Input required type="number" min="0" step="0.01" value={form.minOrderAmount} onChange={updateForm("minOrderAmount")} />
          </label>
          <label className="studio-field">
            <span>Usage limit</span>
            <Input required type="number" min="-1" step="1" value={form.maxUsageCount} onChange={updateForm("maxUsageCount")} />
          </label>
          <label className="studio-field">
            <span>Expiry date</span>
            <Input type="date" value={form.expiryDate} onChange={updateForm("expiryDate")} />
          </label>
        </div>
        <div className="studio-form-actions">
          <Button type="submit" loading={saving} iconLeft={<TicketPercent size={17} />}>Create coupon</Button>
        </div>
      </form>

      <section className="studio-section">
        <div className="studio-section-heading">
          <div><h2>Coupon catalog</h2><span>{visible.length} offers in this view.</span></div>
        </div>
        <div className="studio-toolbar">
          <Input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search coupon code"
            iconLeft={<Search size={17} />}
            wrapStyle={{ flex: "1 1 320px" }}
          />
          <Select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)} wrapStyle={{ width: 170 }}>
            <option value="">All statuses</option>
            <option value="active">Active</option>
            <option value="inactive">Inactive</option>
            <option value="expired">Expired</option>
            <option value="exhausted">Exhausted</option>
          </Select>
        </div>

        {error && <div className="studio-error">{String(error)}</div>}
        {notice && <div className="studio-notice">{notice}</div>}

        <div className="studio-table-wrap">
          <table className="studio-table studio-coupon-table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Discount</th>
                <th>Minimum order</th>
                <th>Usage</th>
                <th>Expires</th>
                <th>Status</th>
                <th aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {visible.map((coupon) => {
                const status = couponStatus(coupon);
                return (
                  <tr key={coupon.id}>
                    <td><strong className="studio-coupon-code">{coupon.code}</strong></td>
                    <td className="studio-number">{discountLabel(coupon)}</td>
                    <td className="studio-number">{money(coupon.minOrderAmount)}</td>
                    <td className="studio-number">
                      {coupon.usedCount} / {coupon.maxUsageCount < 0 ? "Unlimited" : coupon.maxUsageCount}
                    </td>
                    <td>{coupon.expiryDate || "No expiry"}</td>
                    <td><Badge tone={status.tone}>{status.label}</Badge></td>
                    <td>
                      <div className="studio-row-actions">
                        <button
                          type="button"
                          onClick={() => setActive(coupon)}
                          title={coupon.active ? "Deactivate coupon" : "Activate coupon"}
                          aria-label={`${coupon.active ? "Deactivate" : "Activate"} ${coupon.code}`}
                        >
                          {coupon.active ? <PowerOff size={17} /> : <Power size={17} />}
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
              {!loading && visible.length === 0 && (
                <tr><td colSpan="7" className="studio-empty">No coupons match this view.</td></tr>
              )}
              {loading && (
                <tr><td colSpan="7" className="studio-empty">Loading coupons...</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
