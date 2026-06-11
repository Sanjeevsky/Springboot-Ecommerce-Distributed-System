import React, { useEffect, useMemo, useState } from "react";
import { Download, Minus, Plus, Save, Search } from "lucide-react";
import { Badge, Button, Input, Switch } from "../../components/index.js";
import { catalog, inventory } from "../../lib/services.js";
import { downloadCsv } from "../../lib/csv.js";

export default function StudioInventory() {
  const [rows, setRows] = useState([]);
  const [products, setProducts] = useState([]);
  const [query, setQuery] = useState("");
  const [lowOnly, setLowOnly] = useState(false);
  const [error, setError] = useState("");

  const load = () => Promise.all([inventory.list(), catalog.adminList({ size: 100 })])
    .then(([stockRows, productResult]) => {
      setRows(stockRows);
      setProducts(productResult.items);
    })
    .catch((err) => setError(err.message));

  useEffect(() => { load(); }, []);

  const productMap = useMemo(
    () => new Map(products.map((product) => [product.id, product])),
    [products]
  );

  const visible = rows.filter((row) => {
    const product = productMap.get(row.productId);
    const text = `${product?.title || ""} ${product?.model || ""} ${row.productId}`.toLowerCase();
    const available = row.totalQty - row.reservedQty;
    return (!query || text.includes(query.toLowerCase())) && (!lowOnly || available <= 5);
  });

  const setRowQty = (id, totalQty) => {
    setRows((current) => current.map((row) => row.id === id ? { ...row, totalQty: Math.max(row.reservedQty, Number(totalQty)) } : row));
  };

  const save = async (row) => {
    setError("");
    try {
      const saved = await inventory.set(row.productId, row.variantId, row.totalQty);
      setRows((current) => current.map((entry) => entry.id === row.id ? saved : entry));
    } catch (err) {
      setError(err.body || err.message);
    }
  };

  return (
    <div>
      <div className="studio-page-heading studio-action-heading">
        <div><h1>Inventory</h1><p>{visible.length} stock records in this view.</p></div>
        <div className="studio-heading-actions">
          <Button
            variant="secondary"
            iconLeft={<Download size={17} />}
            disabled={!visible.length}
            onClick={() => downloadCsv("trove-inventory.csv", [
              { label: "Product ID", value: "productId" },
              { label: "Product", value: (row) => productMap.get(row.productId)?.title || "" },
              { label: "Model", value: (row) => productMap.get(row.productId)?.model || "" },
              { label: "Variant ID", value: (row) => row.variantId || "" },
              { label: "Reserved", value: "reservedQty" },
              { label: "Total", value: "totalQty" },
              { label: "Available", value: (row) => row.totalQty - row.reservedQty },
            ], visible)}
          >
            Export CSV
          </Button>
        </div>
      </div>

      <div className="studio-toolbar">
        <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search product or ID" iconLeft={<Search size={17} />} wrapStyle={{ flex: "1 1 320px" }} />
        <label className="studio-toggle-label"><Switch checked={lowOnly} onChange={setLowOnly} /> Low stock only</label>
      </div>

      {error && <div className="studio-error">{String(error)}</div>}

      <div className="studio-table-wrap">
        <table className="studio-table">
          <thead>
            <tr><th>Product</th><th>Variant</th><th>Available</th><th>Reserved</th><th>Total quantity</th><th aria-label="Save" /></tr>
          </thead>
          <tbody>
            {visible.map((row) => {
              const product = productMap.get(row.productId);
              const variant = product?.variants.find((item) => item.id === row.variantId);
              const available = row.totalQty - row.reservedQty;
              return (
                <tr key={row.id}>
                  <td><strong>{product?.title || row.productId}</strong><div className="studio-muted">{product?.model}</div></td>
                  <td>{variant ? `${variant.condition1Name}: ${variant.condition1Value}` : "Default"}</td>
                  <td><Badge tone={available <= 5 ? "warning" : "success"}>{available}</Badge></td>
                  <td className="studio-number">{row.reservedQty}</td>
                  <td>
                    <div className="studio-quantity-control">
                      <button type="button" onClick={() => setRowQty(row.id, row.totalQty - 1)} title="Decrease total" aria-label="Decrease total"><Minus size={15} /></button>
                      <input type="number" min={row.reservedQty} value={row.totalQty} onChange={(e) => setRowQty(row.id, e.target.value)} />
                      <button type="button" onClick={() => setRowQty(row.id, row.totalQty + 1)} title="Increase total" aria-label="Increase total"><Plus size={15} /></button>
                    </div>
                  </td>
                  <td><button type="button" className="studio-icon-command" onClick={() => save(row)} title="Save quantity" aria-label="Save quantity"><Save size={17} /></button></td>
                </tr>
              );
            })}
            {visible.length === 0 && <tr><td colSpan="6" className="studio-empty">No stock records match this view.</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  );
}
