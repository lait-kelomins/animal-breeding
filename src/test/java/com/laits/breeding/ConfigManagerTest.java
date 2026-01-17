package com.laits.breeding;

import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.util.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConfigManager.
 */
class ConfigManagerTest {

    private ConfigManager config;

    @BeforeEach
    void setUp() {
        config = new ConfigManager();
    }

    @Test
    void testDefaultBreedingCooldowns() {
        // Check default cooldowns (5 minutes for most, 3 for chicken)
        assertEquals(TimeUnit.MINUTES.toMillis(5), config.getBreedingCooldown(AnimalType.COW));
        assertEquals(TimeUnit.MINUTES.toMillis(5), config.getBreedingCooldown(AnimalType.PIG));
        assertEquals(TimeUnit.MINUTES.toMillis(3), config.getBreedingCooldown(AnimalType.CHICKEN));
        assertEquals(TimeUnit.MINUTES.toMillis(5), config.getBreedingCooldown(AnimalType.SHEEP));
    }

    @Test
    void testDefaultGestationPeriods() {
        // Check default gestation (2 minutes for most, 30s for chicken)
        assertEquals(TimeUnit.MINUTES.toMillis(2), config.getGestationPeriod(AnimalType.COW));
        assertEquals(TimeUnit.MINUTES.toMillis(2), config.getGestationPeriod(AnimalType.PIG));
        assertEquals(TimeUnit.SECONDS.toMillis(30), config.getGestationPeriod(AnimalType.CHICKEN));
        assertEquals(TimeUnit.MINUTES.toMillis(2), config.getGestationPeriod(AnimalType.SHEEP));
    }

    @Test
    void testDefaultGrowthStageDurations() {
        assertEquals(TimeUnit.MINUTES.toMillis(3), config.getGrowthStageDuration(GrowthStage.BABY));
        assertEquals(TimeUnit.MINUTES.toMillis(3), config.getGrowthStageDuration(GrowthStage.JUVENILE));
        assertEquals(0L, config.getGrowthStageDuration(GrowthStage.ADULT));
    }

    @Test
    void testTimeToReachStage() {
        // Time to reach BABY stage = 0 (start)
        assertEquals(0, config.getTimeToReachStage(GrowthStage.BABY));

        // Time to reach JUVENILE = baby duration
        assertEquals(TimeUnit.MINUTES.toMillis(3), config.getTimeToReachStage(GrowthStage.JUVENILE));

        // Time to reach ADULT = baby + juvenile duration
        assertEquals(TimeUnit.MINUTES.toMillis(6), config.getTimeToReachStage(GrowthStage.ADULT));
    }

    @Test
    void testSetBreedingCooldown() {
        config.setBreedingCooldown(AnimalType.COW, 10.0);
        assertEquals(TimeUnit.MINUTES.toMillis(10), config.getBreedingCooldown(AnimalType.COW));
    }

    @Test
    void testSetGestationPeriod() {
        config.setGestationPeriod(AnimalType.PIG, 5.0);
        assertEquals(TimeUnit.MINUTES.toMillis(5), config.getGestationPeriod(AnimalType.PIG));
    }

    @Test
    void testSetGrowthStageDuration() {
        config.setGrowthStageDuration(GrowthStage.BABY, 10.0);
        assertEquals(TimeUnit.MINUTES.toMillis(10), config.getGrowthStageDuration(GrowthStage.BABY));
    }

    @Test
    void testDebugMode() {
        assertFalse(config.isDebugMode());
        config.setDebugMode(true);
        assertTrue(config.isDebugMode());
    }

    @Test
    void testMessageFormatting() {
        // Messages should convert & to section symbol
        String message = config.getBreedingStartedMessage();
        assertTrue(message.contains("\u00A7a")); // §a (green color)

        String birthMessage = config.getBirthMessage(AnimalType.COW);
        assertTrue(birthMessage.contains("cow"));
        assertTrue(birthMessage.contains("\u00A7b")); // §b (aqua color)
    }

    @Test
    void testGrowthStageMessage() {
        String message = config.getGrowthStageMessage(AnimalType.PIG, GrowthStage.JUVENILE);
        assertTrue(message.contains("pig"));
        assertTrue(message.contains("Juvenile"));
    }
}
