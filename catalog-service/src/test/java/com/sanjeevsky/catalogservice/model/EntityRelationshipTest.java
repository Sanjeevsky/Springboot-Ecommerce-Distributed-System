package com.sanjeevsky.catalogservice.model;

import org.junit.jupiter.api.Test;

import javax.persistence.CascadeType;
import javax.persistence.ManyToOne;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class EntityRelationshipTest {

    @Test
    void variantDeletionDoesNotCascadeToProduct() throws NoSuchFieldException {
        assertParentRelationshipDoesNotCascadeRemove(Variant.class, "product");
    }

    @Test
    void subCategoryDeletionDoesNotCascadeToCategory() throws NoSuchFieldException {
        assertParentRelationshipDoesNotCascadeRemove(SubCategory.class, "category");
    }

    private void assertParentRelationshipDoesNotCascadeRemove(
            Class<?> entityType,
            String fieldName) throws NoSuchFieldException {
        Field field = entityType.getDeclaredField(fieldName);
        ManyToOne relationship = field.getAnnotation(ManyToOne.class);

        assertThat(relationship).isNotNull();
        assertThat(relationship.cascade())
                .doesNotContain(CascadeType.ALL, CascadeType.REMOVE);
    }
}
