package com.laits.breeding;

import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.util.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ConfigManager.
 */
class ConfigManagerTest {

    private ConfigManager config;

    @BeforeEach
    void setUp() {
        config = new ConfigManager();
    }

    @Nested
    @DisplayName("Default timing values")
    class DefaultTimingValues {

        @Test
        @DisplayName("should have default breeding cooldowns")
        void testDefaultBreedingCooldowns() {
            // Default cooldown is 5 minutes for all animals
            long expectedCooldown = TimeUnit.MINUTES.toMillis(5);
            assertThat(config.getBreedingCooldown(AnimalType.COW)).isEqualTo(expectedCooldown);
            assertThat(config.getBreedingCooldown(AnimalType.PIG)).isEqualTo(expectedCooldown);
            assertThat(config.getBreedingCooldown(AnimalType.CHICKEN)).isEqualTo(expectedCooldown);
            assertThat(config.getBreedingCooldown(AnimalType.SHEEP)).isEqualTo(expectedCooldown);
        }

        @Test
        @DisplayName("should have instant gestation (0)")
        void testDefaultGestationPeriods() {
            // Default gestation is 0 (instant)
            assertThat(config.getGestationPeriod(AnimalType.COW)).isEqualTo(0L);
            assertThat(config.getGestationPeriod(AnimalType.PIG)).isEqualTo(0L);
            assertThat(config.getGestationPeriod(AnimalType.CHICKEN)).isEqualTo(0L);
            assertThat(config.getGestationPeriod(AnimalType.SHEEP)).isEqualTo(0L);
        }

        @Test
        @DisplayName("should have default growth stage durations")
        void testDefaultGrowthStageDurations() {
            // Default growth time is 30 minutes total, divided by 2 stages = 15 min each
            long expectedStageDuration = TimeUnit.MINUTES.toMillis(15);
            assertThat(config.getGrowthStageDuration(GrowthStage.BABY)).isEqualTo(expectedStageDuration);
            assertThat(config.getGrowthStageDuration(GrowthStage.JUVENILE)).isEqualTo(expectedStageDuration);
            assertThat(config.getGrowthStageDuration(GrowthStage.ADULT)).isEqualTo(0L);
        }

        @Test
        @DisplayName("should have default growth time of 30 minutes")
        void testDefaultGrowthTime() {
            // Default growth time is 30 minutes
            long expectedGrowthTime = TimeUnit.MINUTES.toMillis(30);
            assertThat(config.getGrowthTime(AnimalType.COW)).isEqualTo(expectedGrowthTime);
            assertThat(config.getGrowthTime(AnimalType.PIG)).isEqualTo(expectedGrowthTime);
        }
    }

    @Nested
    @DisplayName("Setters")
    class Setters {

        @Test
        @DisplayName("should allow changing breeding cooldown")
        void testSetBreedingCooldown() {
            config.setBreedingCooldown(AnimalType.COW, 10.0);

            assertThat(config.getBreedingCooldown(AnimalType.COW))
                .isEqualTo(TimeUnit.MINUTES.toMillis(10));
        }

        @Test
        @DisplayName("should allow changing growth time")
        void testSetGrowthTime() {
            config.setGrowthTime(AnimalType.PIG, 8.0);

            assertThat(config.getGrowthTime(AnimalType.PIG))
                .isEqualTo(TimeUnit.MINUTES.toMillis(8));
        }
    }

    @Nested
    @DisplayName("Debug mode")
    class DebugMode {

        @Test
        @DisplayName("should be disabled by default")
        void testDefaultDebugMode() {
            assertThat(config.isDebugMode()).isFalse();
        }

        @Test
        @DisplayName("should be toggleable")
        void testSetDebugMode() {
            config.setDebugMode(true);
            assertThat(config.isDebugMode()).isTrue();

            config.setDebugMode(false);
            assertThat(config.isDebugMode()).isFalse();
        }
    }

    @Nested
    @DisplayName("Messages")
    class Messages {

        @Test
        @DisplayName("should format breeding started message")
        void testBreedingStartedMessage() {
            String message = config.getBreedingStartedMessage();

            assertThat(message).isNotNull();
            // Message should contain color codes (§ character)
            assertThat(message).contains("\u00A7");
        }

        @Test
        @DisplayName("should format birth message with animal type")
        void testBirthMessage() {
            String message = config.getBirthMessage(AnimalType.COW);

            assertThat(message).isNotNull();
            assertThat(message.toLowerCase()).contains("cow");
        }

        @Test
        @DisplayName("should format growth stage message")
        void testGrowthStageMessage() {
            String message = config.getGrowthStageMessage(AnimalType.PIG, GrowthStage.JUVENILE);

            assertThat(message).isNotNull();
            assertThat(message.toLowerCase()).contains("pig");
            assertThat(message).containsIgnoringCase("juvenile");
        }
    }

    @Nested
    @DisplayName("Animal enable/disable")
    class AnimalEnableDisable {

        @Test
        @DisplayName("livestock should be enabled by default")
        void testLivestockEnabledByDefault() {
            // Livestock animals should be enabled
            assertThat(config.isAnimalEnabled(AnimalType.COW)).isTrue();
            assertThat(config.isAnimalEnabled(AnimalType.PIG)).isTrue();
            assertThat(config.isAnimalEnabled(AnimalType.SHEEP)).isTrue();
        }

        @Test
        @DisplayName("should allow disabling animals")
        void testDisableAnimal() {
            config.setAnimalEnabled(AnimalType.COW, false);

            assertThat(config.isAnimalEnabled(AnimalType.COW)).isFalse();
        }

        @Test
        @DisplayName("should allow re-enabling animals")
        void testReEnableAnimal() {
            config.setAnimalEnabled(AnimalType.COW, false);
            config.setAnimalEnabled(AnimalType.COW, true);

            assertThat(config.isAnimalEnabled(AnimalType.COW)).isTrue();
        }
    }

    @Nested
    @DisplayName("Breeding foods")
    class BreedingFoods {

        @Test
        @DisplayName("should return breeding food for animal")
        void testGetBreedingFood() {
            String food = config.getBreedingFood(AnimalType.COW);

            assertThat(food).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should check if item is breeding food")
        void testIsBreedingFood() {
            // Get the actual food for cow
            String cowFood = config.getBreedingFood(AnimalType.COW);

            assertThat(config.isBreedingFood(AnimalType.COW, cowFood)).isTrue();
            assertThat(config.isBreedingFood(AnimalType.COW, "Dirt_Block")).isFalse();
        }

        @Test
        @DisplayName("should allow setting breeding food")
        void testSetBreedingFood() {
            config.setBreedingFood(AnimalType.COW, "Corn");

            assertThat(config.isBreedingFood(AnimalType.COW, "Corn")).isTrue();
        }

        @Test
        @DisplayName("should allow adding breeding food")
        void testAddBreedingFood() {
            String originalFood = config.getBreedingFood(AnimalType.COW);
            config.addBreedingFood(AnimalType.COW, "Extra_Food");

            assertThat(config.isBreedingFood(AnimalType.COW, originalFood)).isTrue();
            assertThat(config.isBreedingFood(AnimalType.COW, "Extra_Food")).isTrue();
        }
    }

    @Nested
    @DisplayName("Growth system")
    class GrowthSystem {

        @Test
        @DisplayName("should be enabled by default")
        void testGrowthEnabledByDefault() {
            assertThat(config.isGrowthEnabled()).isTrue();
        }

        @Test
        @DisplayName("should allow disabling growth")
        void testDisableGrowth() {
            config.setGrowthEnabled(false);

            assertThat(config.isGrowthEnabled()).isFalse();
        }
    }
}
