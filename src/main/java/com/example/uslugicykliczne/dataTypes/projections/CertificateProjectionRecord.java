package com.example.uslugicykliczne.dataTypes.projections;

import java.time.LocalDateTime;

public record CertificateProjectionRecord(
    int idCertificate,
    String certificateSerialNumber,
    LocalDateTime validFrom,
    LocalDateTime validTo,
    Integer cardType,
    String cardNumber,
    String nameInOrganisation
) {
}
