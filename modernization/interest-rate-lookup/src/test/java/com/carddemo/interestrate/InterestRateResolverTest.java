package com.carddemo.interestrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterestRateResolverTest {
    private DisclosureGroupReadModel readModel;
    private InterestRateResolver resolver;

    @BeforeEach
    void loadFixture() throws Exception {
        readModel = DisclosureGroupReadModel.fromPath(FixtureSupport.fixture());
        resolver = new InterestRateResolver(readModel);
    }

    @Test
    void directHitReturnsRate() {
        InterestRateResolution result = resolver.resolve(new DisclosureGroupKey("A000000000", "01", "0001"));
        assertEquals(ResolutionPath.DIRECT_HIT, result.path());
        assertEquals(Optional.of(new BigDecimal("1.50")), result.rate());
        assertEquals(List.of("00"), result.fileStatuses());
        assertTrue(result.displayMessages().isEmpty());
    }

    @Test
    void fallbackUsesDefaultRowAfterStatus23() {
        InterestRateResolution result = resolver.resolve(new DisclosureGroupKey("B000000000", "01", "0002"));
        assertEquals(ResolutionPath.DEFAULT_FALLBACK, result.path());
        assertEquals(Optional.of(new BigDecimal("2.50")), result.rate());
        assertEquals(List.of("23", "00"), result.fileStatuses());
        assertEquals(List.of("DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE"),
                result.displayMessages());
    }

    @Test
    void absentFromBothAbendsWithDefaultMessage() {
        InterestRateResolution result = resolver.resolve(new DisclosureGroupKey("B000000000", "05", "0002"));
        assertEquals(ResolutionPath.ABEND_DEFAULT_MISSING, result.path());
        assertEquals(Optional.empty(), result.rate());
        assertEquals(List.of("23", "23"), result.fileStatuses());
        assertEquals(List.of(
                "DISCLOSURE GROUP RECORD MISSING",
                "TRY WITH DEFAULT GROUP CODE",
                "ERROR READING DEFAULT DISCLOSURE GROUP"), result.displayMessages());
    }

    @Test
    void zeroAprGroupIsAValidDirectHit() {
        InterestRateResolution result = resolver.resolve(new DisclosureGroupKey("ZEROAPR   ", "07", "0001"));
        assertEquals(ResolutionPath.DIRECT_HIT, result.path());
        assertEquals(Optional.of(new BigDecimal("0.00")), result.rate());
    }

    @Test
    void injectedReadErrorReturnsDisclosureReadError() {
        DisclosureGroupReader failing = key -> new ReadResult("37", Optional.empty());
        InterestRateResolution result = new InterestRateResolver(failing)
                .resolve(new DisclosureGroupKey("A000000000", "01", "0001"));
        assertEquals(ResolutionPath.ABEND_READ_ERROR, result.path());
        assertEquals(List.of("37"), result.fileStatuses());
        assertEquals(List.of("ERROR READING DISCLOSURE GROUP FILE"), result.displayMessages());
    }

    @Test
    void status23IsSuccessBeforeFallbackDecisionAtLine422() {
        // CBACT04C.cbl:422 classifies '23' as successful before :437 decides to fall back.
        InterestRateResolution result = resolver.resolve(new DisclosureGroupKey("B000000000", "01", "0002"));
        assertEquals(ResolutionPath.DEFAULT_FALLBACK, result.path());
        assertEquals("23", result.fileStatuses().get(0));
    }

    @Test
    void defaultLookupHasDifferentSuccessSetAtLine446() {
        // CBACT04C.cbl:422 accepts '00' or '23'; :446 accepts only '00'.
        InterestRateResolution result = resolver.resolve(new DisclosureGroupKey("B000000000", "05", "0002"));
        assertEquals(List.of("23", "23"), result.fileStatuses());
        assertEquals(ResolutionPath.ABEND_DEFAULT_MISSING, result.path());
    }

    @Test
    void fallbackChangesOnlyGroupAndKeepsTypeAndCategoryAtLine437() {
        // CBACT04C.cbl:437 replaces only FD-DIS-ACCT-GROUP-ID.
        DisclosureGroupKey requested = new DisclosureGroupKey("B000000000", "01", "0002");
        InterestRateResolution result = resolver.resolve(requested);
        assertEquals(Optional.of(new BigDecimal("2.50")), result.rate());
        assertEquals(Optional.of(new BigDecimal("0.00")),
                resolver.resolve(new DisclosureGroupKey("DEFAULT   ", "02", "0001")).rate());
    }

    @Test
    void effectiveDefaultKeyIsSpacePaddedAtLine437() {
        // CBACT04C.cbl:437 moves seven chars into PIC X(10), yielding 'DEFAULT   '.
        assertEquals(Optional.of(new BigDecimal("2.50")),
                resolver.resolve(new DisclosureGroupKey("B000000000", "01", "0002")).rate());
        assertEquals(Optional.of(new BigDecimal("2.50")),
                readModel.read(new DisclosureGroupKey("DEFAULT   ", "01", "0002")).record()
                        .map(DisclosureGroupRecord::disIntRate));
    }
}
