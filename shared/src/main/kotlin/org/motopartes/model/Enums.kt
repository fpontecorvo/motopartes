package org.motopartes.model

enum class Currency { USD, ARS }

enum class OrderStatus { CREATED, CONFIRMED, ASSEMBLED, INVOICED, CANCELLED }

enum class MovementType { CLIENT_PAYMENT, SUPPLIER_PAYMENT, SALE, PURCHASE }
