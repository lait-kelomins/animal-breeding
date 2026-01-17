package com.laits.breeding;

import com.laits.breeding.models.GrowthStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GrowthStage enum.
 */
class GrowthStageTest {

    @Test
    void testBabyProperties() {
        GrowthStage baby = GrowthStage.BABY;

        assertEquals(0.5f, baby.getSizeMultiplier());
        assertEquals(0, baby.getStageIndex());
        assertFalse(baby.canBreed());
        assertTrue(baby.hasNextStage());
        assertEquals(GrowthStage.JUVENILE, baby.getNextStage());
        assertEquals("Baby", baby.getDisplayName());
    }

    @Test
    void testJuvenileProperties() {
        GrowthStage juvenile = GrowthStage.JUVENILE;

        assertEquals(0.75f, juvenile.getSizeMultiplier());
        assertEquals(1, juvenile.getStageIndex());
        assertFalse(juvenile.canBreed());
        assertTrue(juvenile.hasNextStage());
        assertEquals(GrowthStage.ADULT, juvenile.getNextStage());
        assertEquals("Juvenile", juvenile.getDisplayName());
    }

    @Test
    void testAdultProperties() {
        GrowthStage adult = GrowthStage.ADULT;

        assertEquals(1.0f, adult.getSizeMultiplier());
        assertEquals(2, adult.getStageIndex());
        assertTrue(adult.canBreed());
        assertFalse(adult.hasNextStage());
        assertEquals(GrowthStage.ADULT, adult.getNextStage()); // Returns itself
        assertEquals("Adult", adult.getDisplayName());
    }

    @Test
    void testGrowthProgression() {
        GrowthStage stage = GrowthStage.BABY;

        // Baby -> Juvenile
        assertTrue(stage.hasNextStage());
        stage = stage.getNextStage();
        assertEquals(GrowthStage.JUVENILE, stage);

        // Juvenile -> Adult
        assertTrue(stage.hasNextStage());
        stage = stage.getNextStage();
        assertEquals(GrowthStage.ADULT, stage);

        // Adult stays Adult
        assertFalse(stage.hasNextStage());
        stage = stage.getNextStage();
        assertEquals(GrowthStage.ADULT, stage);
    }
}
