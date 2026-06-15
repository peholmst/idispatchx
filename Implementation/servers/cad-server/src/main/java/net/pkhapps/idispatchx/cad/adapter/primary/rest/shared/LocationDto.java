package net.pkhapps.idispatchx.cad.adapter.primary.rest.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import net.pkhapps.idispatchx.cad.domain.model.shared.location.Location;
import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import org.jspecify.annotations.Nullable;

/**
 * Sealed interface for REST API location DTOs.
 * <p>
 * The REST API represents coordinates without the domain model's CRS discriminator field,
 * using a simple {@code {"latitude": ..., "longitude": ...}} format (always EPSG:4326).
 * <p>
 * The {@code type} field drives Jackson polymorphic deserialization/serialization.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = LocationDto.ExactAddressDto.class, name = "exact_address"),
        @JsonSubTypes.Type(value = LocationDto.RoadIntersectionDto.class, name = "road_intersection"),
        @JsonSubTypes.Type(value = LocationDto.NamedPlaceDto.class, name = "named_place"),
        @JsonSubTypes.Type(value = LocationDto.RelativeLocationDto.class, name = "relative_location")
})
public sealed interface LocationDto
        permits LocationDto.ExactAddressDto, LocationDto.RoadIntersectionDto,
        LocationDto.NamedPlaceDto, LocationDto.RelativeLocationDto {

    /**
     * Converts this DTO to the domain {@link Location} model.
     */
    Location toDomain();

    /**
     * Creates a LocationDto from a domain {@link Location}, or {@code null} if the input is null.
     */
    static @Nullable LocationDto fromDomain(@Nullable Location location) {
        if (location == null) return null;
        return switch (location) {
            case Location.ExactAddress ea -> new ExactAddressDto(
                    ea.municipality(), ea.addressName(), ea.addressNumber(),
                    CoordinatesDto.fromDomain(ea.coordinates()), ea.additionalDetails());
            case Location.RoadIntersection ri -> new RoadIntersectionDto(
                    ri.municipality(), ri.roadNameA(), ri.roadNameB(),
                    CoordinatesDto.fromDomain(ri.coordinates()), ri.additionalDetails());
            case Location.NamedPlace np -> new NamedPlaceDto(
                    np.municipality(), np.name(),
                    CoordinatesDto.fromDomain(np.coordinates()), np.additionalDetails());
            case Location.RelativeLocation rl -> new RelativeLocationDto(
                    rl.municipality(), rl.referencePlace(), rl.additionalDetails(),
                    CoordinatesDto.fromDomain(rl.coordinates()));
        };
    }

    /**
     * Simple latitude/longitude pair (always EPSG:4326 in the REST API).
     */
    record CoordinatesDto(double latitude, double longitude) {

        static @Nullable CoordinatesDto fromDomain(@Nullable Coordinates coordinates) {
            if (coordinates == null) return null;
            if (coordinates instanceof Coordinates.Epsg4326 c) {
                return new CoordinatesDto(c.latitude(), c.longitude());
            }
            throw new IllegalArgumentException("Unsupported coordinates type for REST API: " + coordinates);
        }

        Coordinates.Epsg4326 toDomain() {
            return Coordinates.Epsg4326.of(latitude, longitude);
        }
    }

    record ExactAddressDto(
            Municipality municipality,
            MultilingualName addressName,
            @Nullable String addressNumber,
            @Nullable CoordinatesDto coordinates,
            @Nullable String additionalDetails
    ) implements LocationDto {

        @Override
        public Location toDomain() {
            return new Location.ExactAddress(municipality, addressName, addressNumber,
                    coordinates != null ? coordinates.toDomain() : null, additionalDetails);
        }
    }

    record RoadIntersectionDto(
            Municipality municipality,
            MultilingualName roadNameA,
            MultilingualName roadNameB,
            @Nullable CoordinatesDto coordinates,
            @Nullable String additionalDetails
    ) implements LocationDto {

        @Override
        public Location toDomain() {
            return new Location.RoadIntersection(municipality, roadNameA, roadNameB,
                    coordinates != null ? coordinates.toDomain() : null, additionalDetails);
        }
    }

    record NamedPlaceDto(
            Municipality municipality,
            MultilingualName name,
            @Nullable CoordinatesDto coordinates,
            @Nullable String additionalDetails
    ) implements LocationDto {

        @Override
        public Location toDomain() {
            return new Location.NamedPlace(municipality, name,
                    coordinates != null ? coordinates.toDomain() : null, additionalDetails);
        }
    }

    record RelativeLocationDto(
            Municipality municipality,
            MultilingualName referencePlace,
            String additionalDetails,
            @Nullable CoordinatesDto coordinates
    ) implements LocationDto {

        @Override
        public Location toDomain() {
            return new Location.RelativeLocation(municipality, referencePlace, additionalDetails,
                    coordinates != null ? coordinates.toDomain() : null);
        }
    }
}
