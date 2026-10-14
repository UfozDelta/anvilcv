package com.resumepipeline.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fabricated-metric gate has to see engineering quantities too: an invented "500 psi" or
 * "0.05 mm" is exactly as made-up as an invented "200ms". See MULTI_FIELD_PLAN.md step 1.
 */
class EngineeringUnitsTest {

    private static final String SRC = "Sized the press frame for 500 psi at 120 °C. Held 0.05 mm on the housings. "
            + "Spindle ran 3,000 rpm with a 15 kW motor, 8 kN preload and a 12 N·m torque limit.";

    private static List<String> fab(String bullet) {
        return BulletTextRules.fabricatedNumbers(bullet, SRC);
    }

    @Test void sourcedPressureIsKept()    { assertTrue(fab("Rated the frame to **500 psi**.").isEmpty()); }
    @Test void sourcedTemperatureIsKept() { assertTrue(fab("Validated operation at **120 °C**.").isEmpty()); }
    @Test void sourcedToleranceIsKept()   { assertTrue(fab("Held **0.05 mm** across the housings.").isEmpty()); }
    @Test void sourcedSpeedIsKept()       { assertTrue(fab("Ran the spindle at **3000 rpm**.").isEmpty()); }
    @Test void sourcedPowerIsKept()       { assertTrue(fab("Specified a **15 kW** motor.").isEmpty()); }
    @Test void sourcedForceIsKept()       { assertTrue(fab("Set an **8 kN** preload.").isEmpty()); }
    @Test void sourcedTorqueIsKept()      { assertTrue(fab("Capped torque at **12 N·m**.").isEmpty()); }

    @Test void inventedPressureIsFabricated() {
        assertEquals(List.of("750"), fab("Rated the frame to **750 psi**."));
    }

    @Test void inventedGluedUnitIsFabricated() {
        assertEquals(List.of("90kg"), fab("Cut the bracket to **90kg**."));
    }

    @Test void inventedSpeedIsFabricated() {
        assertEquals(List.of("6000"), fab("Spun the spindle at **6000 rpm**."));
    }

    // "500 psi" in the source must not vouch for "500 MPa": same digits, a thousand times the pressure.
    @Test void sameDigitsInAnotherUnitAreFabricated() {
        assertEquals(List.of("500"), fab("Rated the frame to **500 MPa**."));
    }

    // A bare count in the source ("500 cycles") never states a pressure.
    @Test void bareCountDoesNotVouchForAUnit() {
        assertEquals(List.of("500"), BulletTextRules.fabricatedNumbers("Rated it to **500 psi**.", "Ran 500 cycles."));
    }

    // Part, material and standard names carry digits that are identifiers, never claims.
    @Test void identifiersAreNotClaims() {
        String bullet = "Machined **6061-T6** brackets with M8 bolts to ISO 9001 and SAE J403 steel.";
        assertTrue(BulletTextRules.fabricatedNumbers(bullet, "Worked on brackets.").isEmpty());
    }

    // A lone capital N is Newtons only after a number; "N" the letter and "in" the word stay words.
    @Test void unitWordsInProseAreNotClaims() {
        assertTrue(BulletTextRules.fabricatedNumbers("Led a team of 4 in the lab with N sensors.", "Led a team of 4.").isEmpty());
    }

    // A decimal is one number. "0.05 mm" and "0.5 mm" are a tenfold difference in tolerance, not the same digit run.
    @Test void aTenTimesLooserToleranceIsFabricated() {
        assertEquals(List.of("0.5"), fab("Held **0.5 mm** across the housings."));
    }

    @Test void aTenTimesTighterToleranceIsFabricated() {
        assertEquals(List.of("0.05"), BulletTextRules.fabricatedNumbers("Held **0.05 mm**.", "Held 0.5 mm on the housings."));
    }

    @Test void trailingZerosDoNotChangeTheQuantity() {
        assertTrue(BulletTextRules.fabricatedNumbers("Held **0.50 mm**.", "Held 0.5 mm on the housings.").isEmpty());
    }

    @Test void aDecimalPercentIsNotVouchedForByItsFractionalDigit() {
        assertEquals(List.of("99.9%"),BulletTextRules.fabricatedNumbers("Reached **99.9%** uptime.", "Cut errors by 9% last quarter."));
        assertTrue(BulletTextRules.fabricatedNumbers("Reached **99.9%** uptime.", "Held 99.9% uptime.").isEmpty());
    }

    @Test void aDecimalMagnitudeScalesExactly() {
        assertTrue(BulletTextRules.fabricatedNumbers("Served **1.5M** users.", "Served 1.5 million users.").isEmpty());
        assertEquals(List.of("15M"), BulletTextRules.fabricatedNumbers("Served **15M** users.", "Served 1.5 million users."));
    }

    @Test void softwareQuantitiesStillWork() {
        assertEquals(List.of("500ms"), BulletTextRules.fabricatedNumbers("Cut latency to **500ms**.", "Served 5 regions."));
    }
}
