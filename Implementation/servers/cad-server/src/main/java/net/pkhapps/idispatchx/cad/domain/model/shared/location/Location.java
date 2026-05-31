package net.pkhapps.idispatchx.cad.domain.model.shared.location;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Sealed interface representing where a call, incident, unit, or station is located.
 * <p>
 * A location is exactly one of four variants depending on the precision and type of
 * information available.
 */
public sealed interface Location permits Location.ExactAddress, Location.RoadIntersection,
        Location.NamedPlace, Location.RelativeLocation {

    int MAX_ADDRESS_NUMBER_LENGTH = 30;
    int MAX_ADDITIONAL_DETAILS_LENGTH = 1000;

    private static void requireNonNull(Object value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
    }

    private static void validateAddressNumber(@Nullable String addressNumber) {
        if (addressNumber != null && addressNumber.length() > MAX_ADDRESS_NUMBER_LENGTH) {
            throw new IllegalArgumentException(
                    "addressNumber must not exceed " + MAX_ADDRESS_NUMBER_LENGTH + " characters");
        }
    }

    private static void validateAdditionalDetails(@Nullable String additionalDetails) {
        if (additionalDetails != null && additionalDetails.length() > MAX_ADDITIONAL_DETAILS_LENGTH) {
            throw new IllegalArgumentException(
                    "additionalDetails must not exceed " + MAX_ADDITIONAL_DETAILS_LENGTH + " characters");
        }
    }

    /**
     * An exact street address or address point.
     *
     * @param municipality      the municipality (required)
     * @param addressName       the address name (required)
     * @param addressNumber     the address number, max 30 chars (optional)
     * @param coordinates       the coordinates (optional)
     * @param additionalDetails additional details, max 1000 chars (optional)
     */
    record ExactAddress(
            Municipality municipality,
            MultilingualName addressName,
            @Nullable String addressNumber,
            @Nullable Coordinates coordinates,
            @Nullable String additionalDetails
    ) implements Location {

        public ExactAddress {
            requireNonNull(municipality, "municipality");
            requireNonNull(addressName, "addressName");
            validateAddressNumber(addressNumber);
            validateAdditionalDetails(additionalDetails);
        }
    }

    /**
     * An intersection between two named roads.
     *
     * @param municipality      the municipality (required)
     * @param roadNameA         the first road name (required)
     * @param roadNameB         the second road name (required)
     * @param coordinates       the coordinates (optional)
     * @param additionalDetails additional details, max 1000 chars (optional)
     */
    record RoadIntersection(
            Municipality municipality,
            MultilingualName roadNameA,
            MultilingualName roadNameB,
            @Nullable Coordinates coordinates,
            @Nullable String additionalDetails
    ) implements Location {

        public RoadIntersection {
            requireNonNull(municipality, "municipality");
            requireNonNull(roadNameA, "roadNameA");
            requireNonNull(roadNameB, "roadNameB");
            validateAdditionalDetails(additionalDetails);
        }
    }

    /**
     * A named geographical place such as an island, village, or landmark.
     *
     * @param municipality      the municipality (required)
     * @param name              the place name (required)
     * @param coordinates       the coordinates (optional)
     * @param additionalDetails additional details, max 1000 chars (optional)
     */
    record NamedPlace(
            Municipality municipality,
            MultilingualName name,
            @Nullable Coordinates coordinates,
            @Nullable String additionalDetails
    ) implements Location {

        public NamedPlace {
            requireNonNull(municipality, "municipality");
            requireNonNull(name, "name");
            validateAdditionalDetails(additionalDetails);
        }
    }

    /**
     * An approximate location described relative to a known reference point.
     *
     * @param municipality      the municipality (required)
     * @param referencePlace    the reference place name (required)
     * @param additionalDetails the relative description, max 1000 chars (required)
     * @param coordinates       approximate coordinates (optional)
     */
    record RelativeLocation(
            Municipality municipality,
            MultilingualName referencePlace,
            String additionalDetails,
            @Nullable Coordinates coordinates
    ) implements Location {

        public RelativeLocation {
            requireNonNull(municipality, "municipality");
            requireNonNull(referencePlace, "referencePlace");
            requireNonNull(additionalDetails, "additionalDetails");
            validateAdditionalDetails(additionalDetails);
        }
    }
}
