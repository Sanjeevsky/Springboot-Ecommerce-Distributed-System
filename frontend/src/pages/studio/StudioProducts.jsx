import React, { useEffect, useState } from "react";
import { Archive, Download, Edit3, Plus, Search } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { Badge, Button, Input, Select } from "../../components/index.js";
import { catalog } from "../../lib/services.js";
import { money } from "../../lib/format.js";
import { downloadCsv } from "../../lib/csv.js";

export default function StudioProducts() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = React.useCallback(() => {
    setLoading(true);
    setError("");
    catalog.adminList({ q: query, status, size: 100 })
      .then((result) => setProducts(result.items))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [query, status]);

  useEffect(() => {
    const timer = setTimeout(load, 200);
    return () => clearTimeout(timer);
  }, [load]);

  const retire = async (product) => {
    if (!window.confirm(`Retire "${product.title}" from the storefront?`)) return;
    try {
      await catalog.retire(product.id);
      load();
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div>
      <div className="studio-page-heading studio-action-heading">
        <div><h1>Products</h1><p>{products.length} catalog records in this view.</p></div>
        <div className="studio-heading-actions">
          <Button
            variant="secondary"
            iconLeft={<Download size={17} />}
            disabled={!products.length}
            onClick={() => downloadCsv("trove-products.csv", [
              { label: "Product ID", value: "id" },
              { label: "Name", value: "title" },
              { label: "Brand", value: "brand" },
              { label: "Model", value: "model" },
              { label: "Category", value: "catLabel" },
              { label: "Sale price", value: "salePrice" },
              { label: "MRP price", value: "mrpPrice" },
              { label: "Variants", value: (product) => product.variants.length },
              { label: "Status", value: (product) => product.active ? "Active" : "Retired" },
            ], products)}
          >
            Export CSV
          </Button>
          <Button iconLeft={<Plus size={17} />} onClick={() => navigate("/studio/products/new")}>New product</Button>
        </div>
      </div>

      <div className="studio-toolbar">
        <Input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search name or model"
          iconLeft={<Search size={17} />}
          wrapStyle={{ flex: "1 1 320px" }}
        />
        <Select value={status} onChange={(event) => setStatus(event.target.value)} wrapStyle={{ width: 170 }}>
          <option value="">All statuses</option>
          <option value="1">Active</option>
          <option value="0">Retired</option>
        </Select>
      </div>

      {error && <div className="studio-error">{error}</div>}

      <div className="studio-table-wrap">
        <table className="studio-table">
          <thead>
            <tr>
              <th>Product</th>
              <th>Category</th>
              <th>Price</th>
              <th>Variants</th>
              <th>Status</th>
              <th aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {products.map((product) => (
              <tr key={product.id}>
                <td>
                  <div className="studio-product-cell">
                    <img src={product.image} alt="" />
                    <div><strong>{product.title}</strong><span>{product.brand} · {product.model}</span></div>
                  </div>
                </td>
                <td>{product.catLabel || "Uncategorized"}</td>
                <td>
                  <div className="studio-price-cell">
                    <strong>{money(product.salePrice)}</strong>
                    {product.mrpPrice > product.salePrice && <span>{money(product.mrpPrice)}</span>}
                  </div>
                </td>
                <td>{product.variants.length}</td>
                <td><Badge tone={product.active ? "success" : "neutral"}>{product.active ? "Active" : "Retired"}</Badge></td>
                <td>
                  <div className="studio-row-actions">
                    <button onClick={() => navigate(`/studio/products/${product.id}`)} title="Edit product" aria-label={`Edit ${product.title}`}>
                      <Edit3 size={17} />
                    </button>
                    {product.active && (
                      <button onClick={() => retire(product)} title="Retire product" aria-label={`Retire ${product.title}`}>
                        <Archive size={17} />
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {!loading && products.length === 0 && (
              <tr><td colSpan="6" className="studio-empty">No products match this view.</td></tr>
            )}
            {loading && (
              <tr><td colSpan="6" className="studio-empty">Loading products...</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
