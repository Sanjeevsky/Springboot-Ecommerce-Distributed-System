import React, { useEffect, useMemo, useState } from "react";
import { ArrowLeft, Plus, Save, Trash2 } from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { Badge, Button, Input, Select } from "../../components/index.js";
import { catalog, inventory } from "../../lib/services.js";

const EMPTY_PRODUCT = {
  name: "", model: "", description: "", mrpPrice: "", salePrice: "",
  gstValue: 0, discount: 0, status: 1, hasVariant: false,
  brandId: "", categoryId: "", subCategoryId: "", images: "",
};

const EMPTY_VARIANT = {
  condition1Name: "Option",
  condition1Value: "",
  condition2Name: "",
  condition2Value: "",
  mrpPrice: "",
  salePrice: "",
};

export default function StudioProductEditor() {
  const navigate = useNavigate();
  const { productId } = useParams();
  const editing = Boolean(productId);
  const [form, setForm] = useState(EMPTY_PRODUCT);
  const [brands, setBrands] = useState([]);
  const [categories, setCategories] = useState([]);
  const [variants, setVariants] = useState([]);
  const [stockRows, setStockRows] = useState([]);
  const [newVariant, setNewVariant] = useState(EMPTY_VARIANT);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    Promise.all([catalog.brandOptions(), catalog.categoryOptions()])
      .then(([brandRows, categoryRows]) => {
        setBrands(brandRows);
        setCategories(categoryRows);
      });
  }, []);

  useEffect(() => {
    if (!editing) return;
    Promise.all([catalog.get(productId), inventory.list()])
      .then(([product, inventoryRows]) => {
        setForm({
          name: product.name,
          model: product.model,
          description: product.description,
          mrpPrice: product.mrpPrice,
          salePrice: product.salePrice,
          gstValue: product.gstValue,
          discount: product.discount,
          status: product.status,
          hasVariant: product.hasVariant,
          brandId: product.brandId || "",
          categoryId: product.categoryId || "",
          subCategoryId: product.subCategoryId || "",
          images: product.images.join("\n"),
        });
        setVariants(product.variants);
        setStockRows(inventoryRows.filter((row) => row.productId === productId));
      })
      .catch((err) => setError(err.message));
  }, [editing, productId]);

  const subCategories = useMemo(
    () => categories.find((category) => category.id === form.categoryId)?.subCategories || [],
    [categories, form.categoryId]
  );

  const setField = (key, value) => setForm((current) => ({ ...current, [key]: value }));

  const saveProduct = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");
    const payload = {
      ...form,
      mrpPrice: Number(form.mrpPrice),
      salePrice: Number(form.salePrice),
      gstValue: Number(form.gstValue),
      discount: Number(form.discount),
      status: Number(form.status),
      images: form.images.split("\n").map((value) => value.trim()).filter(Boolean),
    };
    try {
      const saved = editing
        ? await catalog.update(productId, payload)
        : await catalog.create(payload);
      if (!editing) {
        navigate(`/studio/products/${saved.id}`, { replace: true });
      } else {
        setNotice("Product saved.");
      }
    } catch (err) {
      setError(err.body || err.message);
    } finally {
      setSaving(false);
    }
  };

  const addVariant = async () => {
    setError("");
    try {
      const saved = await catalog.addVariant(productId, {
        ...newVariant,
        mrpPrice: Number(newVariant.mrpPrice),
        salePrice: Number(newVariant.salePrice),
      });
      setVariants((current) => [...current, saved]);
      setNewVariant(EMPTY_VARIANT);
      setField("hasVariant", true);
      setNotice("Variant added.");
    } catch (err) {
      setError(err.body || err.message);
    }
  };

  const saveVariant = async (variant) => {
    try {
      const saved = await catalog.updateVariant(variant.id, {
        condition1Name: variant.condition1Name,
        condition1Value: variant.condition1Value,
        condition2Name: variant.condition2Name,
        condition2Value: variant.condition2Value,
        mrpPrice: Number(variant.mrpPrice),
        salePrice: Number(variant.salePrice),
      });
      setVariants((current) => current.map((row) => row.id === saved.id ? saved : row));
      setNotice("Variant saved.");
    } catch (err) {
      setError(err.body || err.message);
    }
  };

  const deleteVariant = async (variantId) => {
    if (!window.confirm("Delete this variant? Existing inventory history will remain.")) return;
    setError("");
    try {
      await catalog.deleteVariant(variantId);
      const remaining = variants.filter((row) => row.id !== variantId);
      setVariants(remaining);
      if (remaining.length === 0) setField("hasVariant", false);
      setStockRows((current) => current.filter((row) => row.variantId !== variantId));
      setNotice("Variant deleted.");
    } catch (err) {
      setError(err.body || err.message);
    }
  };

  const updateVariantField = (variantId, key, value) => {
    setVariants((current) => current.map((row) => row.id === variantId ? { ...row, [key]: value } : row));
  };

  const stockFor = (variantId) => stockRows.find((row) => (row.variantId || null) === (variantId || null));

  const setStock = async (variantId, totalQty) => {
    try {
      const saved = await inventory.set(productId, variantId, Number(totalQty));
      setStockRows((current) => {
        const exists = current.some((row) => (row.variantId || null) === (variantId || null));
        return exists
          ? current.map((row) => (row.variantId || null) === (variantId || null) ? saved : row)
          : [...current, saved];
      });
      setNotice("Stock saved.");
    } catch (err) {
      setError(err.body || err.message);
    }
  };

  return (
    <div>
      <div className="studio-page-heading">
        <div className="studio-heading-with-back">
          <button className="studio-icon-command" onClick={() => navigate("/studio/products")} title="Back to products" aria-label="Back to products">
            <ArrowLeft size={18} />
          </button>
          <div><h1>{editing ? "Edit product" : "New product"}</h1><p>{editing ? productId : "Create a catalog record."}</p></div>
        </div>
        {editing && <Badge tone={Number(form.status) === 1 ? "success" : "neutral"}>{Number(form.status) === 1 ? "Active" : "Retired"}</Badge>}
      </div>

      {error && <div className="studio-error">{String(error)}</div>}
      {notice && <div className="studio-notice">{notice}</div>}

      <form onSubmit={saveProduct}>
        <section className="studio-form-section">
          <div className="studio-section-heading"><h2>Product details</h2></div>
          <div className="studio-form-grid">
            <Field label="Name"><Input required value={form.name} onChange={(e) => setField("name", e.target.value)} /></Field>
            <Field label="Model"><Input required value={form.model} onChange={(e) => setField("model", e.target.value)} /></Field>
            <Field label="Brand">
              <Select required value={form.brandId} onChange={(e) => setField("brandId", e.target.value)} wrapStyle={{ width: "100%" }}>
                <option value="">Select brand</option>
                {brands.map((brand) => <option key={brand.id} value={brand.id}>{brand.label}</option>)}
              </Select>
            </Field>
            <Field label="Category">
              <Select required value={form.categoryId} onChange={(e) => {
                setField("categoryId", e.target.value);
                setField("subCategoryId", "");
              }} wrapStyle={{ width: "100%" }}>
                <option value="">Select category</option>
                {categories.map((category) => <option key={category.id} value={category.id}>{category.label}</option>)}
              </Select>
            </Field>
            <Field label="Subcategory">
              <Select required value={form.subCategoryId} onChange={(e) => setField("subCategoryId", e.target.value)} wrapStyle={{ width: "100%" }}>
                <option value="">Select subcategory</option>
                {subCategories.map((sub) => <option key={sub.id} value={sub.id}>{sub.label}</option>)}
              </Select>
            </Field>
            <Field label="Status">
              <Select value={form.status} onChange={(e) => setField("status", e.target.value)} wrapStyle={{ width: "100%" }}>
                <option value="1">Active</option>
                <option value="0">Retired</option>
              </Select>
            </Field>
            <Field label="Description" wide>
              <textarea className="studio-textarea" value={form.description} onChange={(e) => setField("description", e.target.value)} rows="4" />
            </Field>
            <Field label="Image URLs, one per line" wide>
              <textarea className="studio-textarea" value={form.images} onChange={(e) => setField("images", e.target.value)} rows="3" />
            </Field>
          </div>
        </section>

        <section className="studio-form-section">
          <div className="studio-section-heading"><h2>Pricing</h2></div>
          <div className="studio-form-grid studio-form-grid-four">
            <Field label="MRP"><Input required type="number" min="0.01" step="0.01" value={form.mrpPrice} onChange={(e) => setField("mrpPrice", e.target.value)} /></Field>
            <Field label="Sale price"><Input required type="number" min="0.01" step="0.01" value={form.salePrice} onChange={(e) => setField("salePrice", e.target.value)} /></Field>
            <Field label="GST %"><Input type="number" min="0" step="0.01" value={form.gstValue} onChange={(e) => setField("gstValue", e.target.value)} /></Field>
            <Field label="Discount"><Input type="number" min="0" step="0.01" value={form.discount} onChange={(e) => setField("discount", e.target.value)} /></Field>
          </div>
        </section>

        <div className="studio-form-actions">
          <Button type="submit" loading={saving} iconLeft={<Save size={17} />}>Save product</Button>
        </div>
      </form>

      {editing && (
        <section className="studio-form-section">
          <div className="studio-section-heading"><h2>Variants and stock</h2></div>

          {!form.hasVariant && (
            <StockEditor
              row={stockFor(null)}
              onSave={(qty) => setStock(null, qty)}
              label="Product stock"
            />
          )}

          {variants.map((variant) => (
            <div className="studio-variant-row" key={variant.id}>
              <Input value={variant.condition1Name || ""} onChange={(e) => updateVariantField(variant.id, "condition1Name", e.target.value)} placeholder="Option" />
              <Input value={variant.condition1Value || ""} onChange={(e) => updateVariantField(variant.id, "condition1Value", e.target.value)} placeholder="Value" />
              <Input type="number" value={variant.mrpPrice} onChange={(e) => updateVariantField(variant.id, "mrpPrice", e.target.value)} prefix="$" />
              <Input type="number" value={variant.salePrice} onChange={(e) => updateVariantField(variant.id, "salePrice", e.target.value)} prefix="$" />
              <StockEditor row={stockFor(variant.id)} onSave={(qty) => setStock(variant.id, qty)} compact />
              <button type="button" className="studio-icon-command" onClick={() => saveVariant(variant)} title="Save variant" aria-label="Save variant"><Save size={17} /></button>
              <button type="button" className="studio-icon-command danger" onClick={() => deleteVariant(variant.id)} title="Delete variant" aria-label="Delete variant"><Trash2 size={17} /></button>
            </div>
          ))}

          <div className="studio-variant-row studio-new-variant">
            <Input value={newVariant.condition1Name} onChange={(e) => setNewVariant({ ...newVariant, condition1Name: e.target.value })} placeholder="Option" />
            <Input value={newVariant.condition1Value} onChange={(e) => setNewVariant({ ...newVariant, condition1Value: e.target.value })} placeholder="Value" />
            <Input type="number" value={newVariant.mrpPrice} onChange={(e) => setNewVariant({ ...newVariant, mrpPrice: e.target.value })} prefix="$" placeholder="MRP" />
            <Input type="number" value={newVariant.salePrice} onChange={(e) => setNewVariant({ ...newVariant, salePrice: e.target.value })} prefix="$" placeholder="Sale" />
            <Button variant="secondary" iconLeft={<Plus size={16} />} onClick={addVariant} disabled={!newVariant.condition1Value || !newVariant.mrpPrice || !newVariant.salePrice}>Add variant</Button>
          </div>
        </section>
      )}
    </div>
  );
}

function Field({ label, wide = false, children }) {
  return <label className={wide ? "studio-field is-wide" : "studio-field"}><span>{label}</span>{children}</label>;
}

function StockEditor({ row, onSave, label, compact = false }) {
  const [qty, setQty] = useState(row?.totalQty ?? 0);
  useEffect(() => setQty(row?.totalQty ?? 0), [row?.totalQty]);
  return (
    <div className={compact ? "studio-stock-editor compact" : "studio-stock-editor"}>
      {label && <span>{label}</span>}
      <Input type="number" min={row?.reservedQty || 0} value={qty} onChange={(e) => setQty(e.target.value)} />
      {row && <small>{row.reservedQty} reserved</small>}
      <button type="button" className="studio-icon-command" onClick={() => onSave(qty)} title="Save stock" aria-label="Save stock"><Save size={16} /></button>
    </div>
  );
}
