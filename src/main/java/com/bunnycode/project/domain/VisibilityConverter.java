package com.bunnycode.project.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps the legacy {@code is_public tinyint(1)} column to an explicit enum. */
@Converter
class VisibilityConverter implements AttributeConverter<Visibility, Boolean> {

    @Override
    public Boolean convertToDatabaseColumn(Visibility visibility) {
        return visibility == null ? null : visibility == Visibility.PUBLIC;
    }

    @Override
    public Visibility convertToEntityAttribute(Boolean isPublic) {
        if (isPublic == null) {
            return null;
        }
        return isPublic ? Visibility.PUBLIC : Visibility.PRIVATE;
    }
}
