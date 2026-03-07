package net.pkhapps.idispatchx.gis.server.api.geocode;

import io.javalin.http.Context;
import net.pkhapps.idispatchx.common.api.ValidationException;
import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import net.pkhapps.idispatchx.gis.server.api.error.GisErrorCode;
import net.pkhapps.idispatchx.gis.server.service.geocode.DatabaseUnavailableException;
import net.pkhapps.idispatchx.gis.server.service.geocode.GeocodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeocodeControllerTest {

    @Mock
    private GeocodeService geocodeService;

    @Mock
    private Context ctx;

    private GeocodeController controller;

    @BeforeEach
    void setUp() {
        controller = new GeocodeController(geocodeService);
    }

    // ==================== Successful search ====================

    @Test
    void handleSearch_validRequest_returnsJsonResponse() throws Exception {
        when(ctx.queryParam("q")).thenReturn("Mannerheimintie 5");
        when(ctx.queryParam("limit")).thenReturn("10");
        when(ctx.queryParam("municipality")).thenReturn(null);

        var response = SearchResponse.of(List.of(
                new AddressResult(
                        MultilingualName.ofFinnishFields("Mannerheimintie", null, null, null, null),
                        "5",
                        Municipality.of(MunicipalityCode.of("091"),
                                MultilingualName.ofFinnishFields("Helsinki", null, null, null, null)),
                        Coordinates.Epsg4326.of(60.17, 24.93),
                        AddressSource.ADDRESS_POINT)
        ), "Mannerheimintie 5");

        when(geocodeService.search(any(SearchRequest.class))).thenReturn(response);

        invokeHandleSearch();

        verify(ctx).json(response);
    }

    // ==================== Query validation errors → INVALID_QUERY ====================

    @Test
    void handleSearch_nullQuery_throwsValidationExceptionWithInvalidQueryCode() {
        when(ctx.queryParam("q")).thenReturn(null);
        when(ctx.queryParam("limit")).thenReturn(null);
        when(ctx.queryParam("municipality")).thenReturn(null);

        var ex = assertThrows(ValidationException.class, this::invokeHandleSearch);
        assertEquals(GisErrorCode.INVALID_QUERY, ex.getErrorCode());
    }

    @Test
    void handleSearch_blankQuery_throwsValidationExceptionWithInvalidQueryCode() {
        when(ctx.queryParam("q")).thenReturn("  ");
        when(ctx.queryParam("limit")).thenReturn(null);
        when(ctx.queryParam("municipality")).thenReturn(null);

        var ex = assertThrows(ValidationException.class, this::invokeHandleSearch);
        assertEquals(GisErrorCode.INVALID_QUERY, ex.getErrorCode());
    }

    @Test
    void handleSearch_queryTooShort_throwsValidationExceptionWithInvalidQueryCode() {
        when(ctx.queryParam("q")).thenReturn("ab");
        when(ctx.queryParam("limit")).thenReturn(null);
        when(ctx.queryParam("municipality")).thenReturn(null);

        var ex = assertThrows(ValidationException.class, this::invokeHandleSearch);
        assertEquals(GisErrorCode.INVALID_QUERY, ex.getErrorCode());
    }

    // ==================== Parameter validation errors → INVALID_PARAMETER ====================

    @Test
    void handleSearch_invalidLimit_throwsValidationExceptionWithInvalidParameterCode() {
        when(ctx.queryParam("q")).thenReturn("Helsinki");
        when(ctx.queryParam("limit")).thenReturn("abc");
        when(ctx.queryParam("municipality")).thenReturn(null);

        var ex = assertThrows(ValidationException.class, this::invokeHandleSearch);
        assertEquals(GisErrorCode.INVALID_PARAMETER, ex.getErrorCode());
    }

    // ==================== Database unavailable ====================

    @Test
    void handleSearch_databaseUnavailable_returns503() throws Exception {
        when(ctx.queryParam("q")).thenReturn("Helsinki");
        when(ctx.queryParam("limit")).thenReturn(null);
        when(ctx.queryParam("municipality")).thenReturn(null);

        when(geocodeService.search(any(SearchRequest.class)))
                .thenThrow(new DatabaseUnavailableException("DB down", new RuntimeException()));

        invokeHandleSearch();

        verify(ctx).status(503);
        verify(ctx).json(any(SearchResponse.class));
    }

    // ==================== helpers ====================

    private void invokeHandleSearch() throws Exception {
        var method = GeocodeController.class.getDeclaredMethod("handleSearch", Context.class);
        method.setAccessible(true);
        try {
            method.invoke(controller, ctx);
        } catch (java.lang.reflect.InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }
}
