package com.bunnycode.battle.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
class QuestionLevelConverter implements AttributeConverter<QuestionLevel, Integer> {

    @Override
    public Integer convertToDatabaseColumn(QuestionLevel level) {
        return level == null ? null : level.code();
    }

    @Override
    public QuestionLevel convertToEntityAttribute(Integer code) {
        return code == null ? null : QuestionLevel.fromCode(code);
    }
}
