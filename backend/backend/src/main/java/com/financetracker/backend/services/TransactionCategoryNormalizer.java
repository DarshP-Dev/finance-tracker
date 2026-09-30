package com.financetracker.backend.services;

import com.financetracker.backend.entities.TransactionCategory;

final class TransactionCategoryNormalizer {

    private TransactionCategoryNormalizer() {
    }

    static TransactionCategory normalize(TransactionCategory category) {
        return switch (category) {
            case FOOD -> TransactionCategory.DINING;
            case RENT -> TransactionCategory.HOUSING;
            case HEALTH -> TransactionCategory.HEALTHCARE;
            default -> category;
        };
    }
}
