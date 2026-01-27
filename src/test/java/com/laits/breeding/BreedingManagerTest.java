package com.laits.breeding;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.managers.BreedingManager;
import com.laits.breeding.managers.BreedingManager.BirthEvent;
import com.laits.breeding.managers.BreedingManager.CustomAnimalLoveData;
import com.laits.breeding.managers.BreedingManager.CustomBirthEvent;
import com.laits.breeding.managers.BreedingManager.FeedResult;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.util.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for BreedingManager.
 *
 * <p>Tests cover all feeding outcomes, breeding logic, pregnancy/birth cycle,
 * baby registration, and custom animal breeding.</p>
 */
class BreedingManagerTest {

    private ConfigManager config;
    private BreedingManager manager;
    private List<String> debugLogs;

    @BeforeEach
    void setUp() {
        config = new ConfigManager();
        manager = new BreedingManager(config);
        debugLogs = new ArrayList<>();

        // Enable debug mode for testing
        config.setDebugMode(true);
        manager.setDebugLogger(debugLogs::add);
    }

    @Nested
    @DisplayName("getOrCreateData()")
    class GetOrCreateData {

        @Test
        @DisplayName("should create new data for unknown animal")
        void shouldCreateNewData() {
            UUID cowId = UUID.randomUUID();

            BreedingData data = manager.getOrCreateData(cowId, AnimalType.COW);

            assertThat(data).isNotNull();
            assertThat(data.getAnimalId()).isEqualTo(cowId);
            assertThat(data.getAnimalType()).isEqualTo(AnimalType.COW);
            assertThat(manager.getTrackedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return existing data for known animal")
        void shouldReturnExistingData() {
            UUID cowId = UUID.randomUUID();
            BreedingData first = manager.getOrCreateData(cowId, AnimalType.COW);
            first.setInLove(true);

            BreedingData second = manager.getOrCreateData(cowId, AnimalType.COW);

            assertThat(second).isSameAs(first);
            assertThat(second.isInLove()).isTrue();
        }

        @Test
        @DisplayName("should track multiple animals separately")
        void shouldTrackMultipleAnimals() {
            UUID cow1 = UUID.randomUUID();
            UUID cow2 = UUID.randomUUID();
            UUID sheep = UUID.randomUUID();

            manager.getOrCreateData(cow1, AnimalType.COW);
            manager.getOrCreateData(cow2, AnimalType.COW);
            manager.getOrCreateData(sheep, AnimalType.SHEEP);

            assertThat(manager.getTrackedCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("tryFeed() - FeedResult outcomes")
    class TryFeed {

        @Test
        @DisplayName("should return SUCCESS when feeding correct food to adult")
        void shouldSucceedWithCorrectFood() {
            UUID cowId = UUID.randomUUID();
            String cowFood = config.getBreedingFood(AnimalType.COW);

            FeedResult result = manager.tryFeed(cowId, AnimalType.COW, cowFood);

            assertThat(result).isEqualTo(FeedResult.SUCCESS);
            assertThat(manager.getData(cowId).isInLove()).isTrue();
        }

        @Test
        @DisplayName("should return WRONG_FOOD for incorrect food type")
        void shouldRejectWrongFood() {
            UUID cowId = UUID.randomUUID();

            // Feed wrong food that's definitely not on any list
            FeedResult result = manager.tryFeed(cowId, AnimalType.COW, "Dirt_Block");

            assertThat(result).isEqualTo(FeedResult.WRONG_FOOD);
            assertThat(manager.getData(cowId)).isNull(); // Not tracked if wrong food
        }

        @Test
        @DisplayName("should return NOT_ADULT for baby animal")
        void shouldRejectBabyAnimal() {
            UUID babyId = UUID.randomUUID();
            String cowFood = config.getBreedingFood(AnimalType.COW);
            manager.registerBaby(babyId, AnimalType.COW, null);

            FeedResult result = manager.tryFeed(babyId, AnimalType.COW, cowFood);

            assertThat(result).isEqualTo(FeedResult.NOT_ADULT);
        }

        @Test
        @DisplayName("should return ON_COOLDOWN when recently bred")
        void shouldRejectOnCooldown() {
            UUID cowId = UUID.randomUUID();
            String cowFood = config.getBreedingFood(AnimalType.COW);
            BreedingData data = manager.getOrCreateData(cowId, AnimalType.COW);
            data.setLastBreedTime(System.currentTimeMillis()); // Just bred

            FeedResult result = manager.tryFeed(cowId, AnimalType.COW, cowFood);

            assertThat(result).isEqualTo(FeedResult.ON_COOLDOWN);
        }

        @Test
        @DisplayName("should return ALREADY_IN_LOVE when already in love mode")
        void shouldRejectAlreadyInLove() {
            UUID cowId = UUID.randomUUID();
            String cowFood = config.getBreedingFood(AnimalType.COW);
            manager.tryFeed(cowId, AnimalType.COW, cowFood); // First feed

            FeedResult result = manager.tryFeed(cowId, AnimalType.COW, cowFood); // Second feed

            assertThat(result).isEqualTo(FeedResult.ALREADY_IN_LOVE);
        }

        @Test
        @DisplayName("should return DISABLED when animal type is disabled")
        void shouldRejectDisabledAnimalType() {
            UUID cowId = UUID.randomUUID();
            String cowFood = config.getBreedingFood(AnimalType.COW);
            config.setAnimalEnabled(AnimalType.COW, false);

            FeedResult result = manager.tryFeed(cowId, AnimalType.COW, cowFood);

            assertThat(result).isEqualTo(FeedResult.DISABLED);
        }

        @Test
        @DisplayName("should accept different foods for different animals")
        void shouldAcceptDifferentFoods() {
            UUID cowId = UUID.randomUUID();
            UUID pigId = UUID.randomUUID();
            UUID chickenId = UUID.randomUUID();

            // Each animal has their preferred food from config
            String cowFood = config.getBreedingFood(AnimalType.COW);
            String pigFood = config.getBreedingFood(AnimalType.PIG);
            String chickenFood = config.getBreedingFood(AnimalType.CHICKEN);

            assertThat(manager.tryFeed(cowId, AnimalType.COW, cowFood)).isEqualTo(FeedResult.SUCCESS);
            assertThat(manager.tryFeed(pigId, AnimalType.PIG, pigFood)).isEqualTo(FeedResult.SUCCESS);
            assertThat(manager.tryFeed(chickenId, AnimalType.CHICKEN, chickenFood)).isEqualTo(FeedResult.SUCCESS);
        }
    }

    @Nested
    @DisplayName("tryBreed()")
    class TryBreed {

        @Test
        @DisplayName("should succeed when both animals are in love")
        void shouldSucceedWhenBothInLove() {
            UUID cow1 = UUID.randomUUID();
            UUID cow2 = UUID.randomUUID();

            manager.tryFeed(cow1, AnimalType.COW, config.getBreedingFood(AnimalType.COW));
            manager.tryFeed(cow2, AnimalType.COW, config.getBreedingFood(AnimalType.COW));

            boolean success = manager.tryBreed(cow1, cow2, AnimalType.COW);

            assertThat(success).isTrue();
            assertThat(manager.getData(cow1).isPregnant()).isTrue();
            assertThat(manager.getData(cow1).isInLove()).isFalse();
            assertThat(manager.getData(cow2).isInLove()).isFalse();
        }

        @Test
        @DisplayName("should fail when one animal is not in love")
        void shouldFailWhenOneNotInLove() {
            UUID cow1 = UUID.randomUUID();
            UUID cow2 = UUID.randomUUID();

            manager.tryFeed(cow1, AnimalType.COW, config.getBreedingFood(AnimalType.COW));
            manager.getOrCreateData(cow2, AnimalType.COW); // Not in love

            boolean success = manager.tryBreed(cow1, cow2, AnimalType.COW);

            assertThat(success).isFalse();
        }

        @Test
        @DisplayName("should fail when animal is already pregnant")
        void shouldFailWhenPregnant() {
            UUID cow1 = UUID.randomUUID();
            UUID cow2 = UUID.randomUUID();

            manager.tryFeed(cow1, AnimalType.COW, config.getBreedingFood(AnimalType.COW));
            manager.tryFeed(cow2, AnimalType.COW, config.getBreedingFood(AnimalType.COW));
            manager.getData(cow1).setPregnant(true);

            boolean success = manager.tryBreed(cow1, cow2, AnimalType.COW);

            assertThat(success).isFalse();
        }

        @Test
        @DisplayName("should fail when animal is not tracked")
        void shouldFailWhenNotTracked() {
            UUID cow1 = UUID.randomUUID();
            UUID cow2 = UUID.randomUUID();

            // Neither animal is tracked
            boolean success = manager.tryBreed(cow1, cow2, AnimalType.COW);

            assertThat(success).isFalse();
        }
    }

    @Nested
    @DisplayName("forceBreed()")
    class ForceBreed {

        @Test
        @DisplayName("should bypass all checks and make animal pregnant")
        void shouldBypassChecks() {
            UUID cow1 = UUID.randomUUID();
            UUID cow2 = UUID.randomUUID();

            // Neither in love, but force breed should work
            boolean success = manager.forceBreed(cow1, cow2, AnimalType.COW);

            assertThat(success).isTrue();
            assertThat(manager.getData(cow1).isPregnant()).isTrue();
        }
    }

    @Nested
    @DisplayName("registerBaby()")
    class RegisterBaby {

        @Test
        @DisplayName("should register baby with correct growth stage")
        void shouldRegisterBaby() {
            UUID babyId = UUID.randomUUID();

            BreedingData data = manager.registerBaby(babyId, AnimalType.COW, null);

            assertThat(data.getAnimalId()).isEqualTo(babyId);
            assertThat(data.getAnimalType()).isEqualTo(AnimalType.COW);
            assertThat(data.getGrowthStage().canBreed()).isFalse();
            assertThat(data.getBirthTime()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should store entity ref for baby")
        void shouldStoreEntityRef() {
            UUID babyId = UUID.randomUUID();
            Ref<EntityStore> mockEntityRef = new Ref<EntityStore>(null);

            BreedingData data = manager.registerBaby(babyId, AnimalType.PIG, mockEntityRef);

            assertThat(data.getEntityRef()).isSameAs(mockEntityRef);
        }
    }

    @Nested
    @DisplayName("tickPregnancies()")
    class TickPregnancies {

        @Test
        @DisplayName("should trigger birth when gestation complete")
        void shouldTriggerBirthWhenGestationComplete() {
            UUID cowId = UUID.randomUUID();
            List<BirthEvent> births = new ArrayList<>();
            manager.setOnBirthCallback(births::add);

            // Make cow pregnant with past pregnancy time
            BreedingData data = manager.getOrCreateData(cowId, AnimalType.COW);
            data.setPregnant(true);

            // Simulate past pregnancy start time using reflection
            long gestationTime = config.getGestationPeriod(AnimalType.COW);
            try {
                var field = BreedingData.class.getDeclaredField("pregnancyStartTime");
                field.setAccessible(true);
                field.set(data, System.currentTimeMillis() - gestationTime - 1000);
            } catch (Exception e) {
                fail("Could not set pregnancy start time: " + e);
            }

            manager.tickPregnancies();

            assertThat(births).hasSize(1);
            assertThat(births.get(0).getMotherId()).isEqualTo(cowId);
            assertThat(births.get(0).getAnimalType()).isEqualTo(AnimalType.COW);
            assertThat(data.isPregnant()).isFalse();
        }

        @Test
        @DisplayName("should not process non-pregnant animals")
        void shouldNotProcessNonPregnant() {
            UUID cowId = UUID.randomUUID();
            List<BirthEvent> births = new ArrayList<>();
            manager.setOnBirthCallback(births::add);

            // Create a non-pregnant cow
            BreedingData data = manager.getOrCreateData(cowId, AnimalType.COW);
            // Not calling setPregnant(true)

            manager.tickPregnancies();

            assertThat(births).isEmpty();
            assertThat(data.isPregnant()).isFalse();
        }
    }

    @Nested
    @DisplayName("Custom animal breeding")
    class CustomAnimalBreeding {

        @Test
        @DisplayName("should put custom animal in love mode")
        void shouldPutCustomAnimalInLove() {
            UUID animalId = UUID.randomUUID();

            FeedResult result = manager.tryFeedCustomAnimal(animalId, "Wolf", null);

            assertThat(result).isEqualTo(FeedResult.SUCCESS);
            assertThat(manager.isCustomAnimalInLove(animalId)).isTrue();
        }

        @Test
        @DisplayName("should reject already in love custom animal")
        void shouldRejectAlreadyInLoveCustomAnimal() {
            UUID animalId = UUID.randomUUID();
            manager.tryFeedCustomAnimal(animalId, "Wolf", null);

            FeedResult result = manager.tryFeedCustomAnimal(animalId, "Wolf", null);

            assertThat(result).isEqualTo(FeedResult.ALREADY_IN_LOVE);
        }

        @Test
        @DisplayName("should breed two custom animals of same type")
        void shouldBreedCustomAnimals() {
            UUID wolf1 = UUID.randomUUID();
            UUID wolf2 = UUID.randomUUID();
            List<CustomBirthEvent> births = new ArrayList<>();
            manager.setOnCustomBirthCallback(births::add);

            manager.tryFeedCustomAnimal(wolf1, "Wolf", null);
            manager.tryFeedCustomAnimal(wolf2, "Wolf", null);

            boolean success = manager.tryBreedCustomAnimals(wolf1, wolf2, "Wolf");

            assertThat(success).isTrue();
            assertThat(births).hasSize(1);
            assertThat(births.get(0).getModelAssetId()).isEqualTo("Wolf");
        }

        @Test
        @DisplayName("should not breed custom animals of different types")
        void shouldNotBreedDifferentTypes() {
            UUID wolf = UUID.randomUUID();
            UUID fox = UUID.randomUUID();

            manager.tryFeedCustomAnimal(wolf, "Wolf", null);
            manager.tryFeedCustomAnimal(fox, "Fox", null);

            boolean success = manager.tryBreedCustomAnimals(wolf, fox, "Wolf");

            assertThat(success).isFalse();
        }

        @Test
        @DisplayName("should expire love mode after timeout")
        void shouldExpireLoveMode() {
            UUID animalId = UUID.randomUUID();
            manager.tryFeedCustomAnimal(animalId, "Wolf", null);

            // Manually set love start time to past
            CustomAnimalLoveData data = manager.getCustomAnimalLoveData(animalId);
            // Simulate 31 seconds ago (love expires after 30s)
            long pastTime = System.currentTimeMillis() - 31_000;
            // Using reflection to set private field for testing
            try {
                var field = CustomAnimalLoveData.class.getDeclaredField("loveStartTime");
                field.setAccessible(true);
                field.set(data, pastTime);
            } catch (Exception e) {
                fail("Could not set love start time: " + e);
            }

            manager.tickCustomAnimalLove();

            assertThat(data.isInLove()).isFalse();
        }
    }

    @Nested
    @DisplayName("clearAll()")
    class ClearAll {

        @Test
        @DisplayName("should remove all tracked animals")
        void shouldClearAllAnimals() {
            manager.getOrCreateData(UUID.randomUUID(), AnimalType.COW);
            manager.getOrCreateData(UUID.randomUUID(), AnimalType.PIG);
            manager.tryFeedCustomAnimal(UUID.randomUUID(), "Wolf", null);

            manager.clearAll();

            assertThat(manager.getTrackedCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Statistics")
    class Statistics {

        @Test
        @DisplayName("should count pregnant animals correctly")
        void shouldCountPregnant() {
            UUID cow1 = UUID.randomUUID();
            UUID cow2 = UUID.randomUUID();
            UUID cow3 = UUID.randomUUID();

            manager.getOrCreateData(cow1, AnimalType.COW).setPregnant(true);
            manager.getOrCreateData(cow2, AnimalType.COW).setPregnant(true);
            manager.getOrCreateData(cow3, AnimalType.COW);

            assertThat(manager.getPregnantCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should count animals in love correctly")
        void shouldCountInLove() {
            UUID cow1 = UUID.randomUUID();
            UUID cow2 = UUID.randomUUID();
            UUID cow3 = UUID.randomUUID();

            manager.tryFeed(cow1, AnimalType.COW, config.getBreedingFood(AnimalType.COW));
            manager.tryFeed(cow2, AnimalType.COW, config.getBreedingFood(AnimalType.COW));
            manager.getOrCreateData(cow3, AnimalType.COW);

            assertThat(manager.getInLoveCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("removeData()")
    class RemoveData {

        @Test
        @DisplayName("should remove tracked animal")
        void shouldRemoveAnimal() {
            UUID cowId = UUID.randomUUID();
            manager.getOrCreateData(cowId, AnimalType.COW);
            assertThat(manager.getTrackedCount()).isEqualTo(1);

            manager.removeData(cowId);

            assertThat(manager.getTrackedCount()).isEqualTo(0);
            assertThat(manager.getData(cowId)).isNull();
        }
    }

    @Nested
    @DisplayName("canBreed()")
    class CanBreed {

        @Test
        @DisplayName("should return true for adult not on cooldown")
        void shouldAllowAdultToBreed() {
            UUID cowId = UUID.randomUUID();

            boolean canBreed = manager.canBreed(cowId, AnimalType.COW);

            assertThat(canBreed).isTrue();
        }

        @Test
        @DisplayName("should return false for animal on cooldown")
        void shouldRejectOnCooldown() {
            UUID cowId = UUID.randomUUID();
            BreedingData data = manager.getOrCreateData(cowId, AnimalType.COW);
            data.setLastBreedTime(System.currentTimeMillis());

            boolean canBreed = manager.canBreed(cowId, AnimalType.COW);

            assertThat(canBreed).isFalse();
        }
    }
}
