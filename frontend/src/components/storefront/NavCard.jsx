import React from "react";
import { useNavigate } from "react-router-dom";
import { ProductCard } from "../index.js";

// Wraps ProductCard so a click navigates to the product page (except when the
// add-to-cart or wishlist buttons are pressed).
export function NavCard({ product, layout }) {
  const navigate = useNavigate();
  const { onWish, onAdd, wished } = product;
  return (
    <div
      onClickCapture={(e) => {
        if (e.target.closest("button")) return;
        e.preventDefault();
        navigate(`/p/${product.id}`);
      }}
      style={{ cursor: "pointer", display: "contents" }}
    >
      <ProductCard {...product} layout={layout} wished={wished} onWish={onWish} onAdd={onAdd} href={`/p/${product.id}`} />
    </div>
  );
}
