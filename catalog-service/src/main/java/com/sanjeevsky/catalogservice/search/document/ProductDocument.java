package com.sanjeevsky.catalogservice.search.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

@Document(indexName = "products")
@Setting(shards = 1, replicas = 0)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String brand;

    @Field(type = FieldType.Keyword)
    private String brandId;

    @Field(type = FieldType.Keyword)
    private String categoryId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String categoryName;

    @Field(type = FieldType.Double)
    private double salePrice;

    @Field(type = FieldType.Double)
    private double mrpPrice;

    @Field(type = FieldType.Integer)
    private int status;
}
