package com.laits.breeding;

import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.models.TamedAnimalData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for TamedAnimalData model.
 *
 * <p>Tests cover construction, property accessors, ownership checks,
 * interaction permissions, and breeding data synchronization.</p>
 */
class TamedAnimalDataTest {

    @Nested
    @DisplayName("Constructors")
    class Constructors {

        @Test
        @DisplayName("default constructor should set sensible defaults")
        void defaultConstructorShouldSetDefaults() {
            TamedAnimalData data = new TamedAnimalData();

            assertThat(data.isAllowInteraction()).isTrue();
            assertThat(data.getGrowthStage()).isEqualTo(GrowthStage.ADULT);
            assertThat(data.getAnimalUuid()).isNull();
            assertThat(data.getOwnerUuid()).isNull();
        }

        @Test
        @DisplayName("parameterized constructor should set all fields")
        void parameterizedConstructorShouldSetFields() {
            UUID animalId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            String name = "Bessie";

            TamedAnimalData data = new TamedAnimalData(animalId, ownerId, name, AnimalType.COW);

            assertThat(data.getAnimalUuid()).isEqualTo(animalId);
            assertThat(data.getOwnerUuid()).isEqualTo(ownerId);
            assertThat(data.getCustomName()).isEqualTo("Bessie");
            assertThat(data.getAnimalType()).isEqualTo(AnimalType.COW);
            assertThat(data.getTamedTime()).isGreaterThan(0);
            assertThat(data.isDespawned()).isFalse();
            assertThat(data.isAllowInteraction()).isTrue();
            assertThat(data.getGrowthStage()).isEqualTo(GrowthStage.ADULT);
            assertThat(data.getWorldId()).isEqualTo("default");
        }
    }

    @Nested
    @DisplayName("Core Identity")
    class CoreIdentity {

        @Test
        @DisplayName("should store and retrieve animal UUID")
        void shouldStoreAnimalUuid() {
            TamedAnimalData data = new TamedAnimalData();
            UUID animalId = UUID.randomUUID();

            data.setAnimalUuid(animalId);

            assertThat(data.getAnimalUuid()).isEqualTo(animalId);
        }

        @Test
        @DisplayName("should store and retrieve owner UUID")
        void shouldStoreOwnerUuid() {
            TamedAnimalData data = new TamedAnimalData();
            UUID ownerId = UUID.randomUUID();

            data.setOwnerUuid(ownerId);

            assertThat(data.getOwnerUuid()).isEqualTo(ownerId);
        }

        @Test
        @DisplayName("should store and retrieve custom name")
        void shouldStoreCustomName() {
            TamedAnimalData data = new TamedAnimalData();

            data.setCustomName("Daisy");

            assertThat(data.getCustomName()).isEqualTo("Daisy");
        }

        @Test
        @DisplayName("should store and retrieve animal type")
        void shouldStoreAnimalType() {
            TamedAnimalData data = new TamedAnimalData();

            data.setAnimalType(AnimalType.SHEEP);

            assertThat(data.getAnimalType()).isEqualTo(AnimalType.SHEEP);
        }
    }

    @Nested
    @DisplayName("Location")
    class Location {

        @Test
        @DisplayName("should store and retrieve position")
        void shouldStorePosition() {
            TamedAnimalData data = new TamedAnimalData();

            data.setLastPosition(100.5, 64.0, -200.25);

            assertThat(data.getLastX()).isEqualTo(100.5);
            assertThat(data.getLastY()).isEqualTo(64.0);
            assertThat(data.getLastZ()).isEqualTo(-200.25);
        }

        @Test
        @DisplayName("should store and retrieve rotation")
        void shouldStoreRotation() {
            TamedAnimalData data = new TamedAnimalData();

            data.setLastRotation(45.5f);

            assertThat(data.getLastRotation()).isEqualTo(45.5f);
        }

        @Test
        @DisplayName("should store and retrieve world ID")
        void shouldStoreWorldId() {
            TamedAnimalData data = new TamedAnimalData();

            data.setWorldId("overworld");

            assertThat(data.getWorldId()).isEqualTo("overworld");
        }
    }

    @Nested
    @DisplayName("Breeding Data")
    class BreedingDataSync {

        @Test
        @DisplayName("should store and retrieve last breed time")
        void shouldStoreLastBreedTime() {
            TamedAnimalData data = new TamedAnimalData();
            long time = System.currentTimeMillis();

            data.setLastBreedTime(time);

            assertThat(data.getLastBreedTime()).isEqualTo(time);
        }

        @Test
        @DisplayName("should store and retrieve birth time")
        void shouldStoreBirthTime() {
            TamedAnimalData data = new TamedAnimalData();
            long time = System.currentTimeMillis();

            data.setBirthTime(time);

            assertThat(data.getBirthTime()).isEqualTo(time);
        }

        @Test
        @DisplayName("should store and retrieve growth stage")
        void shouldStoreGrowthStage() {
            TamedAnimalData data = new TamedAnimalData();

            data.setGrowthStage(GrowthStage.JUVENILE);

            assertThat(data.getGrowthStage()).isEqualTo(GrowthStage.JUVENILE);
        }
    }

    @Nested
    @DisplayName("Taming Metadata")
    class TamingMetadata {

        @Test
        @DisplayName("should store and retrieve tamed time")
        void shouldStoreTamedTime() {
            TamedAnimalData data = new TamedAnimalData();
            long time = System.currentTimeMillis();

            data.setTamedTime(time);

            assertThat(data.getTamedTime()).isEqualTo(time);
        }

        @Test
        @DisplayName("should store and retrieve despawned state")
        void shouldStoreDespawnedState() {
            TamedAnimalData data = new TamedAnimalData();
            assertThat(data.isDespawned()).isFalse();

            data.setDespawned(true);

            assertThat(data.isDespawned()).isTrue();
        }

        @Test
        @DisplayName("should store and retrieve allow interaction flag")
        void shouldStoreAllowInteraction() {
            TamedAnimalData data = new TamedAnimalData();
            assertThat(data.isAllowInteraction()).isTrue();

            data.setAllowInteraction(false);

            assertThat(data.isAllowInteraction()).isFalse();
        }
    }

    @Nested
    @DisplayName("Entity Reference")
    class EntityReference {

        @Test
        @DisplayName("should store and retrieve entity ref")
        void shouldStoreEntityRef() {
            TamedAnimalData data = new TamedAnimalData();
            Object mockRef = new Object();

            data.setEntityRef(mockRef);

            assertThat(data.getEntityRef()).isSameAs(mockRef);
        }

        @Test
        @DisplayName("entity ref should be null by default")
        void entityRefShouldBeNullByDefault() {
            TamedAnimalData data = new TamedAnimalData();

            assertThat(data.getEntityRef()).isNull();
        }
    }

    @Nested
    @DisplayName("isOwnedBy()")
    class IsOwnedBy {

        @Test
        @DisplayName("should return true for owner")
        void shouldReturnTrueForOwner() {
            UUID ownerId = UUID.randomUUID();
            TamedAnimalData data = new TamedAnimalData(
                UUID.randomUUID(), ownerId, "Bessie", AnimalType.COW);

            assertThat(data.isOwnedBy(ownerId)).isTrue();
        }

        @Test
        @DisplayName("should return false for non-owner")
        void shouldReturnFalseForNonOwner() {
            UUID ownerId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            TamedAnimalData data = new TamedAnimalData(
                UUID.randomUUID(), ownerId, "Bessie", AnimalType.COW);

            assertThat(data.isOwnedBy(otherId)).isFalse();
        }

        @Test
        @DisplayName("should return false for null player")
        void shouldReturnFalseForNullPlayer() {
            UUID ownerId = UUID.randomUUID();
            TamedAnimalData data = new TamedAnimalData(
                UUID.randomUUID(), ownerId, "Bessie", AnimalType.COW);

            assertThat(data.isOwnedBy(null)).isFalse();
        }

        @Test
        @DisplayName("should return false when owner is null")
        void shouldReturnFalseWhenOwnerIsNull() {
            TamedAnimalData data = new TamedAnimalData();
            data.setOwnerUuid(null);

            assertThat(data.isOwnedBy(UUID.randomUUID())).isFalse();
        }
    }

    @Nested
    @DisplayName("canInteract()")
    class CanInteract {

        @Test
        @DisplayName("owner should always be able to interact")
        void ownerShouldAlwaysInteract() {
            UUID ownerId = UUID.randomUUID();
            TamedAnimalData data = new TamedAnimalData(
                UUID.randomUUID(), ownerId, "Bessie", AnimalType.COW);
            data.setAllowInteraction(false);

            assertThat(data.canInteract(ownerId)).isTrue();
        }

        @Test
        @DisplayName("non-owner should interact when allowed")
        void nonOwnerShouldInteractWhenAllowed() {
            UUID ownerId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            TamedAnimalData data = new TamedAnimalData(
                UUID.randomUUID(), ownerId, "Bessie", AnimalType.COW);
            data.setAllowInteraction(true);

            assertThat(data.canInteract(otherId)).isTrue();
        }

        @Test
        @DisplayName("non-owner should not interact when disallowed")
        void nonOwnerShouldNotInteractWhenDisallowed() {
            UUID ownerId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            TamedAnimalData data = new TamedAnimalData(
                UUID.randomUUID(), ownerId, "Bessie", AnimalType.COW);
            data.setAllowInteraction(false);

            assertThat(data.canInteract(otherId)).isFalse();
        }
    }

    @Nested
    @DisplayName("copyFromBreedingData()")
    class CopyFromBreedingData {

        @Test
        @DisplayName("should copy breeding state from BreedingData")
        void shouldCopyBreedingState() {
            UUID animalId = UUID.randomUUID();
            BreedingData breedingData = new BreedingData(animalId, AnimalType.COW);
            long breedTime = System.currentTimeMillis() - 60000;
            breedingData.setLastBreedTime(breedTime);

            TamedAnimalData tamedData = new TamedAnimalData();
            tamedData.copyFromBreedingData(breedingData);

            assertThat(tamedData.getLastBreedTime()).isEqualTo(breedTime);
            assertThat(tamedData.getGrowthStage()).isEqualTo(breedingData.getGrowthStage());
        }

        @Test
        @DisplayName("should handle null BreedingData gracefully")
        void shouldHandleNullBreedingData() {
            TamedAnimalData tamedData = new TamedAnimalData();

            assertThatCode(() -> tamedData.copyFromBreedingData(null))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should copy baby growth stage")
        void shouldCopyBabyGrowthStage() {
            UUID babyId = UUID.randomUUID();
            BreedingData babyData = BreedingData.createBaby(babyId, AnimalType.PIG);

            TamedAnimalData tamedData = new TamedAnimalData();
            tamedData.copyFromBreedingData(babyData);

            assertThat(tamedData.getGrowthStage()).isEqualTo(GrowthStage.BABY);
            assertThat(tamedData.getBirthTime()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("applyToBreedingData()")
    class ApplyToBreedingData {

        @Test
        @DisplayName("should apply saved state to BreedingData")
        void shouldApplySavedState() {
            TamedAnimalData tamedData = new TamedAnimalData();
            long breedTime = System.currentTimeMillis() - 120000;
            tamedData.setLastBreedTime(breedTime);
            tamedData.setGrowthStage(GrowthStage.JUVENILE);

            UUID animalId = UUID.randomUUID();
            BreedingData breedingData = new BreedingData(animalId, AnimalType.SHEEP);
            tamedData.applyToBreedingData(breedingData);

            assertThat(breedingData.getLastBreedTime()).isEqualTo(breedTime);
            assertThat(breedingData.getGrowthStage()).isEqualTo(GrowthStage.JUVENILE);
        }

        @Test
        @DisplayName("should handle null BreedingData gracefully")
        void shouldHandleNullBreedingData() {
            TamedAnimalData tamedData = new TamedAnimalData();

            assertThatCode(() -> tamedData.applyToBreedingData(null))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToString {

        @Test
        @DisplayName("should include relevant info")
        void shouldIncludeRelevantInfo() {
            UUID ownerId = UUID.randomUUID();
            TamedAnimalData data = new TamedAnimalData(
                UUID.randomUUID(), ownerId, "Daisy", AnimalType.SHEEP);

            String result = data.toString();

            assertThat(result)
                .contains("Daisy")
                .contains("SHEEP")
                .contains(ownerId.toString())
                .contains("despawned=false");
        }
    }

    @Nested
    @DisplayName("Round-trip scenarios")
    class RoundTripScenarios {

        @Test
        @DisplayName("should preserve breeding state through copy and apply cycle")
        void shouldPreserveBreedingStateThroughCycle() {
            // Original breeding data
            UUID animalId = UUID.randomUUID();
            BreedingData original = new BreedingData(animalId, AnimalType.COW);
            long breedTime = System.currentTimeMillis() - 180000;
            original.setLastBreedTime(breedTime);

            // Copy to tamed data (simulate save)
            TamedAnimalData tamed = new TamedAnimalData(
                animalId, UUID.randomUUID(), "Bessie", AnimalType.COW);
            tamed.copyFromBreedingData(original);

            // Apply to new breeding data (simulate load after respawn)
            UUID newAnimalId = UUID.randomUUID();
            BreedingData restored = new BreedingData(newAnimalId, AnimalType.COW);
            tamed.applyToBreedingData(restored);

            // Verify state preserved
            assertThat(restored.getLastBreedTime()).isEqualTo(breedTime);
            assertThat(restored.getGrowthStage()).isEqualTo(original.getGrowthStage());
        }

        @Test
        @DisplayName("tamed baby should remain baby after respawn")
        void tamedBabyShouldRemainBabyAfterRespawn() {
            // Create baby breeding data
            UUID babyId = UUID.randomUUID();
            BreedingData babyBreeding = BreedingData.createBaby(babyId, AnimalType.CHICKEN);

            // Tame the baby
            TamedAnimalData tamedBaby = new TamedAnimalData(
                babyId, UUID.randomUUID(), "Chicky", AnimalType.CHICKEN);
            tamedBaby.copyFromBreedingData(babyBreeding);

            // Simulate despawn
            tamedBaby.setDespawned(true);

            // Simulate respawn with new UUID
            UUID newBabyId = UUID.randomUUID();
            BreedingData respawnedBreeding = new BreedingData(newBabyId, AnimalType.CHICKEN);
            tamedBaby.applyToBreedingData(respawnedBreeding);
            tamedBaby.setAnimalUuid(newBabyId);
            tamedBaby.setDespawned(false);

            // Baby should still be baby
            assertThat(respawnedBreeding.getGrowthStage()).isEqualTo(GrowthStage.BABY);
            assertThat(tamedBaby.getAnimalUuid()).isEqualTo(newBabyId);
            assertThat(tamedBaby.isDespawned()).isFalse();
        }
    }
}
