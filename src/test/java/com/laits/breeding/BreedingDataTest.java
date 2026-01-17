package com.laits.breeding;

import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.GrowthStage;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BreedingData model.
 */
class BreedingDataTest {

    @Test
    void testNewAdultAnimalCanBreed() {
        UUID animalId = UUID.randomUUID();
        BreedingData data = new BreedingData(animalId, AnimalType.COW);

        // New adult should be able to breed (no cooldown)
        assertTrue(data.canBreed(TimeUnit.MINUTES.toMillis(5)));
        assertEquals(GrowthStage.ADULT, data.getGrowthStage());
        assertFalse(data.isPregnant());
        assertFalse(data.isInLove());
    }

    @Test
    void testBabyCannotBreed() {
        UUID babyId = UUID.randomUUID();
        BreedingData baby = BreedingData.createBaby(babyId, AnimalType.PIG);

        // Baby should not be able to breed
        assertFalse(baby.canBreed(TimeUnit.MINUTES.toMillis(5)));
        assertEquals(GrowthStage.BABY, baby.getGrowthStage());
        assertTrue(baby.getBirthTime() > 0);
    }

    @Test
    void testCooldownPreventsBreeding() {
        UUID animalId = UUID.randomUUID();
        BreedingData data = new BreedingData(animalId, AnimalType.SHEEP);

        // Set last breed time to now
        data.setLastBreedTime(System.currentTimeMillis());

        // Should not be able to breed (on cooldown)
        long cooldown = TimeUnit.MINUTES.toMillis(5);
        assertFalse(data.canBreed(cooldown));
        assertTrue(data.getCooldownRemaining(cooldown) > 0);
    }

    @Test
    void testPregnancyPreventsBreeding() {
        UUID animalId = UUID.randomUUID();
        BreedingData data = new BreedingData(animalId, AnimalType.CHICKEN);

        // Make pregnant
        data.setPregnant(true);

        // Should not be able to breed while pregnant
        assertFalse(data.canBreed(TimeUnit.MINUTES.toMillis(5)));
        assertTrue(data.isPregnant());
        assertTrue(data.getPregnancyStartTime() > 0);
    }

    @Test
    void testGestationCompletion() {
        UUID animalId = UUID.randomUUID();
        BreedingData data = new BreedingData(animalId, AnimalType.COW);

        // Not pregnant - should not be ready
        assertFalse(data.isReadyToGiveBirth(TimeUnit.MINUTES.toMillis(2)));

        // Make pregnant
        data.setPregnant(true);

        // With 0 gestation time, should be ready immediately
        assertTrue(data.isReadyToGiveBirth(0));

        // With long gestation, should not be ready
        assertFalse(data.isReadyToGiveBirth(TimeUnit.HOURS.toMillis(1)));
    }

    @Test
    void testLoveState() {
        UUID animalId = UUID.randomUUID();
        BreedingData data = new BreedingData(animalId, AnimalType.PIG);

        assertFalse(data.isInLove());

        data.setInLove(true);
        assertTrue(data.isInLove());
        assertTrue(data.getLoveStartTime() > 0);

        data.resetLove();
        assertFalse(data.isInLove());
        assertEquals(0, data.getLoveStartTime());
    }

    @Test
    void testCompleteBreeding() {
        UUID animalId = UUID.randomUUID();
        BreedingData data = new BreedingData(animalId, AnimalType.SHEEP);

        data.setInLove(true);
        data.setPregnant(true);

        data.completeBreeding();

        assertFalse(data.isInLove());
        assertFalse(data.isPregnant());
        assertTrue(data.getLastBreedTime() > 0);
    }

    @Test
    void testBabyAge() {
        UUID babyId = UUID.randomUUID();
        BreedingData baby = BreedingData.createBaby(babyId, AnimalType.CHICKEN);

        // Baby just created should have very small age
        long age = baby.getAge();
        assertTrue(age >= 0);
        assertTrue(age < 1000); // Less than 1 second
    }
}
